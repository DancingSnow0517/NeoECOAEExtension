# 附属扩展 API v1

适用：Minecraft 1.21.1 / NeoForge。入口版本为 `ECOAddonApi.VERSION == 1`；功能名可用 `ECOAddonApi.supports(name)` 查询。API 随完整模组 JAR 分发。

## 兼容边界

本页列出的公开接口、注册器及其上下文/描述对象属于 v1 接入契约。同一 API 大版本内通过 default 方法或重载添加能力，保留已有方法和构造器。需要删除或改变既有语义时，先提供弃用说明与迁移入口，再提高 API 大版本。此约定适用于对应 Minecraft/加载器分支，不表示不同 Minecraft 版本的 JAR 可以互换。

`ECOAddonApi`、`ECOExtensionRegistry` 和下表中的入口为公开契约。结构定义的 builder、槽位需求以及既有模型注册方法也在本页描述的范围内。求解器、CPU 实现、集群内部可变列表和存档引擎不属于附属扩展接口。

可编译接入示例：[`AddonApiExample.java`](../src/test/java/cn/dancingsnow/neoecoae/api/extension/AddonApiExample.java)。该示例随 `compileTestJava` 编译；真实注册项及行为由契约测试验证。

| 能力 | 入口 | 功能名 |
| --- | --- | --- |
| 部件/主机/驱动器待机功耗 | `ECOPowerProfiles`、`IECOTier.getComponentIdlePower` | `power_profiles` |
| 按主机交出建造定义 | `ECOMultiblockDefinitions`、`IECOTier.getBuildDefinition(kind)` | `multiblock_definitions` |
| 部件角色、驱动器上下排及排序 | `ECOMultiblockParts` | `multiblock_parts` |
| 同一槽位的多个合法部件 | `ECOMultiblockRequirement`、`ECOMultiblockRequirements` | `multiblock_requirements` |
| 独立计算晶阵 Item、每栈容量与适配器 | `IECOComputationCellItem`、`ECOComputationCells` | `computation_cells` |
| 按驱动器宿主选择线缆模型 | `ECOComputationModels` 的宿主重载/模型提供器 | `host_cable_models` |
| 高级功能资格 | `IECOTier.supportsFeature(ECOFeature)` | `tier_features` |
| 存储资源名称、图标、色条、别名 | `ECOStorageChannelDisplays` | `storage_channel_displays` |
| 外部工作站配方适配 | `ECOWorkstationRecipeAdapters` | `workstation_adapters` |
| 自定义工作站模块、升级卡及外观 | `ECOWorkstationModules` | `workstation_modules` |

## 注册和生命周期

使用附属自己的命名空间和稳定 ID，在模组初始化/注册阶段完成注册。公共注册器按优先级从高到低解析，同优先级按 ID 字典序解析，结果与模组加载顺序无关。重复 ID 抛出异常；要替换自己的注册，先 `unregister(id)` 再注册。`entries()` 返回不可变快照，`revision()` 在成功注册或撤销时递增。

一般提供器返回 null 表示继续解析，最终回落内置行为。工作站配方适配器在匹配源类型后必须返回完整结果，不能用 null 表示忽略某项成本。提供器发生异常时不会自动使用一个较宽松的规则继续执行；配方读取入口会记录错误并排除无法适配的配方。

描述回调必须只读，不提取物品、不修改世界、不加载区块。注册项跨世界保留，世界关闭不应重新注册。游戏状态变更在所属服务器线程执行。功耗、部件与结构规则应在服务端注册；客户端需要解析的档位/模型/模块也必须在客户端可用。模型路径需在模型烘焙前注册，不能等到进入世界后才声明。

## 功耗

`ECOPowerProfiles.Context` 包含实际宿主、放置态、可空档位、角色、原有底耗和盘耗。返回值单位为 AE/t，是完整待机功耗。若只想缩放原值，使用 `context.defaultPower() * multiplier`。零功耗有效；负数、NaN 和无穷大被拒绝。

普通部件读取 `IECOTier.getComponentIdlePower()`，默认 16；合成主机/核心/工作单元/通风口、存储主机、驱动器和储能单元继续传入各自原有底耗。驱动器把自身底耗与已安装盘的耗电分别交给提供器。最终结果用于 AE2 节点，功耗统计读取同一个节点值。

