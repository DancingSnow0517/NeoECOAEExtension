# NeoECOAEExtension 合成性能优化技术报告

> 项目：NeoECOAEExtension-1.21.1（Minecraft NeoForge AE2 扩展模组）
> 优化目标：ECO 合成 CPU 的吞吐量提升与物品安全保障
> 涉及模块：CPULogic、PatternBus、Worker、Thread、FastPath、StorageCell

---

## 一、背景与优化动机

ECO 合成系统是 AE2 合成服务的扩展实现，通过多方块结构（ECOCraftingSystem → PatternBus → Worker → Thread）提供比原版分子装配室更高吞吐的合成能力。原始实现在以下方面存在性能瓶颈：

1. **逐 Pattern 推送**：每 tick 逐个推送 pattern 到 provider，大量时间消耗在迭代和 IPC 上
2. **合成模拟重复计算**：每个 Worker 线程收到 pattern 后都要重新执行 `assemble()` 计算输出，即使相同配方已执行过多次
3. **状态变更通知泛滥**：每次 pattern 推送/完成都触发独立监听器回调，UI 刷新消耗高
4. **物品回滚/恢复逻辑粗糙**：取消合成或 Worker 破坏时可能出现物品丢失或复制

---

## 二、优化技术路径总览

```
┌─────────────────────────────────────────────────────────────────┐
│                     ECOCraftingCPULogic                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ 状态批处理    │  │ Push Tick Cap│  │ FastPath 批量尝试      │  │
│  │ (Batching)   │  │ (限流控制)    │  │ + 精准回滚             │  │
│  └──────────────┘  └──────────────┘  └───────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│              PatternBus / Worker / Thread                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────────┐  │
│  │Round-Robin│ │槽位判断  │ │Worker    │ │输出喷射 + Remainder│  │
│  │调度      │ │(双层)    │ │Cache     │ │重试机制            │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                     FastPath 子系统                              │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ FastPathKey  │  │ FastPathCache│  │ AE2PatternIntrospection│  │
│  │ (不可变键)   │  │ (LRU + 代际) │  │ (安全校验 + 重载感知)  │  │
│  └──────────────┘  └──────────────┘  └───────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                     ECOStorageCell                               │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ insert/extract 增量计数 + persist() 全量校验 + 类型溢出日志  │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、各模块技术细节

### 3.1 ECOCraftingCPULogic — 核心调度引擎

#### 3.1.1 状态变更批处理 (Status Change Batching)

**问题**：每次 pattern 推送、物品注入、输出就绪都触发 `postChange(AEKey)` → 遍历所有监听器。一个大合成任务可能有上千次变更，UI 刷新消耗 O(n × listeners)。

**方案**：在 `executeCrafting()` 单次 tick 执行期间，将状态变更聚合为批量通知：

```
BEGIN_BATCH → [postChange(k1), postChange(k2), ..., postChange(null)]
           → END_BATCH → 去重后一次性通知所有监听器
