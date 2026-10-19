---
navigation:
  title: 大型集成工作站
  icon: neoecoae:integrated_working_station
  position: 21
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:large_integrated_working_station_casing
  - neoecoae:large_integrated_working_station_input_hatch
  - neoecoae:large_integrated_working_station_output_hatch
  - neoecoae:large_integrated_working_station_interface
---

# 大型集成工作站

<GameScene interactive={true} fullWidth={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" p:formed="true" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" p:formed="true" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" p:formed="true" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
</GameScene>

## 并行与能耗

大型集成工作站常规单批上限为 **256**。默认开启温度机制，开启超频并维持对应温度可以使用更高并行。也可以通过服务器配置关闭温度机制，改用下文的冷却液模式。

| 档位 | 持续工作温度 | 单批并行上限 | 每份配方能耗 |
| --- | ---: | ---: | ---: |
| 常规 | 无要求 | 256 | 1× |
| 2 | ≥20% | 16,384 | 1× |
| 6 | ≥50% | 65,536 | 1× |
| 9 | ≥80% | 262,144 | 1× |

所有档位均按实际基础配方次数支付能量，**1× 不代表整批只支付一份配方的能量**。例如同时加工 100 次配方，就支付 100 份基础耗能；电预热的用电另行计算。

## 配置：关闭温度机制

服务器配置 `largeWorkstationTemperatureEnabled` 默认为 `true`。设为 `false` 并重新进入世界或重启服务器后，机器恢复按冷却液等级提供并行的运行方式，无需预热或维持温度，也不触发过热及 15 秒恢复限制。

| 冷却液 | 单批并行上限 | 每份配方能耗 |
| --- | ---: | ---: |
| 无冷却液或未开启超频、主动冷却 | 256 | 1× |
| 水（等级 2） | 16,384 | 1× |
| 钠（等级 6） | 65,536 | 1× |
| 凛冰溶液（等级 9） | 262,144 | 1× |

冷却液模式需要同时开启**超频**和**主动冷却**，并通入对应等级的冷却液。液体等级直接决定新批次上限，手动最大档位及温度门槛不参与判断。超频批次每次实际推进加工消耗 **100 mB**，空闲、缺电及暂停时不消耗；冷却液不足、等级低于该批次要求或副产物输出受阻时暂停，材料和进度保留。高级冷却液可维持较低档位的已有批次。

关闭温度机制会停止预热和温度结算，界面显示冷却液模式，预热和温度选档操作不生效。已有温度与过热锁定保留在存档中，关闭期间不影响加工；重新开启后恢复检查这些状态，原先过热的机器仍需完成凛冰恢复。模式切换不会丢弃已有批次。

**以下预热、升温、降温与过热规则只在温度机制开启时适用。**

## 启动与日常控温

1. 在控制器侧边栏选择最大超频档位，按 **2 / 6 / 9** 循环切换；悬停按钮可以查看当前选择。
2. 点击预热按钮。预热会开启超频、关闭主动冷却，满功率消耗 **1,000 AE/t**，每秒升温 **2 个百分点**；供电不足时按实际支付比例升温。
3. 等温度达到所选档位门槛。预热会自动停止，此时再发送高并行加工任务。
4. 观察温度和加工负载，按需要通入合适的冷却液并开启主动冷却。冷却过强也会失去高并行所需的温度。
5. 若跌破批次门槛，减少或关闭冷却，重新手动预热。系统不会自动补热抵消冷却。

新批次使用温度已经达标、且不超过所选最大档位的最高档位。加工过程中会持续检查该批次的温度门槛，**不是接单时达标一次就能一直运行**。跌破门槛或关闭超频会暂停该档批次，输入与进度保留；条件恢复后继续。切换最大档位只影响后续接单，不改写已接收批次。

## 升温与冷却液选择

温度范围为 **0%～100%**。超频加工的满载产热为每秒 `0.2 + 0.8 × (温度百分比 / 100)²` 个百分点，再按实际推进的基础配方工作量折算，倍率样板展开计入。**温度越高，产热越快**；常规加工及暂停的批次不产加工热。

主动冷却独立于超频开关，以 **100 mB/服务端 tick（2 B/s）** 消耗液体：

| 冷却液 | 每秒移除温度 | 冷却副产物 |
| --- | ---: | --- |
| 水 | 0.1 个百分点 | Mekanism 加载时产生蒸汽，否则无 |
| 钠 | 0.3 个百分点 | 过热钠（Mekanism） |
| 凛冰溶液 | 0.6 个百分点 | 无 |

高级冷却液不保证温度只升不降。例如 20% 温度满载时产热为 **+0.232 个百分点/s**，通入凛冰溶液后净变化约为 **−0.368 个百分点/s**，温度会跌破 2 档门槛。低负载下产热更少，也更容易被冷却压低温度。

控温目标是留在所需档位的工作区间，避免冲到 100%；系统不提供自动恒温。主动冷却开启时，即使加工暂停或机器空闲，只要温度大于 0 且液体足够，仍会继续消耗并降温；到 0 后停止消耗。缺液或副产物输出堵塞时不提供冷却，也不扣除无法处理的冷却液。

输入、输出仓各容纳 **1,024,000 mB**。使用有副产物的冷却液时，需要保持输出仓畅通。

## 100% 过热与强制恢复

达到 **100%** 后，机器锁定加工及新接单，关闭预热，保留已经接收的输入与进度。按以下步骤恢复：

1. 将输入仓的冷却液换为**凛冰溶液**，准备至少 **30,000 mB（30 B）**，或保证持续供液。
2. 开启主动冷却，保持液体供应及冷却副产物输出畅通。
3. 等待界面显示的凛冰冷却进度完成 **15 秒（300 个服务端 tick）**。强制恢复过程会将温度降到 **0%**，整个过程完成前仍处于锁定状态。
4. 解除锁定后重新选择所需档位并手动预热，再恢复高并行加工。

恢复要求**连续成功消耗凛冰溶液**。关闭冷却、断液、输出堵塞、结构解体或重载会清除计时并恢复到 100%，需要重新完成 15 秒。水、钠、其他自定义高等级冷却液、开关超频以及单纯等待，都不能解除锁定。

## 常见状态排查

| 现象 | 处理方法 |
| --- | --- |
| 开启超频后仍只有 256 并行 | 检查所选档位及当前温度，先完成预热 |
| 温度不足、批次暂停 | 减少或关闭冷却，再手动预热；材料和进度仍保留 |
| 使用高级冷却液后温度下降 | 降温能力超过实际产热，换弱一些的冷却液或暂时关闭冷却 |
| 开启冷却后温度仍上升 | 产热超过散热时，冷却只会减缓升温；同时检查冷却开关、液量、配方及输出仓是否畅通 |
| 过热恢复计时不增长或归零 | 确认真正通入凛冰溶液，保持每 tick 有效冷却，避免中断或重载 |

## 扩展机器配方

大型集成工作站成型后，还能加工已安装模组的以下配方：

- AE2 Lightning Tech Reborn：过载处理工厂、闪电装配室、闪电模拟室的全部配方。
- AE2 Crystal Science：电路蚀刻、晶能聚合配方。
- Applied Generators：起源装配器配方。
- AdvancedAE：反应仓配方，包括流体产物。
- ExtendedAE Plus：超级水晶装配器的全部配方，包括从 ExtendedAE 水晶装配器继承的配方。

将完整的物品、流体输入与输出编码为加工样板，放入大型工作站的通讯接口。JEI、EMI 会在独立的“大型集成工作站”分类下显示这些配方。单方块集成工作站不能加工这些扩展配方。配方跟随当前数据包，支持重载后的新增、修改与删除。

AE2LT 配方保留原有闪电档位与数量。控制器会从连接的 ME 网络自动提取所需闪电，无需将闪电加入加工样板。对应档位的闪电不足时，批次会暂停等待补充。闪电消耗仅按实际合成次数放大，不乘超频耗电倍率；高压闪电不能替代超高压闪电。提取进度会保存，重载不会重复扣除；取消尚未完成且由工作站追踪的合成任务时，会归还已提取资源。

加工耗能统一由工作站以 AE 支付。AE2LT 的 FE 耗能按 AE2 的 FE/AE 比例换算，其余联动沿用原配方 AE 耗能；超级水晶装配器每次配方为 2,000 AE（200 进度 × 10 AE）。现有超频和冷却规则同样适用于这些配方。
