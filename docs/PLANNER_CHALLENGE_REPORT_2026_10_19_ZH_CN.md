# 科技配方规划器挑战报告（2026-10-19）

本文保留修复前的配方、漏解与性能基线。当前源码的修复结果见 [规划器挑战修复报告](PLANNER_CHALLENGE_FIX_REPORT_2026_10_19_ZH_CN.md)，原有 26 个场景已全部符合独立证明的预期。

本次为当前 ECO 规划器编写并运行了 **26 个场景**。其中 24 个有逐次执行验证过的完整合成顺序，2 个是通过穷举关闭全部可达状态确认不可完成的对照。

默认预算实测结果：**18 个可行场景成功、2 个不可行对照正确拒绝、6 个可行场景被报为缺料**。6 个漏解样例归属于两类问题：启动补料与循环回收不能组合、带合成返还工具的不同原料路线不能混合。另有一类能成功但明显偏慢的启动扩培场景。

所有结果都通过正常的 `ECOCraftingPlannerService.createSession(...).plan(...)` 入口取得，开启循环规划。使用当前代码默认的 **120000 ms、50000000 工作量检查点、64 MiB 循环内存估算预算**；没有降低物品数、样板数、状态数或搜索深度上限。前期 5 秒筛查结果不用于判定默认配置下的漏解。

## 1. 配方与验证边界

- Mekanism 的基片、HDPE、电解水与液化/汽化数量来自更新后的 `Mekanism` 仓库，`1.21.x` 提交 `bcd7a8bf594cff9614eb12238fe3776f19da24d9`。对应 `PressurizedReactionRecipeProvider.java`、`SeparatingRecipeProvider.java`、`RotaryRecipeProvider.java`。液化和汽化仍为 1:1，在样板里按 50 mB 一批表达。
- 盐酸蒸馏的 `2000 mB 稀盐酸 -> 1000 mB 盐酸 + 1000 mB 水`，以及硫酸蒸馏的 `3000 -> 2000 + 1000`，来自 `GregTech-Modern` 更新后的 `origin/1.21` 提交 `36a6b38c580e54445e055e3ca05eecf675f2cb96`，文件 `src/main/java/com/gregtechceu/gtceu/data/recipe/serialized/chemistry/DistillationRecipes.java`。
- 酸洗、制酸原料包、催化剂再生、培养液扩培、晶种增殖和电路零件是按科技整合包常见形式构造的测试配方，**不是声称逐项复刻某个 MOD 的原版配方**。使用少量整数、常见 mB 批量、固定返还和有限库存，不使用随机成功率或虚构无限原料。
- 测试使用带不同组件名的真实 `AEItemKey`、`AEFluidKey` 代表材料；气体和液体使用不同完整 Key。没有注册新的全局流体。化学物在这里按其数量映射到流体 Key，因此本报告验证定量规划与 AE2 样板契约，不证明 Mekanism 化学 Key 适配器或机器发配已通过实机测试。
- 样板由公开 `IPatternDetails` 输入/输出及返还接口进入真实编译器，没有直接给循环求解器塞入预先认定安全的编译结果。
- 可行性证明逐次扣除输入、加入输出/返还，检查每次执行前库存足够，最后检查**新产出量**满足订单。该证明不调用规划器、整数方程或生产代码的循环摘要算法。成功的规划结果还独立检查了总预留和物料收支，以及执行计划构造是否报错。

这是一组有针对性的挑战，不是随机抽样；不能把 6/26 当作玩家的真实失败率。耗时是本机 JVM 测试数据，不是 MC 服务器 TPS 或机器实际合成时间。

## 2. 反例一：先补一份启动酸，再循环回收

最小复现 ID：`acid_recovery_500_with_makeup_q3`。只有 **3 个配方、6 种材料，订单为 3 个洗净矿粉**。

| 配方 | 输入 | 输出 |
| --- | --- | --- |
| 酸洗 | 矿粉 ×1、盐酸 500 mB、水 500 mB | 洗净矿粉 ×1、稀盐酸 1000 mB |
| 回收盐酸 | 稀盐酸 2000 mB | 盐酸 1000 mB、水 1000 mB |
| 补充启动酸 | 制酸原料包 ×1 | 盐酸 500 mB |

