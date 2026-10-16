# 03 · 插件

> 一个 jar 怎么变成服务器的一部分、它能从服务器拿到什么、它的依赖放在哪，以及为什么加载器一辈子不关。

## 这一篇讲什么

- `Plugin` 的三个生命周期回调、构造要求与线程约定。
- `PluginContext` 交给插件的六样东西，以及它**不**给什么。
- `omnicom-plugin.properties` 的四个键、`PluginDescriptor` 与 `DiscoveryResult`。
- 发现顺序（服务文件 → 描述文件 → 全类扫描）、确定性排序、坏 jar 只算它自己。
- **依赖规则**：每个 jar 一个 `URLClassLoader`，自己的 jar 排第一，同目录其他 jar 排在后面。
- **为什么加载器从不关闭**，以及它避开的 `NoClassDefFoundError`。
- 只读请求缝：`RequestHandler` 与 `RequestRegistry`。
- 一个最小可编译的插件。

## `Plugin` 生命周期

插件是一个普通的、由操作员提供的 jar。服务器对它一无所知，只知道这个接口。加载器用 `getDeclaredConstructor().newInstance()` 构造它，所以实现需要**公开的无参构造器**。

| 回调 | 何时 | 该做什么 |
| --- | --- | --- |
| 构造函数 | 加载时 | 只做平凡初始化；**不要**在这里找文件或起线程 |
| `onLoad(PluginContext)` | 构造之后立刻，**socket 还没开** | 读配置、建数据目录、记住 context、注册请求；抛异常 = 这个插件永久停用 |
| `onEnable()` | `onLoad` 成功之后 | 发布游戏内容、启动后台任务；此时插件算服务器的一部分 |
| `onDisable()` | 关闭时（`PluginLoader.unloadAll()`），按加载顺序 | 释放文件、daemon 线程、连接；之后服务器不会再调它 |

三个生命周期方法都有空的默认实现：只提供静态内容的插件只实现 `name()` 与 `version()` 就行，加载器不需要为它特判 `description()` 默认返回空串。

失败路径要说清楚：

- 构造函数、`onLoad` 或类型转换失败 → 记一条问题，插件**不启用、不进插件列表**，也不会收到 `onDisable()`。
- `onLoad` 成功但 `onEnable` 失败 → 记一条问题，并**补调一次** `onDisable()`——它已经拿到过资源（目录、注册过的请求），不补调就会留下一个只有一半的插件。
- 这些失败全都不会中断加载：剩下的 jar 照常轮到。

**线程约定**：插件不得起非 daemon 线程。那种线程会在服务器停掉之后把 JVM 吊住，症状是"进程看起来卡住了，没有窗口、日志里也解释不了"。要起线程就设成 daemon，或者在 `onDisable()` 里停掉它。

## `PluginContext`：插件拿到的全部东西

| 方法 | 给的是什么 |
| --- | --- |
| `pluginsDirectory()` | jar 被读取的目录（绝对、已 `normalize()`）；插件可以看，但目录归服务器管 |
| `dataDirectory()` | 每个插件数据目录的根；插件自己的在 `dataDirectoryOf(String)` |
| `serverVersion()` | 运行中的服务器版本（例如 `2.0a`） |
| `logger()` | 服务器的 logger；插件的消息只有走这里才能到达操作员 |
| `requests()` | 只读请求面：把名字与 `RequestHandler` 发布进去 |
| `dataDirectoryOf(String)` | 返回 `dataDirectory/<插件名>/`，不存在就创建（连同父目录） |

