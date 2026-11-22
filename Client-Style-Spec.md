# 客户端页面风格规格（Client Style Spec）

> **基线**：`web-client/`（Clients 仓库合并版，OSA 1.0a），2026-11-21 从一手源码提取。
> **用途**：业主要求做一个"风格类似"的客户端框架，本文是该风格的唯一基准。
> 引用格式为 `web-client/` 下相对路径:行号。数值全部来自源码原文，非转述。
> **总原则：风格要像，实现债不要抄**——第七节列出勿继承清单。

---

## 一、设计 token

### 1.1 色板（全局仅 8 个颜色，无 CSS 变量）

| 角色 | 值 | 来源 |
|---|---|---|
| 页面底色（最深层） | `#101010` | global.css:27（body 背景）、:36（input 背景） |
| 卡片 / 导航底色 | `#202020` | global.css:82；osa.css ×9 处 |
| 边框色 | `#303030` | global.css:35、:89；osa.css ×9 处 |
| 正文字色 | `#e0e0e0` | global.css:37、:70 |
| 强调色（标题条专用） | `#6060ff` | **只存在于 TS 内联样式**（login-page.ts:56 等 ~12 处，CSS 里没有） |
| toast 背景 | `#402020` | global.css:56 |
| toast 文字 | `#ffe0e0` | global.css:57 |
| toast 边框 | `#503030` | global.css:58 |

配色逻辑：三档灰阶（101010 → 202020 → 303030）分别承担底 / 卡 / 边，`#e0e0e0` 做字；全站唯一彩色是 `#6060ff`（蓝紫，仅页面标题条）和 toast 的暗红色系（错误/提示语义）。

### 1.2 字体

- `@font-face { font-family: jetbrains; src: url("../fonts/JetBrainsMono-Medium.ttf"); }`（global.css:1-4）——**小写家族名、Medium 单字重、无 fallback**（连 `monospace` 都没有）。
- body 统一 `font-family: jetbrains`（global.css:28）；input 规则未写 font-family（global.css:33-39），输入框实际退回浏览器默认字体——**新框架应让 input 继承**。
- **全项目没有任何 font-size 声明**，全部依赖浏览器默认 16px；字号层级完全靠"居中 + 颜色"表达，不靠大小。

### 1.3 尺寸 / 间距系统

- 基础节奏两档：**16px**（button 高、toast 高、nav li 下边距、toast 内边距）与 **32px**（区块间距、input 高）。
- 卡片圆角一律 **16px**（osa.css 每块 `border-radius: 16px`）。
- 布局列宽：`grid-template-columns: 10% 90%`（global.css:66）；导航 li 宽 `9.9vw`（补差右侧 1px 边框，global.css:94）。
- 头像 64×64（osa/utilities.ts:47）；toast 宽 800px、内边距 16px，动画起点 `right: -834px`（= 800 + 16×2 + 1×2 的补差魔数，global.css:52-54、:8）。
- 每页卡片宽度不同（见 3.2 表）：160 / 352 / 352 / 384 / 384 / 448 / 480 / 480 / 512 px。

## 二、布局骨架

- index.html 是空壳（全文 11 行）：charset + title + `<div class="app-div" id="app-div"></div>`；无 lang、无 viewport、无静态资源链接；favicon 由 html-webpack-plugin 注入（webpack.config.js:32）。
- `div.app-div`：100vw × 100vh，双列 grid——左 10% 导航栏、右 90% 内容区（global.css:62-67）。
- `ul.navigator-ul`：`#202020` 底、右侧 1px `#303030` 边框、占满高度、overflow auto（global.css:81-90）；li 内堆 `.button`，每个按钮 `margin-bottom: 16px`（TS 内联，src/ts/utilities.ts:27）。
- 内容区：页面卡片经 `div.xxx-div { display: grid; place-items: center; }` 在 90% 列内**水平垂直双居中**（osa.css:1-4 等每组第一条规则）。
- body：`margin: 0; overflow: hidden`（global.css:29-30）——overflow hidden 同时承担藏住 toast 场外起点。

