# 计算管道与盘体缺失修复

## 原因

用户当前运行的 Project Infinity 1.20.1 实例装有 `xenon-0.3.31+mc1.20.1.jar`。其区块编译器通过 `ChunkMeshEvent` 收集静态附加网格，不执行原版 `ChunkRenderDispatcher.RenderChunk.RebuildTask.compile`。

本移植版此前只在原版 `compile` 中调用 `FixedBlockEntityRenderers`，因此计算元件、连接／断开的管道、存储盘外观及成型冷却控制器失去公共绘制入口。界面中的元件图标走物品模型，仍然能显示，与用户提供的两张截图一致。

已更新并阅读参考仓库。主体移植基线保持 `porting/source-lock.json` 指定的 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`，未自动切换到更新的 main。

| 参考源码 | 阅读提交 | 用途 |
| --- | --- | --- |
| NeoECOAE 主体基线 | `dc8344f48ee866e57e02c285fdda5e0fb5da9421` | 原有固定模型、变换及光照职责 |
| Xenon `20.1/forge` | `6267f3913c82adae056a8412c657ab5b9e5c7b47` | 实际区块编译器与网格接口 |
| Embeddium `20.1/forge` | `dee0ebde6aea8b158613cd3b8ea5cdf0c1f6a9bf` | 确认两者公开接口一致 |

另用 `javap` 核对了当前实例实际安装的 Xenon JAR；其 SHA-256 为 `1ad6300ae3b586b031846c204a126c21d94c6b07fbb19dfea9b50db2c6bf6b41`。

## 实现

`EmbeddiumFixedGeometry` 在客户端注册可选的 `ChunkMeshEvent` 监听器，为含有已注册固定模型的区段添加 `MeshAppender`。通过反射解析公开接口，避免把 Xenon／Embeddium 变成正常构建和服务端的必需依赖；不访问其内部网格实现。

主线程只收集当前区段的方块实体位置。异步渲染时从接口提供的 `BlockAndTintGetter` 读取方块实体和光照，并把顶点写入接口提供的各渲染层缓冲区。空区段、相邻区段的实体和已移除的实体不会产生附加网格。

原版钩子与替代编译器共同调用 `FixedBlockEntityRenderers.renderSection`，沿用区段相对坐标、元件朝向、下层镜像、分面光照和透明层。固定网格的 partial tick 固定为零。元件模型、资源包贴图、模型注册和游戏机制保持现有契约。

测试场景为各档计算主机安装同档计算元件，并保留空槽，覆盖 L4／L6／L9 已连接与断开管道。另保留了高档主机安装 L4 元件的 Xenon 验证日志和截图。

## 验证入口

使用 Java 17，Gradle 串行执行。针对渲染的独立任务仍编译完整 main 源码：

```powershell
.\build-local.ps1 -GradleArguments @(
    '-I', 'tools/porting/fixed_geometry.init.gradle',
    'fixedGeometryTest', 'jar', '--offline'
)
```

普通渲染器的真实客户端验证：

```powershell
.\build-local.ps1 -GradleArguments @(
    '-I', 'tools/porting/fixed_geometry.init.gradle',
    'runClient', '-PfixedGeometryRenderer=vanilla', '--offline'
)
```

Xenon 真实客户端验证时传入本机安装包路径；正常构建不包含该依赖：

```powershell
.\build-local.ps1 -GradleArguments @(
    '-I', 'tools/porting/fixed_geometry.init.gradle',
    'runClient', '-PfixedGeometryRenderer=xenon',
    '-PfixedGeometryRendererJar=D:/Minecraft/1.20.0/.minecraft/versions/Project Infinity 0.1 0.0.51/mods/xenon-0.3.31+mc1.20.1.jar',
    '--offline'
)
```

机器可读结果见 `porting/fixed-geometry-rendering-validation.json`。日志、全部截图及可替换 JAR 保存在 `validation/fixed-geometry-20261023/`；代表截图位于本目录 `images/fixed-geometry-*`。

真实客户端验证覆盖九种结构、四个朝向、两种镜像和两种重复长度，每轮 144 个场景，检查成型、拆除、修复、同步和模型加载，并为正面长结构生成 18 张截图。代表截图经过人工查看，确认管道、元件和盘体恢复显示。

该结果属于隔离开发客户端中的真实区块绘制验证。原整合包仍运行此前加载的旧 JAR；普通安装、完整整合包交互及完整业务测试套件不计作本次已验收项目。替换 JAR 并重启客户端后才会加载修复。
