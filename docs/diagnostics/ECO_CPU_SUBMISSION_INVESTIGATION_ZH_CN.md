# ECO CPU 点击开始后停留在确认界面的排查

排查基线：本工程 `809988a6`（20.5.2）。输入为用户提供的 20.5.0 / 20.5.2 问题报告与日志片段，没有使用故障当时的完整日志或存档。

## 结论与边界

已经确认并修复提交包的异常反馈缺失：提交动作在服务端排队任务中抛出运行时异常或 API 链接错误时，原处理器没有记录完整异常，也没有给确认菜单写入错误。排队执行器可以把异常保存在返回的 Future 中，而 `NEPacketContext.enqueueWork` 没有观察这个 Future。因此“收到提交包，此后既不跳转，也没有 submitError”不能据此推断为正常拒单或没有异常。

本次不能确认旧故障中究竟是哪一个调用抛出异常，也没有复现“正常 ECO CPU 一定不能下单”。实际 Forge/Mixin 调用验证中，基础环境以及 AE2 15.4.10 + GTLCore 1.2.3.2-fix4 的受控接单检查均能通过。修复范围是让提交异常可见；它不是已经证明修复了旧故障的全部接单根因。

## 已核对的提交链路

用户日志为：

```text
[big-order-submit] Client start: container=3, forced=false, bigOrder=false, status=SUCCESS
[craft-submit] Server received: container=3, currentContainer=3, forced=false
```

实际路径是：

```text
ECOCraftConfirmScreen.submit(false)
  -> ECOForceCraftStartFlagC2SPacket
  -> CraftConfirmMenu.startJob()
  -> CraftingService.submitJob(..., selectedCpu, ...)
  -> CraftingServiceEarlyMixin
  -> NEComputationCluster.submitJob()
  -> threadingCore.spawn()
  -> ECOCraftingCPULogic.trySubmitJob()
```

这一条日志不经过 `neoecoae$startBigOrder`，所以缺少 big-order 提交或拒绝日志不构成异常证据。

- AE2 的 `ICraftingCPU.getName()` 明确允许 null。本工程 ECO CPU 的返回值符合接口约定；用户也已观察到未命名原版 CPU 返回 null 且能提交。没有修改这个返回值。
- AE2 原生服务只直接处理 `CraftingCPUCluster`，但本工程存在先于原生自动选择的 ECO 转交。实际变换后的代码可以命中这一入口，没有发现“只加入列表、完全没有实现提交转交”的普遍性缺陷。
- CPU 离线、忙碌、存储不足、初始材料不足等正常拒绝会返回 `ICraftingSubmitResult`；这些拒绝与未写入错误的静默返回应分别处理。
- 原生 `startJob()` 的计划缺失或模拟计划分支会不提交就返回。GTLCore 也有提交重算等待状态。旧片段没有相应状态，不能排除这两类提前结束调用；本次没有绕过它们。

## 异常为何可能没有出现在日志里

核对了 Forge 47.4.28 的 `NetworkEvent.Context.enqueueWork` 源码：非主线程收到的任务交给 `BlockableEventLoop.submitAsync`，返回 `CompletableFuture<Void>`。

原调用链没有检查该 Future，两个提交包中也没有覆盖整个提交动作的异常边界。普通错误返回的处理代码只有在 `menu.startJob()` 正常返回后才会执行，因此提交中途抛出的异常不会走到它。

本次引入 `CraftSubmissionPacketFeedback`，用于普通提交和 big-order 提交：

1. 只执行一次提交动作，不自动重试，不切换目标 CPU。
2. 捕获 `RuntimeException` 与 `LinkageError`，保留完整异常栈、分支、菜单 ID 与强制提交标志。
3. 关闭自动提交，写回 AE2 已有的 `INCOMPLETE_PLAN` 错误，使现有错误界面能够提供反馈。
4. 不改变正常成功、CPU 拒绝、菜单有效性或远程访问校验。

这一改动没有承诺回滚提交动作抛出异常前可能发生的内部状态变更，也没有加入自动重试。原始内部异常及其事务恢复需要根据实际触发点分别修复。

## 验证

### 针对性测试

以下 4 个测试类共 18 项，0 失败、0 错误、0 跳过：

- `CraftStartPacketTest`：合法菜单提交、无效菜单、缺少接口，以及普通和 big-order 排队任务中发生异常时的界面反馈。
- `CraftingSubmissionSelectionTest`：选中的 ECO CPU 接收原计划、原请求者和原动作来源；拒绝不改投其他 CPU。
- `ForeignPlannerEcoCpuSubmissionContractTest`：外部规划结果的 ECO 自动选择和拒绝保留。
- `ForcedCraftingSubmissionTest`：强制提交与缺失材料等待契约。

排队测试使用 Future 保存任务异常的执行方式。普通提交分别覆盖 `IllegalStateException` 和 `NoSuchMethodError`；新增测试在旧的无保护处理器中不能得到菜单错误。

### 实际 Forge/Mixin 调用检查

独立启动引导探针，检查 Forge 实际变换后的类，随后在 EULA 门槛停止，没有加载世界：

| 环境 | 验证结果 |
| --- | --- |
| 本工程基础环境，AE2 15.4.11 | `CraftingService` 能转交选中的 ECO CPU；菜单接到成功结果后调用返回主界面 |
| AE2 15.4.10 + GTLCore 1.2.3.2-fix4 + GTCEu 1.4.4 + LDLib 1.0.33.b 及必要依赖 | 同上；另验证真实 ECO 接单逻辑抽取 1 个橡木原木并建立木板任务 |

限制：路由测试使用受控的集群返回值；接单测试调用真实 ECO 逻辑，使用测试库存和简化样板。没有搭建真实多方块、执行机器 tick、操作客户端按钮或验证玩家存档。不能把这些检查视为完整游戏内复现。第三方 JAR 仅将 Minecraft SRG 成员名转换为开发环境名称，没有改写其提交业务。

### 构建与证据

- Java 17 下 `jar` / `reobfJar` 成功。
- 探针及中间映射文件均位于忽略目录 `build/diagnostics/craft-submit/`，不进入 Mod JAR。
- 针对性测试：`build/diagnostics/craft-submit/focused-tests.log`。
- GTL 调用检查：`build/diagnostics/craft-submit/gtl-result.txt`。
- 构建：`build/diagnostics/craft-submit/jar-build.log`。

### 2026-10-28 提交前复核

在当前 20.5.3 工作区通过 `build-local.ps1` 使用 Java 17 重新运行上述 4 个测试类，18 项全部通过（0 失败、0 错误、0 跳过），`jar` / `reobfJar` 成功。当前结果位于 `build/test-results/test/`，产物为 `build/libs/neoecoae-20.5.3.jar`；JAR 已检查包含 `CraftSubmissionPacketFeedback` 的 Java 17 字节码，且未混入提交探针。

本次复核没有重跑前述 Forge/Mixin 探针，也没有进行客户端或存档中的实际提交交互；前述探针结果仍仅对应原排查环境。没有修改原整合包、恢复全局距离绕过或修改 CPU 名字。
