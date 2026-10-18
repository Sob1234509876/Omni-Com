[English](README.md) | 简体中文

# 纹理图集契约（插件资源）

**贴图现在由插件提供，不再由这个目录提供。** 客户端启动时向服务器要
`GET /plugin-assets/manifest.json`，再按清单里的 `atlasUrl` 逐张加载图片；本地的
`public/textures/atlas.json` 不再被读取。这个目录只保留本文，作为插件清单的格式说明。

## 贴图放在哪里

把两个文件打进**插件 jar** 的 `assets/` 目录：

| 路径（jar 内） | 作用 |
| --- | --- |
| `assets/manifest.json` | 图集清单，本文下面的格式 |
| `assets/atlas.png` | 清单 `image` 指向的图片，与清单同目录 |

用 Gradle 构建的插件把它们放在 `src/main/resources/assets/` 即可，`jar` 任务会自动打包。例如示例插件：

```text
projects/oc/oc-game-server-hello/src/main/resources/assets/manifest.json
projects/oc/oc-game-server-hello/src/main/resources/assets/atlas.png
```

把构建出的 jar 放进服务器的 `plugins/`，重启服务器，浏览器客户端就会自动用上它——不需要重新构建
`web-client`，也不需要往 `public/` 里放任何文件。插件夹在 `plugins/<插件>/assets/` 下的散文件**不会**被
服务：路由只通过插件自己的类加载器读 jar 内的资源。

## 服务器发布的内容

服务器把每个带 `assets/manifest.json` 的已加载插件合并成一个数组，顺序就是插件加载顺序（jar 文件名排序）：

```json
[
  {
    "plugin": "oc-hello",
    "atlasUrl": "/plugin-assets/oc-hello/atlas.png",
    "tilePixels": 32,
    "tiles": { "grass": [64, 0, 32, 32] },
    "entities": { "worker": { "frames": [[0, 32, 32, 48]], "fps": 4 } }
  }
]
```

- `plugin` 是插件的 `Plugin#name()`，通常与插件描述文件的 `id` 相同；它也是 URL 里的插件 id。
- `atlasUrl` 由服务器根据清单里的 `image` 改写，**清单本身不得包含绝对 URL**。
- 没有插件发布清单时，这个路由回 `200` 和 `[]`；客户端于是使用程序化绘制。
- 所有 `/plugin-assets` 响应都是 `Cache-Control: no-cache`，替换插件后刷新页面即可看到新图。
- 客户端按数组顺序合并，**后面的插件覆盖前面的插件**（同名地形路径或同名实体 kind）。数组顺序就是
  服务器返回的顺序，客户端不重新排序。

原始文件由 `GET /plugin-assets/<插件 id>/<文件>` 提供，例如
`/plugin-assets/oc-hello/atlas.png`。这个路由只允许 `GET`/`HEAD`，不需要登录，也不属于 OC/1 协议；
细节见 [`protocol/oc-protocol_zh_CN.md`](../../../protocol/oc-protocol_zh_CN.md)。

## 基本约定

- **任意图集尺寸都可以**，例如 256×256、512×512、1024×1024。
- 推荐 `tilePixels` 为 **32**：地形帧按 32×32 绘制最容易对齐。客户端会把地形帧拉伸到屏幕上的一个格子，所以 16、32、64 也能用。
- 所有帧坐标都是图集图片内的**像素**，原点在左上角，向右 x 增大，向下 y 增大。
- 地形帧可以是任意矩形；建议每格一个矩形。
- **每种实体占一行动画帧**，从左到右是播放顺序。帧矩形不要求等宽等高，但同一行动画里保持一致最省心。
- 实体帧里的 `anchor` 是**脚底锚点**：`[0.5, 0.75]` 表示帧宽 50%、帧高 75% 的位置贴在地面位置上。人物/建筑从锚点向上生长，所以帧可以比一个格子高。

## 清单结构

`assets/manifest.json` 就是原来的 `atlas.json` 格式，只有一个字段的含义被服务器接管：`image` 必须是本插件
`assets/` 目录里的**文件名**（不能带 `/`、`..`、盘符或绝对 URL）。

