# NeoECOAE UI JSON

NeoECOAE 保留 LDLib2 作为 UI 运行时，并用 `assets/neoecoae/ui/*.json` 描述稳定的界面树。JSON 负责节点顺序、尺寸、坐标、间距、背景纹理、静态文本和槽位位置；菜单同步、服务端业务、动态列表、特殊绘制以及可交互行为仍由 Java 提供。

## 资源格式

格式定义在 [`schema.json`](../src/main/resources/assets/neoecoae/ui/schema.json)。根节点可以是 `element` 或 `container`，常用节点包括：

```json
{
  "type": "element",
  "class": "panel_bg",
  "layout": {
    "width": 176,
    "height": 180,
    "padding": 4,
    "gap": 2,
    "flexDirection": "column"
  },
  "children": [
    {
      "type": "texture",
      "texture": "neoecoae:textures/guis/example.png",
      "sprite": [0, 0, 176, 180],
      "layout": { "position": "absolute", "left": 0, "top": 0 }
    },
    { "type": "custom", "custom": "runtime_part" }
  ]
}
```

支持像素和百分比尺寸，`position` 支持 `relative`/`absolute`，并支持 padding、margin、gap、flex、display、对齐方式和 `overflow: "visible"`。纹理节点只创建 LDLib2 的 `SpriteTexture`，不会执行 JSON 中的类名或脚本。

`custom` 节点由对应界面的 `NeoUiJsonContext` 白名单创建；`action`、`binding` 和其它业务字段也只由该 context 解释。这使按钮仍然使用 LDLib2 的事件、数据绑定和菜单 RPC，同时避免把业务逻辑塞进资源文件。

## 当前资源

- `ui/crafting/`：合成控制器、合成接口、样板总线。
- `ui/computation_*.json`：计算控制器、设置页、计算接口。
- `ui/storage/`：存储控制器、存储接口、MEGA 面板、优先级面板。
- `ui/workstation/`：整合工作站、大型工作站、输出面板和网络工具箱。
- `ui/multiblock_builder.json`、`ui/fluid_hatch.json`：多方块建造器和流体舱。

`NeoUiJsonLoader` 通过 LDLib2 的 `ResourceHelper` 读取客户端或服务端资源，并在无资源管理器的测试环境使用 classpath fallback。新增界面时，只需增加 JSON、在 context 中注册动态组件，并让 screen 的 `create` 调用 loader。
