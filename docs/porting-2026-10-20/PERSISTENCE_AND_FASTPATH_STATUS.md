# 保存周期与 FastPath 状态边界检查点

主体继续固定为 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`，没有引入旧 1.20.1 工程的业务或存储后端。读取 AE2 参考代码前执行了 `git fetch origin --prune --tags`，实际按 `forge/v15.4.11` 固定提交核对；目标 Minecraft/Forge 接口同时以当前生成的 1.20.1/47.4.28 sources JAR 为准。未调整四附属和四类大集成范围。

## 无限存储的保存与保护

1. 以项目 `LoadFactory` 记录传递原生反序列化函数和构造函数，接入真实 `DimensionDataStorage.computeIfAbsent`。已有权威快照读取失败时返回 `UNAVAILABLE` 域，不使用空库存替代旧文件。
2. 覆盖 1.20.1 `SavedData.save(File)`：在正常世界保存周期中生成完整压缩临时文件、强制写出，再用原子替换发布到权威路径。写入、序列化和日志清理失败均保留重试依据。没有每次插入/抽取的文件 I/O，也没有引入另一个后台线程保存系统。
3. 保留 v3 格式字段、超 long 数量、迁移回执、待恢复目标分配、未知 key 原始条目和旧日志序列号。只有快照发布后才删除已折叠日志；留在磁盘上的旧序列不会重复重放。损坏日志导致域不可用，原始文件保留。
4. 缺失旧分片保留 `RECOVERY_READ_ONLY`；未知 key/元数据保持 `DEGRADED`，保留可读条目，阻止退出或恢复；待恢复 key 从可用库存隐藏，仍可取得其恢复账本数量。

目标 1.20.1 没有 main 使用的 IOUtilities 工具，本阶段按原生同步世界保存周期适配。临时文件原子替换不支持时报告失败并等待下次保存，不改用可能覆盖旧文件的降级路径。已验证本机临时目录上的替换及失败保护；大型真实存档的保存延迟、网络文件系统和断电场景没有验收。

## FastPath 的物品状态与网格

- 使用 1.20 原生物品 NBT 与 `isSameItemSameTags`，比较包含 Forge capability 一致性。数值型自定义 NBT 可以延续 main 的等形线性状态模型；原版耐久、名称、附魔、药水、模型、方块实体物品等专用字段变化不冒充自定义计数器。
- 继续保留二次执行证明、耐久模型、催化剂只保留一次、数量消耗上限、原始元数据复制和溢出拒绝。未知变化继续回退原有慢路；没有禁用整个状态模型。
- AE2 15 的分子装配配方直接接收完整 CraftingContainer，返回物已经按完整网格给出。工作线程和 Plus 矩阵改用该契约，返回物复制保持原槽位，避免再次应用新版压缩坐标偏移。
- 批次 GenericStack 编解码改用 AE2 15 原生 NBT，保留 long 数量和原有条目/数量校验；移除无意义的新版 registry 参数。

## 其他保存边界

精确 CPU 库存和精确虚拟账本保留 `ecoExactAmount` 及长整型兼容窗口；接受部分输出后先扣除自身账本，保存重载不会再发送已接受窗口。未知或负数精确数据在替换已有库存前拒绝。

冷却余数以原生 FluidStack NBT 保存，保留标签和按配方分隔的余数；规划但未提交的输出不推进余数，流体标签变化会按原规则重置。27 槽存储筛选改用实际 AE2 key 通道，缺失通道或物品不转为允许全部。

CPU、BE、线程、执行计划和附加状态的保存调用已在源码上改为目标接口；任务状态通知使用 AE2 15 NetworkHandler。完整 CPU/BE、任务终止账本、原生和附属 Mixin 链尚不能独立宣称通过：它们仍依赖当前未完成的全量 main 编译。

## 验证及边界

Gradle 使用 `build-local.ps1` 和 Java 17，所有 Gradle 调用串行运行。`portingPlatform` 只增加明确命名的实际生产类，没有从 main 排除文件或用 fixture 替换业务实现。测试使用真实 AE2 编解码与 Forge key registry，少量启动器 fixture 仍只负责隔离 ModLauncher。

| 新增测试类 | 测试数 | 验证内容 |
|---|---:|---|
| ForgeInfiniteStoragePersistenceTest | 10 | 原生保存周期、实际文件、权威坏文件保护、原子替换失败、重试、未知数据、恢复分配、回执与日志 |
| ForgeFastPathStateBoundaryTest | 12 | 原生 NBT 状态、二次规则、原版字段拒绝、耐久/催化剂、溢出、完整网格返回物、批次编码 |
| ForgeExactLedgerPersistenceTest | 6 | 超 long 库存、未知/负数拒绝、部分接受后重载、输入/产出/返回物精确数量 |
| ForgeCoolingRemainderPersistenceTest | 2 | 流体 NBT 和分数持久化、标签变化、未提交输出 |
| ForgeStorageFilterTest | 3 | 原生物品/流体 key、标签、未知 key 拒绝及槽位边界 |

合计新增 **33 项**；当前 `portingPlatformTest` **79 项**、`portingContractsTest` **4 项**，共 **83 项通过，0 失败、0 跳过**。测试日志为 `validation/storage-fastpath-test.log`，机器记录为 `porting/validation-state.json` 和 `porting/persistence-fastpath-adaptation.json`。

最新完整编译为 **1,454 errors、12 warnings**，日志 `validation/compile-storage-fastpath-final.log`，显示上限为 10,000。上一完整检查点为 1,505 errors，中间检查点为 1,499；级联错误数不代表功能完成比例。仍有大量 LDLib2 UI、AE2 15、客户端/注册及附属接口需要迁移。

没有执行完整业务测试、datagen、当前业务客户端/专服、普通 Forge 安装、多人、实际机器投料/产出或真实存档故障恢复。最小入口曾启动的历史记录仍不适用于当前业务树。
