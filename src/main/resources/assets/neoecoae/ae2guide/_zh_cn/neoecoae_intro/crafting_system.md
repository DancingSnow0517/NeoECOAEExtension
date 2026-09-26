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

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/craft_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

ECO 合成系统负责存放和执行样板。把样板装进总线，工作核心就能并行处理网络交来的合成任务；超频和主动冷却可以进一步提高吞吐。

上方是带普通网络交换模块的长度 1 结构。先按下面的步骤认识基础部件；交换模块只供 F9 主机组网使用，具体规则在本页后半部分。

## 围绕工作核心搭建

<ItemGrid>
  <ItemIcon id="neoecoae:crafting_system_l4" />
  <ItemIcon id="neoecoae:crafting_system_l6" />
  <ItemIcon id="neoecoae:crafting_system_l9" />
  <ItemIcon id="neoecoae:crafting_worker" />
  <ItemIcon id="neoecoae:crafting_pattern_bus" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l4" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l6" />
  <ItemIcon id="neoecoae:crafting_parallel_core_l9" />
  <ItemIcon id="neoecoae:crafting_interface" />
  <ItemIcon id="neoecoae:input_hatch" />
  <ItemIcon id="neoecoae:output_hatch" />
  <ItemIcon id="neoecoae:crafting_vent" />
  <ItemIcon id="neoecoae:crafting_casing" />
</ItemGrid>

主机决定等级并管理样板处理。每个 <ItemLink id="neoecoae:crafting_worker" /> 提供一条独立的物理执行通道；<ItemLink id="neoecoae:crafting_pattern_bus" /> 存放样板，需要容纳更多样板时可以增加总线。

并行核心（<ItemLink id="neoecoae:crafting_parallel_core_l4" />、<ItemLink id="neoecoae:crafting_parallel_core_l6" />、<ItemLink id="neoecoae:crafting_parallel_core_l9" />）提供结构并行能力。超过 FX 工作核心可用容量的部分会形成溢出超频，具体效果见后面的冷却说明。

<ItemLink id="neoecoae:crafting_interface" /> 连接 ME 网络，<ItemLink id="neoecoae:crafting_casing" /> 构成框架。<ItemLink id="neoecoae:crafting_vent" /> 提供被动散热，<ItemLink id="neoecoae:input_hatch" /> 和 <ItemLink id="neoecoae:output_hatch" /> 分别接收主动冷却所需流体、排出使用过的冷却剂。

1. 放置主机，使其朝外，用外壳搭出周围框架。
2. 在主机左后方放通讯接口，接口上方放流体输入仓，下方放流体输出仓。
3. 从主机右侧外壳的右侧开始，水平排列工作核心。
4. 在工作核心上下各放一排并行核心，后方放散热器。
5. 在散热器上下各放一排样板总线。
6. 用外壳补齐剩余结构。

扩建时一起添加工作核心、并行核心、样板总线和散热器。也可以通过[自动搭建](multiblock_builder.md)检查长度、材料并补齐方块。

## 装入样板，开始合成

结构形成后，通过通讯接口接入 ME 网络，再把合成样板插入样板总线，就可以作为样板供应器参与自动合成。

界面会显示工作核心、样板总线、并行核心的数量，以及物理 FX、活跃 FX 数量和 FT 并行能力。先确认结构和样板能正常工作，再根据供电情况启用超频。

## F4、F6、F9 的批量有什么区别？

| 等级 | 控制器 | 单槽基础批量 | 超频后单槽批量 |
|------|--------|--------------|----------------|
| F4 | <ItemLink id="neoecoae:crafting_system_l4" /> | 32 | 128 |
| F6 | <ItemLink id="neoecoae:crafting_system_l6" /> | 32 | 256 |
| F9 | <ItemLink id="neoecoae:crafting_system_l9" /> | 32 | 512 |

正常模式下，每条 FX 工作核心线程的基础批量都是 32；开启超频后，主机等级将批量分别放大至 x4、x8、x16，同时增加能量消耗。

超频提高每个任务槽一次承担的批量，不增加任务槽数量。并行核心带来的溢出超频另行影响任务耗时，不决定 FX 线程的单槽批量。

## 为超频准备冷却液

在界面启用主动冷却，并向输入仓送入冷却剂，可以进一步增强性能并消除超频带来的额外能耗。冷却剂配方可以在 JEI 中查看；使用过的冷却剂从输出仓排出。输出仓满时无法继续转换冷却剂，也就无法补充冷却值。

系统先把冷却剂转换成冷却值缓冲。普通有限批量在接收时按合成次数消耗冷却值，x2/x8 网络交换也遵循这条规则；八主机虚拟合成的消耗方式见后文。

### 理论超频与生效超频

