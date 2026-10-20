# Mixin迁移逐项清单（核心与四附属）

调查日期：2026-10-20。main基准`dc8344f48ee866e57e02c285fdda5e0fb5da9421`。原调查候选 **53项**，不是原全量63项，也不是53项已通过目标注入测试。

后续范围补充：保留1.20.1结构终端，删除机器UI自动搭建按钮，大集成仅常规、AAE、EAE、AE2LT四种；Plus其他联动保留。实施前针对结构终端接入与范围外入口清理复核Mixin/菜单动作，必要时增删清单；53项仅为现有main调查基线，不承诺调整后的最终数量。结构终端需要的公共搭建后端不得随UI按钮误删，EAE配方缺口仍是必留机型的待解决项。

范围：核心AE2/Minecraft/LDLib、终端精确显示、AdvancedAE、ExtendedAE、AE2LT及其Thunderbolt前置桥。非目标附属专属配置不纳入；核心配置中仅服务非目标功能的DigitalSingularityEnergyServiceMixin剔除。StoredEnergyAmountMixin属于核心NBT/同步，保留。源码目录中未在配置启用的ECOThunderboltProviderMixin不自动加入；不能把“文件存在”等同有效设计。

本清单保留main原始注入点/描述符，数组内非目标附属专用方法分支已省略。目标声明来自明确版本源码，发布API另有javap。出现同名方法只证明候选位置；局部变量、ordinal、调用次数、执行顺序、客户端/服务端和生产混淆必须在实施阶段验证。

## 1. 执行规则

- 核心必须的注入失败就阻断该阶段；不批量设置require=0或required=false绕过。
- Forge47使用Manifest MixinConfigs与插件门禁，不能依赖NeoForge的requiredMods声明。
- MC目标正确生成refmap；AE2/附属未混淆成员按需remap=false，混合目标单独检查。
- 无@Inject的类可能通过Accessor/接口/继承/覆盖方法扩展，同样有ABI迁移工作。
- 每项至少保存目标描述符/源位置、注入日志或export、一个功能正例与缺席/异常反例；最终在普通安装的reobf JAR验证。
- ExtendedAE源码用明确1.20.1提交4a815ff9…；异常发布tag不作为源码依据。

## 2. 汇总

