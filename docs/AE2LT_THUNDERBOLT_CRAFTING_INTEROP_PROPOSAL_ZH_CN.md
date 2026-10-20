# ECO / AE2LT / Thunderbolt 互通边界修复与大数策略

更新日期：2026-10-20。状态：生产代码与全量单元测试验证完成；游戏内验收尚未进行。

本轮 ECO 正式发布版本为 `21.2.9`，不带 beta 后缀。以下保留最初配套验证制品的版本号和 SHA-256，作为历史测试证据。

最初配套验证版本：ECO `21.2.9-beta`、AE2LT `2.1.2-beta4`、Thunderbolt `2.0.3-beta3`。这是对同一轮互通修复的版本元数据调整；AE2LT 的 Thunderbolt 最低依赖同步为 `2.0.3-beta3`。

发布兼容补充（2026-10-20）：上面的安装版本是本地配套制品，不等于 CI 的默认依赖。当前默认构建使用已发布 AE2LT `2.1.2-beta3`（Modrinth `AosdXlfS`）及 Thunderbolt `2.0.3-beta2`（`oyVnlG5A`）。发布版缺少新增的过载外部认领/存档门面和精确流式大数接口，不能直接引用开发版类或方法。

## 1. 既有互通与审查基线

四条基础互通原本就有生产调用链。专用批量接口没有被使用，不能据此判定普通互通不存在。

| 方向 | 现有生产路径 | 本轮处理 |
| --- | --- | --- |
| 天枢 → ECO F / FX | Thunderbolt 适配器 → `ECOFastPathFacade.prepareAllocated`；不支持批量时普通单份回退 | 全程保留任务 UUID；为 FX 增加显式精确大数交接 |
| ECO → 天枢合成器 | 矩阵端口普通 `pushPattern` → 矩阵核心原有 5 tick 缓冲返回 | 保留普通路径并回归接受、拒收、材料退款和等待账；专用矩阵批量入口仍未接入 |
| 天枢 → ECO 大集成 | 工作站批量桥 → `eco$pushPatternBatch`，原本已传递 UUID | 修复任务终态通知，并验证取消后的队列解除和输入/产物回网 |
| ECO → 过载样板供应器 | ECO 加工发配器 → 受控直传；失败时普通单份回退 | 保留 ECO 智能翻倍，补齐 ID_ONLY 产物认领、存档与结束清理 |

开始修复前先 fetch/pull 核对的源码：

| 仓库 | 本地 main | origin/main | origin/alpha | 源码版本 |
| --- | --- | --- | --- | --- |
| NeoECOAEExtension-1.21.1 | `da1500759` | `da1500759` | 本轮不作为基线 | `21.2.8` |
| AE2-Lightning-Tech-Reborn | `b9f45a99` | `7c76557f` | `7417bd90` | `2.1.2-beta3` |
| Thunderbolt-Core-Reborn | `da92416` | `bfb53ac` | `f77f0cb` | `2.0.3-beta2` |

AE2LT、Thunderbolt 本地 main 已分别合并 alpha，领先 origin/main 2、7 个提交。本次修复建立在这些本地合并之上。发布状态以提交及远端分支为准，不能仅凭未改变的版本号或 JAR 文件名判断代码内容。

## 2. 已修复的三项边界问题

### 2.1 天枢取消不能可靠通知 ECO 工作单元

时间轮实际持有私有 `TimeWheelJob.link`，旧桥读取不存在的 `job.id`，导致通知被静默跳过。现在从 `link.getCraftingID()` 获取真实 UUID，在 CPU 清除任务前调用 `ECOCraftingJobLifecycle.cancelAndRecover`：先持久记录终态，再唤醒同一服务器已加载的 F / FX 工作单元。

大集成在下一次 tick 读取同一持久终态。未完成的批次归还实际保管的输入，已完成的批次交付实际产物；成功交付后移除队列项。卸载的机器再次加载时也能查询持久终态，不靠猜测 CPU 是否在线来取消任务。

天枢大数后端另通过公开 `ECOFastPathFacade.cancelJob` 发送相同通知，避免只修复时间轮而遗漏 `BigCraftingJob`。

源码：