附属更改自身配置或状态后，可调用 `NEBlockEntity.refreshIdlePower()`。本体状态更新也会重新解析；相同结果不重复通知 AE2。本接口负责待机功耗，配方加工能耗仍由配方及原有执行事务负责。

## 主机定义与实际成型

`ECOMultiblockDefinitions.Context` 提供主机实体、`COMPUTATION/CRAFTING/STORAGE` 类别和档位。注册器优先，其后读取档位 `getBuildDefinition(kind)`，最终读取内置定义。无参 `getBuildDefinition()` 作为档位默认实现保留；同一档位有不同种类主机时使用带 kind 的形式。

主机预览、长度选择和自动搭建读取这个定义。JEI/EMI 的结构列表可继续通过 `MultiBlockDefinition.Builder.create()` 注册；`create(consumer)` 允许调用方管理展示列表。

实际成型仍通过 `NEBlockEntity.registerCalculatorFactory(type, factory)` 注入计算器。只改几何时覆写 `verifyStructure`，让父类继续处理镜像、网络状态和集群副作用。形状完全自定义时，计算器可调用 `MultiBlockPlacementService.matches(...)`，用建造计划同源的规则验证世界。

## 可替代部件和结构查询

`ECOMultiblockRequirement.of(defaultState, alternatives...)` 描述一个槽位的默认建造态与其他合法态。通过 builder 的 `setRequirement`、`setRequirementRepeatable` 放入结构。显示虚拟世界使用默认态；计划保留所有候选态，旋转/镜像时一起变换。`definition.requirements(length)` 返回不可变的槽位描述列表。

`ECOMultiblockRequirements.registerAlternatives(id, priority, expectedBlock, alternatives)` 允许为已有定义补充替代方块，公共校验函数和自动搭建均读取该规则。共享的结构属性必须相同；formed、working、mirrored、invisible、classic_visible、has_cell、lit 和网络开关显示标记属于派生态，不参与结构相等比较。复杂需求使用接收 `Context` 的谓词，明确检查朝向和其他属性。调用路径有时只能提供位置和状态，因此 `Context.level` 可以为 null。

已有合法部件会被复用，不会被拆掉重建或重复扣材料。放置前仍重新检查，预览结果不是永久有效的放置授权。

## 部件角色与面板位置

`ECOMultiblockParts.Provider` 可注册，也可由附属方块实现。`Part` 包含角色集合、可空档位、可空 `UPPER/LOWER` 行和排序值。较小排序值靠前，同值沿用原有距离/镜像规则。

计算集群、面板行和朝向渲染使用一致的上下排结果；本体负责移除旧分组、防止重复和更新 `isLowerDrive`。附属状态改变后可调用 `cluster.reclassifyDrive(drive)`，无需修改集群列表。底部传输器也可用 `TRANSMITTER` 角色识别。

本体的共享成型谓词读取声明的角色和档位。角色用于识别与布局，不会凭空创建执行后端：并行核心、线程核心、工作单元、驱动器等仍需使用对应 ECO 实体类型或其子类，并为自己的实体类型注册合适计算器。横向部件继续遵守朝向检查。

## 计算晶阵

独立 Item 实现 `IECOComputationCellItem`，提供 `getTier()`；需要按组件变化时覆写 `getTier(stack)` 和 `getComputationBytes(stack)`。容量单位为字节，必须非负。不是本附属拥有的 Item 可通过 `ECOComputationCells.register` 注册适配器。

识别入口用于手动安装、自动化插槽、计算集群容量、终局槽位检查、线缆/晶阵渲染和 Jade 档位提示。每次读取实际物品栈，不按 Item 缓存容量。内置 `ECOComputationCellItem` 实现该接口，原有子类继续有效。

## 线缆和晶阵模型

```java
ECOComputationModels.registerCableModel(tier, hostBlock, disconnectedModel, connectedModel);
```

解析顺序：按优先级的模型提供器、`(tier, hostBlock)` 专用条目、原档位条目、可选默认线缆条目。旧单参 getter 和旧注册方法保留，等价于没有宿主专用条目时的读取。未知条目返回 null，不直接解引用空记录。

