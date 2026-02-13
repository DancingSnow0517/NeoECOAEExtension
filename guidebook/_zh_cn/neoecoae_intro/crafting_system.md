---
navigation:
  title: ECO 合成系统
  icon: neoecoae:crafting_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:crafting_system_l4
  - neoecoae:crafting_system_l6
  - neoecoae:crafting_system_l9
  - neoecoae:crafting_worker
  - neoecoae:crafting_pattern_bus
  - neoecoae:crafting_parallel_core_l4
  - neoecoae:crafting_parallel_core_l6
  - neoecoae:crafting_parallel_core_l9
  - neoecoae:crafting_interface
  - neoecoae:crafting_casing
  - neoecoae:crafting_vent
  - neoecoae:input_hatch
  - neoecoae:output_hatch
---

# ECO 合成系统

ECO 合成系统是一个高级多方块样板供应器，可并行处理合成样板，大幅提高合成效率。

## 概述

与处理合成任务的计算系统不同，合成子系统是一个样板供应器，可以同时执行多个样板。它支持超频和主动冷却以增强性能。

## 等级

共有三个等级的合成系统可用：

| 等级 | 控制器 | 并行数 | 超频并行数 |
|------|--------|--------|------------|
| F4 | <ItemLink id="neoecoae:crafting_system_l4" /> | 24 | 32 |
| F6 | <ItemLink id="neoecoae:crafting_system_l6" /> | 72 | 96 |
| F9 | <ItemLink id="neoecoae:crafting_system_l9" /> | 256 | 384 |

## 结构组件

### 主机

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_system_l4" />
  <ItemIcon id="neoecoae:crafting_system_l6" />
  <ItemIcon id="neoecoae:crafting_system_l9" />
</ItemGrid>

合成系统主机（<ItemLink id="neoecoae:crafting_system_l4" />、<ItemLink id="neoecoae:crafting_system_l6" /> 或 <ItemLink id="neoecoae:crafting_system_l9" />）管理所有样板处理操作，并决定系统等级。

### 工作核心

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_worker" />
</ItemGrid>

<ItemLink id="neoecoae:crafting_worker" /> 是执行合成样板的核心处理单元。它根据样板处理实际的物品转换。

### 样板总线

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_pattern_bus" />
</ItemGrid>

<ItemLink id="neoecoae:crafting_pattern_bus" /> 用于放置合成样板。可以添加多个样板总线以存储更多样板。

### 并行核心

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_parallel_core_l4" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l6" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l9" />
</ItemGrid>

并行核心（<ItemLink id="neoecoae:crafting_parallel_core_l4" />、<ItemLink id="neoecoae:crafting_parallel_core_l6" /> 或 <ItemLink id="neoecoae:crafting_parallel_core_l9" />）为样板处理提供额外的并行能力。等级必须与控制器等级匹配。

### 通讯接口

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_interface" />
</ItemGrid>

<ItemLink id="neoecoae:crafting_interface" /> 将系统连接到ME网络。

### 流体输入仓

<ItemGrid>
  <ItemIcon id="neoecoae:input_hatch" />
</ItemGrid>

<ItemLink id="neoecoae:input_hatch" /> 接收主动冷却模式所需的冷却剂流体。

### 流体输出仓

<ItemGrid>
  <ItemIcon id="neoecoae:output_hatch" />
</ItemGrid>

<ItemLink id="neoecoae:output_hatch" /> 排出系统使用过的冷却剂。

### 散热器

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_vent" />
</ItemGrid>

<ItemLink id="neoecoae:crafting_vent" /> 为合成系统提供被动热量管理。

### 结构外壳

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_casing" />
</ItemGrid>

<ItemLink id="neoecoae:crafting_casing" /> 方块构成多方块结构的框架。

## 搭建结构

1. 放置**主机**，使其朝外
2. 使用**合成系统结构外壳**在控制器周围搭建结构框架
3. 在指定位置（控制器左后方）放置**通讯接口**
4. 在接口上方添加**流体输入仓**
5. 在接口下方添加**流体输出仓**
6. 从控制器开始水平排列放置**工作核心**
7. 在工作核心上方和下方添加**并行核心**（上下各一排）
8. 在工作核心后方放置**散热器**
9. 在散热器上方和下方添加**样板总线**（上下各一排）
10. 使用剩余的外壳方块完成结构

结构可扩展——添加更多工作核心、并行核心、样板总线和散热器以增加容量。

## 使用方法

结构形成后，合成系统作为样板供应器接入ME网络。将样板插入样板总线即可启用自动合成。

### 配置选项

GUI提供以下设置：

#### 超频
启用超频可增加并行数，但会消耗更多能量。
- 正常模式：基础并行数
- 超频模式：增强并行数（见等级表）

#### 主动冷却
启用主动冷却可进一步增强性能并消除超频带来的额外能耗。
- 需要在输入仓中放入冷却剂流体
- 可在JEI中查看冷却剂配方
- 如果运行时冷却剂耗尽，系统将停止运行
- 如果输出仓已满，将无法消耗冷却剂

### GUI信息

界面显示：
- 工作核心数量
- 样板总线数量
- 并行核心数量
- 总并行数
- 工作线程（活动/总计）
- 最大能耗

## 提示

- 当能量充足时使用超频以加快处理速度
- 结合超频启用主动冷却以获得最佳效率
- 更多工作核心允许更多样板同时处理
- 更多并行核心增加每次操作处理的物品数量
- 确保输出仓有空间容纳使用过的冷却剂，以避免系统停机