- [ECO 时间轮取消桥](../src/main/java/cn/dancingsnow/neoecoae/compat/ae2lt/ECOAe2LtCancellationBridge.java)
- [持久终态与即时回收](../src/main/java/cn/dancingsnow/neoecoae/crafting/execution/worker/ECOCraftingJobLifecycle.java)
- [天枢大数后端](../../AE2-Lightning-Tech-Reborn/src/main/java/com/moakiee/ae2lt/crafting/big/BigCraftingService.java)

### 2.2 普通回退丢失任务 UUID

新增 `ECOJobAwareCraftingProvider` 和 `ECOFastPathFacade.pushPatternForJob`。Thunderbolt 的 FastPath 不支持、准备失败或确认拒收后，普通回退仍携带原订单 UUID。批量端点本 tick 降到容量 1 后，天枢后续普通派发也走这个入口；时间轮和大数后端都已接入。

普通调用只移交当前一份材料，不重复扣料、退款、放大材料或另开预算。没有任务上下文能力的供应器保留直接 `pushPattern` 调用；是否支持上下文按供应器类型缓存，普通外部供应器不增加每份反射调用。

天枢公开回料 API 同时识别时间轮 CPU 与大数 CPU，并校验仍属于同一订单。旧任务的产物不能记到后来替换的新任务上。

源码：

- [公开普通上下文接口](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOJobAwareCraftingProvider.java)
- [Thunderbolt 普通回退](../../Thunderbolt-Core-Reborn/src/main/java/com/moakiee/thunderbolt/compat/neoeco/NeoEcoFastPathBatchAdapter.java)
- [天枢公开合成与回料 API](../../AE2-Lightning-Tech-Reborn/src/main/java/com/moakiee/ae2lt/api/crafting/Ae2LtCraftingIntegration.java)

### 2.3 ECO 过载登记、认领、存档和清理没有闭环

AE2LT 旧 ECO Mixin 目标是兼容壳，真实执行已迁到 `crafting.execution.ECOCraftingCPULogic`。本轮把接入放到真实执行路径，通过 AE2LT 的公开 API 完成：

1. 派发前校验严格等待与同 ID 模糊产物是否冲突；冲突时不移交材料。
2. 仅为实际接受的份数登记过载预期产物，普通回退也登记一次。
3. 收到精确模板或同物品 ID 的不同组件产物时，同步扣减过载账和 ECO 的预期键等待量，实际键按原样保管/交付。
4. SIMULATE 不改账。部分认领保留未接受尾量；先退役过载认领，再调用实际交付，防止回调重入重复认领。
5. 使用现有任务附件系统保存 `ae2lt:overload_outputs`，恢复同一任务的剩余账；取消、完成及替换 CPU 检查点时清理。

请求端部分接受或拒收时，剩余实际产物留在 CPU 并重试，不把它当作已交付的最终产物。新任务不会复用旧任务的过载登记。

过载供应器仍由 **ECO 智能翻倍** 决定份数、增长、退避和预算，没有启用 AE2LT 自适应倍率，也没有把材料乘两次。当前 ID_ONLY 过载协议仍按 long 批次记账，不因装有闪电就把所有过载接口当作大数接口。

源码：

- [ECO 过载公开 API 桥及任务附件](../src/main/java/cn/dancingsnow/neoecoae/compat/thunderbolt/ECOOverloadCpuAccountingBridge.java)
- [ECO 实际输出认领与返回](../src/main/java/cn/dancingsnow/neoecoae/crafting/execution/ECOCraftingOutputDelivery.java)
- [AE2LT 外部 CPU 过载协议](../../AE2-Lightning-Tech-Reborn/src/main/java/com/moakiee/ae2lt/api/crafting/Ae2LtCraftingIntegration.java)

## 3. 数量与性能边界

遵循用户确认的规则：**优先 long，数量确有需要且目标具备明确精确协议时才用 BigInteger。** 大数能力按具体接口启用，不按 Mod 名称整体放行。

| 边界 | 常用路径 | 超过 long 安全范围时 |
| --- | --- | --- |
| ECO 内部任务与 FX | long 发配；精确任务保留完整总量 | 使用已有精确任务、库存和 FX 线程账本，不截断任务或物料总量 |
| ECO 内部 FX → CPU | 普通 long 定向回料 | 同任务、精确等待账且预期键明确时精确交接；普通和过载特殊匹配保留原路径 |
| 天枢大数 CPU ↔ ECO FX | 次数及各键总量都安全时继续 long 批量 | 通过新增 `prepareExactAllocated` / 精确回料 API 移交 BigInteger 批次 |
| ECO CPU → ECO 存储 | 能放进 long 的数量调用普通插入 | 通过 `ECOBigIntegerStorage` 返回完整数量，只扣减实际接受量 |
| 普通外部容器或供应器 | long | 按次数、每种输入/输出/返还物合计的上界拆批；余量留在精确账本 |
| 普通 AE2 存储 | long | 每轮最多交付一个安全 long 窗口，保留余量继续返回 |

