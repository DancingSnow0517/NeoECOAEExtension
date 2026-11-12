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

## 轻量合成模式

计算主机的“轻量”开关默认开启。每种材料固定一个样板，不回溯其他路线，也不复用替代输入、工具或容器返还以及副产物。无循环的订单按依赖顺序汇总一次需求；涉及循环时，开启“循环”开关会在固定样板路线内求解循环增长、启动种子与外部材料需求，不搜索其他样板。未开启循环求解时，循环链只遍历一次，回到已访问材料的需求仅从剩余库存补齐。已启用 NBT 忽略规则的加工输入按物品 ID 匹配，并记录实际消耗的 NBT 变体。最终产物仍精确匹配。关闭轻量开关后，始终使用完整规划。

本次实际采用轻量计算时，统计底部以绿色显示“轻量合成模式”，并隐藏“计算结果尚未完整”提示。改用完整规划的订单保留完整诊断；缺少的材料仍正常列出，“存在循环”只作为信息提示。

## 配方连续发配

同一合成任务内，当前配方只要材料足够且供应器继续接收，就持续优先发配该配方，不设置连续成功次数上限。缺料、供应器无法接收、任务完成，或配方被暂停及受依赖或循环限制时，再尝试其他合法配方。

每 tick 的执行与探测预算保持生效。加工批量按接口提交次数而非发送份数计入预算：每个 CPU 每 tick 最多提交 64 次，单个配方最多使用其中一半；能整批接收的接口一次即可收下材料、能量、循环种子与接口容量允许的全部份数。仅因预算耗尽暂停时不视为供应器失败，下一 tick 继续当前配方。批量按实际接收量扣料并记录待回收产物。

## 合成报告与递归图

计算完成后，确认页显示本次选定的最终方案。报告先显示摘要与材料清单，递归图随后载入。材料格分别列出已有库存、待合成数量和缺少数量；非轻量模式下，只完成部分估算时，字节用量会标明估算尚不完整。

从左侧工具栏打开合成图，可以展开递归模块，查看启动种子、每一层补入的材料、加工后的库存，以及最终交付和留下的材料。大型循环会折叠重复步骤；搜索未完成的部分显示为未知，不代表已有可执行方案。

悬停材料或递归节点时，按住 **Shift** 查看完整精确数量。数量超过普通整数范围时仍保留完整数值；缺少启动种子与缺少整单原料会分别列出。

材料清单载入后，点击左侧工具栏的图片导出按钮，将完整清单保存到游戏目录的 `screenshots` 文件夹。大清单会自动排列成多栏 PNG，聊天栏中的文件名可以点击打开。

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

## 线程、并行与存储

| 等级 | 控制器 | 加速器 | 线程数 | 每单元存储 |
|------|--------|--------|--------|------------|
| C4 | <ItemLink id="neoecoae:computation_system_l4" /> | 64 | 4 | 64 MiB |
| C6 | <ItemLink id="neoecoae:computation_system_l6" /> | 192 | 16 | 256 MiB |
| C9 | <ItemLink id="neoecoae:computation_system_l9" /> | 576 | 64 | 1 GiB |

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

普通计算网络按实际安装的晶阵汇总 CPU 存储。要进入极限模式，整个逻辑组中每台主机的上下两排驱动器都必须装满 CE9 闪存晶阵；混入一枚 CE4、CE6 或留下一个空槽，都会使整个组不满足条件。

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

组网要求同一维度、同一在线 ME 网络和相同逻辑频道；计算与合成系统分别分组。频道可选 1～4，每个逻辑组最多 8 台。极限模式不另要求冷却控制器，具体缺项可悬停信息提示查看。

### 模块倍率

| 项目 | 普通网络交换 | 高能网络交换 |
|------|--------------|--------------|
| 资源倍率 | 线程、并行数和 CPU 存储 **x2** | 线程、并行数和 CPU 存储 **x8** |
| 耗电倍率 | **x4** | **x16** |

### 资源如何汇总

| 项目 | 规则 |
|------|------|
| 生效条件 | 同一 ME 网络中至少有 **2 台**已安装模块的 C9 计算主机；只有一台主机时保持 **x1**。 |
| 资源聚合 | 每台物理主机先按自身模块计算有效资源，再将线程、并行数和存储容量汇总到 ME 网络。 |
| 模块混用 | 两种模块可以混用，每台主机按自身模块档位贡献资源。 |

### 达到极限模式的条件

以下条件必须同时满足：

1. 同一逻辑组恰好有 **8 台 C9 计算主机**。
2. 每台主机都安装 <ItemLink id="neoecoae:computation_high_energy_network_switch" />。
3. 每台主机都达到当前配置允许的最大结构长度。
4. **8 台主机上下两排的每一个晶阵槽位**都装有 <ItemLink id="neoecoae:eco_computation_cell_l9" />，全部为 **CE9**。

满足全部条件后，整个计算网络进入极限模式：

| 项目 | 极限模式容量 |
|------|--------------|
| CPU 存储 | 9,223,372,036,854,775,807 字节（64 位整数上限） |
| 线程数 | 2,147,483,647（32 位整数上限） |
| 并行数 | 2,147,483,647（32 位整数上限） |

例如，其余槽位全是 CE9，但任意一台主机下排混入一枚 CE4，整个组的 CPU 存储、线程和并行都会恢复为按实际组件与交换模块倍率汇总的有限容量。CE6、空槽或无效物品也会阻止极限模式。将所有槽位补齐为 CE9，并满足其余条件后，才会重新获得极限容量。

正在运行的任务仍会占用 CPU 存储，可用字节数会扣除这些占用。将鼠标移到主机界面的信息图标上，可查看整个计算网络尚未满足的条件。

## 配合高速合成系统派单

ECO CPU 会在每个 tick 尽量向空闲的样板供应器和工作核心补充任务。原版 AE 风格更偏向将样板提交平滑分配到多个 tick；ECO 的派单方式着重维持持续吞吐。

这在配合 [ECO 合成系统](crafting_system.md)时尤其有用：高超频的工作核心可能只需很少的 tick 就完成一批样板，及时补上下一批任务可以减少等待派单造成的空档。


启用快速规划后，在线主机还须接受当前请求来源。循环路线需要开启循环规划并准备实际启动种子；“搜索未完成”与“缺少材料”不同。计算通讯接口可标记允许忽略组件差异的物品：手持样本左键设置、右键清空，不消耗样本；实际样板和机器仍须接受对应输入。

该标记也适用于支持兼容接口的 AE2LT / Thunderbolt 规划器，无需切换到 ECO 规划器。原生 AE2 处理样板会按物品类型接受所标记的组件变体；合成配方和自定义样板保留自身校验。任务保存下单时的标记，之后修改接口或重新加载存档，不会改变已有任务的匹配规则。ECO 规划器也会读取 AE2LT 过载样板自身的忽略 NBT 输入设置。

能自我增殖的循环（例如晶种循环）会保留最小启动种子，保证之后的订单仍能继续增殖：库存不少于“启动种子 + 本次需求”时直接用库存，种子留在网络中；库存不足时先运行循环补足缺口，循环可以临时借用种子，但结束时必须把种子还回网络。例如制作水晶粉时，水晶足够就直接磨粉并留下启动所需的水晶，水晶不足才通过循环补足缺口并补回种子。若燃料等材料不足以完成增殖，计划会列出缺少的材料，而不会消耗掉种子；网络中没有任何种子时，同样会提示缺少启动种子。催化剂和容器仍按样板返回。
