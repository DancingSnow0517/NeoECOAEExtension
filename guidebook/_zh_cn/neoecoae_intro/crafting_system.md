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
  - neoecoae:crafting_network_switch
  - neoecoae:crafting_high_energy_network_switch
---

# ECO 合成系统

ECO 合成系统是一个高级多方块样板供应器，可并行处理合成样板，大幅提高合成效率。

## 概述

与处理合成任务的计算系统不同，合成子系统是一个样板供应器，可以同时执行多个样板。它支持超频和主动冷却以增强性能。

## 等级

共有三个等级的合成系统可用：

| 等级 | 控制器 | 单槽基础批量 | 超频追加批量 |
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

<ItemLink id="neoecoae:crafting_worker" /> 提供可同时运行的独立任务线程。x1 状态下每个 FX 工作核心提供 **1 条**线程；网络交换生效时，每个 FX 工作核心的线程数等于参与交换的 F 主机数。

### 样板总线

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_pattern_bus" />
</ItemGrid>

<ItemLink id="neoecoae:crafting_pattern_bus" /> 用于放置合成样板。可以添加多个样板总线以存储更多样板；接入网络交换组后，各成员主机向 ME 网络发布所有成员样板的并集。

### 并行核心

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_parallel_core_l4" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l6" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l9" />
</ItemGrid>

并行核心（<ItemLink id="neoecoae:crafting_parallel_core_l4" />、<ItemLink id="neoecoae:crafting_parallel_core_l6" /> 或 <ItemLink id="neoecoae:crafting_parallel_core_l9" />）决定对应 FX 工作核心每条线程一次可处理的合成数量。

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

### 网络交换模块

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_network_switch" />
  <ItemIcon id="neoecoae:crafting_high_energy_network_switch" />
</ItemGrid>

<ItemLink id="neoecoae:crafting_network_switch" /> 和 <ItemLink id="neoecoae:crafting_high_energy_network_switch" /> 可将多台 F9 合成主机接入同一个逻辑合成网络。面向控制器正面时，用模块替换控制器右侧相邻的中央结构外壳；模块仅支持 F9 主机。

- 普通网络交换模块使每个任务槽一次可处理 **x2** 的合成数量；高能模块为 **x8**
- 网络交换生效时，每个 FX 工作核心的线程数等于参与交换的 F 主机数；网络总线程数为所有主机 FX 工作核心线程数之和
- 样板取所有成员样板总线的并集；进入交换组的任务可公平分配到任意有合适空闲槽位的成员主机
- 普通网络交换模块生效时耗电量为 **x4**，高能网络交换模块生效时耗电量为 **x16**；交换生效期间，每台主机持续按全部可用 FX 线程拉满额定耗电，供电不足时相关任务暂停
- 同一 ME 网络中必须至少有 **2 台**已安装网络交换模块的 F9 合成主机，倍率才会生效
- 只有一台主机时，即使安装了模块也保持 **x1**，不会获得容量提升
- 两种模块可以混用，每台主机按自身安装的模块档位贡献容量
- 网络中的主机共用一套 GUI、超频开关、主动冷却开关和聚合能耗显示
- 主动冷却开启后，所有成员缓存的冷却值构成一个网络冷却池；消耗会在所有满足冷却等级的成员间轮转均摊，不要求执行任务的工作核心所在主机单独有冷却液
- 普通模块要求网络冷却池可提供合格冷却液；高能模块要求网络冷却池可提供最高级冷却液（最高支持超频 9）。条件不满足时，对应倍率按 **x1** 处理
- 只有已生效的网络交换任务改用按 tick 冷却：普通模块每条活跃任务线程每 tick 消耗 **4** 冷却值，高能模块消耗 **16**；单线程批量不参与计算
- 网络冷却池不足时，相关任务暂停推进，补充冷却值后从原进度继续

## 搭建结构