只检查“份数不超过 long”还不够。例如 9 个同类输入乘批次数时，单键材料合计也必须能由该接口表示。long 分片继续执行这项校验；只有明确支持精确材料交接的适配才允许越过它。

天枢到 FX 的新精确入口复用 FX 原有虚拟大数线程：一个批次占一个线程槽，CPU 保留精确材料与等待账的所有权。份数、材料或输出超出 long 时才尝试该入口；材料、能量或目标容量缩小批次后，未接受量继续保留。确认拒收则回滚；提交结果不确定时停止该任务，不自动退款或重试。

该精确入口当前要求 FX 完整虚拟模式及经过 FastPath 验证的 NORMAL 配方，无返还容器、耐久工具或共享种子状态。其他配方继续走保留 UUID 的普通路径，不能宣称已扩展为任意状态配方的大数合成。特殊机制仍由各自机器控制器执行。

CPU 向 ECO 存储返回时也优先 long：超过 long 才使用精确存储能力。存储部分接受时保留余量，余量回到 long 范围后自动恢复普通调用；回调重入不会重复返回同一笔库存。

源码：

- [ECO FastPath 普通、long 及精确入口](../src/main/java/cn/dancingsnow/neoecoae/api/me/ECOFastPathFacade.java)
- [天枢 FX 精确发配](../../AE2-Lightning-Tech-Reborn/src/main/java/com/moakiee/ae2lt/crafting/big/NeoEcoBigIntegerCompat.java)
- [FX 精确保管账本](../src/main/java/cn/dancingsnow/neoecoae/crafting/execution/worker/ECOExactVirtualLedger.java)
- [ECO CPU 返回存储](../src/main/java/cn/dancingsnow/neoecoae/crafting/execution/ECOCraftingCPULogic.java)
- [优先 long 的精确存储入口](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOBigIntegerStorage.java)

## 4. 验证记录

三仓库使用相互配对的当前本地 JAR 验证。临时依赖覆盖位于各仓库被 Git 忽略的 `build/interop-review-20261020.init.gradle`：Thunderbolt 使用当前 ECO JAR；AE2LT 使用当前 Thunderbolt JAR；ECO 另用 `-Pae2lt_local_jar` 指向当前 AE2LT JAR。这不是对远端同名旧制品的验证，也没有发布新的 Mod 版本。

完整单元测试结果（版本调整前的修复制品，合计 4382 项通过、8 项跳过，0 失败、0 错误；本次版本调整没有改动运行逻辑，不重复运行完整机制测试）：

| 仓库 | 总数 | 通过 | 跳过 | 失败 / 错误 |
| --- | --- | --- | --- | --- |
| ECO | 1506 | 1504 | 2 | 0 / 0 |
| AE2LT | 1522 | 1518 | 4 | 0 / 0 |
| Thunderbolt | 1362 | 1360 | 2 | 0 / 0 |

Thunderbolt 在全量测试后另验证了兼容旧可选 API 的测试写法，`compat.neoeco.*` 17 项全部通过，不与上表重复相加。三个项目的编译和 JAR 打包通过；全量测试后另执行 `build -x test` 检查打包与其余构建任务，没有重复计数测试，也没有把这些结果记作游戏内验收。

版本调整前的配对验证制品 SHA-256（保留原测试证据；版本调整后的历史制品另见下表）：

| 制品 | SHA-256 |
| --- | --- |
| `neoecoae-21.2.8.jar` | `32D00FC144A78BCB01D5F9A0D0536B97EDFF974E9EE69BB825E5AC3F5E3C20C2` |
| `ae2lt-2.1.2-beta3.jar` | `6FE9EB54D5156027C88726B853AFF4AF569BE7C45C3CD588FE597E9929C42702` |
| `thunderbolt-2.0.3-beta2.jar` | `1D45EB138DB8CD0E1DA95C4B341323AFF169CD6735DFD0C995680EDA31DEC4B0` |