显式晶阵/线缆注册中的模型会自动加入附加模型列表。动态 `registerCableModelProvider` 返回的其他模型应使用 `registerAdditionalModel(id)` 预先声明。模型提供器的宿主为正在渲染的驱动器方块。

## 高级能力

`ECOFeature` 区分网络交换机、虚拟合成、无限存储主机和无限存储盘资格。档位通过 `supportsFeature` 自述；旧档位默认判定保持原有数值比较/内置 L9 身份判定。组件档位兼容仍走 `supportsComponentTier`。

资格判断已用于实际准入与相关诊断。附属声明资格后，仍须满足本体的物理拓扑、最大结构、材料/冷却和持久化恢复条件。无限盘还必须实现完整的迁移接口并显式允许参与无限存储。

## 存储通道展示

`ECOStorageChannelDisplays.Display` 接收通道 ID、别名集合、名称、颜色、可空图标/色条图片与显示顺序。图标与色条各为独立 8×8 纹理；色条两端各保留一列边框。

服务端把实际名称、颜色和纹理位置放入面板快照，客户端不按本地数字 ID 猜通道。别名使用同一通道标识合并资源分布；同色但不同通道不会合并。统计量仍保留 BigInteger。该接口仅改变展示，不将不同 AEKey 的实际库存合并。

## 工作站配方和模块

`ECOWorkstationRecipeAdapters.register(id, priority, sourceRecipeType, adapter)` 将外部配方转换成 `LargeWorkstationRecipe`。必须保留源配方 ID 和类型，并完整提供输入、输出、AE 能耗和额外资源；无法准确表达时抛出异常使该配方停用。额外资源由本体执行器获取、记账及恢复。

自定义模块通过 `ECOWorkstationModules.Module` 注册独立 ID、升级卡 Item ID、允许的源类型、完整/简短名称、颜色及可选界面图标、芯片图和两种模型。旧枚举与 BlockState 保存值保留，自定义模块在旧枚举视图中投影为 `OTHER`，实际配方限制以模块 ID 判断。使用 `getInstalledModuleId()` 或 `getInstalledModuleDescription()` 读取完整身份。

升级卡识别、广告样板筛选、实际配方匹配、队列来源、终端分组、界面图标/芯片及成型模型均读取该身份。源类型被自定义模块认领后，需要对应卡；服务器原有的忽略卡配置仍可统一解除限制。配方/模块注册版本变化会使配方与样板广告缓存失效。

大型工作站现在区分展示分组与执行变体。`LargeWorkstationRecipes.getAll(level)` 返回每组一个代表配方；`getGroups(level)` 返回完整分组；`getGroup(level, recipeId)` 可用任一原始或借用变体 ID 查组。分组的 `supportedModules()`、`upgradeCards()`、`availableWithoutCard()` 用于展示卡片，`availableVariants(moduleId)` 提供对应卡的原始批量与费用。每个 `Variant` 保留 `sourceId`、`sourceType` 和 `borrowed`，不要用代表配方的单一来源推断整组权限。

`getAvailable(level, moduleId)` 返回当前卡的全部合法执行变体，可能多于展示条目；忽略卡配置也只解除权限，不移除材料、额外资源或最小批量。`findScaled(...)` 按完整样板匹配实际变体并比较同批次费用，返回的 `recipe().id()` 应用于保存任务。配方组不会把 32 个一批的配方变成 4 个一批。标签重载、配方替换及模块/适配器注册变更都会重建分组缓存。

界面图标为 15×15，芯片图为 28×28。可选模型为完整的控制器模型，使用北向坐标；本体按放置朝向旋转。只有成型主机使用自定义模型，原有小型工作站不受影响。缺失模型回落原模型。模块卡本身保存在原物品槽，旧存档无需转换。

## 验证范围

契约测试覆盖注册顺序、回落、撤销、功耗实际写入、结构候选复用、独立晶阵 Item、模块卡准入、配方缓存失效、同步后的自定义存储图案和模型旋转。真实客户端的资源外观与实际整合包联动需另行验收。