```

关键实现：
- `beginStatusChangeBatch()` / `endStatusChangeBatch()` 包装每次 tick 的 pattern 推送循环
- `batchedStatusChanges: Set<AEKey>` 自动去重，避免同一物品重复通知
- `batchedFullStatusChange: boolean` 处理 `null` key（全量刷新信号）
- `endStatusChangeBatchSafely()` 在 finally 块中调用，捕获 `RuntimeException`/`Error` 后清理批处理状态再重新抛出，防止状态泄漏

**效果**：单 tick 内 N 次变更 → 1 次批量通知，UI 刷新开销从 O(n²) 降至 O(n)。

#### 3.1.2 Push Tick Cap（推送速率限制）

**问题**：无限制的 pattern 推送可能在单 tick 内耗尽所有 CPU 时间。

**方案**：
```java
ECO_CPU_PUSH_TICK_LIMIT = Integer.getInteger("neoecoae.ecoCpuPushTickLimit", Integer.MAX_VALUE)
getOperationLimit() = min(cpu.getCoProcessors() + 1, ECO_CPU_PUSH_TICK_LIMIT)
```
默认不限制（`Integer.MAX_VALUE`），服务器管理员可通过 JVM 系统属性按需限流。

#### 3.1.3 FastPath 批量尝试 + 精准回滚

**问题**：逐 pattern 推送效率低，批量推送需要处理部分失败的物品回滚。

**方案**：
1. `tryPushVerifiedFastPathBatch()` 尝试将多个相同 pattern 合并为一次批量推送
2. 批量大小由多因素决定：库存余量、能量余量、冷却液余量、FastPath 缓存命中、tick 预算
3. **精准回滚** `rollbackBatchInputs()`：
   - `firstInputsOwned`：父级已提取的第一份 pattern 输入是否需要回滚
   - `extraInputsExtracted`：批量额外提取的 N-1 份输入是否需要回滚
   - 分离两个布尔值避免旧版"一把梭 `inputTotal`"导致的物品凭空出现/消失

**效果**：批量大小从 1 提升到 min(64, 库存/能量/冷却液允许)，理论吞吐提升 64x（实际受限于多方块线程数）。

---

### 3.2 PatternBus / Worker / Thread — 执行层

#### 3.2.1 Round-Robin 调度 + 线程槽位检查

**PatternBus → Worker 分配**：
```java
nextWorkerIndex = Math.floorMod(nextWorkerIndex, workers.size())
// 从上次成功分配的 Worker 的下一个开始遍历
```
每次成功推送后更新 `nextWorkerIndex`，失败不更新 → 自动跳过繁忙 Worker。

**Worker → Thread 分配**：
```java
nextFreeThreadIndex = Math.floorMod(nextFreeThreadIndex, threadCount)
// 同上，在 Worker 内部的线程间轮转
```

**双层槽位检查**：
- **PatternBus 层**（保守）：先检查 Controller 全局 `threadCount - runningThreads`，再逐个 Worker 检查空闲
- **Worker 层**（精确）：检查 `threadCountPerWorker - getRunningThreads()`

修复了一个关键 bug：旧版 `pushPattern` 遍历线程时，第一个空闲线程失败后直接 `return false`，不继续尝试后续线程。新版的 for 循环自然 fall through 继续迭代。

#### 3.2.2 Worker 级 FastPath 缓存

每个 Worker 维护独立的 `ECOCraftingFastPathCache`（LRU，默认 512 条目）：

```
配方首次执行 → calcPatternSlow() → verifyAndCacheFastPath() → 缓存 (outputs, inputs, remaining)
配方再次执行 → acceptPattern() → cache.get(key) → 命中 → 跳过 assemble() → 直接使用缓存数据
```

缓存键 `ECOFastPathKey` 包含：
- `patternIdentity`（配方定义 AEItemKey）
- `dimension`（维度，跨维度配方可能不同）
- `reloadGeneration`（配方/标签重载代际，重载后旧缓存自动失效）
- `slots`（输入槽位签名，区分相同配方不同输入排列）

#### 3.2.3 输出喷射 + Remainder 重试

**旧版流程**：
```
SIMULATE 检查存储空间 → 通过？→ MODULATE 插入 → 忽略返回值 → 物品可能丢失
```

**新版流程**：
```
直接 MODULATE → ejectAllAndCollectRemainder() → 追踪实际插入量
  → remainder 为空？→ 成功，clearWork()
  → remainder 非空？→ retainRemainderForRetry() → 保留未插入部分，下 tick 重试
```

关键方法：
- `ejectAllAndCollectRemainder()`：逐个 AEKey 尝试 `insertIntoCpus`（链式 CPU）→ `storage.insert`（网络存储），收集未插入的 remainder
- `retainRemainderForRetry()`：将 remainder 转为 ItemStack 列表，替换 `outputItems`，保持 `isBusy=true`，下 tick 重新尝试喷射
- `retainInputRemainderForRetry()`：同上但用于输入恢复场景

**效果**：彻底消除 TOCTOU 窗口，物品零丢失。网络拥堵时自动降级为重试循环，恢复后自动完成。

#### 3.2.4 RecoveryState 状态机

防止物品重复恢复/复制：

```
                 startWork()
                     │
                     ▼
    ┌─────────────────────────────────┐
    │           ACTIVE                │
    │  (正常工作)                      │
    └────┬──────────────┬────────────┘
         │              │
         │ recover      │ dropRecoverables
         │ InputsTo     │ AndClear
         │ Network      │
         ▼              ▼
    ┌────────────┐  ┌────────────────┐
    │ RECOVERED  │  │ DROPPED_TO     │
    │ _TO_NETWORK│  │ _WORLD         │
    └────────────┘  └────────────────┘
         │              │
         └──────┬───────┘
                │ clearWork()
                ▼
         ┌──────────┐
         │ CLEARED  │
         └──────────┘