互通修复时版本调整后的制品（历史测试记录；重新 `build -x test`，已核对 JAR 内嵌版本及 AE2LT 的 Thunderbolt 依赖约束）：

| 制品 | SHA-256 |
| --- | --- |
| `neoecoae-21.2.9-beta.jar` | `A7D1DA7D35C5BD5EC862555A6B72E5714905B5FBE2F4A9DB6733066CCD88E27E` |
| `ae2lt-2.1.2-beta4.jar` | `8824FBAD5D1E61D1254A721F33FB20619A0285DBBEA2D6EADEFEF076FEF5F4BD` |
| `thunderbolt-2.0.3-beta3.jar` | `9F834FE60B0A5401CFD6A7748A90E974C865CBDE2B93F581D63334080FBDC3D8` |

新增回归包括：

- 实际私有 `TimeWheelJob.link` 的正向取消通知；通知先于 CPU 清理，持久终态先于工作单元回收；同一终态解除大集成未完成/已完成批次队列。
- FastPath 普通回退和后续容量 1 派发保留 UUID；拒收保留借用输入；异常不触发第二次提交；天枢实际普通派发入口使用当前任务 UUID。
- ID_ONLY 不同组件与精确模板均同步退役两本账；模拟、部分认领、拒收、错任务、冲突串行化、请求端部分交付、重入、附件保存/恢复、完成与取消清理。
- `10^28 + 17` 次合成：天枢真实精确托管账 → ECO 公开准备入口 → 实际 FX 线程 → 实际定向回料桥 → 天枢精确托管账。检查输入扣减、等待量、单线程槽和整笔输出，无 long 窗口循环。
- 内部 FX 精确回料到 ECO CPU，再由 CPU 整笔返回支持大数的存储；普通接口、能量限制后的小批次和普通存储保留 long；部分容量、失败回滚、不确定接受与库存返回重入保持材料守恒。

测试中的世界发现、结构、网络、配方凭据等有替身。托管账、材料事务、FX 线程、公共桥、输出认领和附件读写执行生产代码。自动化通过不等于真实客户端接线、可选 Mod 缺席启动、卸载重启或吞吐验收已完成。

## 5. 后续公共协议整理

本轮修复现有功能和已确认的边界缺陷，没有整体删除双方跨项目 Mixin。时间轮取消暂时仍使用可选私有字段桥；完整去除此依赖需要天枢提供稳定生命周期回调。

后续统一协议应复用 ECO 的生命周期、任务附件、产物认领 API，以及天枢的派发事务和物理 tick 预算。供应器能力、任务身份、实际接受量、精确数量、异步保管、取消及存档恢复必须一起迁移。专用矩阵批量、状态配方和旧 Mixin 删除应各自形成可独立验收的改动，不能通过重新启用旧倍率接口代替。

## 6. 已发布版本兼容与 CI 基线

- 新版公开门面完整时，继续使用原有公开 API 桥；门面不完整但安装了已发布闪电时，使用隔离的 `ECOAe2LtReleasedAccounting`，只调用发布 JAR 已具备的公开过载状态管理、冲突检查和认领提交契约。
- 发布版同样支持精确模板与 ID_ONLY 变体的同步退账、模拟、部分认领、重入防护、任务附件存档、恢复和清理。认领提交仍先于实际交付，结果不确定时不会退款或再次移交材料。
- 已发布时间轮在普通 `pushPattern` 回退中不传任务 UUID。可选 Mixin 只包装这个旧调用，通过真实活动 `link.getCraftingID()` 补上 ECO 任务上下文；非 ECO 供应器保留原调用。开发版改用原生任务入口后，旧调用不再匹配，`require = 0` 保持兼容。
- 精确流式大数能力按实际 API 探测，不能由版本字符串或 Mod 名称推断。缺少接口时不伪造精确认领、不截断数量，继续保留现有普通/long 路径和未交付库存。
- 开发版专属的大数互通测试通过反射发现类和方法，缺少能力时由 JUnit 明确跳过；普通 long 回料、发布版过载和时间轮任务上下文测试继续执行。默认 `gradlew build` 不需要本地 JAR、不使用临时测试排除脚本。

本轮按 CI 相同命令验证默认已发布依赖；上面的历史配套 JAR 全量记录与本轮发布兼容验证分开保留。用户已明确跳过游戏内验收。