## 三、组件规范（7 类）

### 3.1 navigator（左侧导航栏）

- 结构：`div.navigator-div > ul.navigator-ul > li.navigator-li > div.button`（src/ts/utilities.ts:19-34）。
- 导航项即按钮：innerHTML 为 i18n 文案（`name.*` 键），onclick 直接调用其他页面的 `loadXxxPage()`。
- **无选中态 / 激活态 / 禁用态**——只有 hover 变暗。

### 3.2 页面卡片（xxx-div + xxx-ul）

| 页面 | 卡片宽 | 备注 |
|---|---|---|
| logout | 160px | osa.css:43 |
| delete | 352px | osa.css:95 |
| profile | 352px | osa.css:112 |
| debug | 384px | osa.css:26 |
| update-avatar | 384px | osa.css:138 |
| personal | 448px | **唯一非 0 padding：`padding-top: 32px`**（osa.css:62） |
| login | 480px | osa.css:9 |
| register | 480px | osa.css:79 |
| update-profile | 512px | osa.css:150 |

- 统一样式（除宽度逐字节相同）：`#202020` 底、1px `#303030` 边框、`border-radius: 16px`、margin 0、padding 0、list-style none、overflow auto、height auto。
- 卡片本体是 `<ul>`，内容堆在 li 里：标题条 → 输入行 / 按钮行 / 内容行。

### 3.3 .button（可点击文本）

- `div.button`：`#e0e0e0` 字色、height 16px、text-align center、cursor pointer、width auto；hover `filter: brightness(75%)`（global.css:69-79）。无 transition——瞬时变暗。
- 不是 `<button>` 元素：无键盘可达性、无 focus 态、无 disabled 态（新框架应补齐语义）。

### 3.4 输入行

- input 全局样式：`#101010` 底、1px `#303030` 边框、`#e0e0e0` 字、height 32px、padding 0（global.css:33-39）。
- 行内定位靠内联样式：`width: 90%; margin-left: 5%; margin-bottom: 32px`（个别 16px 变体），约 12 处复制。
- 无 label 元素、无 autocomplete、无 `<form>`/submit——Enter 不提交，只能点按钮；输入框上方通常有文案 li 说明语义。

### 3.5 标题条（页面大标题）

- 卡片内第一个 li，内联样式：`width: 100%; height: 16px; margin-top: 32px; margin-bottom: 32px; text-align: center; color: #6060ff`（login-page.ts:55-56 等 5 处变体，属性顺序还各不相同）。
- 内容为 i18n `title.*` 键；同时 `document.title` 也设为同一文案（setTitle），导航栏大标题再从 `<head><title>` 反扫回来（src/ts/utilities.ts:33-42）——三者联动。

### 3.6 toast（通知条）

- 右上角滑入：`position: absolute; top: 16px`，动画 1s forwards，`right: -834px → 16px`（global.css:41-60、:6-24）。
- 暗红色系（`#402020` / `#ffe0e0` / `#503030`）——**成功与失败共用同一视觉**，不区分语义色。
- **点击才消失，不自动消失**；连续触发会叠加多条，互不管理；`right` 起点、宽度、padding、border 四个魔数必须同步改，否则穿帮。
- 手写了 `-webkit-` 前缀重复（global.css:16-24、:46-48），新框架不需要。

### 3.7 资料卡（inner-profile-ul）

- 嵌在大卡片内的 100% 宽 ul（osa.css:121-128）——**无底色、无边框、无圆角**，视觉完全复用外层卡片。
- 头像区：64×64 `<img>` 外包 `height: 64px; display: grid; place-items: center; margin-bottom: 32px` 容器居中；加载失败兜底 `user-solid-full.svg`（osa/utilities.ts:43-56）。
- 文本行：`width: 100%; text-align: center; margin-bottom: 32px; color: #e0e0e0`（osa/utilities.ts:67-68）。

## 四、交互氛围