```text
{
  "image": "atlas.png",          // 本插件 assets/ 里的图片文件名
  "tilePixels": 32,              // 推荐的美术格尺寸，仅作元数据
  "tiles": {
    "<TerrainType 名称>": [x, y, w, h]  // 旧形式，没有显示名
    // 命名形式： "<TerrainType 名称>": { "rect": [x, y, w, h], "name": "草地" }
  },
  "entities": {
    "<实体 kind>": {
      "frames": [[x, y, w, h], ...],  // 至少一帧，按播放顺序
      "fps": 4,                       // 可选，默认 4
      "anchor": [0.5, 0.75],          // 可选，默认 [0.5, 0.75]
      "scale": 1,                     // 可选，默认 1，叠在相机缩放之上
      "name": "普通"                  // 可选显示名
    }
  }
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `image` | 是 | 本插件 `assets/` 内的图片文件名；通常就是 `atlas.png` |
| `tilePixels` | 否 | 建议的美术格像素；缺失或非法时按 32 处理 |
| `tiles` | 否 | 地形帧表；缺失时全部地形使用程序化绘制 |
| `tiles.<name>` | — | 引擎的地形内容路径，见下表；值是 `[x, y, w, h]` 或 `{ "rect": [x, y, w, h], "name": "..." }` |
| `entities` | 否 | 实体动画表；缺失时实体使用程序化图形 |
| `entities.<kind>.frames` | 是 | 至少一个 `[x, y, w, h]` |
| `entities.<kind>.fps` | 否 | 正数，默认 4 |
| `entities.<kind>.anchor` | 否 | 两个数字，默认 `[0.5, 0.75]`（脚底） |
| `entities.<kind>.scale` | 否 | 正数，默认 1；与相机缩放相乘 |
| `entities.<kind>.name` | 否 | 显示名；缺失或为空时回退到原始 kind |

## 显示名

两张表都可以带人类可读的**显示名**：实体的 `entities.<kind>.name`，以及地形命名对象里的 `name` 成员。客户端按同样的
**后者覆盖前者**规则把名字和帧一起合并，并用在 HUD 选中行、选中建筑行、TRAIN/BUILD 按钮文字和成功日志里（例如
`已下训练 普通 (unit.melee.t1)`）。协议命令仍然发送原始内容路径。

名字是可选的。没有名字的旧清单、缺失的 `name`、空白的 `name` 都会回退到原始 id，所以页面不会显示 `undefined`。
`check-render-math` 会验证标签表、回退规则，以及经典插件在磁盘上的清单确实给每个地块和每种实体都写了名字。

服务器不解释 `tiles` 和 `entities`，只转发给客户端；帧的合法性由客户端按下面“校验与失败行为”检查。

## 完整示例

下面的 JSON 对应一张 256×320 的 `atlas.png`。地形帧占最上面一行，每种实体各占一行；建筑只用一帧。
把这段 JSON 写成 `assets/manifest.json`、图片写成 `assets/atlas.png`，打进插件 jar 即可发布。

```json
{
  "image": "atlas.png",
  "tilePixels": 32,
  "tiles": {
    "water": [0, 0, 32, 32],
    "sand": [32, 0, 32, 32],
    "grass": [64, 0, 32, 32],
    "forest": [96, 0, 32, 32],
    "hill": [128, 0, 32, 32],
    "rock": [160, 0, 32, 32],
    "void": [192, 0, 32, 32]
  },
  "entities": {
    "worker": {
      "frames": [
        [0, 32, 32, 48],
        [32, 32, 32, 48],
        [64, 32, 32, 48],
        [96, 32, 32, 48]
      ],
      "fps": 4,
      "anchor": [0.5, 0.75],
      "scale": 1
    },
    "soldier": {
      "frames": [
        [0, 80, 32, 48],
        [32, 80, 32, 48],
        [64, 80, 32, 48],
        [96, 80, 32, 48]
      ],
      "fps": 6,
      "anchor": [0.5, 0.75],
      "scale": 1
    },
    "medic": {
      "frames": [
        [0, 128, 32, 48],
        [32, 128, 32, 48],
        [64, 128, 32, 48],
        [96, 128, 32, 48]
      ],
      "fps": 4,
      "anchor": [0.5, 0.75],
      "scale": 1
    },
    "warehouse": {
      "frames": [[0, 176, 96, 72]],
      "fps": 1,
      "anchor": [0.5, 0.75],
      "scale": 1
    },
    "barracks": {
      "frames": [[96, 176, 96, 72]],
      "fps": 1,
      "anchor": [0.5, 0.75],
      "scale": 1
    }
  }
}
```

## 地形名称

`tiles` 的键就是引擎 `TerrainType.key().path()`。当前自检场景定义的是：

| 键 | 说明 |
| --- | --- |
| `water` | 水 |
| `sand` | 沙 / 海岸 |
| `grass` | 草地 |
| `forest` | 森林 |
| `hill` | 丘陵 |
| `rock` | 岩石 / 不可通行 |
| `void` | 地图外 / 引擎兜底地形 |

程序化绘制还认识这些别名：`deep_water`、`shallow_water`、`grassland`、`meadow`、`trees`、`hills`、`mountain`、`rocky`、`stone`、`desert`、`dirt`、`soil`、`snow`、`ice`、`road`、`bridge`。图集里写这些键也会生效；不认识的键会得到程序化生成的颜色。

## 实体 kind

`entities` 的键要匹配 `EntityView.kind`，即实体的内容路径。自检场景和占位地图使用：

| kind | 类型 |
| --- | --- |
| `worker` | 工人（单位） |
| `soldier` | 士兵（单位） |
| `medic` | 医疗兵（单位） |
| `warehouse` | 仓库（3×3 建筑） |
| `barracks` | 兵营（3×3 建筑） |

带命名空间的路径（例如 `oc:soldier`）也能匹配到 `soldier`。没有对应条目的实体使用程序化图形。

## 多个插件同时提供图集

- 客户端按服务器数组的顺序加载，**最后一个定义某个地形路径或实体 kind 的插件获胜**；它没有定义的名字
  继续由更早的插件提供。不同插件可以各画一部分地形，也可以整张覆盖。
- 每个获胜的帧都会连同它所属插件的图片一起记录，渲染器不会把 A 插件的坐标画到 B 插件的图片上。
  所以多个插件可以各自维护自己的图集，不需要拼成一张大图。
- HUD 的来源标签显示 `atlas · <插件 id>`（最多三个插件时列出全部 id）或 `atlas · N plugins`；回退到
  程序化绘制时显示 `procedural`。

## 校验与失败行为

- 服务器要求清单是 JSON 对象、`image` 是非空且安全的文件名；`tiles` / `entities` 只要出现就必须是对象。
  不符合的清单会被跳过并在服务器日志里留一行原因，其它插件不受影响。
- 客户端要求每个条目有同源的 `atlasUrl`；没有、不是本服务器路径或不是对象的条目会被**跳过并警告**，
  其余条目照常加载。
- 顶部必须是 JSON 对象；每个帧必须是恰好四个有限数字的数组，`w` 和 `h` 必须大于 0。
- 帧必须完全落在图片内。越界的帧会被**静默丢弃**，并在控制台用一次 `console.warn` 汇总报告；同一实体的其他合法帧继续使用。
- 没有任何合法帧的实体条目会被丢弃；一个没有任何可用地形帧和实体帧的条目会被判为不可用，只跳过该插件。
- 地块可以是旧的 `[x, y, w, h]` 数组，也可以是命名的 `{ "rect": [x, y, w, h], "name": "..." }` 对象；格式错误或缺少 `rect` 的地块会被跳过并给出警告。`name` 不是字符串、为空或只有空白时按“没有名字”处理，回退到原始路径。
- 图片载入失败只跳过该插件；`fps`、`anchor`、`scale` 非法时按默认值处理，并给出警告。
- 清单请求失败、返回 `[]`、或所有条目都不可用时，客户端回到程序化绘制，不会阻止页面加载，也不会影响协议面板。

## 动画与选中

- 客户端用每秒 `fps` 帧循环播放该 kind 的 `frames`。不同实体按 id 有轻微相位差，避免整屏单位同步踏步。
- 将来的快照可以在 `EntityView.frames` 里给出一个帧索引序列（例如 `[2, 3, 4, 5]`），用来在同一条图集行里选择“行走”片段；不提供时播放整行。
- 选中环、阴影和血条由客户端绘制，不需要画进 `atlas.png`。
