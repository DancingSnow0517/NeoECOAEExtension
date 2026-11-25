# 5 亿 UHV 场发生器订单的循环供料排查

## 复现条件

- 2026 年 11 月 24 日在 `GregTech Leisure-猪咪增强版 04.3` 单人存档中复现。运行环境为 Minecraft `1.20.1`、AE2 Forge `15.4.10`、NeoECOAE `20.5.10`。源码对照使用标签 `20.5.10`（`f7dd0efeb`）。
- 在已有样板和库存的 AE 网络中，反复请求合成 `500000000` 个 `gtceu:uhv_field_generator`。确认页显示「已算字节：500 MB」「存在循环」「合成树复杂请检查样板」和「路线搜索未完成，已保留已算出的材料」，「开始」灰显。
- 同一数量的请求多次返回 `CYCLE_UNRESOLVED`，也有一次在另一条路线下返回 `MISSING_ITEMS`。复现依赖该存档中的样板、库存和路线选择。

## 运行证据

| 时间（本机） | 观测结果 |
| --- | --- |
| `01:30:48.058` | 主循环组件 `844` 求解返回 `SUCCESS`，候选执行次数为 `19531250`，`stateBudgetExhausted=false`。 |
| `01:30:48.062` | 验证外部供料时得到 `PARTIAL_UNSUPPORTED`，诊断为 `CANDIDATE_DEFERRED_CYCLE`，表示选中的替代生产路线需要循环组件规划。 |
| `01:30:48.064` | 组件 `844` 最终为 `UNSUPPORTED / BLOCKED`，`externalStatus=UNSUPPORTED`，`missingLeaves={}`。 |
| `01:30:48.103` | 确认菜单收到 `CYCLE_UNRESOLVED`，`simulation=true`，`bytes=500000008`，`noCPU=false`，禁用原因为 `SIMULATION_REQUIRES_FORCE`。 |
| `01:27:09.558` 至 `01:27:09.764` | 另一条路线下组件 `841` 报 `INSUFFICIENT_EXTERNAL_INPUT / MISSING`，确认菜单收到 `MISSING_ITEMS`。 |

对游戏 JVM `43748` 抓取了 12 份线程快照和 60 秒 JFR。JFR 中有 789 个 `ECO Planning Worker` 执行样本，主要调用链包括 `SeedFloors.compute` → `CycleStateEquation` 和 `CycleCandidateSearch.supply` → `ExternalDemandPlanner.solveExactDemands`。内置服务端持续接到后续请求，故障订单返回了诊断结果。

## 代码链路

- `AcyclicCraftingSolver.cyclicRouteOutcome` 在发现选中的路线需要循环规划时，返回 `PARTIAL_UNSUPPORTED`、`CANDIDATE_DEFERRED_CYCLE` 及 `cyclicRouteChoices`。
- 顶层 `ComponentPlanner` 消费 `cyclicRouteChoices`，按具体选择重建路线并重新规划。`ExternalDemandPlanner.solveDeficits` 调用同一个无环求解器，收到这类返回值时落入 `UNSUPPORTED` 分支。日志命中的是这条外部供料路径。
- `ECOCraftConfirmScreen` 在 `CYCLE_UNRESOLVED` 且仍有材料明细时显示当前提示。该订单以模拟计划进入确认菜单。

## GTL 缺料对照

用户实测，GTL 原规划会直接给出这笔 5 亿订单的缺料，按报告补齐后 ECO 也能算出订单。另有同量成功日志可供对照。`01:51:24.426` 的主循环组件 `844` 达到 `SOLVED / ORDERED_EXECUTION`，确认页在 `01:51:24.509` 达到 `SUCCESS / READY`。`01:51:55.597` 的另一轮请求仍出现 `CANDIDATE_DEFERRED_CYCLE` 和空 `missingLeaves`。两次请求的完整库存快照未随日志保存。

- `20.5.10` 的 `AcyclicCraftingSolver.solveGoals` 在遇到缺料并切换路线时，会暂存 `missingRoute`。替代路线如果在环检查处提前返回，`cyclicRouteOutcome` 会创建新的 `SolveState`，绕过重试结束时恢复 `missingRoute` 的逻辑。外部供料层接到这个结果会返回 `UNSUPPORTED` 和空的缺料明细。此路径可以解释为何先前路线的缺料未能进入报告，本单是否实际保存过 `missingRoute` 仍待查证。
- `ComponentPlanner` 已能保留首条完整路线生成的缺料报告，再继续尝试替代路线。外部供料层若先返回空缺料，上层保住的预览也可能只剩成品缺口和已算出的材料。界面文案由 `CYCLE_UNRESOLVED` 触发，最终是否耗尽预算仍需对应的阶段诊断。
- 方案评估时区分一条明确路线的缺料清单与整个网络的可行性。前者可用来指导补料，替代路线仍可继续搜索。对照验证使用同一份库存和样板，记录 GTL 缺料、ECO 首条路线缺料、替代路线退出原因，以及补料后 ECO 是否返回 `SUCCESS / READY`。

## 待确认

- 具体是哪一组外部物料和替代样板形成了第二个循环。日志打印了外部需求和诊断，具体的 `cyclicRouteChoices` 待采集。
- 最终路线搜索在哪个条件下退出。当前证据定位到外部供料分支的失败，最终退出所对应的预算项、路线限制或候选状态仍待确认。
- 根据实际库存和样板，是否存在可执行路线，或能否给出完整的缺料证明。

## 本机证据位置

- 游戏日志 `E:\Minecraft\1.20.0\.minecraft\versions\GregTech Leisure-猪咪增强版 04.3\logs\debug.log`，主要复现从第 85507 行开始。`MISSING_ITEMS` 对照从第 84763 行开始，同量 `SUCCESS / READY` 见第 94180 行。
- 线程与 JFR 文件位于 `C:\Users\hnhjy\AppData\Local\Temp\eco-craft-43748-20261124-012657`，主录制为 `craft.jfr`，典型供料线程栈见 `threads-02-012705025.txt`。
