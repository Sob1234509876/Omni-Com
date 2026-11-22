# OSC-Server 缺陷交接（web-client AI → osc-server AI）

> 来源：web-client AI 为对接 osc-server 2.1a（fc6f37b 之后的 OSC 提交）做的双子智能体契约审计（控制器/实体/配置全文 + 关键 Spring jar 字节码验证）+ 运行时探针复现（raw WS 客户端 + 自带日志实例的完整异常栈）。
> 优先级排序；①②⑤阻塞客户端实时功能，③④为改善项。修完请在本文件末尾"处理记录"补记（沿用 Engine-Fix-Requests.md 惯例）。

## ⑤ WS 会话拿不到 principal：每个连接建立即 1011 关闭（实时事件整体不可用）

- **现象**：任何客户端连 `/private/event-bus?token=<有效JWT>`，握手 101 成功后约 1 秒被服务端以 **1011** 关闭；事件永远收不到。浏览器侧表现为 onerror+onclose(1006) 循环。
- **根因**（运行时异常栈，自带日志实例复现）：`OscServerEventBusService.afterConnectionEstablished → userId()(:83)` 抛 `RuntimeException: WebSocket session has no OSC user principal`——`WebSocketSession.getPrincipal()` 为 null。JwtFilter 在握手 GET 上正确设置了 SecurityContext（`authenticated()` 通过、握手 101 成功），但 Tomcat 的 WS 升级（`WsHttpUpgradeHandler.init`）捕获的 principal 不来自 Spring Security 包装后的 request——Spring Security 手动 `SecurityContextHolder.setAuthentication` 不会传导到 Tomcat 原生 upgrade 的 principal。这是 Spring Security + 原生 WebSocket 的已知坑。
- **修复**（推荐 a）：注册 `HandshakeInterceptor`，在 `beforeHandshake` 里从 `SecurityContextHolder` 取认证主体存入 `attributes`（如 `attributes.put("oscUser", chatUser)`），EventBus 改从 `session.getAttributes().get("oscUser")` 取用户；`afterConnectionClosed` 同样改读 attributes（当前它也会抛同样的异常，见日志第二段）。
  ```java
  public class OscServerUserHandshakeInterceptor implements HandshakeInterceptor {
      public boolean beforeHandshake(request, response, wsHandler, attributes) {
          var auth = SecurityContextHolder.getContext().getAuthentication();
          if (auth != null && auth.getPrincipal() instanceof OscApiChatUser user)
              attributes.put("oscUser", user);
          return true;
      }
  }
  // registry.addHandler(handler, PATH).addInterceptors(interceptor).setAllowedOrigins("*");
  ```
- **验证法**：raw WS 客户端连上后保持 5 秒不被 1011 关闭即修复。

## ① `/private/upload-file` 当前必返回 500（文件消息链路不可用）

- **现象**：任意 multipart 上传 500，无 description 头（Spring `/error` JSON）。
- **根因**：`OscServerCreateController` 用 `@ModelAttribute("file") MultipartFile`——multipart 文件不是"请求参数"，`ServletModelAttributeMethodProcessor` 的 `createAttribute` 返回 null 后走 `DataBinder.construct` → `BeanUtils.getResolvableConstructor(MultipartFile.class)`（接口无构造器）→ `IllegalStateException`。已对项目锁定的 spring-web/beans 6.2.11 字节码逐步验证。
- **修复**：改 `@RequestParam("file") MultipartFile`（osa-server 的 avatar 上传就是这么写的，可直接照抄）。

## ② EventBus 事件推送序列化必炸（并连累触发请求 500）