初始库存：矿粉 ×3、盐酸 500 mB、水 1000 mB、制酸原料包 ×1。

可执行顺序：

1. 制酸一次，库存变成盐酸 1000 mB、水 1000 mB。
2. 酸洗两次，得到洗净矿粉 ×2、稀盐酸 2000 mB。
3. 回收一次，恢复盐酸 1000 mB、水 1000 mB。
4. 再酸洗一次，完成第 3 个产物，剩余盐酸 500 mB、水 500 mB、稀盐酸 1000 mB。

**实际结果：`MISSING_ITEMS`，报告缺稀盐酸 1000 mB。**

扩大到 64 个产物仍失败；把制酸样板放在回收样板前面也仍失败。作为对照，直接持有盐酸 1000 mB 时，同一循环能完成 64 个订单；只有盐酸 500 mB 且没有补酸原料包时，拒绝是正确的。

诊断出现 `CYCLE_EXTERNAL_ROUTE_FORBIDDEN`。源码定位线索：循环的第一个酸洗本来能启动，但后续要凑够蒸馏批量；`startupCandidates` 只收集初始不能执行的样板所缺材料，容易漏掉“给已经能执行的酸洗再补 500 mB 酸”。循环局部回放选择补稀盐酸后，外部供料又因重新进入当前循环而被拒绝。需要考察的是**短启动前缀与长期循环路线的组合**，而不是增加该场景的搜索时长。

相关入口：`BoundedCycleSolver.startupCandidates`、`ComponentPlanner` 的启动恢复、`JointRouteOptimizer` 的前缀补料。

## 3. 反例二：两种原料各做半单，工具每次原样返还

最小复现 ID：`mixed_feed_crafting_remainder_q2`。只有 **2 个配方、4 种物品，订单为 2 个成品**。

| 配方 | 输入 | 产物 | 合成返还 |
| --- | --- | --- | --- |
| 一级原料路线 | 原料 A ×1、工具 T ×1 | 成品 P ×1 | 工具 T ×1 |
| 二级原料路线 | 原料 B ×1、工具 T ×1 | 成品 P ×1 | 工具 T ×1 |

初始库存：A ×1、B ×1、T ×1。

可执行顺序：先用 A 做一次并拿回工具，再用 B 做一次，得到 P ×2，工具仍为 T ×1。

**实际结果：`MISSING_ITEMS`，报告缺 B ×1；结果选择 B 路线做整单两次。**

将库存改成 A ×32、B ×32、T ×1，订单设为 64，也失败。交换样板优先级会改为报告缺 A ×32，并不能完成混合合成。

两个对照均成功：

- 同样的两种原料与订单，移除返还工具。
- 保留工具，但将其作为真实加工输出明确列出，而不是通过合成返还接口提供。

这里的对照用于定位契约处理差异，不能据此要求玩家给真实机器虚报产物。源码中 `JointRouteOptimizer.safe` 排除带 `returnedOutputs` 的候选，而普通路线选择仍然为同一个产物选择一条生产者路线。于是每条路线各自都做不完整单时，缺少把两条含返还工具的路线组合起来的路径。

这是**原料路线组合的漏解**，不是大循环或大数量问题，也不是工具实际损耗造成的缺料。

## 4. 性能案例：必须先扩培，才能同时拥有液体和菌种

ID 前缀：`enzyme_startup_detour_`。共 **3 个配方、4 种材料**。

| 配方 | 输入 | 输出 |
| --- | --- | --- |
| 产酶 | 培养液 1000 mB、菌种 ×1、营养料 ×1 | 酶 ×1、培养液 2000 mB |
| 分离菌种 | 培养液 1000 mB | 菌种 ×1 |
| 扩培 | 菌种 ×1、营养料 ×1 | 培养液 2000 mB |

初始库存：培养液 1000 mB、营养料 `N + 1`，目标为酶 `N`。