并行核心处理能力超过 FX 工作核心承载能力时，每 **5%** 溢出提供一级加速，最高 **9 级**。这种溢出超频只缩短任务耗时，不增加任务槽，也不计入 x2/x8 的单槽批量倍率。

主动冷却决定当前真正生效的超频次数。冷却液等级低于结构上限时，系统会按较低的生效超频继续运行并补冷。

| 冷却液 | 每 100 mB 提供冷却值 | 最高支持超频 |
|--------|----------------------|--------------|
| 水 | 1500 | 2 |
| 水转蒸汽 | 1500 | 2 |
| 钠 | 5000 | 6 |
| 极寒之凛冰溶液 | 12000 | 9 |

以上是当前默认冷却液分级。界面会同时显示理论超频、生效超频和当前冷却液支持的最高超频。若生效超频低于理论值，可以换用更高级的冷却液；切换前可使用界面的“清空”按钮。

控制器按当前缺少的冷却值批量补冷，使大型结构也能维持补冷吞吐。运行时留意最大能耗和输出仓空间，保证供电和冷却剂排出通畅。

## 将多台 F9 连接起来

<Row>
  <BlockImage id="neoecoae:crafting_network_switch" scale="2"></BlockImage>
  <BlockImage id="neoecoae:crafting_high_energy_network_switch" scale="2"></BlockImage>
</Row>

<ItemLink id="neoecoae:crafting_network_switch" /> 和 <ItemLink id="neoecoae:crafting_high_energy_network_switch" /> 可将多台 F9 合成主机接入同一个逻辑合成网络。面向控制器正面时，普通结构用模块替换控制器右侧相邻的中央结构外壳，镜像结构则替换左侧；模块仅支持 F9 主机。

### 网络倍率怎么算

每台普通交换主机贡献 2，每台高能交换主机贡献 8，其中 a、b 分别为普通、高能交换主机数量，统一得到全网络倍率 **M = 2a + 8b**。逻辑网络内所有物理 FX 执行通道 都使用相同的 **512 x M** 单通道批量；不会按交换模块类型拆分倍率池。

### 样板、能耗与冷却如何共享

| 项目 | 规则 |
|------|------|
| 生效条件 | 同一 ME 网络中至少有 **2 台**已安装模块的 F9 合成主机；只有一台主机时保持 **x1**。 |
| 样板与派单 | 所有成员样板总线取并集，任务公平分配到有合适空闲槽位的成员主机。 |
| 模块混用 | 两种模块可以混用，所有贡献统一汇总成 `M = 2a + 8b`。 |
| 能耗与界面 | 交换生效时，每台主机按全部可用 FX 线程持续拉满额定耗电；供电不足时相关任务暂停，GUI 显示聚合能耗。 |
| 共享控制 | 网络成员共用一套 GUI、超频开关和主动冷却开关，GUI 同时显示聚合能耗并控制主动冷却。 |
| 冷却池 | 开启主动冷却后，所有成员的冷却值缓存合并为一个网络冷却池。普通有限批量 在接收时沿用按合成次数计费；冷却液等级只限制有效超频，不改变网络倍率。 |
| 虚拟 Tick 冷却 | 只有最终 虚拟合成模式 使用固定 tick 成本：开启主动冷却时，每条活跃物理 FX 执行通道 每 tick 消耗 10000 冷却值，虚拟合成次数不参与计算。 |

### 八台满长主机的虚拟合成

当同一个逻辑合成交换网络**恰好**连接 **8 台 F9 合成主机**，每台均安装高能网络交换模块，并且实际形成到当前配置允许的最大长度时，网络才会进入虚拟合成模式。默认每台实际有 **11 个物理 FX 执行通道，共 88 个**；只要任意一台少于 11 个实际 FX，网络就保持有限合成模式。

- 每条 FX 工作核心任务线程承载一种配方任务，并一次接收该任务的全部剩余合成数量。
- 输入和输出以物品键和 64 位数量汇总，不在 FX 工作核心内展开或保存为实际物品堆。
- 虚拟任务在工作核心的首个 tick 完成并返回全部汇总输出。
- 开启主动冷却时，每条活跃 虚拟任务执行通道 固定从共享池中消耗 **10000 冷却值/tick**；共享池无法一次支付完整消耗时，该通道等待。未开启主动冷却时，虚拟任务仍在 1 tick 内执行。
- 任务数量支持有符号 64 位范围；只有真正的 虚拟合成模式 显示为**无限**，普通有限模式的大数始终显示真实数字。

组网后，可以在界面查看网络组成、倍率、单 FX 处理能力和网络总处理能力。交换模块改变的是各物理执行通道一次承担的批量，工作核心的实际数量仍决定物理通道数量。

高速工作核心也需要 CPU 及时补充任务，相关派单方式见 [ECO 计算系统](neoecoae:neoecoae_intro/computation_system.md)。