- **现象**：只要**接收方 WS 在线**，`/private/upload-message`、accept/reject-request、update-room、update-user、mute、give-nickname、remove-user 都在**持久化成功后**返回 500，且事件永远不会送达。接收方离线时 push 提前 return，一切正常——这就是服务端此前自测全绿的原因（当时无 WS 客户端在线）。已用 raw WS 客户端运行时复现路径确认。
- **根因**：`OscServerEventBusService:28` 用裸 `new ObjectMapper()`，未注册 `jackson-datatype-jsr310`；Jackson 2.19.2 对 `java.time.Instant` 直接抛 `InvalidDefinitionException`，`push()` 捕获后重抛 RuntimeException（:58-63）。消息/请求/room/roomUser 实体全部含 Instant。
- **修复**（任选其一）：
  1. 注入 Spring 的 ObjectMapper（Boot 自动配好 JSR-310，与 REST 响应的时间格式也一致——推荐）；
  2. 或 `new ObjectMapper().findAndRegisterModules()`；或 `registerModule(new JavaTimeModule())`。
- **注意**：修复后裸 mapper 若保留，`WRITE_DATES_AS_TIMESTAMPS` 默认 true，事件里时间会是 epoch 数字而 REST 是 ISO 字符串——注入 Spring mapper 则两端一致，客户端按 ISO 解析。

## ③ `@ModelAttribute` 必填参数缺失回 500 而非 400（改善项）

所有 `@ModelAttribute` 简单参数（id/roomId/userId/chatId/requestId/minutes/name/type/title 等）缺失或空白时走同一构造路径 500（`/error` JSON，无 description 头）；传非法值才是 400。客户端已对策（必填参数永远拼全），但服务端若想统一错误语义可改 `@RequestParam(required=...)`。

## ④ CORS 未暴露 `description` 响应头（改善项）

`SecurityConfiguration` 的 CorsConfiguration 未设 `Access-Control-Expose-Headers: description`——跨域部署的浏览器客户端读不到业务错误文案（错误形状=状态码+description 头+空 body，文案全在这个头里）。同源部署不受影响；加一行 exposeHeaders 即可让跨域客户端也拿到。

---

## web-client 侧已做的兜底（修复后自动受益，无需客户端改动）

- 发消息遇 500：自动 GET `/public/message` 核对该消息是否已入库（bug ②期间消息实际已送达），命中则按成功处理并弹黄色警告 toast 说明原因。
- 错误 toast：先读 `description` 头，缺省再解析 `/error` JSON 的 `error` 字段，再缺省本地化文案。
- WS 断线 3 秒自动重连；离线期间的丢事件靠进入会话时 REST 全量/游标补拉兜底。

## ⑥ 功能缺口：房间成员列表端点（新需求，非 bug）——已由 web-client AI 实施在工作树

禁言/踢人/给称号的客户端 UI 需要"某房间的成员列表"，现契约只有单查（`GET /public/room-user?roomId=&userId=`），没有列举端点——客户端无法枚举成员。请求新增：

- `GET /public/room-users?roomId=<id>` → `OscApiRoomUser[]`（DAO 侧 `findByRoomId` 已存在，只差 controller/service 暴露）。

**2026-11-22 实施**：因业主直接指令群成员页需求，web-client AI 已自行在工作树实施（未提交）——`OscServerReadController.getRoomUsers` + `OscServerReadService.getRoomUsers`（room 存在性校验 → 404 `Room not found`，成员列表走 `roomUserDao.findByRoomId`），真机 :8083 实测 200 返回双成员。osc-server AI 后续提交时请一并纳入（或按自家风格改写后纳入）。

## ⑦ avatarFileId 删除（业主决策：OSC 用户名/头像直接用 OSA 的）

OSC 用户头像回归 OSA（客户端经 OSA `/public/avatar?id=` 展示），`avatarFileId` 概念冗余。已由 web-client AI 在工作树实施（未提交）：

- `osc-api OscApiChatUser`：删 `avatarFileId` 字段（Mongo 旧文档多出的字段会被忽略，无需迁移）。
- `osc-server UpdateController/UpdateService`：`update-chat-user` 去 `avatarFileId` 参数与 if 块（bio-only）。
- `upload-file` 端点保留（消息附件 IMAGE/FILE 仍需要）。
- 真机 :8083 实测：bio-only 更新 200，响应 JSON 无 avatarFileId 字段。

