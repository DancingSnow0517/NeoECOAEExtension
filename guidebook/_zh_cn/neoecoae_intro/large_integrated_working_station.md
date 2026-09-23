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
