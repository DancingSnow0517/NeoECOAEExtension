# SavedData 存储架构与验收

日期：2026-10-21。适用工程：本目录的 Forge 1.20.1 移植工程。业务和容量规则继续采用 `porting/source-lock.json` 锁定的 main 输入，没有导入旧 1.20.1 的存储后端。

## 数据所有权

普通盘和无限域均由主世界的 `DimensionDataStorage` 管理。一个盘 UUID 对应一个 `ECOCellSavedData`；一个无限域 UUID 对应一个 `ECOInfiniteStorageData`。仓库缓存引用原版持有的同一个对象，多个视图不会持有互相覆盖的库存副本。

| 组件 | 职责 |
| --- | --- |
| `StorageAmounts` / `StorageTotal` | 唯一数量表、精确汇总、内容与结构版本；常规操作使用原始 long，溢出数量采用 BigInteger 基数和 long 增量 |
| `ECOStorageCell` | 保留 main 的容量、类型、过滤、虚空卡等策略；提供 long 和 BigInteger 存取接口 |
| `StorageSavedData` | 区分接受版本与落盘版本，负责统一保存、重试和计时 |
| `ECOStorageRepository` | 每台服务器独立的盘实例、挂载租约、摘要通知与分散检查点 |
| `StorageTransferCoordinator` / `StorageTransferData` | 普通盘迁入无限域的持久化意图、回执及恢复；每个源盘复用一个事务记录 |
| 无限域内的恢复计划 | 普通盘恢复目标的精确 before/after/累计转移量，以及源域资源退休顺序 |
| `StorageCatalogSavedData` | 记录成功发布过的域，防止其文件丢失后重新创建成可写空域 |

盘物品仅携带 `neoecoae_storage_ref`、配置和显示摘要。摘要含 UUID、版本、种类数、数量投影、必要时的精确总量及最多五条预览。成员关系和恢复回执的权威副本位于 SavedData；旧区块或背包中滞后的物品标记会在挂载时校正。

盘 UUID 的第二个物理宿主不能重复挂载。不会通过自动复制库存来解决 UUID 冲突。无宿主查询用于检查或迁移，不会取得另一个宿主的租约。

## 文件与格式

| 路径（相对世界目录） | 内容 |
| --- | --- |
| `data/neoecoae_cells/<uuid>.dat` | 普通盘，格式 2；兼容本架构格式 1 的 long 数量 |
| `data/neoecoae_infinite_<uuid>_v3.dat` | 无限域，内部格式 4；为保留原存档身份而沿用既有文件名，读取旧格式 1–3 |
| `data/neoecoae_transfers/<source-uuid>.dat` | 源盘的最新一次迁入事务及可重放快照 |
| `data/neoecoae_storage_catalog.dat` | 已成功发布的无限域身份 |
| 同名 `.bak` | 上一次被替换的文件，不自动回退使用 |

当前 main 物品内的 `neoecoae_cell_contents` 格式 1，在首次服务端挂载时导入。新 SavedData 成功落盘后才替换物品引用并移除完整物品库存；导入失败会保留原物品内容。未知资源条目保留原始 NBT，限制会丢弃这些条目的修改、清空或迁出操作。

对升级时已处于迁移中的旧物品，保留旧逐键回执的兼容身份，并写入独立事务快照。重启后即使没有原驱动器位置或回调，仍能跳过已经迁入的键。普通的新迁移只使用新的事务身份，避免历史回执影响后来独立存入的资源。

这不是将旧分支的独立后端格式全部导入的转换器。已发布域的缺失检测从其首次成功登记到 catalog 后生效；无法从缺失文件推断升级前是否曾存在一个从未登记的域。

## 数量与性能

1. AE2 的 long 接口只提供饱和投影。BigInteger 插入、取出、终端精确数量、SavedData、迁移回执和恢复分配均使用完整值；普通盘继续遵守实际容量，没有扩大注册盘的游戏容量。
2. 普通数量操作没有 NBT 编码或磁盘写入。大数库存中的小额 long 存取累积为原始数值增量，精确读取、保存或跨越数值边界时才归并 BigInteger。
3. 数量与类型统计增量维护。热路径不扫描整个库存，不重建整个 KeyCounter，不排序全部库存生成物品预览。
4. 同一控制器批次合并摘要和宿主通知。批次之外的通知按物品身份合并到服务器 tick；取盘前会刷新并验证持久化边界。
5. 检查点从标脏约 100 tick 后开始，按队列分散执行，每 tick 最多检查 16 项，使用 2 ms 的启动预算。单个快照是完整串行写入，预算不是单次文件 I/O 的耗时上限。原版自动保存和明确 flush 仍会完成所有必要保存。
6. AEKey 的 NBT 编码在记录内缓存，失效键在保存时清理。原版 SavedData 生命周期会保留加载过的记录至停服，没有声称通过删除业务 Map 就能卸载原版缓存。
7. 迁入事务在双方快照成功保存后清理重放载荷，仅保留小型完成回执；清空数量表时收缩底层数组，避免大量已退休源盘继续占用原库存的内存。

