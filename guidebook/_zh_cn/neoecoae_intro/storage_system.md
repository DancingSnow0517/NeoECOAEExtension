---
navigation:
  title: ECO 存储系统
  icon: neoecoae:storage_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:storage_system_l4
  - neoecoae:storage_system_l6
  - neoecoae:storage_system_l9
  - neoecoae:eco_drive
  - neoecoae:storage_interface
  - neoecoae:storage_casing
  - neoecoae:storage_vent
  - neoecoae:energy_cell_l4
  - neoecoae:energy_cell_l6
  - neoecoae:energy_cell_l9
  - neoecoae:eco_item_storage_cell_16m
  - neoecoae:eco_item_storage_cell_64m
  - neoecoae:eco_item_storage_cell_256m
  - neoecoae:eco_fluid_storage_cell_16m
  - neoecoae:eco_fluid_storage_cell_64m
  - neoecoae:eco_fluid_storage_cell_256m
---

# ECO 存储系统

ECO 存储系统是一个可扩展的多方块存储解决方案，为你的ME网络提供大容量存储。

## 概述

存储子系统作为AE2网络的高容量存储扩展。它由控制器、存储单元驱动器、能量元件以及各种结构组件组成。

## 等级

共有三个等级的存储系统可用：

| 等级 | 控制器 | 存储容量 | 能量存储 |
|------|--------|----------|----------|
| L4 | <ItemLink id="neoecoae:storage_system_l4" /> | 每单元16MB | 10,000,000 AE |
| L6 | <ItemLink id="neoecoae:storage_system_l6" /> | 每单元64MB | 100,000,000 AE |
| L9 | <ItemLink id="neoecoae:storage_system_l9" /> | 每单元256MB | 1,000,000,000 AE |

## 结构组件

### 主机

<ItemGrid>
  <ItemIcon id="neoecoae:storage_system_l4" />
  <ItemIcon id="neoecoae:storage_system_l6" />
  <ItemIcon id="neoecoae:storage_system_l9" />
</ItemGrid>

主机（<ItemLink id="neoecoae:storage_system_l4" />、<ItemLink id="neoecoae:storage_system_l6" /> 或 <ItemLink id="neoecoae:storage_system_l9" />）是存储系统的核心。它必须放置在多方块结构的有效位置，并决定整个系统的等级。

### 存储矩阵驱动器

<ItemGrid>
  <ItemIcon id="neoecoae:eco_drive" />
</ItemGrid>

<ItemLink id="neoecoae:eco_drive" /> 用于放置ECO存储单元。可以添加多个驱动器以扩展存储容量。驱动器沿控制器延伸的方向排列放置。

### 能量元件

<ItemGrid>
  <ItemIcon id="neoecoae:energy_cell_l4" />
  <ItemIcon id="neoecoae:energy_cell_l6" />
  <ItemIcon id="neoecoae:energy_cell_l9" />
</ItemGrid>

高密度能量元件（<ItemLink id="neoecoae:energy_cell_l4" />、<ItemLink id="neoecoae:energy_cell_l6" /> 或 <ItemLink id="neoecoae:energy_cell_l9" />）为系统提供能量存储。能量元件等级必须与控制器等级匹配。

### 通讯接口

<ItemGrid>
  <ItemIcon id="neoecoae:storage_interface" />
</ItemGrid>

<ItemLink id="neoecoae:storage_interface" /> 将存储系统连接到ME网络。

### 散热器

<ItemGrid>
  <ItemIcon id="neoecoae:storage_vent" />
</ItemGrid>

<ItemLink id="neoecoae:storage_vent" /> 用于存储系统的热量管理。

### 结构外壳

<ItemGrid>
  <ItemIcon id="neoecoae:storage_casing" />
</ItemGrid>

<ItemLink id="neoecoae:storage_casing" /> 方块构成多方块结构的框架。

## 搭建结构

1. 放置**主机**，使其朝外
2. 使用**存储系统结构外壳**在控制器（不包含右方和右后方）搭建结构框架
3. 在指定位置（控制器左后方）放置**通讯接口**
4. 在控制器右侧水平排列添加**驱动器**
5. 在控制器右侧每一纵列驱动器的背面放置：上下方各一个能源元件、中间一个散热器
6. 使用剩余的外壳方块完成结构

结构可扩展——你可以添加更多驱动器和能量元件以增加容量。

<GameScene zoom="4">
  <ImportStructure src="../scenes/store_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

<GameScene zoom="4">
  <ImportStructure src="../scenes/store_min.nbt" />
</GameScene>

## 存储单元

以下ECO存储单元可用于驱动器：

### 物品存储

<ItemGrid>
  <ItemIcon id="neoecoae:eco_item_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_item_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_item_storage_cell_256m" />
</ItemGrid>

- <ItemLink id="neoecoae:eco_item_storage_cell_16m" /> - 16MB容量
- <ItemLink id="neoecoae:eco_item_storage_cell_64m" /> - 64MB容量
- <ItemLink id="neoecoae:eco_item_storage_cell_256m" /> - 256MB容量

### 流体存储

<ItemGrid>
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_256m" />
</ItemGrid>

- <ItemLink id="neoecoae:eco_fluid_storage_cell_16m" /> - 16MB容量
- <ItemLink id="neoecoae:eco_fluid_storage_cell_64m" /> - 64MB容量
- <ItemLink id="neoecoae:eco_fluid_storage_cell_256m" /> - 256MB容量

## 使用方法

结构形成后，存储系统将通过接口自动连接到ME网络。所有存储的物品和流体将可通过任何连接的终端访问。

GUI显示：
- 当前能量存储水平
- 能量容量百分比
