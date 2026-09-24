---
navigation:
  title: 集成工作站
  icon: neoecoae:integrated_working_station
  position: 20
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:integrated_working_station
  - neoecoae:crystal_ingot
  - neoecoae:energized_superconductive_ingot
  - neoecoae:superconducting_processor
---

# 集成工作站

<BlockImage id="neoecoae:integrated_working_station" scale="2"></BlockImage>

有了[盈能水晶](energized_crystal.md)，下一步就是制作 <ItemLink id="neoecoae:integrated_working_station" />。ECO 的多数高级组件都要经过这台机器加工，包括三种多方块系统的控制器。

<RecipeFor id="neoecoae:integrated_working_station" />

## 让工作站开始加工

放下工作站后，接入 ME 网络获取电力，或使用外部电源供能。加工消耗 FE；批量制作前，可以接上致密能量元件或高容量电源，保证供能连续。

按照配方把物品放进输入槽，需要流体的配方还要向输入储罐注入流体。工作站有 9 个物品输入槽，支持最多 9 种不同的输入物品；流体输入、输出储罐各可容纳 16,000 mB。流体可以通过管道或流体元件送入。

材料和能量满足要求后，工作站会自动处理。成品可以从输出槽取走，也可以启用自动导出，将产出推送到相邻容器。装入 AE2 速度升级卡可以缩短处理时间。

## 从晶体锭做到多方块组件

<ItemLink id="neoecoae:crystal_ingot" /> 需要赛特斯石英粉、福鲁伊克斯粉、盈能水晶粉和熔岩流体。这个配方也可以用来熟悉工作站的物品与流体输入。

<RecipeFor id="neoecoae:crystal_ingot" />

更高级的组件会用到 <ItemLink id="neoecoae:energized_superconductive_ingot" />：

<RecipeFor id="neoecoae:energized_superconductive_ingot" />

16MB、64MB、256MB 的 ECO 存储元件组件，以及计算系统的计算元件，也都在这里合成。准备好材料后，继续查看要搭建的[存储系统](storage_system.md)、[计算系统](computation_system.md)或[合成系统](crafting_system.md)，按对应等级制作控制器和结构部件。