`dataDirectoryOf(String)` 会校验名字，因为它会变成一个路径元素：空名字、含 `/`、含 `\`、含 `..`、含 `:` 全部拒绝。冒号不是理论问题：Windows 上 `resolve("C:evil")` 得到的是**驱动器相对路径** `C:evil`，直接跑出数据目录；校验之后再 `startsWith(root)` 复核一次，作为"平台分隔符不一致"的第二道意见。

关键限制：**context 里没有服务器对象图**。没有 `World`、没有引擎、没有 `UserRecord`、没有 socket。所以插件改不了它拿不到的东西，服务器也能自由改内部实现而不破坏插件。唯一的例外是 `requests()`，而它是一张"字节进、字节出"的表，从请求走不到 socket、用户记录或一局游戏。

还有一个容易忽略的细节：`PluginContext` 自己也能被构造，而**四参数构造会给它一个私有的、空的注册表**。也就是说，只有服务器用四参数 `PluginLoader` 建出来的那份注册表才连到 socket；用两参数或三参数 `PluginLoader` 加载的插件拿到的是一个"能注册但没人转达"的表面（自校验就是靠这一点在进程内直接驱动插件的）。

## `PluginDescriptor`

描述文件是 jar 根目录下的 `omnicom-plugin.properties`（`PluginDescriptor.RESOURCE`），用 `Properties.load` 读取——也就是 ISO-8859-1 编码，所以 `description` 里的非 ASCII 要写成 `\uXXXX`。

```properties
id=hello
version=1.0
main=example.hello.HelloPlugin
description=Answers hello-world with one fixed line
```

| 键 | 必填 | 用途 |
| --- | --- | --- |
| `id` | 是 | 插件的稳定标识，跨版本不变 |
| `version` | 是 | **jar 自己的**版本，不是服务器的 |
| `main` | 是 | `Plugin` 实现的二进制类名，优先加载 |
| `description` | 否 | 一行说明，给日志和列表用；缺失时是空串 |

三个必填键有一个缺失或空白，整个描述文件就**不可用**：加载器不猜，因为猜的后果是让两个插件共用同一个数据目录。jar 里**根本没有**这个文件不算问题（插件目录也是依赖库的家，库没有理由带插件描述文件）；文件在但坏掉才算问题，因为那是操作员能修的打包错误。

## `DiscoveryResult`

`PluginLoader.discoveries()` 对每个碰过的 jar 返回一条：

| 访问器 | 内容 |
| --- | --- |
| `jar()` | 被检查的 jar |
| `descriptor()` | 解析出的描述文件，空 `Optional` 表示没有可读的描述文件 |
| `pluginClasses()` | 找到的、可实例化的具体 `Plugin` 实现类名，**去重且排序** |
| `problems()` | 这个 jar 出的所有问题，按发现顺序 |
| `usable()` | `problems()` 为空**且** `pluginClasses()` 非空 |

注意 `pluginClasses()` 非空不等于可用：一个坏描述文件的 jar 仍然会报告它扫到的插件类，同时把问题写进 `problems()`。两个问题分开问，操作员才知道该不该被警告。

## 发现顺序与加载顺序

**目录层面**：`plugins/` 下所有以 `.jar` 结尾的普通文件（后缀判断忽略大小写），**按文件名排序**。所以同一个目录启动两次，看到的顺序一样。

**jar 层面**，三步依次进行，前一步的结果不会被后一步覆盖：

1. `META-INF/services/io.github.sob1234509876.oc.server.plugin.Plugin`（按 ServiceLoader 规范，UTF-8，`#` 后面是注释）——用构建插件或 `ServiceLoader` 的插件作者免费得到这一步；
2. 描述文件里 `main` 指向的类；
3. 扫描 jar 里**所有** `.class`：跳过 `module-info`、`package-info`，跳过内部/局部/匿名类（最后一个点之后出现 `$` 的名字），保留"实现了 `Plugin` 且不是接口也不是抽象类"的。

两步的区别在于**怎么对待失败**：

- 写在服务文件或 `main` 里的名字是**刻意写下**的，所以类型不对、是抽象类、加载失败都会记成问题；
- 扫描**顺手碰到**的类（绝大多数都不是插件）读不了才记问题，其余不匹配的情况沉默跳过。

一个 jar 里找到的插件类最后放进 `TreeSet` 排序，再按类名顺序逐个实例化。所以"同一份目录 + 同一批 jar = 同样的启动顺序"这条性质是有保证的，可以放心依赖。

**jar 坏掉只算它自己**：读不出描述文件、某个类加载失败、`onLoad` 抛异常——每一种都只是往 `problems()` 里加一行、往日志里写一条 WARNING，然后继续下一个 jar。

## 依赖规则

这是整个模块最需要说清楚的一条。**插件目录本身就是依赖机制**：没有仓库、没有 POM、没有下载、没有传递依赖解析。把库的 jar 丢进同一个目录，插件就能用它——这才让服务器能在一台没网、插着 U 盘的机器上跑起来。

每个 jar 拿到的 `URLClassLoader` 的 URL 列表是：

```text
[ 这个 jar 自己 ] + [ 目录里其他所有 jar，按文件名排序 ]
```

由此得到下面这套规则，逐条都重要：

| 规则 | 后果 |
| --- | --- |
| **自己的 jar 排第一** | 插件自带的类永远赢过同目录其他 jar 里的同名类；两个插件可以各自带一份同一个库 |
| 其他 jar 排在后面（按文件名顺序） | 插件没自带的类，从目录里第一个含有它的 jar 解析 |
| 父加载器是读取 `Plugin` 的那个加载器，委派是**双亲优先** | 服务器 API 全服务器共享一份；插件不能悄悄用自己的版本替换掉它 |
| **同一目录同一时刻只能有一个版本的某个库** | 因为每个插件都看得见整个目录，而排序靠前的那份会先命中；要放两份就只能让需要的那一方自带 |
| 依赖 jar 里没有描述文件是正常的 | 加载器会扫描它（多一点开销），但它不贡献插件，也不记问题 |