专服探针分别测量生产 `ECOStorageCell` 方法和保存了 100 位大数的无限域，均执行 50 万次小额存取。报告给出耗时、线程分配量、身份与精确数量校验，并要求测量循环内无快照写入。该测量是同一进程内的热路径微基准，不是整服 TPS、万人网络或百万种资源快照的性能保证。原始结果见 `evidence/server-write.json` 和 `evidence/server-read.json`。

## 保存与故障恢复

`contentRevision` 表示已接受的修改，`durableRevision` 表示已完成的快照。`acceptChanges()` / 兼容接口 `commit()` 不执行 I/O；迁移必须通过 `flush()` / `flushThrough(revision)` 的结果判断能否继续。

统一写入顺序为：完整临时快照 → 在写入所用 FileChannel 上 force → 解压读回校验 → 保留前一文件 → 原子替换。文件系统不支持原子替换时保留原文件并维持脏状态。目录同步在提供者支持时执行。

迁入无限域的顺序：保存源基线 → 保存事务意图 → 保存源封印 → 按事务回执导入并保存目标 → 清空源并保存成员归属 → 保存事务完成 → 解锁目标。失败时保留源或封印，重启在盘挂载前恢复。源码中保留的开发探针可在目标已保存的中断点停止事务，然后由下一次服务器启动完成。

退出无限模式的顺序：精确模拟目标容量 → 保存域内恢复分配并锁定源键 → 修改并保存目标盘内容和回执 → 扣减并保存源域 → 解除物品成员关系。模拟使用独立计数，不清除复制物品上的真实 SavedData 成员关系。

损坏、身份不匹配或已知数据缺失时，保留 UUID 和原始文件并锁定操作。不会把历史备份静默当作最新库存。保存失败后的取盘请求会被拒绝，调用方在转移物品所有权前会复查宿主是否实际移除了盘；区块卸载时则保留原版 SavedData 重试义务。

保证范围是本 Mod 管理的库存和上述持久化协议。普通未保存存取仍可能回退。第三方 ME 库存、玩家存档、区块与外部 Mod 未共同参与的 I/O，不具有覆盖整个世界的原子事务保证。

## 验证入口

```powershell
./build-local.ps1 compileJava portingContractsTest portingPlatformTest jar --console=plain
./build-local.ps1 -PstorageSavedDataSmoke=write -PstorageAcceptanceWorld=<新的测试世界名> runServer --console=plain
./build-local.ps1 -PstorageSavedDataSmoke=read -PstorageAcceptanceWorld=<同一个测试世界名> runServer --console=plain
python tools/porting/audit_package.py --output docs/storage/evidence/package-audit.json
```

开发专服探针只在非生产环境且显式启用属性时运行，使用单独世界和端口 25596，结束后自动停服。write 阶段会构造测试专用的中断事务和缺失文件，禁止指向玩家正在使用的世界。

单元/平台验收覆盖：真实 AE2 键编解码、原版保存循环、旧物品导入、UUID 复用与冲突、未知条目、损坏文件、路径失败重试、各迁移中断阶段、200–300 位数值、随机数量操作对照、精确恢复分配、旧半途迁移回执，以及物品取出失败时不产生复制品。

开发专服验收覆盖：真实注册盘、实际容量规则、存取、迁入/迁出、精确终端侧通道、两个进程之间的身份和数量恢复、中断事务的冷启动恢复，以及普通盘和已登记域缺失文件后拒绝写入。大容量普通盘接口的 BigInteger 验证通过探针中的扩展容量适配器进行，注册盘容量没有改动。

各层结果和性能原始证据见 `evidence/validation.json`。专服程序化验收与客户端人工交互、截图及普通整合包安装验收分别记录。

## 本次结果

| 验证层 | 结果 |
| --- | --- |
| 完整 `compileJava` | 通过 |
| `portingContractsTest` | 6 项通过 |
| `portingPlatformTest` | 137 项通过；0 失败、0 跳过 |
| 开发专服 write → 新进程 read | 通过；包含普通盘、BigInteger、迁入与迁出、中断事务和缺失文件 |
| 最终构建再次进入同一专服世界 | 通过 |
| JAR 静态检查 | 944 个源码文件对应类齐全；1,552 个类均为 Java 17；资源、Mixin、内嵌依赖检查通过 |
| `git diff --check` | 通过 |
| 客户端人工交互、普通整合包安装 | 本次未执行 |

两次完整验收进程中，50 万次生产普通盘存取分别耗时 44.404 ms、43.908 ms；100 位大数无限库存中的 50 万次小额存取分别耗时 16.537 ms、17.839 ms。测量循环内均无快照写入，大数循环线程分配量为 0 B/op。这些数值只描述上述微基准。

构建文件为 `build/libs/neoecoae-20.6.0-dev.jar`，最终 SHA-256、字节大小、逐层证据及最后一次重进的测量保存在 `evidence/package-audit.json` 与 `evidence/validation.json`。改动保留在本工作区，未发布远端。