可执行顺序：先分离一次、扩培一次，把培养液增至 2000 mB；然后重复“分离一次、产酶一次”共 N 轮。精确次数为：分离 `N + 1` 次、扩培 1 次、产酶 N 次。

`N = 1、1024、65536、131072、1000000` 都成功。较大三档会消耗约 **3863 万次取消检查回调**，实测约十几秒，最后成功的库存状态搜索却只记录 **7 个访问状态、5 个展开状态和 4 个压缩步骤**。诊断先出现 `STATE_EQUATION_BUDGET`，再出现 `WITNESS_FOUND`。

因此这组不是“算不出来”的证据，而是**可行整数次数不能直接启动时，见证构造阶段可能花费过多预算**的性能证据。源码排查应关注 `equationWitness` 中为候选顺序补入假设种子的过程，以及何时切换到实际库存搜索。筛查预算下较早切换反而更快，这也说明不能只用延长预算评价效果。

## 5. 默认预算实测快照

“正确拒绝”是不可行对照，不计为漏解；“成功”包括偏慢的扩培案例。检查数为 `ECOCancellation` 回调计数，便于比较本组用例，不等于运行时机器操作次数。各材料与输入、输出、人工见证完整保存在生成的 JSON 中。

| ID | 订单 | 配方数 | 结果 | 耗时 ms | 检查回调数 |
| --- | ---: | ---: | --- | ---: | ---: |
| `acid_recovery_1000` | 64 | 2 | 成功 | 13.44 | 786 |
| `acid_recovery_500` | 64 | 2 | 正确拒绝 | 60.40 | 1058 |
| `acid_recovery_500_with_makeup` | 64 | 3 | **漏解** | 27.23 | 1841 |
| `acid_recovery_500_with_makeup_makeup_first` | 64 | 3 | **漏解** | 17.54 | 1912 |
| `acid_recovery_500_with_makeup_q3` | 3 | 3 | **漏解** | 17.74 | 1551 |
| `catalyst_regeneration_fresh_start` | 64 | 3 | 成功 | 24.70 | 2951 |
| `catalyst_regeneration_spent_start` | 64 | 3 | 成功 | 34.34 | 2765 |
| `coupled_catalyst_solvent_recovery` | 4096 | 4 | 成功 | 13.81 | 1244 |
| `crystal_nine_to_ten` | 4096 | 2 | 成功 | 7.27 | 301 |
| `enzyme_startup_detour_1` | 1 | 3 | 成功 | 16.52 | 1743 |
| `enzyme_startup_detour_1000000` | 1000000 | 3 | 成功 | 16188.60 | 38625692 |
| `enzyme_startup_detour_1024` | 1024 | 3 | 成功 | 1111.09 | 1517995 |
| `enzyme_startup_detour_131072` | 131072 | 3 | 成功 | 15815.78 | 38625692 |
| `enzyme_startup_detour_65536` | 65536 | 3 | 成功 | 18023.72 | 38625692 |
| `mek_hdpe_bootstrap_biomass_first` | 64 | 6 | 成功 | 47.67 | 931 |
| `mek_hdpe_bootstrap_growth_first` | 64 | 6 | 成功 | 12.64 | 1006 |
| `mek_hdpe_byproduct_oxygen` | 64 | 4 | 成功 | 44.24 | 673 |
| `mek_hdpe_liquid_feed` | 64 | 4 | 成功 | 20.15 | 673 |
| `mek_hdpe_no_seed_control` | 16 | 4 | 正确拒绝 | 41.74 | 344 |
| `mek_substrate_stock_seed` | 1024 | 1 | 成功 | 402.05 | 121 |
| `mixed_feed_crafting_remainder` | 64 | 2 | **漏解** | 25.70 | 218 |
| `mixed_feed_crafting_remainder_q2` | 2 | 2 | **漏解** | 6.76 | 218 |
| `mixed_feed_crafting_remainder_reverse` | 64 | 2 | **漏解** | 7.15 | 218 |
| `mixed_feed_no_tool_control` | 64 | 2 | 成功 | 3.76 | 207 |
| `mixed_feed_processing_return` | 64 | 2 | 成功 | 26.62 | 531 |
| `shared_metal_8_returned_tool` | 1 | 18 | 成功 | 17.79 | 2011 |