自校验里对应的场景：`dep-a-plugin.jar` 里只有插件，它 `onEnable` 用到的类只存在于同目录的 `dep-b-library.jar`；两个 jar 各有自己的加载器，而且不是同一个对象。另一组场景里两个 jar 带着**同名但实现不同**的类：它们由两个不同的加载器定义，各自执行自己 jar 里的实现。

## 为什么加载器从不关闭

`PluginLoader` 里的加载器是有意**永不 `close()`** 的，`unloadAll()` 也不会关。原因：

`URLClassLoader` 的 `close()` 会释放它打开的 jar 文件，从那一刻起这个加载器**再也不能定义它此前没碰过的类、也读不出没读过的资源**。一个按需加载的插件——序列化器、脚本引擎、JDBC 驱动，任何"第一次用到某个功能时才按名字去找"的东西——就会在功能被使用时炸出 `NoClassDefFoundError`。这正是这个设计要避开的症状。更糟的是，它会让行为取决于"这个插件的功能第一次被用到是什么时候"，属于最难复现的一类 bug。

所以 `unloadAll()` 只做两件事：按加载顺序调用每个插件的 `onDisable()`，然后清空插件列表。类加载器留在 `classLoaders()` 里，也不从列表里移除。

代价说明白：**被停用的插件，它的类会留在内存里直到服务器进程退出**。这不是服务器会察觉到的泄漏——加载器的寿命就是这个进程的寿命，JVM 本来也会让已加载插件的类活着；而服务器进程一定比它加载过的插件活得久。

## 只读请求缝

### `RequestHandler`

```java
byte[] handle(byte[] request);
```

合同只有一句：服务器把客户端为这个注册名发来的**确切字节**交给处理器，再把处理器**返回的字节**原样转发回去。服务器不解释请求、不解析响应、不把它们喂给游戏引擎、命令解析器或世界。这带来两个直接结论：

- 服务器保持"不认识游戏形状的东西"；
- "只返回信息"这个承诺属于注册处理器的插件，不属于这个接口。因为服务器不看内容，一个会回答的处理器**按定义**就是一种"让服务器替客户端干活"的入口——所以插件必须把自己的处理器当成公开入口对待：用服务器不知道的凭据（插件自己的密钥是常见答案）认证请求、只接受自己文档里写过的请求形状、绝不在里面改引擎状态。

处理器跑在**发送请求的那个客户端线程**上，所以可能同时有多个调用在飞，实现必须并发安全；它不该无限期地占住连接，也不该为自己能报告的错误抛异常——真抛了会被服务器接住，变成一个通用协议错误，别的客户端照常服务。

### `RequestRegistry`

| 方法 | 语义 |
| --- | --- |
| `register(String, RequestHandler)` | 以名字发布一个处理器；名字非法或已存在都抛 `IllegalArgumentException`，处理器为 `null` 抛 `NullPointerException` |
| `unregister(String)` | 移除并返回它是否存在；返回 `false` 说明本来就没有 |
| `find(String)` | 按名字查，返回 `Optional`；任何线程都可以调 |
| `names()` | 已注册名字，**按字母排序**的新列表（不是活注册表的视图） |
| `size()` | 已注册数量 |

名字规则是 `[a-z0-9-]{1,32}`（完全匹配，不是"包含"）：小写字母、数字、短横线，1 到 32 个字符。名字会变成命令行里的一个字段，所以必须能毫无歧义地穿过一个空白分隔的协议。大写是**拒绝而不是折叠**，这样两种拼写永远不可能命名或找不到同一个处理器。

- **重复名字被拒绝**，不覆盖前一个：两个插件都声称 `engine-info` 是配置错误，让后一个悄悄赢会变成"过一阵子才发现是错的插件在回答"。
- **注册与注销发生在插件生命周期线程上**（启动或关闭），本来不打算和运行中的服务器竞争；但存储仍然用 `ConcurrentHashMap`，这样"延迟注册功能"的插件注册完立刻对客户端线程可见。
- **读取可以从客户端线程随时做**：`find` 是一次 map 读，`names()` 排的是 key 的快照，不会去遍历一个别的线程正在改的集合。
- 没有插件时注册表是空的：服务器的两参数构造会自建一个空注册表，于是每条 `REQ` 都回 `ERR unknown request`，与"还没有插件这个概念"时逐字节相同。

**服务器侧的规则**（细节见 [02](02-accounts-and-encryption.md)）：名字超过 32 字符直接按未知请求拒绝，连载荷都不解码；载荷不是合法 base64 也拒绝，且**从不交给处理器**；`REQ` 要求同一条连接上成功 `LOGIN` 过；载荷与答复永不记录。

