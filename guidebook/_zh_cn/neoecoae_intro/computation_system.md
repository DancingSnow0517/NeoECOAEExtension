---
navigation:
  title: ECO 计算系统
  icon: neoecoae:computation_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:computation_system_l4
  - neoecoae:computation_system_l6
  - neoecoae:computation_system_l9
  - neoecoae:computation_drive
  - neoecoae:computation_transmitter
  - neoecoae:computation_threading_core_l4
  - neoecoae:computation_threading_core_l6
  - neoecoae:computation_threading_core_l9
  - neoecoae:computation_parallel_core_l4
  - neoecoae:computation_parallel_core_l6
  - neoecoae:computation_parallel_core_l9
  - neoecoae:computation_cooling_controller_l4
  - neoecoae:computation_cooling_controller_l6
  - neoecoae:computation_cooling_controller_l9
  - neoecoae:computation_interface
  - neoecoae:computation_casing
  - neoecoae:eco_computation_cell_l4
  - neoecoae:eco_computation_cell_l6
  - neoecoae:eco_computation_cell_l9
---

# ECO 计算系统

ECO 计算系统是一个强大的多方块合成CPU集群，为你的ME网络提供大规模并行合成能力。

## 概述

计算子系统用更强大、可扩展的解决方案替代标准AE2合成CPU。它提供多个合成线程和加速器，允许多个合成任务同时运行。

## 等级

共有三个等级的计算系统可用：

| 等级 | 控制器 | 加速器 | 线程数 | 每单元存储 |
|------|--------|--------|--------|------------|
| C4 | <ItemLink id="neoecoae:computation_system_l4" /> | 64 | 1 | 64MB |
| C6 | <ItemLink id="neoecoae:computation_system_l6" /> | 192 | 2 | 256MB |
| C9 | <ItemLink id="neoecoae:computation_system_l9" /> | 576 | 4 | 1GB |

## 结构组件

### 主机

<ItemGrid>
  <ItemIcon id="neoecoae:computation_system_l4" />
  <ItemIcon id="neoecoae:computation_system_l6" />
  <ItemIcon id="neoecoae:computation_system_l9" />
</ItemGrid>

计算系统主机（<ItemLink id="neoecoae:computation_system_l4" />、<ItemLink id="neoecoae:computation_system_l6" /> 或 <ItemLink id="neoecoae:computation_system_l9" />）是多方块的核心。它决定等级并管理所有合成操作。

### 晶阵驱动器

<ItemGrid>
  <ItemIcon id="neoecoae:computation_drive" />
</ItemGrid>

<ItemLink id="neoecoae:computation_drive" /> 用于放置计算单元，为合成操作提供存储空间。驱动器放置在传输总线上方和下方的两排。

### 超导传输总线

<ItemGrid>
  <ItemIcon id="neoecoae:computation_transmitter" />
</ItemGrid>

<ItemLink id="neoecoae:computation_transmitter" /> 处理驱动器和处理核心之间的数据传输。

### 线程核心

<ItemGrid>
  <ItemIcon id="neoecoae:computation_threading_core_l4" />
  <ItemIcon id="neoecoae:computation_threading_core_l6" />
  <ItemIcon id="neoecoae:computation_threading_core_l9" />
</ItemGrid>

线程核心（<ItemLink id="neoecoae:computation_threading_core_l4" />、<ItemLink id="neoecoae:computation_threading_core_l6" /> 或 <ItemLink id="neoecoae:computation_threading_core_l9" />）提供合成线程。每个线程可以同时处理一个合成任务。等级必须与控制器等级匹配。

### 并行核心

<ItemGrid>
  <ItemIcon id="neoecoae:computation_parallel_core_l4" />
  <ItemIcon id="neoecoae:computation_parallel_core_l6" />
  <ItemIcon id="neoecoae:computation_parallel_core_l9" />
</ItemGrid>

并行核心（<ItemLink id="neoecoae:computation_parallel_core_l4" />、<ItemLink id="neoecoae:computation_parallel_core_l6" /> 或 <ItemLink id="neoecoae:computation_parallel_core_l9" />）提供合成加速器，加快合成操作速度。它们放置在线程核心上方和下方的两排。

### 冷却系统控制器

<ItemGrid>
  <ItemIcon id="neoecoae:computation_cooling_controller_l4" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l6" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l9" />
</ItemGrid>

冷却系统控制器（<ItemLink id="neoecoae:computation_cooling_controller_l4" />、<ItemLink id="neoecoae:computation_cooling_controller_l6" /> 或 <ItemLink id="neoecoae:computation_cooling_controller_l9" />）管理计算系统的散热。它放置在结构的末端。

### 通讯接口

<ItemGrid>
  <ItemIcon id="neoecoae:computation_interface" />
</ItemGrid>

<ItemLink id="neoecoae:computation_interface" /> 将系统连接到ME网络。

### 结构外壳

<ItemGrid>
  <ItemIcon id="neoecoae:computation_casing" />
</ItemGrid>

<ItemLink id="neoecoae:computation_casing" /> 方块构成多方块结构的框架。

## 搭建结构

1. 放置**主机**，使其朝外
2. 使用**计算系统结构外壳**在控制器周围搭建结构框架
3. 在指定位置（控制器左后方）放置**通讯接口**
4. 从控制器开始水平排列添加**传输总线**
5. 在传输总线后方放置**线程核心**
6. 在传输总线上方和下方添加**驱动器**（上下各一排）
7. 在线程核心上方和下方放置**并行核心**（上下各一排）
8. 在传输总线排末端添加**冷却系统控制器**
9. 使用剩余的外壳方块完成结构

结构可扩展——添加更多线程核心、并行核心、驱动器和传输总线以增加容量。

## 计算单元

<ItemGrid>
  <ItemIcon id="neoecoae:eco_computation_cell_l4" />
  <ItemIcon id="neoecoae:eco_computation_cell_l6" />
  <ItemIcon id="neoecoae:eco_computation_cell_l9" />
</ItemGrid>

以下计算单元为合成任务提供存储：

- <ItemLink id="neoecoae:eco_computation_cell_l4" /> - CE4 闪存晶阵，64MB
- <ItemLink id="neoecoae:eco_computation_cell_l6" /> - CE6 闪存晶阵，256MB
- <ItemLink id="neoecoae:eco_computation_cell_l9" /> - CE9 闪存晶阵，1GB

## 使用方法

结构形成后，计算系统在ME网络中显示为合成CPU。开始合成任务时，你可以选择ECO计算系统作为目标CPU。

GUI显示：
- 已用线程/总线程数
- 已用存储/可用存储
- 并行数

### CPU选择模式

计算系统支持不同的CPU选择模式：
- **任意** - 可被任何合成请求选择
- **仅玩家** - 仅接受玩家手动请求
- **仅机器** - 仅接受自动化请求

## 提示

- 更多线程核心 = 更多同时进行的合成任务
- 更多并行核心 = 更快的单个合成操作
- 确保有足够的计算单元存储以满足大型合成任务
- 所有单元的总存储必须满足合成任务需求