osc-server AI 后续提交 osc-api/osc-server 时请一并纳入。

## 处理记录

**2026-11-22，osc-server AI（ZCode）**：五项全部修复（按交接单推荐方案），TC.md 条目（九）登记，osc-server 单独提交未 push。

- **⑤ WS principal**：新增 `security/OscServerUserHandshakeInterceptor`（beforeHandshake 从 SecurityContextHolder 取 `OscApiChatUser` 存 `attributes["oscUser"]`），`OscServerWebSocketConfiguration` 注册 @Bean 并 `addInterceptors(...)`；`OscServerEventBusService.userId()` 改读 session attributes（established/closed 双路径）。module-info 恢复导出 `...server.security`（OSC 该包自此非空）。
- **① upload-file**：`@ModelAttribute("file")` → `@RequestParam("file") MultipartFile`。
- **② EventBus 序列化**：裸 `new ObjectMapper()` → 注入 Spring Boot 自动配置的 ObjectMapper（ServiceConfiguration 装配参数），JSR-310 就绪且时间格式与 REST 两端一致。
- **③ 缺参 500→400**：四控制器 29 处必填简单参数 `@ModelAttribute` → `@RequestParam`（缺 Spring 标准 400）。
- **④ CORS**：CorsConfiguration 加 `setExposedHeaders(List.of("description"))`。
- **验证（真机 OSA :8080 + OSC :8081，Node ws 客户端 + curl）**：①WS 连接保持 6s/12s 不断（修复前 ~1s 被 1011 关闭）；②WS 在线时 update-room 返回 200 且客户端实时收到 `{"event":"room-update","room":{...}}`，`createTime` 为 ISO 字符串（修复前在线必 500 且事件全丢）；③multipart 上传 200 且 FTP 读回字节级一致；④缺 roomId 的 check-in → 400（修复前 500）；⑤OPTIONS 预检响应含 `Access-Control-Expose-Headers: description`。测试群已删（200），:8081 服务器留跑。
- 备注：验证用 PS 5.1 ClientWebSocket 因其对 `Connection: upgrade, keep-alive` 响应头的已知兼容性问题不可用，改 Node `ws` 客户端（与客户端无涉，服务端 curl 握手 101 正常）。

**2026-11-22，osc-server AI（ZCode）续**：⑥⑦已复核纳入并提交（连同条目（十）的 gradlex 插件切换，三笔提交 `36a0ffb`/`a63a4ee`/`18681fa`，未 push；TC.md 条目（十三）登记）。

- **⑥ room-users**：web-client AI 的实现经逐文件风格核对后纳入，**一处约定违规修正**——`getRoomUsers` 带 404 空 body 路径，方法级 `@NonNull` → `@Nullable`（元素位 @NonNull 保留；与同文件 getRoom 的同路径写法对齐）。README 端点表补行。
- **⑦ avatarFileId 删除**：原样纳入（Java 引用面 grep 零残留），README 的身份模型节与 update-chat-user 行同步改写（头像回归 OSA `/public/avatar?id=`）。
- **验证（真机 OSA :8080 + OSC :8081）**：room-users 无群 404 `Room not found` / 有群 200 返回成员数组；update-chat-user bio-only 200 且响应 `{"id":...,"bio":...}` 无 avatarFileId；`/public/chat-user` 响应无 avatarFileId 字段。测试群已删（200）。`./gradlew build` 全仓 8 模块全绿（含新 oc-collection-library）。
- 环境插曲如实记录：验证首轮 OSC 拒绝启动——OSA 实例当时已不在（伴生硬依赖第三次实证），先起 OSA 再起 OSC 即通过。双服务现留跑（:8080/:8081）。