## 6. 文件与复现

- `src/test/java/cn/dancingsnow/neoecoae/crafting/planner/solve/TechModPlannerScenarios.java`：26 个配方场景、库存、人工执行顺序、独立逐次回放与不可行对照穷举。
- `src/test/java/cn/dancingsnow/neoecoae/crafting/planner/solve/TechModPlannerScenarioTest.java`：正常测试中运行场景的独立可行性证明。
- `src/test/java/cn/dancingsnow/neoecoae/crafting/planner/solve/TechModPlannerChallengeTest.java`：显式启用的规划挑战，真实漏解保留为失败断言。
- `build/reports/planner-challenges/before-fix/default/*.json`：修复前每个场景的库存、配方、人工见证、预算、实际结果、缺料、执行次数、循环诊断与计时。
- `build/reports/planner-challenges/before-fix/audit-environment.json`：修复前被测提交、未提交规划器源文件指纹和配方来源版本。
- `build/reports/planner-challenges/before-fix/tested-planner-working-tree.patch`：相对基线 HEAD 的生产规划器与配置修改快照。
- `build/reports/planner-challenges/before-fix/junit-default/index.html`：修复前完整挑战的 JUnit 报告，52 项测试、6 项失败；对应 XML 保存在同目录的 `xml/`。
- `build/reports/planner-challenges/junit-planner/index.html`：挑战默认关闭时的常规规划器测试报告。

在仓库根目录运行完整挑战（包含独立证明）：

```powershell
.\gradlew.bat '-Dplanner.challenge.enabled=true' '-Dplanner.challenge.profile=default' test --tests 'cn.dancingsnow.neoecoae.crafting.planner.solve.TechModPlanner*Test' --console=plain
```

修复前基线为 52 项测试中 6 项失败：26 项独立证明全部通过，26 项规划挑战中有上述 6 项漏解。当前源码修复后，原有 52 项测试全部通过；没有把错误缺料结果写成“期望缺料”来使测试通过。

只复现最小的两原料/返还工具案例：

```powershell
.\gradlew.bat '-Dplanner.challenge.enabled=true' '-Dplanner.challenge.case=mixed_feed_crafting_remainder_q2' test --tests 'cn.dancingsnow.neoecoae.crafting.planner.solve.TechModPlannerChallengeTest' --console=plain
```

只复现最小的启动酸案例：

```powershell
.\gradlew.bat '-Dplanner.challenge.enabled=true' '-Dplanner.challenge.case=acid_recovery_500_with_makeup_q3' test --tests 'cn.dancingsnow.neoecoae.crafting.planner.solve.TechModPlannerChallengeTest' --console=plain
```

`planner.challenge.profile=screening` 仅用于快速筛查，采用 5000 ms / 1000000 检查点。只有按 `default` 复测仍失败，才归入本报告的默认配置漏解。挑战默认不参与普通测试，避免长时压力任务和已知漏解打断其他开发；场景独立证明仍参与普通测试。

常规规划器测试已另行执行：

```powershell
.\gradlew.bat test --tests 'cn.dancingsnow.neoecoae.crafting.planner.*' --console=plain
```

结果为 **319 项测试，318 项通过、1 项跳过，0 失败、0 错误**，覆盖 50 个测试类；跳过项为默认关闭的挑战测试，26 项独立场景证明全部执行通过。这次只验证规划器测试范围，没有将该结果描述为全仓库测试或游戏内验收。

被测主仓库 HEAD：`00db885822b4f5fba3671542e1becabfc283b04e`。被测内容还包含上一轮未提交的循环快速求解、种子预留与可行解优先修改，因此仅检出该提交不能复现全部结果；应以审计指纹或当前工作区的对应源文件为准。本轮新增的是挑战用例、复现参数和报告。

上一轮验证记录中的 17 个源文件与文档指纹已逐一比对，全部保持一致。