| # | main类 | 目标类别 | 主要风险/动作 |
|---:|---|---|---|
| 1 | [AdvancedAeCraftingJobAccessor](#m01) | AdvancedAE1.3.6 | 1.3.6的job无suspended；用ECO持有的@Unique暂停字段/接口取代同名Accessor，并序列化到job NBT。AdvancedAE执行入口同时阻止快路和原生重试；waitingFor/任务账本按真实接受量更新，覆盖不确定提交后保存、重启、取消和显式恢复。 || 2 | [AdvancedAeElapsedTimeTrackerInvoker](#m02) | AdvancedAE1.3.6 | 目标addMaxItems仍须按发布描述符验证；只在有效接受批次后累加进度量。 || 3 | [AdvancedAeTaskProgressAccessor](#m03) | AdvancedAE1.3.6 | TaskProgress.value存在于发布JAR；保持long边界与任务状态语义，不靠未检查cast兼容。 || 4 | [AdvancedAeCraftingCpuLogicMixin](#m04) | AdvancedAE1.3.6 | 发布JAR有executeCrafting/trySubmitJob/tick/finishJob，NBT方法仅CompoundTag。public方法签名与内部字段同时重接；禁止Native/ECO/AAE重复抽取。 || 5 | [AdvancedAeCraftingServiceMixin](#m05) | AdvancedAE1.3.6 | 服务/CPU扩展接口与字段访问同样要迁移，不因没有@Inject省略。确认1.3.6 CPU类型能进入合法候选，而精确计划仍受能力限制。 || 6 | [ExtendedAeIOPortBlockEntityMixin](#m06) | ExtendedAE1.4.21 | 发布ForgeJAR有TileExIOPort；以1.20.1明确提交为源，不能使用异常的发布tag源码。重绑StorageCells调用并验证搬盘完整性。 || 7 | [ExtendedAeIOPortMenuMixin](#m07) | ExtendedAE1.4.21 | 目标ContainerExIOPort的setupConfig/addSlot以发布描述符核对；mod ID门禁改expatternprovider，保留只有正确输入槽放宽。 || 8 | [Ae2LtTimeWheelCraftingCpuLogicMixin](#m08) | AE2LT2.1.2-beta2 / 前置 | 发布时间轮类存在；finishJob的require=0不能成为未清理完成/取消账本的借口。目标回调、job/cpu/link实际字段和错误分支分别验证。 || 9 | [ECOThunderboltWorkstationProviderMixin](#m09) | AE2LT2.1.2-beta2 / 前置 | 目标是本Mod的LargeWorkstationPatternProvider，增加AE2LT前置需要的批量接口；条件加载、返回未接受份数、所有权和保存责任保持明确。 || 10 | [Ae2LtIOPortBlockEntityMixin](#m10) | AE2LT2.1.2-beta2 / 前置 | 目标构造过滤器和tickingRequest同时处理；API存在仍要核对ordinal和方法描述符，目标缺席不得链接其类型。 || 11 | [Ae2LtIOPortMenuMixin](#m11) | AE2LT2.1.2-beta2 / 前置 | RestrictedInputSlot的NEW挂点按目标构造器参数改写；校验普通/ECO盘、错误物品和Shift移动。 || 12 | [AECraftingPatternAccessor](#m12) | 核心AE2 / Minecraft / LDLib1 | definition仍为AEItemKey，保留读取用途；其上层introspection必须从组件模型改为CraftingPatternEncoding/NBT，并适配sparseInputs数组。 || 13 | [CraftingPlanSummaryAccessor](#m13) | 核心AE2 / Minecraft / LDLib1 | 核对entries字段的目标泛型与摘要条目结构；精确数量附加数据不要通过long摘要丢失。用确认菜单真实发送/接收验证。 || 14 | [DelegatingMEInventoryAccessor](#m14) | 核心AE2 / Minecraft / LDLib1 | 核对delegate字段类型，保留解包与真实库存身份，SIMULATE不能写入。不要把代理层解包变成绕过过滤/权限。 || 15 | [PatternProviderLogicAccessor](#m15) | 核心AE2 / Minecraft / LDLib1 | sendList与onPushPatternSuccess在目标源码可定位；核对列表元素和参数类型，只有实际投料成功后推进状态/锁。 || 16 | [CraftingCalculationMixin](#m16) | 核心AE2 / Minecraft / LDLib1 | 高风险：构造器、runCraftAttempt、run的替换与结果补全一起适配。保留独立规划器、取消与main原始计划契约，不以返回SUCCESS代替可执行结果。 || 17 | [AEProcessingPatternComponentMixin](#m17) | 核心AE2 / Minecraft / LDLib1 | pushInputsToExternalInventory入口保留；组件模糊匹配降为NBT语义，保持具体key、方向/槽顺序、容器返回与输入所有权。 || 18 | [Ae2CpuJobAccessor](#m18) | 核心AE2 / Minecraft / LDLib1 | tasks/waitingFor/timeTracker/link/finalOutput有目标；15.4.11的job没有suspended，不能保留两个同名Accessor。改为ECO持有的@Unique暂停状态/接口，接入目标job NBT构造与writeToNBT；原生executeCrafting入口同步门禁，确保异常后慢路也不重放。 || 19 | [Ae2CpuTaskAccessor](#m19) | 核心AE2 / Minecraft / LDLib1 | 目标嵌套TaskProgress.value需确认long及可写性；加减必须计已接受量，覆盖0、上界和取消，不用强转隐藏溢出。 || 20 | [Ae2CpuTimeTrackerAccessor](#m20) | 核心AE2 / Minecraft / LDLib1 | 核对addMaxItems签名；按批次接受数更新统计，避免进度/ETA与真实任务量不一致。 || 21 | [Ae2CraftingCpuFastPathMixin](#m21) | 核心AE2 / Minecraft / LDLib1 | executeCrafting/trySubmitJob/tick/finishJob仍有对应路径；目标readFromNBT/writeToNBT只有CompoundTag。WrapOperation必须命中实际pushPattern调用并只接管一次输入。 || 22 | [CraftingPlanMixin](#m22) | 核心AE2 / Minecraft / LDLib1 | 这是接口/字段扩展，不是没有@Inject就可以略过。核对目标计划的构造、字段与精确元数据附着/复制；保留原计划身份与不可执行状态。 || 23 | [CraftingServiceMixin](#m23) | 核心AE2 / Minecraft / LDLib1 | 最高风险：供应器变更、服务tick、CPU选择/提交与产物注入相互关联。目标没有三种GlobalCraftingProvider方法；本轮收敛后不保留对应调用者，移除这三个注入方法名，使用现有addNode/removeNode/refreshNodeCraftingProvider推进providerRevision并失效游标。其余FIELD/INVOKE_ASSIGN/shift/order/局部变量逐项重接。 || 24 | [CraftingTreeNodeMixin](#m24) | 核心AE2 / Minecraft / LDLib1 | 按15.x模板/替代输入模型重接findCraftedStack与getValidItemTemplates，保留循环/种子/模糊规则；与独立规划器和原生回退结果做对照。 || 25 | [PatternProviderDiagnosticsMixin](#m25) | 核心AE2 / Minecraft / LDLib1 | WrapMethod和多个内部调用共同提供失败原因；目标findAdapter/adapterAcceptsAll在。逐个核对isEmpty/contains等通用调用的真实含义，使用slice或更稳定入口避免包裹错分支。 || 26 | [CellWorkbenchMenuMixin](#m26) | 核心AE2 / Minecraft / LDLib1 | iterateCellStacks目标存在但属内部方法；适配ECO盘key枚举与过滤语义，测试空盘、内容盘、模拟读取与拆盘返回。 || 27 | [CraftConfirmMenuMixin](#m27) | 核心AE2 / Minecraft / LDLib1 | 最高风险：构造、规划、摘要、CPU选择、开始提交和锁状态。移除非目标附属方法分支，保留Plus强制计划合同；目标网络/GUI类型重接，禁止一个点击提交两次。 || 28 | [CraftConfirmMenuForceStartMixin](#m28) | 核心AE2 / Minecraft / LDLib1 | 服务器startJob前的强制开始条件必须保留缺料/权限/计划有效性约束；同Plus共装验证flag的清除与错误回退，不能只改客户端按钮。 || 29 | [CraftingCpuListEntryMixin](#m29) | 核心AE2 / Minecraft / LDLib1 | 目标嵌套条目的readFromPacket/writeToPacket需成对改为目标buffer和字段格式，双方使用同一协议；验证多种CPU条目不偏移解码。 || 30 | [CraftingCpuMenuMixin](#m30) | 核心AE2 / Minecraft / LDLib1 | 目标没有toggleScheduling；main此处实际切换ECO任务暂停，不能误接15.x的CPU_SELECTION_MODE（自动选择策略）。新增本Mod菜单暂停/恢复动作及服务端校验；目标CraftingStatus只有五参构造、无暂停位，用独立同步状态保留展示。setCPU/cancel/removed/broadcast按目标重接。 || 31 | [CraftingStatusMenuMixin](#m31) | 核心AE2 / Minecraft / LDLib1 | createCpuList中的ArrayList.add注入可能匹配多次，核对实际列表/条目与顺序。只扩展属于ECO/选定CPU的展示信息。 || 32 | [IOPortMenuMixin](#m32) | 核心AE2 / Minecraft / LDLib1 | 核对setupConfig/addSlot目标描述符和ordinal=0，保持只修改盘输入槽，不能意外放宽升级卡或输出槽。 || 33 | [PatternEncodingTermMenuMixin](#m33) | 核心AE2 / Minecraft / LDLib1 | main写死五参数构造器描述符和第一次registerClientAction调用；按15.x构造器真实签名重接，在服务端动作登记后附加功能。 || 34 | [AppEngInternalInventoryMixin](#m34) | 核心AE2 / Minecraft / LDLib1 | onContentsChanged通知与LDLib1 IContentChangeAware接口一起迁移；保留批次合并通知和缓存失效，避免递归或漏同步。 || 35 | [CellWorkbenchBlockEntityMixin](#m35) | 核心AE2 / Minecraft / LDLib1 | 在getCellConfigInventory读取前处理ECO盘配置，改为NBT与目标盘接口；不能在客户端预览时改写服务器盘内容。 || 36 | [IOPortBlockEntityMixin](#m36) | 核心AE2 / Minecraft / LDLib1 | tickingRequest获取CellInventory路径保留；目标StorageCells/StorageCell签名按15.x绑定，转移统计、模拟、拆装和满量背压必须验证。 || 37 | [NativeMEStorageCellFilterMixin](#m37) | 核心AE2 / Minecraft / LDLib1 | 两个内部过滤器各自核对存在性与allowInsert签名。若其中一个改成其它实现，改挂对应宿主过滤构造点；不要只让一个ME驱动器类型支持ECO盘。 || 38 | [NetworkStorageMixin](#m38) | 核心AE2 / Minecraft / LDLib1 | extract/getAvailableStacks绕行保持排除集、权限、模拟和真实库存身份；不得因加入精确显示而改变网络抽取/计数语义。 || 39 | [StoredEnergyAmountMixin](#m39) | 核心AE2 / Minecraft / LDLib1 | 核心同步/NBT桥，必须保留；目标INBTSerializable序列化方法无HolderLookup.Provider参数，迁移LDLib1 IContentChangeAware并核对setStored/getAmount。 || 40 | [AsyncThreadDataMixin](#m40) | 核心AE2 / Minecraft / LDLib1 | 目标包为com.lowdragmc.lowdraglib.async.AsyncThreadData。核对addAsyncLogic是否仍调用CopyOnWriteArrayList.add；若已有等价去重则避免重复补丁，否则重接安全登记点。 || 41 | [LightningBoltMixin](#m41) | 核心AE2 / Minecraft / LDLib1 | Minecraft tick内铜块处理调用是版本敏感定位，按1.20.1官方映射/refmap确认描述符；真实服务端雷击验证，不扩大为每tick重复业务效果。 || 42 | [MenuScreensAccessor](#m42) | 核心AE2 / Minecraft / LDLib1 | 目标SCREENS字段和ScreenConstructor泛型需通过MC映射/AP绑定；客户端侧注册，专服不可加载GUI类型。 || 43 | [AEBaseScreenGrayToolbarMixin](#m43) | 核心AE2 / Minecraft / LDLib1 | 目标VerticalButtonBar构造/样式接口与19.x不同，确认NEW发生位置；优先用目标主题/构造适配保留灰色工具栏外观。 || 44 | [CellWorkbenchScreenMixin](#m44) | 核心AE2 / Minecraft / LDLib1 | 本类有继承/覆盖逻辑，不能按无注入注解跳过。目标Screen/菜单槽位和新NBT盘识别一起适配，保留过滤与提示行为。 || 45 | [CpuSelectionListMixin](#m45) | 核心AE2 / Minecraft / LDLib1 | InfoBar.add的ordinal=1/2和PoseStack.popPose位置需重查；旧版可能没有同一组调用。通过目标CPU数据绘制等价信息，避免让字节提示错位。 || 46 | [CraftConfirmScreenMixin](#m46) | 核心AE2 / Minecraft / LDLib1 | updateBeforeRender/start及GuiText ordinal需重接；UI状态由服务端计划驱动，精确数量和拒绝原因真实，强制开始只发一次请求。 || 47 | [CraftingCpuScreenMixin](#m47) | 核心AE2 / Minecraft / LDLib1 | render里的List.isEmpty不是稳定语义标识，确认包裹的是任务列表；可改由ECO菜单状态决定展示，避免影响其它空列表。 || 48 | [CraftingStatusTableMixin](#m48) | 核心AE2 / Minecraft / LDLib1 | 按目标条目描述/tooltip入口重建精确数量信息；缺失同名方法时移到目标renderer实际调用处，保留单位/资源key和多行提示。 || 49 | [PatternEncodingTermScreenMixin](#m49) | 核心AE2 / Minecraft / LDLib1 | 含覆盖/扩展行为；目标按钮类型、布局、鼠标处理按AE2 15重建，服务端动作与屏幕按钮保持对应。 || 50 | [ModelBakeryMixin](#m50) | 核心AE2 / Minecraft / LDLib1 | 按1.20.1 getModel及UnbakedModel类型重接；测试缺资源回退、classic pack、动态模型和资源重载，不能只验证JSON可解析。 || 51 | [MEStorageMenuExactAmountMixin](#m51) | AE2 15终端 | 捕获实际getAvailableStacks快照并用本Mod通道发送精确信息；不改写AE2 long库存语义，菜单更换/断连清理旧快照。 || 52 | [MEStorageScreenExactAmountMixin](#m52) | AE2 15终端 | containerTick/removed与数量/tooltip实际渲染点一起适配；旧版字段、buffer与GUI参数不同。测试大数显示、关闭重开和小窗口真实槽位。 || 53 | [RepoExactAmountMixin](#m53) | AE2 15终端 | getComparator的目标返回类型、排序方向及缓存失效核对；缺失同名入口时接目标实际排序器，BigInteger排序不得退回double/饱和long。 |
## 3. 逐项目标与注入点

<a id="m01"></a>

### 1. AdvancedAeCraftingJobAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/accessor/AdvancedAeCraftingJobAccessor.java L14](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/accessor/AdvancedAeCraftingJobAccessor.java#L14)；配置`neoecoae.compat.advanced_ae.mixins.json`，侧别`mixins`。
- main目标：`net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob`。
- 迁移判断：1.3.6的job无suspended；用ECO持有的@Unique暂停字段/接口取代同名Accessor，并序列化到job NBT。AdvancedAE执行入口同时阻止快路和原生重试；waitingFor/任务账本按真实接受量更新，覆盖不确定提交后保存、重启、取消和显式恢复。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("link")` |
| `@Accessor("waitingFor")` |
| `@Accessor("tasks")` |
| `@Accessor("timeTracker")` |
| `@Accessor("suspended")` |
| `@Accessor("suspended")` |
| `@Accessor("finalOutput")` |

目标定位：[src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java)。

**该目标源文件未出现这些main成员名**：`suspended`。需确认父类或改接目标实际入口，不能保留失效注入。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L25](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L25)：`private static final String NBT_LINK = "link";`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L27](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L27)：`private static final String NBT_FINAL_OUTPUT = "finalOutput";`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L28](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L28)：`private static final String NBT_WAITING_FOR = "waitingFor";`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L29](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L29)：`private static final String NBT_TIME_TRACKER = "timeTracker";`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L31](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L31)：`private static final String NBT_TASKS = "tasks";`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L34](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L34)：`final CraftingLink link;`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L35](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L35)：`final ListCraftingInventory waitingFor;`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L36](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L36)：`final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java L37](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java#L37)：`final ElapsedTimeTracker timeTracker;`

<a id="m02"></a>

### 2. AdvancedAeElapsedTimeTrackerInvoker

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/accessor/AdvancedAeElapsedTimeTrackerInvoker.java L10](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/accessor/AdvancedAeElapsedTimeTrackerInvoker.java#L10)；配置`neoecoae.compat.advanced_ae.mixins.json`，侧别`mixins`。
- main目标：`net.pedroksl.advanced_ae.common.logic.ElapsedTimeTracker`。
- 迁移判断：目标addMaxItems仍须按发布描述符验证；只在有效接受批次后累加进度量。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Invoker("addMaxItems")` |

目标定位：[src/main/java/net/pedroksl/advanced_ae/common/logic/ElapsedTimeTracker.java](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ElapsedTimeTracker.java)。

<a id="m03"></a>

### 3. AdvancedAeTaskProgressAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/accessor/AdvancedAeTaskProgressAccessor.java L8](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/accessor/AdvancedAeTaskProgressAccessor.java#L8)；配置`neoecoae.compat.advanced_ae.mixins.json`，侧别`mixins`。
- main目标：`net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress`。
- 迁移判断：TaskProgress.value存在于发布JAR；保持long边界与任务状态语义，不靠未检查cast兼容。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("value")` |
| `@Accessor("value")` |

目标定位：[src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java)。

<a id="m04"></a>

### 4. AdvancedAeCraftingCpuLogicMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/crafting/AdvancedAeCraftingCpuLogicMixin.java L36](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/crafting/AdvancedAeCraftingCpuLogicMixin.java#L36)；配置`neoecoae.compat.advanced_ae.mixins.json`，侧别`mixins`。
- main目标：`net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic`。
- 迁移判断：发布JAR有executeCrafting/trySubmitJob/tick/finishJob，NBT方法仅CompoundTag。public方法签名与内部字段同时重接；禁止Native/ECO/AAE重复抽取。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "finishJob", at = @At("HEAD"), remap = false)` |
| `@Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, remap = false)` |
| `@Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true, remap = false)` |
| `@Inject(method = "tickCraftingLogic", at = @At("HEAD"), remap = false)` |
| `@Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)` |
| `@Inject(method = "writeToNBT", at = @At("TAIL"), remap = false)` |
| `@WrapOperation( method = "executeCrafting", at = @At( value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))` |

目标定位：[src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java L71](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java#L71)：`public ICraftingSubmitResult trySubmitJob(`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java L114](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java#L114)：`public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java L160](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java#L160)：`public int executeCrafting(`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java L309](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java#L309)：`private void finishJob(boolean success) {`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java L403](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java#L403)：`public void readFromNBT(CompoundTag data) {`
- [src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java L417](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic.java#L417)：`public void writeToNBT(CompoundTag data) {`

<a id="m05"></a>

### 5. AdvancedAeCraftingServiceMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/crafting/AdvancedAeCraftingServiceMixin.java L23](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/advancedae/crafting/AdvancedAeCraftingServiceMixin.java#L23)；配置`neoecoae.compat.advanced_ae.mixins.json`，侧别`mixins`。
- main目标：`appeng.me.service.CraftingService`。
- 迁移判断：服务/CPU扩展接口与字段访问同样要迁移，不因没有@Inject省略。确认1.3.6 CPU类型能进入合法候选，而精确计划仍受能力限制。

无标准注入注解；需要核对接口/Shadow/覆盖方法。main声明摘要：

```java
@Shadow @Final private IGrid grid;
public long neoecoae$insertIntoAdvancedAeCpuForJob(
```

目标定位：[src/main/java/appeng/me/service/CraftingService.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/me/service/CraftingService.java L87](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L87)：`private static final Comparator<CraftingCPUCluster> FAST_FIRST_COMPARATOR = Comparator`
- [src/main/java/appeng/me/service/CraftingService.java L95](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L95)：`private static final Comparator<CraftingCPUCluster> FAST_LAST_COMPARATOR = Comparator`
- [src/main/java/appeng/me/service/CraftingService.java L99](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L99)：`private static final ExecutorService CRAFTING_POOL;`
- [src/main/java/appeng/me/service/CraftingService.java L102](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L102)：`final ThreadFactory factory = ar -> {`
- [src/main/java/appeng/me/service/CraftingService.java L103](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L103)：`final Thread crafting = new Thread(ar, "AE Crafting Calculator");`
- [src/main/java/appeng/me/service/CraftingService.java L116](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L116)：`private final Set<CraftingCPUCluster> craftingCPUClusters = new HashSet<>();`
- [src/main/java/appeng/me/service/CraftingService.java L117](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L117)：`private final Map<IGridNode, StackWatcher<ICraftingWatcherNode>> craftingWatchers = new HashMap<>();`
- [src/main/java/appeng/me/service/CraftingService.java L118](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L118)：`private final IGrid grid;`
- [src/main/java/appeng/me/service/CraftingService.java L119](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L119)：`private final NetworkCraftingProviders craftingProviders = new NetworkCraftingProviders();`
- [src/main/java/appeng/me/service/CraftingService.java L120](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L120)：`private final Map<UUID, CraftingLinkNexus> craftingLinks = new HashMap<>();`
- [src/main/java/appeng/me/service/CraftingService.java L121](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L121)：`private final Multimap<AEKey, StackWatcher<ICraftingWatcherNode>> interests = HashMultimap.create();`
- [src/main/java/appeng/me/service/CraftingService.java L122](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L122)：`private final InterestManager<StackWatcher<ICraftingWatcherNode>> interestManager = new InterestManager<>(`
- [src/main/java/appeng/me/service/CraftingService.java L124](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L124)：`private final IEnergyService energyGrid;`
- [src/main/java/appeng/me/service/CraftingService.java L125](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L125)：`private final Set<AEKey> currentlyCrafting = new HashSet<>();`
- [src/main/java/appeng/me/service/CraftingService.java L126](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L126)：`private final Set<AEKey> currentlyCraftable = new HashSet<>();`
- [src/main/java/appeng/me/service/CraftingService.java L127](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L127)：`private long lastProcessedCraftingLogicChangeTick;`
- [src/main/java/appeng/me/service/CraftingService.java L128](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L128)：`private long lastProcessedCraftableChangeTick;`
- [src/main/java/appeng/me/service/CraftingService.java L129](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L129)：`private boolean updateList = false;`
- [src/main/java/appeng/me/service/CraftingService.java L131](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L131)：`public CraftingService(IGrid grid, IStorageService storageGrid, IEnergyService energyGrid) {`
- [src/main/java/appeng/me/service/CraftingService.java L141](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L141)：`public void onServerEndTick() {`

<a id="m06"></a>

### 6. ExtendedAeIOPortBlockEntityMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/extendedae/ExtendedAeIOPortBlockEntityMixin.java L16](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/extendedae/ExtendedAeIOPortBlockEntityMixin.java#L16)；配置`neoecoae.compat.extendedae.mixins.json`，侧别`mixins`。
- main目标：`com.glodblock.github.extendedae.common.tileentities.TileExIOPort`。
- 迁移判断：发布ForgeJAR有TileExIOPort；以1.20.1明确提交为源，不能使用异常的发布tag源码。重绑StorageCells调用并验证搬盘完整性。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapOperation(method = "tickingRequest", at = @At(value = "INVOKE", target = "Lappeng/api/storage/StorageCells;getCellInventory(Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)Lappeng/api/storage/cells/StorageCell;"))` |

目标定位：[src/main/java/com/glodblock/github/extendedae/common/tileentities/TileExIOPort.java](https://github.com/GlodBlock/ExtendedAE/blob/4a815ff97b3aefe2a0524dcb97aa4bc29701677b/src/main/java/com/glodblock/github/extendedae/common/tileentities/TileExIOPort.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/com/glodblock/github/extendedae/common/tileentities/TileExIOPort.java L28](https://github.com/GlodBlock/ExtendedAE/blob/4a815ff97b3aefe2a0524dcb97aa4bc29701677b/src/main/java/com/glodblock/github/extendedae/common/tileentities/TileExIOPort.java#L28)：`public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {`

<a id="m07"></a>

### 7. ExtendedAeIOPortMenuMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/extendedae/ExtendedAeIOPortMenuMixin.java L18](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/extendedae/ExtendedAeIOPortMenuMixin.java#L18)；配置`neoecoae.compat.extendedae.mixins.json`，侧别`mixins`。
- main目标：`com.glodblock.github.extendedae.container.ContainerExIOPort`。
- 迁移判断：目标ContainerExIOPort的setupConfig/addSlot以发布描述符核对；mod ID门禁改expatternprovider，保留只有正确输入槽放宽。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapOperation(method = "setupConfig", at = @At(value = "INVOKE", target = "Lcom/glodblock/github/extendedae/container/ContainerExIOPort;addSlot(Lnet/minecraft/world/inventory/Slot;Lappeng/menu/SlotSemantic;)Lnet/minecraft/world/inventory/Slot;", ordinal = 0))` |

目标定位：[src/main/java/com/glodblock/github/extendedae/container/ContainerExIOPort.java](https://github.com/GlodBlock/ExtendedAE/blob/4a815ff97b3aefe2a0524dcb97aa4bc29701677b/src/main/java/com/glodblock/github/extendedae/container/ContainerExIOPort.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/com/glodblock/github/extendedae/container/ContainerExIOPort.java L34](https://github.com/GlodBlock/ExtendedAE/blob/4a815ff97b3aefe2a0524dcb97aa4bc29701677b/src/main/java/com/glodblock/github/extendedae/container/ContainerExIOPort.java#L34)：`protected void setupConfig() {`

<a id="m08"></a>

### 8. Ae2LtTimeWheelCraftingCpuLogicMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/thunderbolt/Ae2LtTimeWheelCraftingCpuLogicMixin.java L12](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/thunderbolt/Ae2LtTimeWheelCraftingCpuLogicMixin.java#L12)；配置`neoecoae.compat.thunderbolt.mixins.json`，侧别`mixins`。
- main目标：`com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic`。
- 迁移判断：发布时间轮类存在；finishJob的require=0不能成为未清理完成/取消账本的借口。目标回调、job/cpu/link实际字段和错误分支分别验证。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "finishJob", at = @At("HEAD"), require = 0)` |

目标定位：[src/main/java/com/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic.java](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/com/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic.java L1365](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic.java#L1365)：`private void finishJob(boolean success) {`

<a id="m09"></a>

### 9. ECOThunderboltWorkstationProviderMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/thunderbolt/ECOThunderboltWorkstationProviderMixin.java L14](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/thunderbolt/ECOThunderboltWorkstationProviderMixin.java#L14)；配置`neoecoae.compat.thunderbolt.mixins.json`，侧别`mixins`。
- main目标：`cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider`。
- 迁移判断：目标是本Mod的LargeWorkstationPatternProvider，增加AE2LT前置需要的批量接口；条件加载、返回未接受份数、所有权和保存责任保持明确。

无标准注入注解；需要核对接口/Shadow/覆盖方法。main声明摘要：

```java
public long getBatchCapacity(IPatternDetails pattern) {
public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long requested) {
public long pushBatch(IPatternDetails pattern, KeyCounter[] oneCopy, long requested, BatchJobView job) {
```

目标是本Mod自己的main派生类；以对应业务实现迁移后的接口和所有权合同为准，条件加载外部接口。

<a id="m10"></a>

### 10. Ae2LtIOPortBlockEntityMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/thunderbolt/Ae2LtIOPortBlockEntityMixin.java L17](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/thunderbolt/Ae2LtIOPortBlockEntityMixin.java#L17)；配置`neoecoae.compat.thunderbolt.mixins.json`，侧别`mixins`。
- main目标：`com.moakiee.ae2lt.blockentity.OverloadedIOPortBlockEntity`。
- 迁移判断：目标构造过滤器和tickingRequest同时处理；API存在仍要核对ordinal和方法描述符，目标缺席不得链接其类型。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lappeng/util/inv/AppEngInternalInventory;setFilter(Lappeng/util/inv/filter/IAEItemFilter;)V", ordinal = 0), index = 0)` |
| `@WrapOperation(method = "tickingRequest", at = @At(value = "INVOKE", target = "Lappeng/api/storage/StorageCells;getCellInventory(Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)Lappeng/api/storage/cells/StorageCell;"))` |

目标定位：[src/main/java/com/moakiee/ae2lt/blockentity/OverloadedIOPortBlockEntity.java](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/blockentity/OverloadedIOPortBlockEntity.java)。

<a id="m11"></a>

### 11. Ae2LtIOPortMenuMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/compat/thunderbolt/Ae2LtIOPortMenuMixin.java L13](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/compat/thunderbolt/Ae2LtIOPortMenuMixin.java#L13)；配置`neoecoae.compat.thunderbolt.mixins.json`，侧别`mixins`。
- main目标：`com.moakiee.ae2lt.menu.OverloadedIOPortMenu`。
- 迁移判断：RestrictedInputSlot的NEW挂点按目标构造器参数改写；校验普通/ECO盘、错误物品和Shift移动。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapOperation(method = "setupConfig", at = @At(value = "NEW", target = "appeng/menu/slot/RestrictedInputSlot"))` |

目标定位：[src/main/java/com/moakiee/ae2lt/menu/OverloadedIOPortMenu.java](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/blob/b36bec80ca30f326afa119cd5e2e964fa51f3a86/src/main/java/com/moakiee/ae2lt/menu/OverloadedIOPortMenu.java)。

<a id="m12"></a>

### 12. AECraftingPatternAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/accessor/AECraftingPatternAccessor.java L8](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/accessor/AECraftingPatternAccessor.java#L8)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.pattern.AECraftingPattern`。
- 迁移判断：definition仍为AEItemKey，保留读取用途；其上层introspection必须从组件模型改为CraftingPatternEncoding/NBT，并适配sparseInputs数组。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("definition")` |

目标定位：[src/main/java/appeng/crafting/pattern/AECraftingPattern.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/pattern/AECraftingPattern.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/crafting/pattern/AECraftingPattern.java L58](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/pattern/AECraftingPattern.java#L58)：`private final AEItemKey definition;`
- [src/main/java/appeng/crafting/pattern/AECraftingPattern.java L75](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/pattern/AECraftingPattern.java#L75)：`public AECraftingPattern(AEItemKey definition, Level level) {`

<a id="m13"></a>

### 13. CraftingPlanSummaryAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/accessor/CraftingPlanSummaryAccessor.java L10](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/accessor/CraftingPlanSummaryAccessor.java#L10)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.me.crafting.CraftingPlanSummary`。
- 迁移判断：核对entries字段的目标泛型与摘要条目结构；精确数量附加数据不要通过long摘要丢失。用确认菜单真实发送/接收验证。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("entries")` |

目标定位：[src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java L52](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java#L52)：`private final List<CraftingPlanSummaryEntry> entries;`
- [src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java L54](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java#L54)：`public CraftingPlanSummary(long usedBytes, boolean simulation, List<CraftingPlanSummaryEntry> entries) {`

<a id="m14"></a>

### 14. DelegatingMEInventoryAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/accessor/DelegatingMEInventoryAccessor.java L8](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/accessor/DelegatingMEInventoryAccessor.java#L8)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.me.storage.DelegatingMEInventory`。
- 迁移判断：核对delegate字段类型，保留解包与真实库存身份，SIMULATE不能写入。不要把代理层解包变成绕过过滤/权限。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("delegate")` |

目标定位：[src/main/java/appeng/me/storage/DelegatingMEInventory.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/storage/DelegatingMEInventory.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/me/storage/DelegatingMEInventory.java L20](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/storage/DelegatingMEInventory.java#L20)：`private MEStorage delegate;`
- [src/main/java/appeng/me/storage/DelegatingMEInventory.java L22](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/storage/DelegatingMEInventory.java#L22)：`public DelegatingMEInventory(MEStorage delegate) {`
- [src/main/java/appeng/me/storage/DelegatingMEInventory.java L30](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/storage/DelegatingMEInventory.java#L30)：`protected void setDelegate(MEStorage delegate) {`

<a id="m15"></a>

### 15. PatternProviderLogicAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/accessor/PatternProviderLogicAccessor.java L12](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/accessor/PatternProviderLogicAccessor.java#L12)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.helpers.patternprovider.PatternProviderLogic`。
- 迁移判断：sendList与onPushPatternSuccess在目标源码可定位；核对列表元素和参数类型，只有实际投料成功后推进状态/锁。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("sendList")` |
| `@Invoker("onPushPatternSuccess")` |

目标定位：[src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java L91](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java#L91)：`public static final String NBT_SEND_LIST = "sendList";`
- [src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java L111](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java#L111)：`private final List<GenericStack> sendList = new ArrayList<>();`
- [src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java L380](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java#L380)：`private void onPushPatternSuccess(IPatternDetails pattern) {`

<a id="m16"></a>

### 16. CraftingCalculationMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingCalculationMixin.java L31](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingCalculationMixin.java#L31)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.CraftingCalculation`。
- 迁移判断：高风险：构造器、runCraftAttempt、run的替换与结果补全一起适配。保留独立规划器、取消与main原始计划契约，不以返回SUCCESS代替可执行结果。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "<init>", at = @At("RETURN"))` |
| `@Inject(method = "runCraftAttempt", at = @At("HEAD"), cancellable = true)` |
| `@Inject(method = "runCraftAttempt", at = @At("RETURN"), order = 2000)` |
| `@Inject(method = "run", at = @At("RETURN"), order = 2000)` |

目标定位：[src/main/java/appeng/crafting/CraftingCalculation.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/CraftingCalculation.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/crafting/CraftingCalculation.java L83](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/CraftingCalculation.java#L83)：`public ICraftingPlan run() {`
- [src/main/java/appeng/crafting/CraftingCalculation.java L137](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/CraftingCalculation.java#L137)：`private CraftingPlan runCraftAttempt(boolean simulate, long amount) throws InterruptedException {`

<a id="m17"></a>

### 17. AEProcessingPatternComponentMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/AEProcessingPatternComponentMixin.java L12](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/AEProcessingPatternComponentMixin.java#L12)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.pattern.AEProcessingPattern`。
- 迁移判断：pushInputsToExternalInventory入口保留；组件模糊匹配降为NBT语义，保持具体key、方向/槽顺序、容器返回与输入所有权。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "pushInputsToExternalInventory", at = @At("HEAD"), cancellable = true)` |

目标定位：[src/main/java/appeng/crafting/pattern/AEProcessingPattern.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/pattern/AEProcessingPattern.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/crafting/pattern/AEProcessingPattern.java L93](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/pattern/AEProcessingPattern.java#L93)：`public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {`

<a id="m18"></a>

### 18. Ae2CpuJobAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CpuJobAccessor.java L13](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CpuJobAccessor.java#L13)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.execution.ExecutingCraftingJob`。
- 迁移判断：tasks/waitingFor/timeTracker/link/finalOutput有目标；15.4.11的job没有suspended，不能保留两个同名Accessor。改为ECO持有的@Unique暂停状态/接口，接入目标job NBT构造与writeToNBT；原生executeCrafting入口同步门禁，确保异常后慢路也不重放。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("tasks")` |
| `@Accessor("waitingFor")` |
| `@Accessor("timeTracker")` |
| `@Accessor("link")` |
| `@Accessor("finalOutput")` |
| `@Accessor("suspended")` |
| `@Accessor("suspended")` |

目标定位：[src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java)。

**该目标源文件未出现这些main成员名**：`suspended`。需确认父类或改接目标实际入口，不能保留失效注入。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L43](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L43)：`private static final String NBT_LINK = "link";`
- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L45](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L45)：`private static final String NBT_FINAL_OUTPUT = "finalOutput";`
- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L46](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L46)：`private static final String NBT_WAITING_FOR = "waitingFor";`
- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L47](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L47)：`private static final String NBT_TIME_TRACKER = "timeTracker";`
- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L49](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L49)：`private static final String NBT_TASKS = "tasks";`
- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L52](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L52)：`final CraftingLink link;`
- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L53](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L53)：`final ListCraftingInventory waitingFor;`
- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L54](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L54)：`final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();`
- [src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java L55](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java#L55)：`final ElapsedTimeTracker timeTracker;`

<a id="m19"></a>

### 19. Ae2CpuTaskAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CpuTaskAccessor.java L6](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CpuTaskAccessor.java#L6)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.execution.ExecutingCraftingJob$TaskProgress`。
- 迁移判断：目标嵌套TaskProgress.value需确认long及可写性；加减必须计已接受量，覆盖0、上界和取消，不用强转隐藏溢出。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("value")` |
| `@Accessor("value")` |

目标定位：[src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java)。

<a id="m20"></a>

### 20. Ae2CpuTimeTrackerAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CpuTimeTrackerAccessor.java L8](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CpuTimeTrackerAccessor.java#L8)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.execution.ElapsedTimeTracker`。
- 迁移判断：核对addMaxItems签名；按批次接受数更新统计，避免进度/ETA与真实任务量不一致。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Invoker("addMaxItems")` |

目标定位：[src/main/java/appeng/crafting/execution/ElapsedTimeTracker.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ElapsedTimeTracker.java)。

<a id="m21"></a>

### 21. Ae2CraftingCpuFastPathMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CraftingCpuFastPathMixin.java L23](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/Ae2CraftingCpuFastPathMixin.java#L23)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.execution.CraftingCpuLogic`。
- 迁移判断：executeCrafting/trySubmitJob/tick/finishJob仍有对应路径；目标readFromNBT/writeToNBT只有CompoundTag。WrapOperation必须命中实际pushPattern调用并只接管一次输入。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)` |
| `@Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)` |
| `@Inject(method = "tickCraftingLogic", at = @At("HEAD"))` |
| `@Inject(method = "finishJob", at = @At("HEAD"))` |
| `@com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))` |
| `@Inject(method = "readFromNBT", at = @At("TAIL"))` |
| `@Inject(method = "writeToNBT", at = @At("TAIL"))` |

目标定位：[src/main/java/appeng/crafting/execution/CraftingCpuLogic.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/crafting/execution/CraftingCpuLogic.java L84](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java#L84)：`public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,`
- [src/main/java/appeng/crafting/execution/CraftingCpuLogic.java L132](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java#L132)：`public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {`
- [src/main/java/appeng/crafting/execution/CraftingCpuLogic.java L175](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java#L175)：`public int executeCrafting(int maxPatterns, CraftingService craftingService, IEnergyService energyService,`
- [src/main/java/appeng/crafting/execution/CraftingCpuLogic.java L325](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java#L325)：`private void finishJob(boolean success) {`
- [src/main/java/appeng/crafting/execution/CraftingCpuLogic.java L423](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java#L423)：`public void readFromNBT(CompoundTag data) {`
- [src/main/java/appeng/crafting/execution/CraftingCpuLogic.java L433](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java#L433)：`public void writeToNBT(CompoundTag data) {`

<a id="m22"></a>

### 22. CraftingPlanMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingPlanMixin.java L10](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingPlanMixin.java#L10)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.CraftingPlan`。
- 迁移判断：这是接口/字段扩展，不是没有@Inject就可以略过。核对目标计划的构造、字段与精确元数据附着/复制；保留原计划身份与不可执行状态。

无标准注入注解；需要核对接口/Shadow/覆盖方法。main声明摘要：

```java

```

目标定位：[src/main/java/appeng/crafting/CraftingPlan.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/CraftingPlan.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/crafting/CraftingPlan.java L28](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/CraftingPlan.java#L28)：`public record CraftingPlan(GenericStack finalOutput,`

<a id="m23"></a>

### 23. CraftingServiceMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingServiceMixin.java L63](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingServiceMixin.java#L63)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.me.service.CraftingService`。
- 迁移判断：最高风险：供应器变更、服务tick、CPU选择/提交与产物注入相互关联。目标没有三种GlobalCraftingProvider方法；本轮收敛后不保留对应调用者，移除这三个注入方法名，使用现有addNode/removeNode/refreshNodeCraftingProvider推进providerRevision并失效游标。其余FIELD/INVOKE_ASSIGN/shift/order/局部变量逐项重接。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = { "addNode", "removeNode", "refreshNodeCraftingProvider", "addGlobalCraftingProvider", "removeGlobalCraftingProvider", "refreshGlobalCraftingProvider" }, at = @At("HEAD"))` |
| `@Inject( method = "onServerEndTick", at = @At( value = "FIELD", target = "Lappeng/me/service/CraftingService;lastProcessedCraftingLogicChangeTick:J", opcode = Opcodes.GETFIELD, ordinal = 0 ) )` |
| `@Inject( method = "onServerEndTick", at = @At( value = "FIELD", target = "Lappeng/me/service/CraftingService;interests:Lcom/google/common/collect/Multimap;", opcode = Opcodes.GETFIELD, ordinal = 0 ) )` |
| `@Inject( method = {"removeNode"}, at = {@At("TAIL")} )` |
| `@Inject( method = {"addNode"}, at = {@At("TAIL")} )` |
| `@Inject( method = {"updateCPUClusters"}, at = {@At("TAIL")} )` |
| `@Inject( method = "insertIntoCpus", at = @At(value = "RETURN", shift = At.Shift.BY, by = -1), order = 500 )` |
| `@Inject(method = "submitJob", at = @At("HEAD"))` |
| `@Inject(method = "submitJob", at = @At("RETURN"))` |
| `@Inject( method = "submitJob", at = @At(value = "INVOKE_ASSIGN", target = "Lappeng/me/service/CraftingService;findSuitableCraftingCPU(Lappeng/api/networking/crafting/ICraftingPlan;ZLappeng/api/networking/security/IActionSource;Lorg/apache/commons/lang3/mutable/MutableObject;)Lappeng/me/cluster/implementations/CraftingCPUCluster;"), cancellable = true, order = 500 )` |
| `@Inject( method = "getCpus", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet$Builder;build()Lcom/google/common/collect/ImmutableSet;"), order = 500 )` |
| `@Inject( method = "getRequestedAmount", at = @At("RETURN"), cancellable = true, order = 500 )` |
| `@Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)` |

目标定位：[src/main/java/appeng/me/service/CraftingService.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java)。

**该目标源文件未出现这些main成员名**：`addGlobalCraftingProvider`, `refreshGlobalCraftingProvider`, `removeGlobalCraftingProvider`。需确认父类或改接目标实际入口，不能保留失效注入。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/me/service/CraftingService.java L141](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L141)：`public void onServerEndTick() {`
- [src/main/java/appeng/me/service/CraftingService.java L218](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L218)：`public void removeNode(IGridNode gridNode) {`
- [src/main/java/appeng/me/service/CraftingService.java L242](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L242)：`public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {`
- [src/main/java/appeng/me/service/CraftingService.java L275](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L275)：`private void updateCPUClusters() {`
- [src/main/java/appeng/me/service/CraftingService.java L304](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L304)：`public long insertIntoCpus(AEKey what, long amount, Actionable type) {`
- [src/main/java/appeng/me/service/CraftingService.java L319](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L319)：`public void refreshNodeCraftingProvider(IGridNode node) {`
- [src/main/java/appeng/me/service/CraftingService.java L344](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L344)：`public ICraftingSubmitResult submitJob(ICraftingPlan job, ICraftingRequester requestingMachine, ICraftingCPU target,`
- [src/main/java/appeng/me/service/CraftingService.java L426](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L426)：`public ImmutableSet<ICraftingCPU> getCpus() {`
- [src/main/java/appeng/me/service/CraftingService.java L447](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L447)：`public long getRequestedAmount(AEKey what) {`
- [src/main/java/appeng/me/service/CraftingService.java L466](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java#L466)：`public boolean hasCpu(ICraftingCPU cpu) {`

<a id="m24"></a>

### 24. CraftingTreeNodeMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingTreeNodeMixin.java L22](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingTreeNodeMixin.java#L22)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.crafting.CraftingTreeNode`。
- 迁移判断：按15.x模板/替代输入模型重接findCraftedStack与getValidItemTemplates，保留循环/种子/模糊规则；与独立规划器和原生回退结果做对照。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "findCraftedStack", at = @At("HEAD"), cancellable = true)` |
| `@Inject(method = "getValidItemTemplates", at = @At("HEAD"), cancellable = true)` |

目标定位：[src/main/java/appeng/crafting/CraftingTreeNode.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/CraftingTreeNode.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/crafting/CraftingTreeNode.java L82](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/CraftingTreeNode.java#L82)：`private AEKey findCraftedStack(ICraftingService cc, AEKey wat) {`
- [src/main/java/appeng/crafting/CraftingTreeNode.java L305](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/CraftingTreeNode.java#L305)：`private Iterable<InputTemplate> getValidItemTemplates(ICraftingInventory inv) {`

<a id="m25"></a>

### 25. PatternProviderDiagnosticsMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/PatternProviderDiagnosticsMixin.java L32](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/PatternProviderDiagnosticsMixin.java#L32)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.helpers.patternprovider.PatternProviderLogic`。
- 迁移判断：WrapMethod和多个内部调用共同提供失败原因；目标findAdapter/adapterAcceptsAll在。逐个核对isEmpty/contains等通用调用的真实含义，使用slice或更稳定入口避免包裹错分支。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapMethod(method = "pushPattern")` |
| `@WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))` |
| `@WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/api/networking/IManagedGridNode;isActive()Z"))` |
| `@WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))` |
| `@Inject(method = "getCraftingLockedReason", at = @At("RETURN"))` |
| `@Inject(method = "findAdapter", at = @At("RETURN"))` |
| `@Inject(method = "adapterAcceptsAll", at = @At("RETURN"))` |
| `@WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/api/implementations/blockentities/ICraftingMachine;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z"))` |
| `@WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/helpers/patternprovider/PatternProviderTarget;containsPatternInput(Ljava/util/Set;)Z"))` |
| `@WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/api/crafting/IPatternDetails;supportsPushInputsToExternalInventory()Z"))` |

目标定位：[src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java L297](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java#L297)：`public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {`
- [src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java L409](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java#L409)：`public LockCraftingMode getCraftingLockedReason() {`
- [src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java L462](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java#L462)：`private PatternProviderTarget findAdapter(Direction side) {`
- [src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java L475](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java#L475)：`private boolean adapterAcceptsAll(PatternProviderTarget target, KeyCounter[] inputHolder) {`

<a id="m26"></a>

### 26. CellWorkbenchMenuMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CellWorkbenchMenuMixin.java L15](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CellWorkbenchMenuMixin.java#L15)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.implementations.CellWorkbenchMenu`。
- 迁移判断：iterateCellStacks目标存在但属内部方法；适配ECO盘key枚举与过滤语义，测试空盘、内容盘、模拟读取与拆盘返回。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "iterateCellStacks", at = @At("HEAD"), cancellable = true)` |

目标定位：[src/main/java/appeng/menu/implementations/CellWorkbenchMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/implementations/CellWorkbenchMenu.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/implementations/CellWorkbenchMenu.java L209](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/implementations/CellWorkbenchMenu.java#L209)：`private Iterator<? extends AEKey> iterateCellStacks(ItemStack is) {`

<a id="m27"></a>

### 27. CraftConfirmMenuMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuMixin.java L62](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuMixin.java#L62)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.me.crafting.CraftConfirmMenu`。
- 迁移判断：最高风险：构造、规划、摘要、CPU选择、开始提交和锁状态。移除非目标附属方法分支，保留Plus强制计划合同；目标网络/GUI类型重接，禁止一个点击提交两次。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "broadcastChanges", at = @At("TAIL"))` |
| `@Inject(method = "onCPUSelectionChanged", at = @At("TAIL"))` |
| `@Inject(method = "broadcastChanges", at = @At("HEAD"))` |
| `@Inject(method = "cpuMatches", at = @At("HEAD"), cancellable = true)` |
| `@Inject(method = "<init>", at = @At("RETURN"))` |
| `@Inject( method = { "planJob" }, at = @At("HEAD"), require = 0 )` |
| `@WrapOperation( method = { "planJob" }, at = @At( value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;beginCraftingCalculation(" + "Lnet/minecraft/world/level/Level;" + "Lappeng/api/networking/crafting/ICraftingSimulationRequester;" + "Lappeng/api/stacks/AEKey;" + "J" + "Lappeng/api/networking/crafting/CalculationStrategy;" + ")Ljava/util/concurrent/Future;" ), require = 0 )` |
| `@Inject( method = "broadcastChanges", at = @At( value = "INVOKE", target = "Lappeng/menu/me/crafting/CraftingPlanSummary;fromJob(" + "Lappeng/api/networking/IGrid;" + "Lappeng/api/networking/security/IActionSource;" + "Lappeng/api/networking/crafting/ICraftingPlan;" + ")Lappeng/menu/me/crafting/CraftingPlanSummary;" ) )` |
| `@WrapOperation( method = "broadcastChanges", at = @At( value = "INVOKE", target = "Lappeng/menu/me/crafting/CraftingPlanSummary;fromJob(" + "Lappeng/api/networking/IGrid;" + "Lappeng/api/networking/security/IActionSource;" + "Lappeng/api/networking/crafting/ICraftingPlan;" + ")Lappeng/menu/me/crafting/CraftingPlanSummary;" ) )` |
| `@Inject(method = "broadcastChanges", at = @At("TAIL"))` |
| `@Inject( method = "startJob", at = @At( value = "INVOKE", target = "Lappeng/menu/me/crafting/CraftConfirmMenu;getGrid()Lappeng/api/networking/IGrid;" ), cancellable = true )` |
| `@WrapOperation( method = "startJob", at = @At( value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingService;submitJob(" + "Lappeng/api/networking/crafting/ICraftingPlan;" + "Lappeng/api/networking/crafting/ICraftingRequester;" + "Lappeng/api/networking/crafting/ICraftingCPU;" + "ZLappeng/api/networking/security/IActionSource;" + ")Lappeng/api/networking/crafting/ICraftingSubmitResult;" ) )` |
| `@Inject(method = "startJob", at = @At("HEAD"))` |
| `@Inject(method = "startJob", at = @At("RETURN"))` |

目标定位：[src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java L69](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java#L69)：`private static final String ACTION_START_JOB = "startJob";`
- [src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java L166](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java#L166)：`public boolean planJob(AEKey what, int amount, CalculationStrategy strategy) {`
- [src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java L203](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java#L203)：`public void broadcastChanges() {`
- [src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java L249](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java#L249)：`private boolean cpuMatches(ICraftingCPU c) {`
- [src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java L256](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java#L256)：`public void startJob() {`
- [src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java L300](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java#L300)：`private void onCPUSelectionChanged(CraftingCPURecord cpuRecord, boolean cpusAvailable) {`

<a id="m28"></a>

### 28. CraftConfirmMenuForceStartMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuForceStartMixin.java L14](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuForceStartMixin.java#L14)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.me.crafting.CraftConfirmMenu`。
- 迁移判断：服务器startJob前的强制开始条件必须保留缺料/权限/计划有效性约束；同Plus共装验证flag的清除与错误回退，不能只改客户端按钮。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "startJob", at = @At("HEAD"), cancellable = true)` |
| `@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "startJob")` |

目标定位：[src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java L69](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java#L69)：`private static final String ACTION_START_JOB = "startJob";`
- [src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java L256](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java#L256)：`public void startJob() {`

<a id="m29"></a>

### 29. CraftingCpuListEntryMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftingCpuListEntryMixin.java L16](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftingCpuListEntryMixin.java#L16)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.me.crafting.CraftingStatusMenu$CraftingCpuListEntry`。
- 迁移判断：目标嵌套条目的readFromPacket/writeToPacket需成对改为目标buffer和字段格式，双方使用同一协议；验证多种CPU条目不偏移解码。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject( method = "readFromPacket", at = @At("RETURN") )` |
| `@Inject( method = "writeToPacket", at = @At("RETURN") )` |

目标定位：[src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java L206](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java#L206)：`private static List<CraftingCpuListEntry> readFromPacket(FriendlyByteBuf data) {`
- [src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java L216](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java#L216)：`public void writeToPacket(FriendlyByteBuf data) {`
- [src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java L233](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java#L233)：`public static CraftingCpuListEntry readFromPacket(FriendlyByteBuf data) {`
- [src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java L245](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java#L245)：`public void writeToPacket(FriendlyByteBuf data) {`

<a id="m30"></a>

### 30. CraftingCpuMenuMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftingCpuMenuMixin.java L37](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftingCpuMenuMixin.java#L37)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.me.crafting.CraftingCPUMenu`。
- 迁移判断：目标没有toggleScheduling；main此处实际切换ECO任务暂停，不能误接15.x的CPU_SELECTION_MODE（自动选择策略）。新增本Mod菜单暂停/恢复动作及服务端校验；目标CraftingStatus只有五参构造、无暂停位，用独立同步状态保留展示。setCPU/cancel/removed/broadcast按目标重接。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject( method = {"setCPU(Lappeng/api/networking/crafting/ICraftingCPU;)V"}, at = {@At("HEAD")}, cancellable = true )` |
| `@Inject( method = {"cancelCrafting"}, at = {@At("TAIL")} )` |
| `@Inject( method = {"removed"}, at = {@At("TAIL")} )` |
| `@Inject( method = {"broadcastChanges"}, at = {@At("HEAD")} )` |
| `@Inject( method = "toggleScheduling", at = @At("TAIL") )` |

目标定位：[src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java)。

**该目标源文件未出现这些main成员名**：`toggleScheduling`。需确认父类或改接目标实际入口，不能保留失效注入。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java L47](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java#L47)：`private static final String ACTION_CANCEL_CRAFTING = "cancelCrafting";`
- [src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java L92](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java#L92)：`protected void setCPU(ICraftingCPU c) {`
- [src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java L121](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java#L121)：`public void cancelCrafting() {`
- [src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java L132](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java#L132)：`public void removed(Player player) {`
- [src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java L140](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java#L140)：`public void broadcastChanges() {`

<a id="m31"></a>

### 31. CraftingStatusMenuMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftingStatusMenuMixin.java L19](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftingStatusMenuMixin.java#L19)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.me.crafting.CraftingStatusMenu`。
- 迁移判断：createCpuList中的ArrayList.add注入可能匹配多次，核对实际列表/条目与顺序。只扩展属于ECO/选定CPU的展示信息。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@org.spongepowered.asm.mixin.injection.Inject(method = "selectCpu", at = @At("HEAD"))` |
| `@WrapOperation( method = "createCpuList", at = @At( value = "INVOKE", target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z" ) )` |

目标定位：[src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java L58](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java#L58)：`private static final String ACTION_SELECT_CPU = "selectCpu";`
- [src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java L144](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java#L144)：`private CraftingCpuList createCpuList() {`
- [src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java L176](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatusMenu.java#L176)：`public void selectCpu(int serial) {`

<a id="m32"></a>

### 32. IOPortMenuMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/IOPortMenuMixin.java L15](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/IOPortMenuMixin.java#L15)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.implementations.IOPortMenu`。
- 迁移判断：核对setupConfig/addSlot目标描述符和ordinal=0，保持只修改盘输入槽，不能意外放宽升级卡或输出槽。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapOperation(method = "setupConfig", at = @At(value = "INVOKE", target = "Lappeng/menu/implementations/IOPortMenu;addSlot(Lnet/minecraft/world/inventory/Slot;Lappeng/menu/SlotSemantic;)Lnet/minecraft/world/inventory/Slot;", ordinal = 0))` |

目标定位：[src/main/java/appeng/menu/implementations/IOPortMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/implementations/IOPortMenu.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/implementations/IOPortMenu.java L54](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/implementations/IOPortMenu.java#L54)：`protected void setupConfig() {`

<a id="m33"></a>

### 33. PatternEncodingTermMenuMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/PatternEncodingTermMenuMixin.java L31](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/PatternEncodingTermMenuMixin.java#L31)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.me.items.PatternEncodingTermMenu`。
- 迁移判断：main写死五参数构造器描述符和第一次registerClientAction调用；按15.x构造器真实签名重接，在服务端动作登记后附加功能。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject( method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V", at = @At( value = "INVOKE", target = "Lappeng/menu/me/items/PatternEncodingTermMenu;registerClientAction(Ljava/lang/String;Ljava/lang/Runnable;)V", ordinal = 0 ) )` |

目标定位：[src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L73](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L73)：`private static final int CRAFTING_GRID_WIDTH = 3;`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L74](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L74)：`private static final int CRAFTING_GRID_HEIGHT = 3;`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L75](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L75)：`private static final int CRAFTING_GRID_SLOTS = CRAFTING_GRID_WIDTH * CRAFTING_GRID_HEIGHT;`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L77](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L77)：`private static final String ACTION_SET_MODE = "setMode";`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L78](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L78)：`private static final String ACTION_ENCODE = "encode";`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L79](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L79)：`private static final String ACTION_CLEAR = "clear";`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L80](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L80)：`private static final String ACTION_SET_SUBSTITUTION = "setSubstitution";`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L81](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L81)：`private static final String ACTION_SET_FLUID_SUBSTITUTION = "setFluidSubstitution";`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L82](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L82)：`private static final String ACTION_SET_STONECUTTING_RECIPE_ID = "setStonecuttingRecipeId";`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L83](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L83)：`private static final String ACTION_CYCLE_PROCESSING_OUTPUT = "cycleProcessingOutput";`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L85](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L85)：`public static final MenuType<PatternEncodingTermMenu> TYPE = MenuTypeBuilder`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L89](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L89)：`private final PatternEncodingLogic encodingLogic;`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L90](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L90)：`private final FakeSlot[] craftingGridSlots = new FakeSlot[9];`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L91](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L91)：`private final FakeSlot[] processingInputSlots = new FakeSlot[AEProcessingPattern.MAX_INPUT_SLOTS];`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L92](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L92)：`private final FakeSlot[] processingOutputSlots = new FakeSlot[AEProcessingPattern.MAX_OUTPUT_SLOTS];`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L93](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L93)：`private final FakeSlot stonecuttingInputSlot;`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L94](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L94)：`private final FakeSlot smithingTableTemplateSlot;`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L95](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L95)：`private final FakeSlot smithingTableBaseSlot;`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L96](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L96)：`private final FakeSlot smithingTableAdditionSlot;`
- [src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java L97](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java#L97)：`private final PatternTermSlot craftOutputSlot;`

<a id="m34"></a>

### 34. AppEngInternalInventoryMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/AppEngInternalInventoryMixin.java L23](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/AppEngInternalInventoryMixin.java#L23)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.util.inv.AppEngInternalInventory`。
- 迁移判断：onContentsChanged通知与LDLib1 IContentChangeAware接口一起迁移；保留批次合并通知和缓存失效，避免递归或漏同步。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "onContentsChanged", at = @At("RETURN"))` |

目标定位：[src/main/java/appeng/util/inv/AppEngInternalInventory.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/util/inv/AppEngInternalInventory.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/util/inv/AppEngInternalInventory.java L126](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/util/inv/AppEngInternalInventory.java#L126)：`protected void onContentsChanged(int slot) {`

<a id="m35"></a>

### 35. CellWorkbenchBlockEntityMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/CellWorkbenchBlockEntityMixin.java L13](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/CellWorkbenchBlockEntityMixin.java#L13)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.blockentity.misc.CellWorkbenchBlockEntity`。
- 迁移判断：在getCellConfigInventory读取前处理ECO盘配置，改为NBT与目标盘接口；不能在客户端预览时改写服务器盘内容。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "getCellConfigInventory", at = @At("HEAD"))` |

目标定位：[src/main/java/appeng/blockentity/misc/CellWorkbenchBlockEntity.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/blockentity/misc/CellWorkbenchBlockEntity.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/blockentity/misc/CellWorkbenchBlockEntity.java L163](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/blockentity/misc/CellWorkbenchBlockEntity.java#L163)：`private ConfigInventory getCellConfigInventory() {`

<a id="m36"></a>

### 36. IOPortBlockEntityMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/IOPortBlockEntityMixin.java L13](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/IOPortBlockEntityMixin.java#L13)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.blockentity.storage.IOPortBlockEntity`。
- 迁移判断：tickingRequest获取CellInventory路径保留；目标StorageCells/StorageCell签名按15.x绑定，转移统计、模拟、拆装和满量背压必须验证。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapOperation(method = "tickingRequest", at = @At(value = "INVOKE", target = "Lappeng/api/storage/StorageCells;getCellInventory(Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)Lappeng/api/storage/cells/StorageCell;"))` |

目标定位：[src/main/java/appeng/blockentity/storage/IOPortBlockEntity.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/blockentity/storage/IOPortBlockEntity.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/blockentity/storage/IOPortBlockEntity.java L263](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/blockentity/storage/IOPortBlockEntity.java#L263)：`public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {`

<a id="m37"></a>

### 37. NativeMEStorageCellFilterMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/NativeMEStorageCellFilterMixin.java L12](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/NativeMEStorageCellFilterMixin.java#L12)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.blockentity.storage.MEChestBlockEntity$CellInventoryFilter`, `appeng.blockentity.storage.DriveBlockEntity$CellValidInventoryFilter`。
- 迁移判断：两个内部过滤器各自核对存在性与allowInsert签名。若其中一个改成其它实现，改挂对应宿主过滤构造点；不要只让一个ME驱动器类型支持ECO盘。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true)` |

目标定位：[src/main/java/appeng/blockentity/storage/DriveBlockEntity.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/blockentity/storage/DriveBlockEntity.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/blockentity/storage/DriveBlockEntity.java L431](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/blockentity/storage/DriveBlockEntity.java#L431)：`public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {`

<a id="m38"></a>

### 38. NetworkStorageMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/NetworkStorageMixin.java L24](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/NetworkStorageMixin.java#L24)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.me.storage.NetworkStorage`。
- 迁移判断：extract/getAvailableStacks绕行保持排除集、权限、模拟和真实库存身份；不得因加入精确显示而改变网络抽取/计数语义。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapOperation( method = "extract", at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J") )` |
| `@WrapOperation( method = "getAvailableStacks", at = @At( value = "INVOKE", target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V" ) )` |

目标定位：[src/main/java/appeng/me/storage/NetworkStorage.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/storage/NetworkStorage.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/me/storage/NetworkStorage.java L173](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/storage/NetworkStorage.java#L173)：`public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {`
- [src/main/java/appeng/me/storage/NetworkStorage.java L204](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/storage/NetworkStorage.java#L204)：`public void getAvailableStacks(KeyCounter out) {`

<a id="m39"></a>

### 39. StoredEnergyAmountMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/StoredEnergyAmountMixin.java L13](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/storage/StoredEnergyAmountMixin.java#L13)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`appeng.me.energy.StoredEnergyAmount`。
- 迁移判断：核心同步/NBT桥，必须保留；目标INBTSerializable序列化方法无HolderLookup.Provider参数，迁移LDLib1 IContentChangeAware并核对setStored/getAmount。

无标准注入注解；需要核对接口/Shadow/覆盖方法。main声明摘要：

```java
@Shadow public abstract void setStored(double amount);
@Shadow public abstract double getAmount();
public @UnknownNullability DoubleTag serializeNBT(HolderLookup.Provider provider) {
public void deserializeNBT(HolderLookup.Provider provider, DoubleTag nbt) {
public void setOnContentsChanged(Runnable onContentChanged) {
public Runnable getOnContentsChanged() {
```

目标定位：[src/main/java/appeng/me/energy/StoredEnergyAmount.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L14](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L14)：`public static final double MIN_AMOUNT = 0.000001;`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L18](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L18)：`public static final double MAX_MAXIMUM = (double) (Long.MAX_VALUE / 10000);`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L25](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L25)：`private final double provideThreshold;`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L31](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L31)：`private final double receiveThreshold;`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L32](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L32)：`private double maximum;`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L33](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L33)：`private final EventEmitter eventEmitter;`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L34](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L34)：`private double stored;`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L37](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L37)：`private boolean isReceiving = true;`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L38](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L38)：`private boolean isProviding = true;`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L40](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L40)：`public StoredEnergyAmount(double stored, double maximum, EventEmitter eventEmitter) {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L44](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L44)：`public StoredEnergyAmount(double stored, double provideThreshold, double receiveThreshold, double maximum,`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L53](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L53)：`public double getAmount() {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L57](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L57)：`public double getMaximum() {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L64](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L64)：`public double insert(double amount, boolean commit) {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L79](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L79)：`public double extract(double amount, boolean commit) {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L91](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L91)：`public void setStored(double amount) {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L100](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L100)：`public void setMaximum(double maximum) {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L107](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L107)：`public double remainingCapacity() {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L111](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L111)：`private boolean canProvide() {`
- [src/main/java/appeng/me/energy/StoredEnergyAmount.java L115](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/energy/StoredEnergyAmount.java#L115)：`private boolean canReceive() {`

<a id="m40"></a>

### 40. AsyncThreadDataMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/ldlib/AsyncThreadDataMixin.java L13](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/ldlib/AsyncThreadDataMixin.java#L13)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`com.lowdragmc.lowdraglib2.async.AsyncThreadData`。
- 迁移判断：目标包为com.lowdragmc.lowdraglib.async.AsyncThreadData。核对addAsyncLogic是否仍调用CopyOnWriteArrayList.add；若已有等价去重则避免重复补丁，否则重接安全登记点。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Redirect( method = "addAsyncLogic", at = @At( value = "INVOKE", target = "Ljava/util/concurrent/CopyOnWriteArrayList;add(Ljava/lang/Object;)Z" ) )` |

目标定位：[common/src/main/java/com/lowdragmc/lowdraglib/async/AsyncThreadData.java](https://github.com/Low-Drag-MC/LDLib-MultiLoader/blob/1d4e4390ae58d16038845c651e122185c29f334b/common/src/main/java/com/lowdragmc/lowdraglib/async/AsyncThreadData.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [common/src/main/java/com/lowdragmc/lowdraglib/async/AsyncThreadData.java L65](https://github.com/Low-Drag-MC/LDLib-MultiLoader/blob/1d4e4390ae58d16038845c651e122185c29f334b/common/src/main/java/com/lowdragmc/lowdraglib/async/AsyncThreadData.java#L65)：`public void addAsyncLogic(IAsyncLogic logic) {`

<a id="m41"></a>

### 41. LightningBoltMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/minecraft/LightningBoltMixin.java L24](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/minecraft/LightningBoltMixin.java#L24)；配置`neoecoae.mixins.json`，侧别`mixins`。
- main目标：`net.minecraft.world.entity.LightningBolt`。
- 迁移判断：Minecraft tick内铜块处理调用是版本敏感定位，按1.20.1官方映射/refmap确认描述符；真实服务端雷击验证，不扩大为每tick重复业务效果。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject( method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;clearCopperOnLightningStrike(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V") )` |

Minecraft目标需从Forge47.4.28生成的官方映射源码/映射文件核对，并以生产refmap和实际渲染/交互验证；本调查没有运行目标游戏。

<a id="m42"></a>

### 42. MenuScreensAccessor

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/accessor/MenuScreensAccessor.java L9](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/accessor/MenuScreensAccessor.java#L9)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`net.minecraft.client.gui.screens.MenuScreens`。
- 迁移判断：目标SCREENS字段和ScreenConstructor泛型需通过MC映射/AP绑定；客户端侧注册，专服不可加载GUI类型。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Accessor("SCREENS")` |

Minecraft目标需从Forge47.4.28生成的官方映射源码/映射文件核对，并以生产refmap和实际渲染/交互验证；本调查没有运行目标游戏。

<a id="m43"></a>

### 43. AEBaseScreenGrayToolbarMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/AEBaseScreenGrayToolbarMixin.java L11](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/AEBaseScreenGrayToolbarMixin.java#L11)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.AEBaseScreen`。
- 迁移判断：目标VerticalButtonBar构造/样式接口与19.x不同，确认NEW发生位置；优先用目标主题/构造适配保留灰色工具栏外观。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@ModifyExpressionValue(method = "<init>", at = @At(value = "NEW", target = "appeng/client/gui/widgets/VerticalButtonBar"), remap = false)` |

目标定位：[src/main/java/appeng/client/gui/AEBaseScreen.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/AEBaseScreen.java L100](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L100)：`private static final Logger LOG = LoggerFactory.getLogger(AEBaseScreen.class);`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L102](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L102)：`private static final Point HIDDEN_SLOT_POS = new Point(-9999, -9999);`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L107](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L107)：`public static final String TEXT_ID_DIALOG_TITLE = "dialog_title";`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L109](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L109)：`private final VerticalButtonBar verticalToolbar;`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L110](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L110)：`private final OpenGuideButton helpButton;`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L113](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L113)：`private final Set<Slot> drag_click = new HashSet<>();`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L114](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L114)：`private boolean disableShiftClick = false;`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L115](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L115)：`private Stopwatch dbl_clickTimer = Stopwatch.createStarted();`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L116](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L116)：`private ItemStack dbl_whichItem = ItemStack.EMPTY;`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L117](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L117)：`private Slot bl_clicked;`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L118](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L118)：`private boolean handlingRightClick;`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L119](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L119)：`private final Map<String, TextOverride> textOverrides = new HashMap<>();`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L120](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L120)：`private final Set<SlotSemantic> hiddenSlots = new HashSet<>();`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L121](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L121)：`protected final WidgetContainer widgets;`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L122](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L122)：`protected final ScreenStyle style;`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L123](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L123)：`protected final AEConfig config = AEConfig.instance();`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L128](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L128)：`private final List<SavedSlotInfo> savedSlotInfos = new ArrayList<>();`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L130](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L130)：`public AEBaseScreen(T menu, Inventory playerInventory, Component title, ScreenStyle style) {`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L155](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L155)：`protected void init() {`
- [src/main/java/appeng/client/gui/AEBaseScreen.java L162](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/AEBaseScreen.java#L162)：`private void positionSlots() {`

<a id="m44"></a>

### 44. CellWorkbenchScreenMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CellWorkbenchScreenMixin.java L14](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CellWorkbenchScreenMixin.java#L14)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.implementations.CellWorkbenchScreen`。
- 迁移判断：本类有继承/覆盖逻辑，不能按无注入注解跳过。目标Screen/菜单槽位和新NBT盘识别一起适配，保留过滤与提示行为。

无标准注入注解；需要核对接口/Shadow/覆盖方法。main声明摘要：

```java
protected CellWorkbenchScreenMixin(CellWorkbenchMenu menu, Inventory playerInventory,
public void init() {
```

目标定位：[src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java L39](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java#L39)：`private final ToggleButton copyMode;`
- [src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java L41](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java#L41)：`private final SettingToggleButton<FuzzyMode> fuzzyMode;`
- [src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java L43](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java#L43)：`public CellWorkbenchScreen(CellWorkbenchMenu menu, Inventory playerInventory,`
- [src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java L56](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java#L56)：`protected void updateBeforeRender() {`
- [src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java L66](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/implementations/CellWorkbenchScreen.java#L66)：`private void toggleFuzzyMode(SettingToggleButton<FuzzyMode> button, boolean backwards) {`

<a id="m45"></a>

### 45. CpuSelectionListMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CpuSelectionListMixin.java L27](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CpuSelectionListMixin.java#L27)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.widgets.CPUSelectionList`。
- 迁移判断：InfoBar.add的ordinal=1/2和PoseStack.popPose位置需重查；旧版可能没有同一组调用。通过目标CPU数据绘制等价信息，避免让字节提示错位。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject( method = "drawBackgroundLayer", at = @At( value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.AFTER ) )` |
| `@WrapOperation( method = "drawBackgroundLayer", at = @At( value = "INVOKE", target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V", ordinal = 1 ) )` |
| `@WrapOperation( method = "getTooltip", at = @At( value = "INVOKE", target = "Lappeng/core/localization/Tooltips;ofBytes(J)Lnet/minecraft/network/chat/MutableComponent;" ) )` |
| `@WrapOperation( method = "drawBackgroundLayer", at = @At(value = "INVOKE", target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V", ordinal = 2) )` |

目标定位：[src/main/java/appeng/client/gui/widgets/CPUSelectionList.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/widgets/CPUSelectionList.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/widgets/CPUSelectionList.java L77](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/widgets/CPUSelectionList.java#L77)：`public Tooltip getTooltip(int mouseX, int mouseY) {`
- [src/main/java/appeng/client/gui/widgets/CPUSelectionList.java L170](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/widgets/CPUSelectionList.java#L170)：`public void drawBackgroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {`

<a id="m46"></a>

### 46. CraftConfirmScreenMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CraftConfirmScreenMixin.java L26](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CraftConfirmScreenMixin.java#L26)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.me.crafting.CraftConfirmScreen`。
- 迁移判断：updateBeforeRender/start及GuiText ordinal需重接；UI状态由服务端计划驱动，精确数量和拒绝原因真实，强制开始只发一次请求。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "updateBeforeRender", at = @At("TAIL"))` |
| `@Inject(method = "start", at = @At("HEAD"), cancellable = true, order = 500)` |
| `@WrapOperation( method = "updateBeforeRender", at = @org.spongepowered.asm.mixin.injection.At( value = "INVOKE", target = "Lappeng/core/localization/GuiText;text([Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;", ordinal = 1 ) )` |

目标定位：[src/main/java/appeng/client/gui/me/crafting/CraftConfirmScreen.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftConfirmScreen.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/me/crafting/CraftConfirmScreen.java L46](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftConfirmScreen.java#L46)：`private final Button start;`
- [src/main/java/appeng/client/gui/me/crafting/CraftConfirmScreen.java L67](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftConfirmScreen.java#L67)：`protected void updateBeforeRender() {`
- [src/main/java/appeng/client/gui/me/crafting/CraftConfirmScreen.java L158](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftConfirmScreen.java#L158)：`private void start() {`

<a id="m47"></a>

### 47. CraftingCpuScreenMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CraftingCpuScreenMixin.java L13](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CraftingCpuScreenMixin.java#L13)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.me.crafting.CraftingCPUScreen`。
- 迁移判断：render里的List.isEmpty不是稳定语义标识，确认包裹的是任务列表；可改由ECO菜单状态决定展示，避免影响其它空列表。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapOperation( method = "render", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z") )` |

目标定位：[src/main/java/appeng/client/gui/me/crafting/CraftingCPUScreen.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftingCPUScreen.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/me/crafting/CraftingCPUScreen.java L119](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftingCPUScreen.java#L119)：`public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float btn) {`

<a id="m48"></a>

### 48. CraftingStatusTableMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CraftingStatusTableMixin.java L14](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/CraftingStatusTableMixin.java#L14)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.me.crafting.CraftingStatusTableRenderer`。
- 迁移判断：按目标条目描述/tooltip入口重建精确数量信息；缺失同名方法时移到目标renderer实际调用处，保留单位/资源key和多行提示。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "getEntryDescription(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)` |
| `@Inject(method = "getEntryTooltip(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)` |

目标定位：[src/main/java/appeng/client/gui/me/crafting/CraftingStatusTableRenderer.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftingStatusTableRenderer.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/me/crafting/CraftingStatusTableRenderer.java L44](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftingStatusTableRenderer.java#L44)：`protected List<Component> getEntryDescription(CraftingStatusEntry entry) {`
- [src/main/java/appeng/client/gui/me/crafting/CraftingStatusTableRenderer.java L69](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/crafting/CraftingStatusTableRenderer.java#L69)：`protected List<Component> getEntryTooltip(CraftingStatusEntry entry) {`

<a id="m49"></a>

### 49. PatternEncodingTermScreenMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/PatternEncodingTermScreenMixin.java L13](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/ae2/PatternEncodingTermScreenMixin.java#L13)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.me.items.PatternEncodingTermScreen`。
- 迁移判断：含覆盖/扩展行为；目标按钮类型、布局、鼠标处理按AE2 15重建，服务端动作与屏幕按钮保持对应。

无标准注入注解；需要核对接口/Shadow/覆盖方法。main声明摘要：

```java
public PatternEncodingTermScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
public void init() {
```

目标定位：[src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L53](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L53)：`private final Map<EncodingMode, EncodingModePanel> modePanels = new EnumMap<>(EncodingMode.class);`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L54](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L54)：`private final Map<EncodingMode, TabButton> modeTabButtons = new EnumMap<>(EncodingMode.class);`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L56](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L56)：`public PatternEncodingTermScreen(C menu, Inventory playerInventory,`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L85](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L85)：`protected void updateBeforeRender() {`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L96](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L96)：`public boolean mouseClicked(double xCoord, double yCoord, int btn) {`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L122](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L122)：`protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L137](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L137)：`protected EmptyingAction getEmptyingAction(Slot slot, ItemStack carried) {`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L152](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L152)：`public void renderSlot(GuiGraphics guiGraphics, Slot s) {`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L165](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L165)：`protected List<Component> getTooltipFromContainerItem(ItemStack stack) {`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L177](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L177)：`private boolean shouldShowCraftableIndicatorForSlot(Slot s) {`
- [src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java L197](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/items/PatternEncodingTermScreen.java#L197)：`public void onClose() {`

<a id="m50"></a>

### 50. ModelBakeryMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/client/minecraft/ModelBakeryMixin.java L12](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/client/minecraft/ModelBakeryMixin.java#L12)；配置`neoecoae.mixins.json`，侧别`client`。
- main目标：`net.minecraft.client.resources.model.ModelBakery`。
- 迁移判断：按1.20.1 getModel及UnbakedModel类型重接；测试缺资源回退、classic pack、动态模型和资源重载，不能只验证JSON可解析。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(at = @At("HEAD"), method = "getModel", cancellable = true)` |

Minecraft目标需从Forge47.4.28生成的官方映射源码/映射文件核对，并以生产refmap和实际渲染/交互验证；本调查没有运行目标游戏。

<a id="m51"></a>

### 51. MEStorageMenuExactAmountMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/terminalbigamount/MEStorageMenuExactAmountMixin.java L14](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/terminalbigamount/MEStorageMenuExactAmountMixin.java#L14)；配置`neoecoae.terminal_big_amount.mixins.json`，侧别`mixins`。
- main目标：`appeng.menu.me.common.MEStorageMenu`。
- 迁移判断：捕获实际getAvailableStacks快照并用本Mod通道发送精确信息；不改写AE2 long库存语义，菜单更换/断连清理旧快照。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@WrapOperation(method = "broadcastChanges", at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))` |

目标定位：[src/main/java/appeng/menu/me/common/MEStorageMenu.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/common/MEStorageMenu.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/menu/me/common/MEStorageMenu.java L244](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/common/MEStorageMenu.java#L244)：`public void broadcastChanges() {`

<a id="m52"></a>

### 52. MEStorageScreenExactAmountMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/terminalbigamount/client/MEStorageScreenExactAmountMixin.java L19](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/terminalbigamount/client/MEStorageScreenExactAmountMixin.java#L19)；配置`neoecoae.terminal_big_amount.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.me.common.MEStorageScreen`。
- 迁移判断：containerTick/removed与数量/tooltip实际渲染点一起适配；旧版字段、buffer与GUI参数不同。测试大数显示、关闭重开和小窗口真实槽位。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "containerTick", at = @At("TAIL"), require = 1)` |
| `@Inject(method = "removed", at = @At("HEAD"))` |
| `@ModifyExpressionValue(method = "renderSlot", at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;"))` |
| `@ModifyExpressionValue(method = "renderGridInventoryEntryTooltip", at = @At(value = "INVOKE", target = "Lappeng/core/localization/Tooltips;getAmountTooltip(Lappeng/core/localization/ButtonToolTips;Lappeng/api/stacks/AEKey;J)Lnet/minecraft/network/chat/Component;"))` |

目标定位：[src/main/java/appeng/client/gui/me/common/MEStorageScreen.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/common/MEStorageScreen.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/me/common/MEStorageScreen.java L503](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/common/MEStorageScreen.java#L503)：`public void removed() {`
- [src/main/java/appeng/client/gui/me/common/MEStorageScreen.java L560](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/common/MEStorageScreen.java#L560)：`public void renderSlot(GuiGraphics guiGraphics, Slot s) {`
- [src/main/java/appeng/client/gui/me/common/MEStorageScreen.java L642](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/common/MEStorageScreen.java#L642)：`protected void renderGridInventoryEntryTooltip(GuiGraphics guiGraphics, GridInventoryEntry entry, int x, int y) {`
- [src/main/java/appeng/client/gui/me/common/MEStorageScreen.java L713](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/common/MEStorageScreen.java#L713)：`public void containerTick() {`

<a id="m53"></a>

### 53. RepoExactAmountMixin

- main：[src/main/java/cn/dancingsnow/neoecoae/mixins/terminalbigamount/client/RepoExactAmountMixin.java L18](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/mixins/terminalbigamount/client/RepoExactAmountMixin.java#L18)；配置`neoecoae.terminal_big_amount.mixins.json`，侧别`client`。
- main目标：`appeng.client.gui.me.common.Repo`。
- 迁移判断：getComparator的目标返回类型、排序方向及缓存失效核对；缺失同名入口时接目标实际排序器，BigInteger排序不得退回double/饱和long。

| 原main注入/访问声明（非目标分支已省略） |
|---|
| `@Inject(method = "getComparator", at = @At("HEAD"), cancellable = true, require = 1)` |

目标定位：[src/main/java/appeng/client/gui/me/common/Repo.java](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/common/Repo.java)。

目标声明摘要（带源码行号；多行签名以链接原文为准）：

- [src/main/java/appeng/client/gui/me/common/Repo.java L319](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/client/gui/me/common/Repo.java#L319)：`private Comparator<? super GridInventoryEntry> getComparator(SortOrder sortOrder, SortDir sortDir) {`

## 4. 统一验收

核心CPU/service/menu链一起验证自动合成、快速规划、精确大订单、取消和恢复；存储链验证有限/超long数、模拟和真实搬运；GUI链验证不同缩放、鼠标真实手势与多人同步。四附属分别通过后再共装，尤其Plus/AdvancedAE方向包装、AE2LT/前置与ECO的批量所有权。

所有条目对应主方案的A系列验收。源码/ABI检查不能替代Mixin应用、游戏运行或最终JAR验收。