## 一个最小插件

下面这个类是完整可编译的（真实 API，无省略）。它注册一个叫 `hello-world` 的请求，忽略载荷，永远回固定一行。

```java
package example.hello;

import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.plugin.RequestRegistry;

import java.nio.charset.StandardCharsets;

public final class HelloPlugin implements Plugin {

    /** 服务器的请求面；留着是为了在 onDisable 里撤回自己注册的名字。 */
    private RequestRegistry requests;

    @Override
    public String name() {
        return "hello";
    }

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public String description() {
        return "Answers hello-world with one fixed line";
    }

    @Override
    public void onLoad(PluginContext context) {
        this.requests = context.requests();
        // 在 onLoad 里创建，而不是第一次用到时创建：数据目录不可写应该在启动时就暴露。
        context.dataDirectoryOf(name());
        context.logger().log(System.Logger.Level.INFO,
                () -> "hello " + version() + " loaded against server " + context.serverVersion());
        requests.register("hello-world", request -> "hello\n".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onDisable() {
        if (requests != null) {
            requests.unregister("hello-world");
        }
    }
}
```

打包时把描述文件放到 jar 根目录：

```properties
id=hello
version=1.0
main=example.hello.HelloPlugin
description=Answers hello-world with one fixed line
```

然后把 jar（以及它需要的任何依赖 jar）丢进服务器的 `plugins/`。客户端登录之后：

```text
REQ hello-world eA==      -> OK aGVsbG8K
```

`eA==` 是任意一个非空载荷的 base64（这里是 `x`），因为载荷必须能通过 base64 解码，否则服务器在到达处理器之前就回 `ERR unknown request`；`aGVsbG8K` 是 `hello` 加换行的 base64。

## 常见坑

1. **服务器 API 只有一份**。加载器的父是读取 `Plugin` 的那个加载器，双亲优先，所以别在自己的 jar 里再打包一套 `io.github.sob1234509876.oc.server.*`——它永远不会被用到，只会让体积变大。
2. **目录不存在 = 没有插件，不是错误**。只有"目录在但列不出来"才记问题。
3. **一个库只能有一个版本**。想同时用两个版本，只能让其中一方把库打包进自己的 jar（自己的 jar 排第一）。
4. **`unloadAll()` 之后插件类还活着**。别指望它释放内存，也别指望在同一个进程里换一个版本的插件 jar 再加载。
5. **非 daemon 线程会让进程停不下来**。起线程就设 daemon，或者在 `onDisable()` 里收掉。
6. **数据目录的名字不能随便写**。`dataDirectoryOf(String)` 拒绝 `..`、`/`、`\`、`:`；不要试图绕过，插件名字本身也不该含这些。
7. **别往 `plugins/` 写状态**。那是操作员的拖放区，jar 会被替换或删除；状态放 `data/`。用 `OcGameServer` 加载的插件会自动拿到正确的数据根（四参数 `PluginLoader`），只有自己用两参数 `PluginLoader` 时数据根才落在 `plugins/data`。
8. **`plugin(String)` 找的是 `Plugin.name()`，不是描述文件里的 `id`**。两者可以不同，日志里两个都会出现，别混。
9. **处理器必须并发安全**。它跑在客户端线程上，可能同时在跑好几个。

## 相关文档

- 服务器怎么把注册表接到 socket 上、`REQ` 的确切回答 → [02-accounts-and-encryption.md](02-accounts-and-encryption.md)
- 一个真的只读插件（`oc-debug`）怎么写 → [04-debug-plugin-and-cli.md](04-debug-plugin-and-cli.md)
- 插件目录与数据目录的默认位置 → [01-getting-started.md](01-getting-started.md)

## 相关源码

- 插件接口与上下文：[`Plugin.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/Plugin.java)、[`PluginContext.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/PluginContext.java)
- 加载器与发现：[`PluginLoader.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/PluginLoader.java)、[`PluginDescriptor.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/PluginDescriptor.java)、[`DiscoveryResult.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/DiscoveryResult.java)
- 请求缝：[`RequestHandler.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/RequestHandler.java)、[`RequestRegistry.java`](../src/main/java/io/github/sob1234509876/oc/server/plugin/RequestRegistry.java)
- 运行时生成真 jar 的自校验：[`PluginCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/PluginCheck.java)、[`RequestCheck.java`](../src/selfcheck/java/io/github/sob1234509876/oc/server/check/RequestCheck.java)
- 模块与构建：[`module-info.java`](../src/main/java/module-info.java)、[`build.gradle`](../build.gradle)
