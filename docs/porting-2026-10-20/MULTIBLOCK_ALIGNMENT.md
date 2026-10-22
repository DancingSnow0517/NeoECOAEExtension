# 多方块结构对齐修复

本次核对存储、运算、合成系统的 L4、L6、L9 共 9 种可扩展结构。主体基线仍为 `porting/source-lock.json` 指定的 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`。参考仓库已 fetch；未引入更新 main 的业务改动。

结构坐标、组件位置和朝向规则与该基线一致。209 个相关模型及方块状态 JSON 在将 `neoforge:composite` 适配为 `forge:composite` 后完全一致。本次问题位于 Forge 事件、配置生命周期及 LDLib 1 同步适配层。

## 修复内容

- **结构不能成型、长度预览异常**：`NEConfig` 的配置加载和重载事件改为订阅 Mod 总线。三类长度上限在服务器配置加载前采用配置规格的默认值，避免初始值为 `0`。结构定义使用 `IntSupplier` 读取当前长度上限，避免在标题画面初始化后一直使用旧值；原有整数构造器和 builder API 保留。
- **成型后缺少矩阵、晶体或连接线模型**：额外模型、存储矩阵模型注册事件及冷却控制器模型替换事件改为客户端 Mod 总线订阅。修复前真实客户端检查的 24 个额外模型全部返回缺失模型。
- **同步数据变化后部件不重绘**：`NEManagedBlockEntity` 接入 LDLib 1 的 `IEnhancedManaged`，使 `@RequireRerender` 生效。客户端重建模型前检查该方块实体仍然安装在原位置，避免旧对象的延迟通知作用于替换后的方块。
- **连续拆建后客户端保留旧方块**：初始同步快照原先调用 `SPacketManagedPayload.of` 消费同步脏标记，再通过 `markAsDirty` 恢复。恢复操作同时触发持久化回调，驱动器会在区块方块广播期间再次请求更新，造成后续该区段更新丢失。现在直接读取字段并构造完整快照，保留原标记，不触发持久化回调。

此次未改变结构布局、组件数量或默认长度参数，未增加机器 UI 自动搭建按钮。

## 验证方式

定向回归测试使用真实生产类，覆盖配置加载后的上限变化、固定整数 API、客户端重绘、替换后的旧对象通知、初始快照脏标记保留及编码失败路径。

```powershell
.\build-local.ps1 -GradleArguments @(
    '--init-script', 'tools/porting/multiblock_alignment.init.gradle',
    'multiblockAlignmentTest', '--offline', '--console=plain'
)
```

真实客户端验收使用独立目录 `run/multiblock-alignment/<profile>/client`，通过正常服务器结构计算器和客户端区块渲染器验证。每种结构测试四个朝向、普通/镜像、1/3 个扩展段，共 144 组；每组检查成型、拆除组件后失效、恢复组件后重新成型以及客户端方块状态。当前验收程序还核对驱动器安装的矩阵/晶体同步及运算驱动器成型、等级同步。截图覆盖 9 种结构的普通和镜像版本。

在隔离目录的 `defaultconfigs/neoecoae-server.toml` 中设置不同于默认值的限制，可验证标题画面初始化后的配置切换：

```toml
[structure]
craftingSystemMaxLength = 9
computationSystemMaxLength = 10
storageSystemMaxLength = 11
```

```powershell
.\build-local.ps1 runClient -PportingProfile=base `
    -PclientSmoke=true -PmultiblockAlignmentSmoke=verified-base --offline
.\build-local.ps1 runClient -PportingProfile=combined `
    -PclientSmoke=true -PmultiblockAlignmentSmoke=verified-combined --offline
```

上述 Gradle 验证串行运行，结果如下：

- 基础环境 144 组、四附属组合环境 144 组全部通过，合计 288 组。组合环境确认加载 AdvancedAE、ExtendedAE、ExtendedAE Plus、AE2LT。
- 配置从标题画面的 `15/15/15` 更新为世界中的 `9/10/11`，对应存储结构预览上限从 11 更新为 7。
- 6 项定向回归测试通过；`portingPlatformTest` 的 139 项测试通过，两套任务均为 0 失败、0 跳过。两套任务包含部分相同的同步回归测试。
- 图像验收检查了组合环境的 18 张原始截图。可查看 [9 种结构](../../validation/multiblock-alignment/nine-structures.png)、[9 种镜像结构](../../validation/multiblock-alignment/nine-structures-mirrored.png) 和 [L9 镜像对照](../../validation/multiblock-alignment/l9-mirrors.png)。
- `build/libs/neoecoae-20.6.0-dev.jar` 构建及内容检查通过：1,557 个 Java 17 类，8 个关键修复类和 209 个相关资源均存在，无重复条目，无临时抓包类。

详细命令输出、截图和 JAR 的 SHA-256 见 `porting/multiblock-alignment-validation.json`。平台测试可使用 `.\build-local.ps1 portingPlatformTest --offline` 单独运行。

完整 `test` 任务仍被既有的 114 处未适配测试 API 编译错误阻断，例如工作站配方 `Serializer.CODEC` 和 `AEKeyType.REGISTRY_KEY`；记录在 `validation/multiblock-alignment-fixed-base.log`。定向测试不代表完整测试集通过，未排除任何生产业务源码。

本次开发客户端验收不代表普通安装包替换、玩家原存档验收或独立专服启动验收。临时抓包 Mixin 已移除。
