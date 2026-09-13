# 无限存储域审查

日期：2026-09-12。目标：接近 Omni 的 long 级存储盘，并降低运行开销。

结论：当前实现明显超出这个目标。它同时承担任意精度库存、16 分片持久化、后台日志与检查点、逐资源迁移事务和恢复状态管理。应收敛库存语义和数据所有权，再优化热点；只把代码拆成更多类不能解决复杂度。

本次为代码审查，未改动运行代码或世界存档，也未运行游戏性能基准。下列性能判断来自调用路径和数据结构，不代表已测得相对 Omni 的速度提升。

## 对照基线

本地 `E:/Minecraft Project/AE2OmniCells` 的 `AEUniversalCellData` 使用 `Object2LongOpenHashMap<AEKey>`，物品通过 UUID 关联世界 SavedData；`AEUniversalCellInventory` 直接引用该 Map，并增量维护字节统计。项目实际依赖的 Omni `8050965` JAR 经 `javap` 检查，也使用这些数据结构。

当前 `impl/storage/infinite` 下有 9 个 Java 文件、约 2315 行，另有控制器和驱动器中的迁移、回迁、封存与回执逻辑。行数只是范围指标，主要问题是这些状态必须共同保持一致。

## 发现

### 1. 高优先级：单资源容量已经偏离 long 目标

- `SavedDataInfiniteStorageEngine.java:47` 的 `acceptableInsertAmount` 不检查数量余量，健康域直接接受整个请求。
- `HugeAmount.java:72` 在 long 不够时提升为 BigInteger。
- 引擎还维护超大堆栈索引、排序接口和饱和显示；控制器 `ECOStorageSystemBlockEntity.java:1653` 则禁止有超 long 堆栈的域回迁。

这不是单纯的溢出保护，而是额外支持了任意精度库存。建议单个 AEKey 的数量明确限定为 `0..Long.MAX_VALUE`，存入量为 `min(requested, Long.MAX_VALUE - current)`，满后返回 0。跨资源统计之和仍可能超过 long；精确统计可以在小规模汇总层使用 BigInteger，不必让每个库存条目都使用 HugeAmount。

本地 Omni 源码本身也有不能照搬的边界行为：`AEUniversalCellInventory.java:308` 算出的接受量未扣除单 key 的 long 余量，随后在 331 行用饱和加法保存。接近上限时，可能报告接受了超过实际增长的数量。应对齐 long 存储目标，而不是复制这种行为。

### 2. 高优先级：网络汇总仍存在 long 溢出路径

`SavedDataInfiniteStorageEngine.java:143` 将 `visibleStacks` 直接 `out.addAll`，过滤分支也直接 `out.add`。虽然每个可见数量已截到 long，但 `out` 可能已包含其他盘的同种资源。

例如 `out` 已有 1 个铁锭，域贡献 `Long.MAX_VALUE`，直接相加会变成 `Long.MIN_VALUE`。项目依赖的 AE2 19.2.17 源码和字节码确认，`KeyCounter` 底层沿 `VariantCounter.add → AEKey2LongMap.addTo` 使用 fastutil 加法，没有饱和保护。未发现本项目对此路径的修补。

应在向已有汇总输出贡献数量时计算剩余 long 余量；同样检查过滤分支。这个修复与是否保留 BigInteger 无关。它只能保护本次贡献，其他存储随后追加数量是否安全仍取决于各自实现及网络汇总路径。

### 3. 高优先级：回迁的同步保存和重复全量扫描违背低开销目标

- `ECOStorageSystemBlockEntity.java:1584`：每个源盘开始迁移时，调用 `getChunkSource().save(true)`。
- `1820`：回迁每种资源前通过 `reserveRestore` 同步提交域。
- `1856`：每种资源写入目标盘后再次调用区块保存。
- `1865`：完成该资源后又同步提交域。
- `1875`、`1892`：每种资源的预期计算和结果校验都调用 `collectRestoreTargetContents`，遍历目标盘全部内容。

