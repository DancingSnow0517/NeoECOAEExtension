# 四个附属与必需基础库：兼容性矩阵

调查日期：**2026-10-20（Asia/Shanghai）**。主体源main：`dc8344f48ee866e57e02c285fdda5e0fb5da9421`；结构终端按用户要求保留1.20.1特性，来源另行针对性锁定。

本文件只覆盖 **ExtendedAE、AdvancedAE、ExtendedAE Plus、AE2LT**，以及运行/开发它们和核心所必需的基础库。其他附属不在本轮调查和移植范围。Thunderbolt是AE2LT的前置，不作为第五个独立联动目标扩展范围。

最新内容约束：**保留1.20.1结构终端，机器UI删除自动搭建按钮；大集成只保留常规、AAE、EAE、AE2LT四种。** AE2LT三类配方归同一机型；其余大集成及专属模块/卡、配方与展示入口整体移除。Plus的其他联动继续保留。EAE大集成是必留项，其配方缺口须补齐并验证，不能作为禁用机型交付。详见[主方案第6.8节](../../PORTING_1_20_1_PLAN_ZH_CN.md)。

“已发布”“所需API存在”“已经通过游戏验收”是三个不同结论。本次前两层使用官方发布、目标源码与JAR验证；第三层尚未实施。

## 1. 四附属版本与依赖矩阵

