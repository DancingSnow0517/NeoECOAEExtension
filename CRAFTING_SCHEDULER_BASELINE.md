# C 系列普通合成执行基线

本次修改恢复简单执行模型，不引入新的调度策略。参考本地 AE2
`CraftingCpuLogic`、`ExecutingCraftingJob` 和 `CraftingCpuHelper`。
工作区的构建版本为 `21.2.0-preview10`，本次未修改版本号。

## 执行规则

- 提交时检查 CPU 忙碌、在线状态、容量，并通过 AE2 helper 提取初始材料。
- Job 保存 crafting link、最终目标、剩余数量、等待输出和 pattern 剩余执行次数。
  保留 AE2 的暂停、耗时及玩家通知元数据。
- 每次只执行一个 pattern。缺少输入时继续寻找可执行的上游 pattern。
- 按 CraftingService 返回顺序跳过忙碌 provider；第一个空闲 provider 拒绝后，
  本 tick 停止。后续 tick 重新读取忙碌状态，不保存拒绝或预测状态。
- 输入提取、失败归还使用 AE2 helper；成功推送后登记等待输出、减少任务次数。
- 返回物品一律进入 CPU inventory。最终产物从该库存交付，拒收或异常时留在库存重试。
- 完成判定只有 remainingAmount <= 0 且 waitingFor 为空。

CPU 核心已移除策略、批量探测、accounting、运行时预测、phase gating、
cycle ownership、feedback reserve 和独立最终产物缓冲。
Planner 仍负责生成 ICraftingPlan，但 Job 不读取其执行契约或编译状态。
FastPath 的实现和 provider 内的慢路径回退保留；CPU 不调用 FastPath/batch API。

## 存档

继续保存 AE2 形式的 inventory、link、finalOutput、remainingAmount、
waitingFor、tasks/#craftingProgress，以及原有暂停和耗时元数据。
旧的 bufferedFinalOutput 在读取时迁入 CPU inventory，新存档不再写该字段。
旧 runtime/phase/ownership 元数据不参与恢复和执行。

## 验证

- gradlew.bat build --offline --console=plain：成功。
- 普通 test：268 项，0 失败，3 跳过。
- ECOSimpleCraftingCPUTest：7 项通过，直接驱动生产 CPU 和 AE2 输入提取 helper。
  包括木板到工作台的等价测试配方、从零水晶执行 500000 份目标产物的两级配方、
  全部 provider 忙碌后恢复、provider 拒绝与顺序、交付失败重试、
  等待容器输出、读取 NBT 后继续交付。
- plannerTest 全量：269 项，15 失败、3 跳过。相同的 15 项 CraftingGraph
  失败已在重构前的 956991bb 对照工作树复现：测试类重复调用
  AEKeyTypesInternal.setRegistry。该测试类单独运行时重构前后均通过。
- delombok 输出可选 AppBot/KubeJS 缺依赖诊断，但 Gradle build 最终成功。

单元测试使用测试 key、配方和 provider，不是整合包内真实初级精华配方的实服验收。
NBT 测试覆盖真实读取路径和恢复后执行；完整物品编码依赖 NeoForge 初始化，
尚未完成真实服务器的写盘、重启和读盘验证。
