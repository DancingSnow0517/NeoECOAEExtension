# AE2LT / Thunderbolt 合成互通

[文档目录](README.md) · [API 调用指南](API_ZH_CN.md)

适用于 Minecraft 1.21.1 / NeoForge，说明 ECO 与 AE2 Lightning Tech、Thunderbolt 的任务身份、资源所有权及数量边界。依赖配置见 [`build.gradle`](../build.gradle)；能力按实际公开接口探测，不由版本字符串或 Mod 名称推断。

## 互通路径

| 方向 | 接入方式与边界 |
| --- | --- |
| 天枢 → ECO F / FX | 通过 `ECOFastPathFacade.prepareAllocated` 移交已分配材料；普通回退保留同一任务 UUID。FX 精确入口需要双方明确支持。 |
| ECO → 天枢合成器 | 通过矩阵端口普通 `pushPattern` 发配，沿用矩阵核心的缓冲返回；专用矩阵批量入口尚未接入。 |
| 天枢 → ECO 大型集成工作站 | 通过 `eco$pushPatternBatch` 携带任务 UUID；工作站查询持久终态并处理取消后的材料与产物。 |
| ECO → 过载样板供应器 | 由 ECO 智能翻倍决定份数、增长、退避和预算，直传失败时普通回退；ID_ONLY 过载协议按 long 批次记账。 |

## 阻挡与 ECO 智能翻倍

| 供应器模式 | ECO 派发规则 |
| --- | --- |
| 阻挡关闭 | 根据材料、能量和机器接收能力增长批量。 |
| 普通阻挡 | 每次派发一份原始样板；阻挡解除后继续，保留手动编码的样板数量。 |
| 允许同一样板继续 | 已完整识别 AE2LT 原生直传契约时允许 ECO 翻倍；每个目标在每次实际投料前校验原始样板，切换样板仍受原生阻挡限制。 |
| 无法可靠识别的第三方阻挡 | 保守使用普通单份派发，不猜测其阻挡模式。 |

同样板模式只开放 AE2LT 原生直传通道，其他批量入口仍遵守原有阻挡限制。模式在派发时实时读取；切回普通阻挡立即停止后续批量，已被供应器接收的材料保留原任务归属。阻挡、材料不足或接收受限时暂停或回退，条件恢复后继续，不重复移交已接受的材料。红石锁和等待产物锁仍由原生供应器检查。

## 任务身份与取消

普通回退通过 `ECOJobAwareCraftingProvider` / `ECOFastPathFacade.pushPatternForJob` 保留任务 UUID，只移交当前一份材料。没有任务上下文能力的外部供应器继续使用普通 `pushPattern`。

时间轮取消桥从活动 `link.getCraftingID()` 取得 UUID，在 CPU 清理前调用 `ECOCraftingJobLifecycle.cancelAndRecover`。先持久记录终态，再通知已加载的 F / FX 工作单元；大型工作站在 tick 时查询，卸载机器重新加载后仍可读取终态。天枢大数后端通过 `ECOFastPathFacade.cancelJob` 发送相同通知。

取消时，未完成工作归还实际保管的输入，已完成工作交付实际产物。交付成功后才能移除对应队列项；回料还需校验目标 CPU 仍属于同一任务，避免将旧产物记入新订单。

## 过载产物与存档

- 派发前检查严格等待与同 ID 模糊产物是否冲突；只为实际接受的份数登记预期产物，普通回退同样只登记一次。
- 精确模板或 ID_ONLY 变体被认领时，同步扣减过载账和 ECO 等待量，实际资源键保持原样。
- SIMULATE 不改账；部分认领保留尾量。先退役认领，再调用实际交付，防止回调重入重复认领。
- 使用任务附件 `ae2lt:overload_outputs` 保存剩余账；恢复同一任务时加载，取消、完成或替换 CPU 检查点时清理。
- 请求端部分接受或拒收后，剩余产物留在 CPU 中重试；结果不确定时停止任务，不自动退款或再次移交材料。

公开门面完整时使用公开 API 桥。已发布依赖缺少新门面时，隔离兼容实现 `ECOAe2LtReleasedAccounting` 使用发布 JAR 已有的过载契约；旧时间轮普通回退由可选 Mixin 补充任务上下文。缺少精确接口时保留普通 / long 路径和未交付库存。

## 数量与资源所有权

优先使用 long；数量确有需要且目标具备明确精确协议时才使用 BigInteger。安全检查同时覆盖批次数和每种输入、输出及返还物的合计数量。

| 边界 | 数量超出 long 时 |
| --- | --- |
| ECO 内部任务与 FX | 使用精确任务、库存和虚拟线程账本，保留完整总量。 |
| 天枢大数 CPU ↔ ECO FX | 双方支持时使用 `prepareExactAllocated` 和精确回料 API；一个批次占一个线程槽，CPU 保留材料和等待账所有权。 |
| ECO CPU → ECO 存储 | 通过 `ECOBigIntegerStorage` 返回精确数量，只扣除实际接受量；余量回到 long 范围后恢复普通调用。 |
| 普通外部容器、供应器与 AE2 存储 | 按安全 long 窗口拆分，余量留在精确账本中继续交付。 |

天枢到 FX 的精确入口要求 FX 完整虚拟模式及经过 FastPath 验证的 NORMAL 配方，不包含返还容器、耐久工具或共享种子状态。其他配方继续使用带 UUID 的普通路径。

材料、能量或容量不足时只接受可处理的部分。确认拒收可以回滚；提交结果不确定时停止任务，不能自动退款或重试。普通供应器的接收、回滚及单次记账约定见 [API 指南](API_ZH_CN.md#6-样板供应器接入)。

## 源码入口

- [FastPath 普通、long 与精确入口](../src/main/java/cn/dancingsnow/neoecoae/api/me/ECOFastPathFacade.java)
- [普通任务上下文接口](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOJobAwareCraftingProvider.java)
- [持久终态与即时回收](../src/main/java/cn/dancingsnow/neoecoae/crafting/execution/worker/ECOCraftingJobLifecycle.java)
- [时间轮取消桥](../src/main/java/cn/dancingsnow/neoecoae/compat/ae2lt/ECOAe2LtCancellationBridge.java)
- [过载 API 桥及任务附件](../src/main/java/cn/dancingsnow/neoecoae/compat/thunderbolt/ECOOverloadCpuAccountingBridge.java)
- [实际输出认领与返回](../src/main/java/cn/dancingsnow/neoecoae/crafting/execution/ECOCraftingOutputDelivery.java)
- [FX 精确保管账本](../src/main/java/cn/dancingsnow/neoecoae/crafting/execution/worker/ECOExactVirtualLedger.java)
- [精确存储入口](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOBigIntegerStorage.java)
