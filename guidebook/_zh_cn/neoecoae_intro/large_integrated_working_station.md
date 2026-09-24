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

大型集成工作站以大型多方块结构批量加工高级配方。关闭超频时，维持原有的单批 1,024 并行上限和配方设定耗电。

控制器提供“超频”和“主动冷却”开关。接单时必须同时开启两个开关，批次才会使用超频：

| 冷却液 | 单批并行上限 | 每次配方耗电 |
| --- | ---: | ---: |
| 水 | 16,384 | ×8 |
| 钠 | 65,536 | ×32 |
| 凛冰溶液 | 262,144 | ×64 |

超频批次每个实际推进加工的 tick 消耗 100 mB 冷却液，与批次大小无关。输入仓和输出仓的容量均为 1,024,000 mB。安装 Mekanism 时，水会产生蒸汽、钠会产生过热钠；未安装 Mekanism 时，水不产生副产物。凛冰溶液没有副产物。

超频或主动冷却关闭、冷却液不足或档位低于接单档位、电力不足或副产物输出仓已满时，超频批次会暂停；条件恢复后继续加工。空闲和暂停的 tick 不消耗冷却液。

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