区块保存是维度区块源级调用，不是仅保存当前矩阵。在很多种类逐步写回目标盘时，反复扫描不断增长的内容可能接近二次复杂度；循环中的纳秒预算也限制不了这些同步保存调用。

若继续支持非空域自动回迁，必须保留必要的所有权保护，但应改为批次级边界和按 key 校验，单独衡量保存耗时。更简单的产品方案是仅允许空域退出，并让 UUID 始终关联原数据；这会改变现有使用方式，应作为独立设计选择，不能混在性能优化中直接实施。

### 4. 中优先级：持久化设计的成本远超已证明的收益

`InfiniteStorageJournal.java:35` 固定建立 16 个分片；各分片有快照、日志、head，运行后还有备份和轮换日志。`append` 在 276 行开始，每批包含日志 force 和 head force，并管理检查点任务。世界保存和迁移提交通过 `ECOInfiniteStorageData.java:533` 等待后台任务、同步清空脏数据。

增量日志确实能减少“大量不同 key、少量 key 经常变化”时的全量序列化成本。但单个数量从 100 变成 long 上限不会增加 key 的数量，单凭容量大并不足以证明需要分片日志。当前还没有本次审查可用的对照性能测量来证明这套成本值得保留。

建议新实现先采用每域一份 SavedData，保留临时文件替换、坏条目原样保存、读取失败不当空域等保护。只有基准证明全量保存成为瓶颈，再引入有界后台快照或增量保存，并明确后台任务与主线程数据的所有权。

### 5. 中优先级：正常存取也支付了日志和任意精度设计的成本

- `ECOInfiniteStorageData.java:412`、`436` 在读写检查中根据已编码 NBT 的 hash 计算分片。复杂 key 的检查成本包含其 NBT 结构遍历；首次写检查还会编码 key。
- `setAmount` 同时维护库存、dirtyKeys、revision 和 keyVersions。
- `SavedDataInfiniteStorageEngine.java:297` 每次修改生成不可变 HugeAmount，并更新可见计数、类别统计及可能存在的超大数量索引。
- 日志分片另外保留按 NBT key 组织的内容视图。

这些机制各有用途，但“平均 O(1) Map 操作”并不等于开销与 Omni 相同。建议以一个 primitive long Map 作为唯一数量来源，类别统计增量维护，UI 摘要按 revision 缓存。是否保留第二份 KeyCounter 索引应根据网络枚举和修改频率测量决定，避免默认复制多套视图。

## 建议的收敛范围

目标结构：`控制器/域 UUID → 单份 SavedData → Object2LongOpenHashMap<AEKey> → MEStorage 适配`。

保留多方块成型条件、组件要求、域 UUID、资源类型兼容、数量增量统计以及基本存档保护。正常存取在服务器线程修改 Map 并标脏；预览和界面同步按需合并，不能每次操作都重建显示数据。

从新库存核心移除任意精度条目、HugeStack 特殊索引、默认 16 分片、逐 key 日志版本和多层健康状态之间的耦合。迁移若仍属于现有功能，就单独承载，不能直接删除封存或回执而保留双份库存。

兼容边界必须明确：

1. 旧 `.store` 日志目录需由旧读取器完整恢复后转换，不能只读取作为标记的 `.dat`。
2. 已有超过 long 上限的库存不能截断。保留旧格式读取/提取能力，或采用显式无损拆分方案，再转换。
3. 未完成迁移、恢复预留和成员盘回执必须结算或完整保留后才能切换所有权。

## 验证标准

正确性至少覆盖：long 上限前后的接受量、SIMULATE 不改库存、多个存储对同 key 的汇总、存档往返、坏条目保留、迁移中断恢复及旧格式超限数据。

性能应在相同 AE2/Omni 依赖、JVM 和硬件下比较：重复单 key 存取、大量不同 key、复杂 NBT key、终端枚举、世界保存以及迁移/回迁。记录吞吐、分配量、保存耗时和服务器 tick 高分位耗时；仅测 insert 循环不能说明整套系统更快。