```

- `recoverInputsToNetwork()` 仅在 `ACTIVE` 状态执行恢复，之后标记为 `RECOVERED_TO_NETWORK`
- `dropRecoverablesAndClear()` 仅在 `ACTIVE` 状态掉落物品，之后标记为 `DROPPED_TO_WORLD`
- 状态通过 NBT 持久化，服务器重启后不会重置

---

### 3.3 FastPath 子系统

| 类 | 职责 |
|----|------|
| `ECOExtractedPatternExecution` | 封装 pattern 执行上下文（container、expectedOutputs、inputs、fastPathKey） |
| `ECOFastPathKey` | 不可变缓存键，预计算 hashCode，包含配方身份 + 维度 + 代际 + 槽位签名 |
| `ECOFastPathResult` | 缓存值（positive/negative），含 outputEntries、inputEntries、remainingEntries、lastAccessTick |
| `ECOCraftingFastPathCache` | LinkedHashMap LRU（默认 512），含命中/未命中统计，WeakHashMap 跟踪所有活跃缓存 |
| `ECOBatchCraftingHelper` | 批量工具：multiply、maxCraftsFromInventory、extractExact、insertAll |
| `AE2PatternIntrospection` | AE2 兼容层：检查 `AECraftingPattern` 类型安全、配方重载时清空所有缓存并递增 `reloadGeneration` |

**FastPath 安全策略**：
- `NEConfig.ecoAe2FastPathEnabled` 默认 `true`，保留配置项用于兼容性回退
- `isKnownSafePatternType()` 仅放行 `instanceof AECraftingPattern`
- `postCraftingEvent` 开启时禁用 FastPath（事件可能改变合成结果）
- 缓存验证：慢速路径计算出结果后与预期对比，不一致时存为 negative cache

---

### 3.4 ECOStorageCell — 增量计数维护

**变更**：从每次 NBT 全量序列化计数，改为 `insert`/`extract` 时增量更新 `storedItemCount` 和 `storedItems`。

**安全保障**：
- `persist()` 时从 `storedAmounts` map 重新计算，作为增量计数的校验点
- 类型数溢出检测：`actualTypes > maxItemTypes` 时输出 WARN 日志
- `saturatedAdd()` 防止 `storedItemCount` 溢出

---

## 四、迭代修复记录

在开发过程中通过多轮代码审查发现并修复了以下问题：

| # | 严重度 | 问题 | 修复方式 |
|---|--------|------|---------|
| 1 | 🔴 致命 | `ejectAll` 忽略 `storage.insert` 返回值 → 物品静默消失 | `ejectAllAndCollectRemainder` + `retainRemainderForRetry` |
| 2 | 🔴 致命 | Worker 破坏 + CPU 取消 = 物品复制 | `RecoveryState` 状态机 + NBT 持久化 |
| 3 | 🔴 高 | 批量回滚 `inputTotal` 包含已由父级管理的第 1 份输入 | `rollbackBatchInputs` 分离 `firstInputsOwned` / `extraInputsExtracted` |
| 4 | 🟡 中 | `Worker.pushPattern` 首个空闲线程失败后直接 `return false` | 删除过早 return，for 循环 fall through |
| 5 | 🟡 中 | 批处理异常导致 `batchingStatusChanges` 永久 stuck | `endStatusChangeBatchSafely` + finally 块 |
| 6 | 🟡 中 | `RecoveryState` 不持久化 → 重启后双重恢复 | NBT 序列化 + `readRecoveryState()` |
| 7 | 🟡 中 | `retainRemainderForRetry` 遇非物品 Key 不清状态 | 添加 `onThreadStop` + `clearWork()` 再 return |
| 8 | 🟡 中 | `canEjectAll`(SIMULATE) → `ejectAll`(MODULATE) TOCTOU | 删除预检，直接 MODULATE + remainder 兜底 |
| 9 | 🟡 中 | PatternBus `isBusy()` 仅用全局槽位 → 假繁忙 | 全局上限保护 + 逐个 Worker 空闲检查 |

---

## 五、性能特征总结

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| Pattern 推送粒度 | 1 pattern/tick/push | 最多 64 pattern/tick/batch |
| 合成模拟 | 每次 `assemble()` 重新计算 | FastPath 命中时跳过计算 |
| UI 状态通知 | N 次/tick（每个 pattern 一次） | 1 次/tick（批量去重） |
| 物品丢失风险 | 存在（未检查返回值） | 零（remainder 追踪 + 重试） |
| 物品复制风险 | 存在（Worker 破坏 + CPU 恢复） | 零（RecoveryState 状态机） |
| 回滚准确性 | 粗糙（全量输入回滚） | 精准（first + extra 分离控制） |
| 线程调度 | 首次失败即放弃 | Round-robin 遍历全部线程 |

---

## 六、配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `NEConfig.ecoAe2FastPathEnabled` | `true` | FastPath 批量缓存总开关 |
| `neoecoae.ecoCpuPushTickLimit` | `Integer.MAX_VALUE` | 每 tick 最大 pattern 推送数 |
| `neoecoae.ecoBatchFastPathLimit` | `64` | 单次批量合成最大数量 |
| `neoecoae.ecoBatchFastPathTickLimit` | `256` | 每 tick 批量合成总上限 |
| `neoecoae.ecoFastPathCacheSize` | `512` | 每个 Worker 的 FastPath 缓存条目数 |