| 项目 | main实际使用 | 建议目标 / Loader / 状态 | 必需依赖与版本边界 | 建议与确定的能力边界 | 官方依据 |
|---|---|---|---|---|---|
| ExtendedAE | 1.21-2.2.36-neoforge；CF8811572；主编译/运行、IOPort Mixin、无界盘与工作站 | **1.20-1.4.21-forge / Forge / Release** | JAR声明Forge[47,)、MC[1.20.1,1.21)、AE2[15.4.1,)、GuideME[20.1.7,20.2.0)、Glodium[1.0,) | **保留并适配**；mod ID改expatternprovider；IOPort/Infinity API存在。EAE大集成必留；目标无crystal_assembler，须落实配方来源/适配并实测后交付。 | [ExtendedAE20Pinned 源码](https://github.com/GlodBlock/ExtendedAE/tree/4a815ff97b3aefe2a0524dcb97aa4bc29701677b)；[发布文件](https://github.com/GlodBlock/ExtendedAE/releases/tag/1.20-1.4.21-forge)（标签源码异常，见下文）；four-addon-published-api.json |
| AdvancedAE | 1.6.12-1.21.1；CF8564177；主编译/运行、CPU Mixin、方向/倍率样板、反应配方 | **1.3.6-1.20.1 / Forge / Release**，Modrinth **d83Wdhdn** | Forge[47,)、MC[1.20.1,1.20.2)、AE2[15.4.9,)；内嵌AE2AddonLib1.0.4-1.20.1；另需GeckoLib4.8.4（官方发布要求及源码确认） | **保留并适配**；IAdvPatternDetails→AdvPatternDetails；CPU与反应室接口在发布JAR确认。NBT/数组/任务字段要重接。 | [AdvancedAE20 源码](https://github.com/pedroksl/AdvancedAE/tree/3137d63403dd35211c886744f094886b6a81e76a)；[发布文件](https://modrinth.com/mod/advancedae/version/d83Wdhdn)；four-addon-deep-api.json |
| ExtendedAE Plus | 1.6.2；CF8755074；testImplementation和主反射桥，默认安装状态不决定是否有联动 | **1.6.3 / Forge 1.20.1 / Release**，Modrinth **HNyEHPOp** | Forge[47,)、MC[1.20.1,)、ExtendedAE ID=expatternprovider≥1.20-1.4.6-forge、AE2≥15.4.10 | **保留其他联动，移除其大集成**；倍率工厂、强制计划、虚拟合成与超级矩阵API已确认；不注册Plus大集成及专属配方/模块。 | [ExtendedAEPlus20 源码](https://github.com/GaLicn/ExtendedAE_Plus/tree/030f340fe0abbd94867afc07d5d9f9a400e88d1a)；[精确Forge文件](https://modrinth.com/mod/extendedae-plus/version/HNyEHPOp)；four-addon-published-api.json |
| AE2LT | 2.1.2-beta3；AosdXlfS；compileOnly/test/dev；本地jar属性可覆盖 | **2.1.2-beta2-forge.1.20.1 / Forge / Beta** | 实际JAR要求Forge[47.1.47,)、MC[1.20.1,1.20.2)；AE2[15.4.10,16)、GuideME[20.1.7,20.2.0)、Thunderbolt[2.0.3-beta2,2.1.0) | **保留并分能力适配**；Lightning/Tianshu批量、long输出、过载登记、三类工作站配方在；发布JAR缺insertExactCpuOutput(BigInteger)。 | [AE2LT20 源码](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/tree/b36bec80ca30f326afa119cd5e2e964fa51f3a86)；[官方版本列表](https://modrinth.com/mod/ae2-lightning-tech/versions)；AE2LT20-release-Ae2LtCraftingIntegration.javap.txt |

### ExtendedAE标签异常的处理

本次更新仓库后，`1.20-1.4.21-forge`解析到`4d28c36b370c42f6614db6719366b00f0bdd941f`，其中gradle.properties声明`minecraft_version=26.1.2`及`mod_version=26.1-1.0.4-neoforge`。**不使用这个标签的源码判断1.20.1 API**。

作为目标API参考，改用1.20.1分支中的`4a815ff97b3a...`版本提交（完整SHA在source-lock.json）；它声明1.20.1/Forge/1.4.21。远端目标分支最新`827a5368...`亦已更新检查。实际发布JAR独立验证为Forge1.20.1，Manifest记录构建时间`2026-09-19T16:16:21+0800`。源码提交/构建时间提供对应线索，**真正的发布ABI以JAR核对结果为准**，未声称做过可复现二进制构建。

证据：`rejected-version-refs.json`、`source-lock.json`、`four-addon-published-api.json`。这是“标签名称、实际源代码和发布工件须分别验证”的实测案例。

## 2. ExtendedAE：具体改动

| main位置/使用能力 | 1.20.1事实 | 实施动作 | 验收 |
|---|---|---|---|
| `compat/extendedae/ECOExtendedAEUnboundedCells` | 发布JAR仍有`common.inventory.InfinityCellInventory`；目标盘类为`InfinityCell` | 保留显式无界识别，沿目标StorageCell/KeyCounter方法取得key；不以Long.MAX_VALUE推断无界 | 无界/有限盘并存；资源投影、抽取、重启与缺席加载 |
| `mixins/compat/extendedae/ExtendedAeIOPortBlockEntityMixin` | `TileExIOPort`存在，tickingRequest及StorageCells获取盘库存路径要按15.x定位 | 重接ECO盘识别/搬运挂点，保留模拟与实际操作语义 | 普通AE2与ECO盘互搬、输出满、停止/重启、不丢不重 |
| `ExtendedAeIOPortMenuMixin` | `ContainerExIOPort`存在；setupConfig/slot调用需核对目标描述符与ordinal | 适配目标菜单槽位和盘过滤，确保可视slot与容器对应 | 放入/取出、Shift手势、错误物品拒绝 |
| `ModList`/Mixin门禁/资源路径 | 目标mod ID和namespace=`expatternprovider` | 注册、可选条件、resource ID和datagen同时改；保留需要的识别别名须有版本内用途 | 只装目标ExtendedAE时联动真正启用；完全不装可启动 |
| EAE大集成与`LargeWorkstationRecipes`的`extendedae:crystal_assembler` | 目标源码/发布JAR未提供这个配方能力；JAR类/JSON搜索无该token | EAE机型必留；明确配方来源、ID/serializer、物料/能量/返回物与datagen，评估ECO自有配方/兼容桥后验证，不宣称上游已有缺失API | 真实配方可执行，数量账本、资源和指南一致；不得以空壳或禁用模块交付 |

源码定位：[InfinityCellInventory.java](https://github.com/GlodBlock/ExtendedAE/blob/4a815ff97b3aefe2a0524dcb97aa4bc29701677b/src/main/java/com/glodblock/github/extendedae/common/inventory/InfinityCellInventory.java)、[TileExIOPort.java](https://github.com/GlodBlock/ExtendedAE/blob/4a815ff97b3aefe2a0524dcb97aa4bc29701677b/src/main/java/com/glodblock/github/extendedae/common/tileentities/TileExIOPort.java)、[ContainerExIOPort.java](https://github.com/GlodBlock/ExtendedAE/blob/4a815ff97b3aefe2a0524dcb97aa4bc29701677b/src/main/java/com/glodblock/github/extendedae/container/ContainerExIOPort.java)。发布ABI摘要：`ExtendedAE20-release-*.javap.txt`。缺配方结论来自**指定目标源码与实际JAR**，不来自联动加入main的时间。

## 3. AdvancedAE：具体改动

| main位置/使用能力 | 1.20.1事实 | 实施动作 | 验收 |
|---|---|---|---|
| `compat/advanced_ae/ECOAdvancedAEPatternScaling` | main的`IAdvPatternDetails`在目标不存在，目标为`AdvPatternDetails`；AdvProcessingPattern仍具方向信息 | 用目标接口重写ScaledAdvancedPattern，getOutputs改数组，保持物理definition/equals/hashCode与方向映射 | 不同面输入、重复原料、NBT差异、批量/单次结果等价 |
| provider判断/阻塞/configManager | AdvPatternProviderLogic在目标JAR存在 | 不只检测类存在；绑定isBlocking/getConfigManager和目标样板接口，防止误走普通无方向投料 | 普通/阻塞/定向模式；目标忙/满/部分接收 |
| CPU与job accessor五个Mixin | AdvCraftingCPULogic、ExecutingCraftingJob、TaskProgress、ElapsedTimeTracker存在 | NBT回调删现代registry参数并匹配旧签名；重新绑定job/cpu/inventory与private任务字段 | 普通计划提交、FastPath、取消、重启、进度统计 |
| `AdvancedAeCraftingServiceMixin` | main将AdvancedAE CPU纳入统一服务/候选列表 | 对照目标CPU类型与AE2 15服务字段，不以任意cast绕过精确计划限制 | 原生/ECO/AdvancedAE CPU同时在网，按main规则筛选 |
| `LargeWorkstationRecipes`反应室 | `advanced_ae:reaction`在；ReactionChamberRecipe有getInputs/getFluid/getEnergy、GenericStack output | 按目标Recipe读取而非1.21 codec；`IngredientStack`来自内嵌AE2AddonLib | 物品/流体输出、标签输入、能量、数量与实际反应配方一致 |

方向接口：[AdvProcessingPattern.java](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/patterns/AdvProcessingPattern.java)；CPU：[AdvCraftingCPULogic.java](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java)；反应配方：[ReactionChamberRecipe.java](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/recipes/ReactionChamberRecipe.java)。`AdvancedAE20-release-AdvProcessingPattern.javap.txt`验证目标实现`AdvPatternDetails`；`getDirectionMap`以LinkedHashMap实现并有桥接方法，不能把缺少I前缀接口误判为方向能力缺失。

**GeckoLib不是可删除的旁支**：该目标AdvancedAE的`mods.toml`未列GeckoLib，但官方Modrinth版本d83Wdhdn明确标为required（项目8BmcQJ2H），且`common/items/armors/QuantumArmorBase.java`第64行直接实现`GeoItem`。选定**GeckoLib4.8.4 Forge1.20.1，aC5KMoNg**；已核对官方目标分支`7d6f32db9168a24e14b327c0ad9f8f87dfc204de`及实际JAR。它作为AdvancedAE前置纳入，不扩展独立附属范围。验收除CPU/配方外，也要确认加载注册与护甲显示没有类链接错误。

**AE2AddonLib边界**：发布JAR含`META-INF/jarjar/ae2addonlib-1.0.4-1.20.1.jar`。检查根JAR找不到IngredientStack不等于缺依赖；编译确需该API时显式引入目标库，运行检查嵌套选择，不另外硬塞main的新版本前置。

## 4. ExtendedAE Plus：具体改动

| main位置/使用能力 | 1.20.1发布ABI | 实施动作与风险 | 验收 |
|---|---|---|---|
| `ECOExtendedAEPlusScaling` | `PatternScaler.createScaled(IPatternDetails,long)`及AEProcessingPattern重载存在 | ECO继续决定倍率；确认返回原pattern的“不支持”语义，不重复放大；超long批次分段 | 普通/方向/分子样板、倍率边界、数量守恒 |
| `EAEPForcedCrafting` | `IForcedCraftingPlan.eap$getManualMissingItems()`、`ForcedCraftingPlan`存在 | 保留manual-missing信息与主计划身份；处理15.x构造参数/输出数组 | 缺料、补料、强制开始、取消/重载不会丢元数据 |
| `ECOExtendedAEPlusVirtualCrafting` / blocking | `PatternProviderLogicVirtualCompatBridge.eap$compatIsVirtualCraftingEnabled()`在 | 重新定位目标provider/虚拟状态，避免把虚拟计划当立即交付 | 虚拟开关、阻塞、背压、断网恢复 |
| `ECOExtendedAEPlusMatrixBridge` | SuperAssemblerMatrixBlockEntity、ScaledMolecularAssemblerPattern及其目标构造器在；PatternCorePlusBlockEntity.eap$getSuperMatrixCluster在 | 区分控制器/样板核心绑定与同矩阵重复provider；保持批次输入所有权与输出交付 | 单/多pattern core、成型变化、重复输入、批次取消与回收 |
| `ECOExternalPatternNormalization` | target wrapper有对应getOriginal路径 | 解包只到已识别的语义层；保留倍率/方向/强制状态，防循环引用 | 嵌套包装与原始样板identity/数量一致 |
| 与AdvancedAE共装 | 目标PatternScaler识别`AdvPatternDetails`并创建ScaledProcessingPatternAdv | 本Mod封装也实现目标接口，不能继续用main的IAdvPatternDetails导致丢方向 | AdvancedAE定向样板经Plus包装后仍按正确面输入 |
| Plus大集成及`extendedae_plus:crystal_assembler_plus` | 原调查指定目标源码及JAR无此类型/配方类；检索精确token为0 | 最新范围整体移除此机型及专属模块/卡、配方、注册与展示入口；其他Plus联动照常移植 | 不生成禁用卡/占位页面；不出现第五种大集成 |

源码：[PatternScaler.java](https://github.com/GaLicn/ExtendedAE_Plus/blob/030f340fe0abbd94867afc07d5d9f9a400e88d1a/src/main/java/com/extendedae_plus/util/smartDoubling/PatternScaler.java)、[IForcedCraftingPlan.java](https://github.com/GaLicn/ExtendedAE_Plus/blob/030f340fe0abbd94867afc07d5d9f9a400e88d1a/src/main/java/com/extendedae_plus/api/crafting/IForcedCraftingPlan.java)、[PatternProviderLogicVirtualCompatBridge.java](https://github.com/GaLicn/ExtendedAE_Plus/blob/030f340fe0abbd94867afc07d5d9f9a400e88d1a/src/main/java/com/extendedae_plus/compat/PatternProviderLogicVirtualCompatBridge.java)、[PatternCorePlusBlockEntity.java](https://github.com/GaLicn/ExtendedAE_Plus/blob/030f340fe0abbd94867afc07d5d9f9a400e88d1a/src/main/java/com/extendedae_plus/content/matrix/PatternCorePlusBlockEntity.java)。对应`ExtendedAEPlus20-release-*.javap.txt`已用实际Forge发布JAR生成。JAR中出现其他名称相近的说明文本不证明注册了缺失的配方类型。

## 5. AE2LT与必需Thunderbolt：具体改动

| main位置/使用能力 | 目标发布事实 | 处理建议 | 验收 |
|---|---|---|---|
| `ECOAe2LtBatchCapability` | LightningBatchProvider与TianshuSynthesizer都在，API_VERSION=1；inspect/submit与各Request存在 | 保留按record字段绑定请求的防御；逐字段核对processingId/inputsPerCraft/requestedAmount/nonce等，不能只凭API_VERSION兼容 | preview不扣料、提交只交付一次、拒绝/部分接收/重复nonce |
| Batch结果 | acceptedAmount/maxSafeBatch/unacceptedAmount可读取 | 验证accepted≤requested且unaccepted=requested−accepted；未知提交结果不能退款后重放 | 结果异常/响应中断/背压时不丢不重 |
| `ECOAe2LtDirectDispatch` | OverloadedPatternProviderLogic、PowerCostUtil、EjectModeRegistry存在，且重度依赖private字段/方法 | 优先证明公开API可覆盖；不能覆盖的直达路径按**完整类型与描述符**适配。核对gridNode/catalog/overflow/source/autoReturn和pushBatchChunk返回所有权，不只按参数个数选重载 | 普通/无线/定向、自动返回、断网、满输出、部分接收与耗能 |
| `ECOAe2LtCraftingAdapter` | `insertCpuOutput(ICraftingCPU,UUID,AEKey,long,Actionable)`存在；**insertExactCpuOutput(...,BigInteger,...)不存在** | 普通long路径保留；精确账本留在ECO侧，建立有回执的long分段桥或明确拒接该专属路径，不能截断/假成功 | >Long.MAX_VALUE请求、分段取消/重启、无exact能力显示 |
| `ECOOverloadCpuAccountingBridge` | `prepareOverload(...)`、OverloadRegistration.canDispatch/registerAccepted(long)存在 | 在真实接受后登记正确订单/样板/最终输出，保持幂等；核对受限超载样板语义 | 输出相同的不同订单、多个CPU、拒绝与取消 |
| `ECOAe2LtCancellationBridge`/时间轮Mixin | 时间轮CPU逻辑类在；finishJob回调及job/cpu/link需对应目标 | 保留失败回收与完成释放，target NBT/字段按53项附录核对 | 成功/失败/取消各一次；跨重启待交付库存 |
| `ECOAe2LtUnboundedCells` | FixedInfiniteCellInventory在 | 保留明确盘类型识别；不通过超大有限值推断无限 | 有限/无界盘混合、key枚举、拆装与重启 |
| AE2LT IOPort两个Mixin | OverloadedIOPortBlockEntity/OverloadedIOPortMenu在 | 构造器过滤器、tickingRequest、RestrictedInputSlot的NEW/调用点逐项映射 | 各类ECO盘可搬运，错误物品仍拒绝 |
| AE2LT大集成的三类配方 | `ae2lt:overload_processing/lightning_assembly/lightning_simulation`均在目标注册源码 | 三类归同一AE2LT机型；使用目标Recipe/serializer及compat holder，保持雷电、物料和能量单位 | 一种AE2LT机型；三类各验收返回物、边界数量与背压场景 |
| Thunderbolt API（AE2LT前置） | 新路径`api.crafting.batch.IBatchCraftingProvider`、PreparedBatch、BatchJobView在，旧包路径不是本版 | 保留main双路径探测意图，锁定本版实际路径。pushBatch返回**未接收份数**；准备凭据和共享输入不能产生双抽取 | prepare→commit/取消、部分剩余、同tick多请求、ECO/原生/AdvancedAE CPU |
| 已有上游ECO适配 | 前置源码提及既有20.x ECO API | 只核对ABI契约，不使用旧NeoECO源码作为实现来源；不让上游兼容层与本Mod重复所有权 | 四附属共装，订单只有一个调度/计费/回收责任方 |

依据：[LightningBatchProvider.java](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/api/lightning/batch/LightningBatchProvider.java)、[TianshuSynthesizer.java](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/api/tianshu/synthesis/TianshuSynthesizer.java)、[Ae2LtCraftingIntegration.java](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/api/crafting/Ae2LtCraftingIntegration.java)、[OverloadedPatternProviderLogic.java](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/logic/provider/OverloadedPatternProviderLogic.java)、[ModRecipeTypes.java](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/registry/ModRecipeTypes.java)、[IBatchCraftingProvider.java](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn/blob/43730db8a4be55dc398ac58543ddd4c13aedc846/src/main/java/com/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider.java)，以及相应发布JAR的javap。

**功能缺口与范围边界**：API名称变化优先适配。EAE大集成是必留机型，配方缺口纳入必须解决的实现项；方案/来源与实际执行未验收前不算完成。Plus大集成按用户范围移除，不保留禁用入口；Plus其他联动继续适配。AE2LT exact输出缺口只影响该边界，不代表ECO精确订单、FastPath、超大存储应全局禁用。未经数量守恒/回执验证的分段桥不得宣称已等价。

## 6. 必需基础库矩阵

| 项目 | 目标/状态 | 直接原因 | 处理方式与证据 |
|---|---|---|---|
| AE2 | 15.4.11 Forge Release；最低运行版本 15.4.10 | 核心网络、CPU、key、存储和菜单 | 开发依赖锁定 15.4.11，运行范围 `[15.4.10,16)`；旧版单位表修复与验证见 [15.4.10 兼容记录](AE2_15_4_10_COMPATIBILITY.md)；[官方版本](https://modrinth.com/mod/ae2/versions)；两端源码与缺失导入清单 |
| GuideME | 20.1.15 Forge Release | AE2/ExtendedAE/AE2LT要求，核心指南按钮 | 显式Legacy mod依赖；[GuidesCommon.java](https://github.com/AppliedEnergistics/GuideME/blob/c2bc0b0c1996cf11fb71c4de348a9cbb004fc0a3/src/main/java/guideme/GuidesCommon.java) |
| LDLib | 1.0.52.a Forge Release | GUI、同步、预览 | 优先验证本项目所用 LDLib2 UI API 子集的兼容层，底层由 LDLib1 承载；允许调整 import/自定义组件后重编译，未覆盖项局部改写；设备同步与预览另核对。方案及原型验收见[主方案第 6.9 节](../../PORTING_1_20_1_PLAN_ZH_CN.md)。[LDLib1 源码](https://github.com/Low-Drag-MC/LDLib-MultiLoader/tree/1d4e4390ae58d16038845c651e122185c29f334b)；ldlib-api-compare.json |
| Registrate | MC1.20-1.3.11 Maven工件 | 核心注册DSL/数据生成 | 以对应sources JAR核对；[作者Maven](https://maven.tterrag.com/com/tterrag/registrate/Registrate/MC1.20-1.3.11/) |
| Glodium | 1.20-1.5-forge Release | ExtendedAE必需前置 | 按目标metadata显式加入，避免Legacy非传递遗漏；[官方列表](https://modrinth.com/mod/glodium/versions) |
| GeckoLib | **4.8.4 Forge1.20.1 Release**；aC5KMoNg | AdvancedAE官方发布前置及GeoItem直接类型依赖 | [目标发布](https://modrinth.com/mod/geckolib/version/aC5KMoNg)；目标JAR/源码4.8.4，内嵌mclib20；不以AdvancedAE元数据漏写为可选依据 |
| AE2AddonLib | 1.0.4-1.20.1，AdvancedAE内嵌 | Reaction IngredientStack等共享API | 内嵌JAR与目标标签已核对，IngredientStack及主类major61确认；Maven Central坐标io.github.pedroksl:ae2addonlib:1.0.4-1.20.1，编译按需modCompileOnly，运行避免重复副本 |
| Thunderbolt | 2.0.3-beta2 Forge Beta | AE2LT必需前置 | 本表与第5节只分析AE2LT需要的接口；[官方列表](https://modrinth.com/mod/thunderbolt-core/versions) |
| Mixin / MixinExtras | Loader自带Mixin；AP0.8.5候选；Extras0.5.3 Forge | 注入/WrapOperation等 | 按MDG Legacy配置AP/refmap及jarJar，确认最终依赖选择；[MDG官方锁定源码](https://github.com/neoforged/ModDevGradle/blob/e9c127db51cd59d8d19e238af6a01e071d769661/LEGACY.md) |
| Lombok/JUnit/Mockito | main1.18.34/5.11.4/5.15.2先作为Java17候选保留 | 编译生成/现有测试 | 不进入玩家必需Mod清单；在Java17和目标反混淆classpath验证 |
| Minecraft基础Java库 | 随Forge1.20.1依赖图 | Guava/Gson/Fastutil/Netty等 | 不将main高版本库/原生库强塞旧运行时；依赖解析及测试证实 |

其余可选集成不做本轮版本兼容性调查。若共享核心仍引用相关DTO/工具，按主方案的边界清单提取共享部分并移除可选入口；不因此扩大附属范围。

## 7. 推荐实施与组合顺序

1. base只有核心与必要库；所有四附属缺席也能运行。
2. 加ExtendedAE与Glodium，验证ID/IOPort/显式无界盘，以及EAE大集成补齐方案和真实配方执行。
3. 加AdvancedAE（含其内嵌AE2AddonLib，并安装GeckoLib4.8.4），验证方向接口、反应配方和CPU。
4. 加ExtendedAE Plus，分别验证倍率/强制/虚拟/矩阵，再测与AdvancedAE方向包装；确认不新增Plus大集成入口。
5. 加AE2LT和指定Thunderbolt，先long/public batch，再私有直达/过载/时间轮、最后评估exact分段边界。
6. 四附属一起进行CPU/GUI/库存和恢复测试，逐台验收常规、AAE、EAE、AE2LT四种大集成及结构终端；确认机器UI无自动搭建按钮、其他机型无入口。每个Mod“单独能启动”不代表共装时不重复调度。

所有结论均按上述具体版本；更新任一JAR必须重做API/元数据和相关组合验收。该矩阵没有宣称游戏验收已经完成。
