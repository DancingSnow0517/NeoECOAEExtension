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
  - neoecoae:computation_network_switch
  - neoecoae:computation_high_energy_network_switch
---

# ECO 计算系统

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/comp_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

ECO 计算系统在 ME 网络中承担合成 CPU 的工作，为任务提供线程、加速器和存储空间。想同时运行更多合成任务，可以扩充线程；要容纳大型任务，还需要足够的计算单元存储。

上方展示的是带普通网络交换模块的长度 1 结构。初次搭建可以先看各部件位置，模块的安装条件和组网规则在本页后半部分说明。

## 沿传输总线搭出计算机组

<ItemGrid>
  <ItemIcon id="neoecoae:computation_system_l4" />
  <ItemIcon id="neoecoae:computation_system_l6" />
  <ItemIcon id="neoecoae:computation_system_l9" />
  <ItemIcon id="neoecoae:computation_drive" />
  <ItemIcon id="neoecoae:computation_transmitter" />
  <ItemIcon id="neoecoae:computation_threading_core_l4" />
  <ItemIcon id="neoecoae:computation_threading_core_l6" />
  <ItemIcon id="neoecoae:computation_threading_core_l9" />
  <ItemIcon id="neoecoae:computation_parallel_core_l4" />
  <ItemIcon id="neoecoae:computation_parallel_core_l6" />
  <ItemIcon id="neoecoae:computation_parallel_core_l9" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l4" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l6" />
  <ItemIcon id="neoecoae:computation_cooling_controller_l9" />
  <ItemIcon id="neoecoae:computation_interface" />
  <ItemIcon id="neoecoae:computation_casing" />
</ItemGrid>

主机决定等级并管理合成操作。<ItemLink id="neoecoae:computation_transmitter" /> 连接驱动器和处理核心，<ItemLink id="neoecoae:computation_drive" /> 则装入计算单元，为任务提供存储。

线程核心（<ItemLink id="neoecoae:computation_threading_core_l4" />、<ItemLink id="neoecoae:computation_threading_core_l6" />、<ItemLink id="neoecoae:computation_threading_core_l9" />）提供合成线程，每个线程同时处理一个任务；主机可以使用同级或更低等级的核心。并行核心（<ItemLink id="neoecoae:computation_parallel_core_l4" />、<ItemLink id="neoecoae:computation_parallel_core_l6" />、<ItemLink id="neoecoae:computation_parallel_core_l9" />）提供加速器，放在线程核心上下两排。

结构末端的冷却系统控制器（<ItemLink id="neoecoae:computation_cooling_controller_l4" />、<ItemLink id="neoecoae:computation_cooling_controller_l6" />、<ItemLink id="neoecoae:computation_cooling_controller_l9" />）管理散热。<ItemLink id="neoecoae:computation_interface" /> 接入 ME 网络，<ItemLink id="neoecoae:computation_casing" /> 构成外部框架。

1. 放置主机，使其朝外，用外壳搭出周围框架。
2. 在主机左后方放置通讯接口。
3. 从主机右侧外壳的右侧开始，水平排列传输总线。
4. 在传输总线后方放线程核心，上下各放一排驱动器。
5. 在线程核心上下各放一排并行核心。
6. 在传输总线排末端放冷却系统控制器：站在结构右侧，面朝主机放置。
7. 用外壳补齐剩余结构。

增加长度时，需要一起添加传输总线、驱动器、线程核心和并行核心。也可以使用[自动搭建](multiblock_builder.md)预览和放置结构。

## 线程、并行和存储怎么选？

| 等级 | 控制器 | 加速器 | 线程数 | 每单元存储 |
|------|--------|--------|--------|------------|
| C4 | <ItemLink id="neoecoae:computation_system_l4" /> | 64 | 1 | 64MB |
| C6 | <ItemLink id="neoecoae:computation_system_l6" /> | 192 | 2 | 256MB |
| C9 | <ItemLink id="neoecoae:computation_system_l9" /> | 576 | 4 | 1GB |

