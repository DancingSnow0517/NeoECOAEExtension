# C 系列合成执行与可选批量能力

执行模型沿用 AE2 的 Job、输入选择与等待输出记账，额外支持显式声明的
`ECOBatchCapacityProvider`。普通 provider 继续使用单份 `pushPattern()`。
工作区的构建版本为 `21.2.0-preview10`，本次未修改版本号。

## 执行规则

- 提交时检查 CPU 忙碌、在线状态、容量，并通过 AE2 helper 提取初始材料。
- Job 保存 crafting link、最终目标、剩余数量、等待输出和 pattern 剩余执行次数。
  保留 AE2 的暂停、耗时及玩家通知元数据。
- 使用 AE2 helper 和稀疏只读库存预览选择每份实际输入，包含替代材料和容器返还物。
  缺少输入时继续寻找可执行的上游 pattern。
- 按 CraftingService 的 provider 列表轮转，实时跳过忙碌 provider；提交拒绝后
  本轮停止，下 tick 从后续 provider 继续。拓扑版本改变时重新获取列表。
- 对可选批量能力，先查询当前容量，再取任务剩余份数、CPU 本 tick 剩余额度、
  实际输入库存份数、可支付能源份数、provider 容量的最小值，并施加整数安全上限。
- 确定批次后一次提取整批输入，原子提交成功后一次登记整批输出和容器返还物，
  扣减任务份数并收取能源。拒绝或异常时完整归还输入，不修改任务或等待输出。
- 返回物品一律进入 CPU inventory。最终产物从该库存交付，拒收或异常时留在库存重试。
- 完成判定要求 remainingAmount <= 0、waitingFor 为空，且没有未派发的 pattern。

## 批量协议

`ECOBatchDispatchContext` 包含 pattern、按槽位保存的每份实际输入、每份输出、
容器返还物、Level 和可选 job id；集合不可修改，提供给适配器的输入计数器为副本。
容量单位始终是完整样板份数。例如 `1A -> 1B` 能接受 4096 份时返回 4096。

CPU 不缓存容量。每次派发重新查询，因此 provider 自身刷新、拓扑刷新、同 tick
库存变化和提交拒绝都不会留下过期容量提示；容量不进入 Job 或 NBT。

ECO 样板总线通过此能力向自己的 worker 提交经过验证的同构批次。
未验证配方及不能表示为 N 份相同实际输入的耐久/状态转换配方继续走单份路径。
MEGA 解压服务按 pending output 的整数剩余空间报告容量，提交前完整校验输出。

旧 `ECOBatchProbeCraftingProvider` 和 `ECOThunderboltBatchBridge` 已删除。
总线旧 `pushBatch()` 入口已移除；AdvancedAE 内部的已验证提交使用
`acceptVerifiedBatch()`。依赖旧方法签名的外部追踪模组需要同步适配。

本地 Thunderbolt 的 `IBatchCraftingProvider` 明确允许部分接受并返回剩余份数，
不能无损转换为本协议的原子 boolean 提交，故保留单份回退。它需要主动实现
`ECOBatchCapacityProvider` 或提供有原子性保证的专用适配器才能参与此路径。
普通 AE2 provider 不从 `isBusy()` 推断容量；原版分子装配室仍使用单份接口。

## 存档

继续保存 AE2 形式的 inventory、link、finalOutput、remainingAmount、
waitingFor、tasks/#craftingProgress，以及原有暂停和耗时元数据。
旧的 bufferedFinalOutput 在读取时迁入 CPU inventory，新存档不再写该字段。
旧 runtime/phase/ownership 元数据不参与恢复和执行。

## 验证范围

`ECOBatchCapacityDispatchTest` 直接驱动生产 CPU 与 AE2 输入 helper，覆盖完整份数、
各项批次上限、提取时序、输出与容器计数、拒绝/异常回滚、后续 provider、
容量重新查询及普通 provider 的单份回退。单元测试使用测试 key、配方和 provider，
不替代整合包实服验收；本次未验证真实服务器写盘、重启或第三方原子批量适配器。