- **暗色唯一主题**：无 `prefers-color-scheme`、无亮色变体、无主题切换。
- **除 toast 滑入外零动画**：无 transition；hover 一律 `filter: brightness(75%)` 瞬时变暗。
- 可点元素 `cursor: pointer`；其余无指针反馈。
- **无响应式**：无 media query、无断点；导航固定 10% 列，小屏不重排。

## 五、页面解剖（loadXxxPage 八步模式）

8 个页面全是同一结构的复制品——这正是"框架"要抽象成脚手架的东西：

1. `setTitle(getString("title.xxx"))` —— 改 document.title；
2. 组 nav 按钮数组 —— 每个按钮 4 行：createElement → className="button" → innerHTML=文案 → onclick=跳页；
3. 按登录态裁剪 nav（profile/debug 页动态判断，其余页固定列表）；
4. `loadNavigator([...])` —— 渲染左栏；
5. `clearPage()` —— 清空 app-div；
6. 造 `div.xxx-div`（双居中容器）+ `ul.xxx-ul`（定宽卡片）；
7. 卡片内堆 li —— 标题条、输入行、按钮行、内容行；
8. `getAppDiv().append(...)` —— 挂载。

## 六、行为风格（框架同样要对齐的约定）

- **i18n**：双语表（en-US / 精确匹配 zh-CN），键名三段式——`name.*`（nav 与按钮文案）、`title.*`（页面标题）、`error.*`（失败 toast）+ `ok`（成功 toast，中文"搞定！"）。失败文案句式："Xxx failed, please check ... in the debug page."
- **导航按登录态裁剪**：未登录可见 Search / Register / Debug；登录后 Personal / Update / Logout / Delete / Debug。
- **入口分流**：page-loader 按 `isLogin()` 分流登录页 / 个人页。
- **服务端地址运行时可改**：debug 页把 basePath 写进 localStorage，全部请求经 `getBasePath()` 拼接（缺省当前源）。
- **导航 = 函数互调**：无路由库、无 URL 变化、无浏览器前进后退支持。

## 七、缺陷勿继承清单（风格要像，实现别抄）

1. `li.innerHTML` 渲染服务端数据（存储型 XSS 面，osa/utilities.ts:69）——新框架用 textContent / DOM API。
2. ~160 行 nav 按钮逐字复制（8 页 × 40 个按钮）——框架提供 `makeNavButton()` 类脚手架。
3. 样式双轨（CSS 类 + TS 内联 style 字符串并存，`#6060ff` 只活在 JS 里）——新框架颜色/间距全进 CSS 变量。
4. `var` 全量、零注释、`console.log` 调试残留（page-loader.ts:9 等）。
5. `<ul>` 直挂 `<input>/<div>` 非法 DOM；div.button 无可达性——新框架用语义元素或至少 role/tabindex。
6. `isLogin()`/`getJwtToken()` 每次调用发真实网络验活（profile/debug 单页 6 次冗余请求）；personal-page.ts:52 `if (!isLogin())` 缺 await 守卫永不触发。
7. `-webkit-` 前缀手写重复；file-loader 在 webpack 5 已过时（asset modules 取代）。
8. toast 不自动消失、叠加不管理；注销账号无二次确认。
9. input 不继承字体、全站无字号层级——新框架补 font-family 继承与字号比例。

## 八、新框架落点（建议，未动手）

- web-client 本就是多项目容器（webpack.config.js 的 `projects` 表、package.json 描述 "Clients for OSLNS and OC"）：新框架建议落 `web-client/src/ts/framework/`（或独立目录），以**第二个 entry + 第二份模板**接入，OSA 1.0a 现有产物零改动。
- 框架形态与 OSA 客户端同哲学：零运行时依赖、纯 TS DOM 工厂 + 一份 token CSS（第一节的 8 色 + 16/32px 节奏全部入册为变量）。
- API 建议围绕第五节八步模式设计：page 壳（标题 + nav + 卡片容器）、nav 工厂、卡片工厂、行工厂（标题条/输入行/按钮行/文本行）、toast 管理、i18n 表——即把第三节的 7 类组件与第五节的八步变成函数。