更多线程核心允许更多任务同时运行，更多并行核心提供更强的合成加速；计算单元的总存储还必须满足任务需求。

将闪存晶阵装进驱动器：

<ItemGrid>
  <ItemIcon id="neoecoae:eco_computation_cell_l4" />
  <ItemIcon id="neoecoae:eco_computation_cell_l6" />
  <ItemIcon id="neoecoae:eco_computation_cell_l9" />
</ItemGrid>

- <ItemLink id="neoecoae:eco_computation_cell_l4" /> - CE4 闪存晶阵，64MB
- <ItemLink id="neoecoae:eco_computation_cell_l6" /> - CE6 闪存晶阵，256MB
- <ItemLink id="neoecoae:eco_computation_cell_l9" /> - CE9 闪存晶阵，1GB

## 让 ME 网络使用这台 CPU

结构形成并接入 ME 网络后，它会显示为合成 CPU。发起合成时，可以选择 ECO 计算系统作为目标 CPU。主机界面显示已用/总线程数、已用/可用存储和并行数。

CPU 选择模式决定它接受哪些请求：

| 模式 | 接受的请求 |
|------|------------|
| 任意 | 玩家和机器发起的合成请求 |
| 仅玩家 | 玩家手动请求 |
| 仅机器 | 自动化请求 |

## 将多台 C9 连接起来

<Row>
  <BlockImage id="neoecoae:computation_network_switch" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_high_energy_network_switch" scale="2"></BlockImage>
</Row>

<ItemLink id="neoecoae:computation_network_switch" /> 和 <ItemLink id="neoecoae:computation_high_energy_network_switch" /> 可将多台 C9 计算主机接入同一个逻辑计算网络。面向控制器正面时，普通结构用模块替换控制器右侧相邻的中央结构外壳，镜像结构则替换左侧；模块仅支持 C9 主机。

### 模块倍率与冷却要求

| 项目 | 普通网络交换 | 高能网络交换 |
|------|--------------|--------------|
| 资源倍率 | 线程、并行数和 CPU 存储 **x2** | 线程、并行数和 CPU 存储 **x8** |
| 耗电倍率 | **x4** | **x16** |
| 冷却系统控制器 | 该主机需要有效的冷却系统控制器 | 该主机需要 **C9** 冷却系统控制器，否则按 **x1** 贡献 |

### 资源如何汇总

| 项目 | 规则 |
|------|------|
| 生效条件 | 同一 ME 网络中至少有 **2 台**已安装模块的 C9 计算主机；只有一台主机时保持 **x1**。 |
| 资源聚合 | 每台物理主机先独立计算模块和冷却条件下的有效资源，再将线程、并行数和存储容量汇总到 ME 网络。 |
| 模块混用 | 两种模块可以混用，每台主机按自身模块档位贡献资源。 |

### 达到极限模式的条件

| 条件 | 结果 |
|------|------|
| 至少 **8 台高能网络 C9 主机**，每台至少有 **10 个线程核心**，且每台主机上下所有晶阵驱动器都装有有效闪存晶阵 | 聚合 CPU 的并行数固定为 **INT32 上限（2,147,483,647）**，CPU 存储固定为 **INT64 上限（9,223,372,036,854,775,807 字节）**。正在运行的任务字节仍会从可用存储中扣除；普通 x2 网络主机不参与判定。 |

## 配合高速合成系统派单

ECO CPU 会在每个 tick 尽量向空闲的样板供应器和工作核心补充任务。原版 AE 风格更偏向将样板提交平滑分配到多个 tick；ECO 的派单方式着重维持持续吞吐。

这在配合 [ECO 合成系统](crafting_system.md)时尤其有用：高超频的工作核心可能只需很少的 tick 就完成一批样板，及时补上下一批任务可以减少等待派单造成的空档。
