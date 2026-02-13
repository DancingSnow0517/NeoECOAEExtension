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

<ItemGrid>
  <ItemIcon id="neoecoae:integrated_working_station" />
</ItemGrid>

<ItemLink id="neoecoae:integrated_working_station" /> 是一台高级合成机器，将多种AE2设备的功能整合到一个强大的工作站中。它是制作本模组大多数高级组件的必需设备。

## 功能特性

- **9个输入槽位** - 支持最多9种不同的输入物品
- **流体输入/输出** - 支持流体配方和输出（每个储罐容量16,000 mB）
- **能量驱动** - 消耗FE（锻造能量）进行合成
- **ME网络集成** - 连接到AE2网络获取电力供应
- **自动导出** - 可自动导出成品到相邻容器
- **升级支持** - 支持AE2速度升级卡加速合成

## 合成配方

集成工作站由多种AE2机器的组件合成：

<RecipeFor id="neoecoae:integrated_working_station" />

## 使用方法

1. **放置工作站** 并连接到ME网络
2. **输入材料** - 在9个输入槽位放入物品，和/或向流体输入储罐注入流体
3. **提供能量** - 确保有足够的FE供应（来自ME网络或外部电源）
4. **等待处理** - 当满足条件时，工作站将自动开始处理
5. **收集产出** - 从输出槽取出成品，或启用自动导出

## 关键配方

集成工作站用于合成许多重要物品：

### 晶体锭

<ItemGrid>
  <ItemIcon id="neoecoae:crystal_ingot" />
</ItemGrid>

<RecipeFor id="neoecoae:crystal_ingot" />

<ItemLink id="neoecoae:crystal_ingot" /> 需要赛特斯石英粉、福鲁伊克斯粉、盈能水晶粉和熔岩流体。

### 盈能超导锭

<ItemGrid>
  <ItemIcon id="neoecoae:energized_superconductive_ingot" />
</ItemGrid>

<RecipeFor id="neoecoae:energized_superconductive_ingot" />

<ItemLink id="neoecoae:energized_superconductive_ingot" /> 是用于高级组件的顶级材料。

### ECO存储元件

所有ECO存储元件组件（16MB、64MB、256MB）都使用集成工作站合成。

### ECO计算元件

计算系统的计算元件也在此合成。

### 多方块控制器

三种ECO多方块系统（存储、计算、合成）的控制器都使用集成工作站合成。

## 使用技巧

- 使用速度升级卡可大幅缩短处理时间
- 连接致密能量元件或高容量电源以保证不间断合成
- 批量处理时启用自动导出，可自动将产出推送到相邻容器
- 工作站可通过管道或流体元件输入流体