1. 放置**主机**，使其朝外
2. 使用**合成系统结构外壳**在控制器周围搭建结构框架；如需网络交换，在控制器右侧相邻位置安装对应模块
3. 在指定位置（控制器左后方）放置**通讯接口**
4. 在接口上方添加**流体输入仓**
5. 在接口下方添加**流体输出仓**
6. 从控制器右侧的结构外壳的右侧开始水平排列放置**工作核心**
7. 在工作核心上方和下方添加**并行核心**（上下各一排）
8. 在工作核心后方放置**散热器**
9. 在散热器上方和下方添加**样板总线**（上下各一排）
10. 使用剩余的外壳方块完成结构

结构可扩展——添加更多工作核心、并行核心、样板总线和散热器以增加容量。

若希望快速完成结构搭建，可参考 [多方块自动搭建](multiblock_builder.md) 中的自动预览与建造功能。

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/craft_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

## 使用方法

结构形成后，合成系统作为样板供应器接入ME网络。将样板插入样板总线即可启用自动合成。

### 配置选项

GUI提供以下设置：

#### 超频
启用超频可增加每个任务槽的批量，但会消耗更多能量。超频不会增加任务槽数量。
- 正常模式：核心的单槽基础批量
- 超频模式：基础批量加上超频追加批量（见等级表）

#### 主动冷却
启用主动冷却可进一步增强性能并消除超频带来的额外能耗。
- 需要在输入仓中放入冷却剂流体
- 可在JEI中查看冷却剂配方
- 系统会将冷却剂转换为冷却值缓冲；x1 模式在工作核心接收样板时按合成次数消耗，已生效的 x2/x8 网络交换任务按活跃槽位每 tick 消耗
- 如果输出仓已满，将无法继续转换冷却剂，也就无法补充冷却值

### 冷却与生效超频

现在的合成系统将“结构可提供的超频能力”和“冷却液实际支持的超频能力”分开计算。

- 多方块结构仍然决定理论溢出超频：并行核心处理能力超过 FX 工作核心承载能力的部分形成溢出，每 5% 溢出提供一级加速，最高 9 级
- 溢出超频只缩短任务耗时，不增加任务槽，也不计入 x2/x8 的单槽批量倍率
- 主动冷却决定当前真正生效的超频次数
- 如果当前冷却液等级低于结构上限，系统不会因为无法达到理论超频而拒绝补冷，而是以较低的生效超频继续运行
- GUI 会同时显示理论超频和当前生效超频
- GUI 还会显示当前冷却液支持的最高超频，并提供“清空”按钮，方便切换冷却液

当前默认冷却液分级如下：

| 冷却液 | 每 100 mB 提供冷却值 | 最高支持超频 |
|--------|----------------------|--------------|
| 水 | 1500 | 2 |
| 水转蒸汽 | 1500 | 2 |
| 钠 | 5000 | 6 |

控制器会根据当前缺少的冷却值批量补冷，而不是每 tick 只按一份配方转换一次。这使大型合成系统也能维持足够的补冷吞吐。

关于底层 ECO 合成 CPU 的派单逻辑与调度行为，详见 [ECO 计算系统](neoecoae:neoecoae_intro/computation_system.md)。

### GUI信息

界面显示：
- 工作核心数量
- 样板总线数量
- 并行核心数量
- 任务槽（活动/总计）
- 当前最高单槽批量
- 最大能耗
- 理论超频与当前生效超频
- 当前冷却液支持的最高超频

## 提示

- 当能量充足时使用超频以加快处理速度
- 结合超频启用主动冷却以获得最佳效率
- 如果生效超频低于理论超频，优先升级冷却液等级
- 从低级冷却液切换到高级冷却液前，可先使用 GUI 中的“清空”按钮
- 每个 FX 工作核心在 x1 状态下提供 1 条线程，网络交换生效时按参与交换的 F 主机数提供线程
- 更高等级的并行核心增加每条 FX 工作核心线程一次处理的物品数量
- 确保输出仓有空间容纳使用过的冷却剂，以避免系统停机
