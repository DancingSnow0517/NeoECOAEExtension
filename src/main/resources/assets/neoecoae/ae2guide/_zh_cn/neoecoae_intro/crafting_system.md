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
  - neoecoae:network_pull_card
  - neoecoae:output_hatch
  - neoecoae:crafting_network_switch
  - neoecoae:crafting_high_energy_network_switch
---

# ECO 合成系统

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/craft_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

ECO 合成系统是一台集成了大容量样板库和高速分子装配室的多方块机器。把样板装入总线，工作核心就能并行处理网络发来的合成任务；配合超频和主动冷却，可进一步提高吞吐。

上图为带普通网络交换模块的最小结构（长度 1）。

## 搭建结构

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

- **主机**：决定系统等级并管理调度。
- **工作核心（FX）**：每个 <ItemLink id="neoecoae:crafting_worker" /> 提供一条独立执行通道。
- **样板总线**：<ItemLink id="neoecoae:crafting_pattern_bus" /> 存放合成样板。每页 63 槽，默认 1 页，服务器可通过 `craftingPatternBusPages` 调整至 1～8 页。
- **并行核心（FT）**：提供结构并行能力，超出工作核心批量上限的部分可转化为溢出加速。
- **通讯接口与散热组件**：<ItemLink id="neoecoae:crafting_interface" /> 接入 ME 网络；<ItemLink id="neoecoae:crafting_vent" /> 负责被动散热，<ItemLink id="neoecoae:input_hatch" /> 和 <ItemLink id="neoecoae:output_hatch" /> 分别用于主动冷却的进液与排液。
- **外壳**：<ItemLink id="neoecoae:crafting_casing" /> 构成外框。

搭建步骤（也可以使用[自动搭建](multiblock_builder.md)预览和补齐）：

1. 放置主机（正面朝外），搭出周围的外壳框架。
2. 在主机左后方放置通讯接口，正上方放流体输入仓，正下方放流体输出仓。
3. 从主机右侧外壳往右水平延伸，放置工作核心。
4. 在工作核心上下各放一排并行核心，后方放散热器。
5. 在散热器上下各放一排样板总线。
6. 用外壳补齐剩余部分。

扩建时成组追加工作核心、并行核心、样板总线和散热器。结构成型并接入 ME 网络后，把合成样板放进总线就能参与自动合成。

> **样板管理**：通过通讯接口集中搜索和整理样板。材料框与产物框命中任一框即显示，单框内以空格分隔的关键词须全部匹配。手持样板**左键**点击列表可插入空槽，**右键**点击已有样板可替换。界面按键与冷却排查见[合成控制器图文教程](crafting_controller.md)。

## 批量、超频与溢出加速

两种提速方式都不增加任务槽数量：

1. **主机超频（提高批量）**：未组网时，每条 FX 通道的基础批量为 **32**。开启主机超频后，单槽批量按等级放大，耗电也随之增加。
2. **并行溢出（缩短耗时）**：当 FT 总能力超过 FX 批量上限（通道数 × 当前单槽批量）时，超出部分按其占 FT 总能力的比例折算，每 **5 个百分点**约为 1 级加速，最高 **9 级**。启用主动冷却时，实际生效等级还受冷却液档位限制。

| 等级 | 主机 | 基础单槽批量 | 超频单槽批量 |
|------|------|--------------|--------------|
| F4 | <ItemLink id="neoecoae:crafting_system_l4" /> | 32 | 128（x4） |
| F6 | <ItemLink id="neoecoae:crafting_system_l6" /> | 32 | 256（x8） |
| F9 | <ItemLink id="neoecoae:crafting_system_l9" /> | 32 | 512（x16） |

## 主动冷却与自动补液

在主机界面开启**主动冷却**，向 <ItemLink id="neoecoae:input_hatch" /> 输入冷却液，可免除超频附加耗电。若生效加速等级低于理论值，可更换支持更高等级的冷却液。

- 冷却液先转换成冷却值缓冲；有限批量在接单时按合成次数扣除冷却值。
- 废液从 <ItemLink id="neoecoae:output_hatch" /> 排出；输出仓满时无法继续补冷。
- 悬停界面并行区域可对比“理论超频”与“生效超频”。更换冷却液前，先停供旧液、排空输入仓，让普通合成用完旧冷却缓冲，再通入新液。

| 默认冷却液 | 每 100 mB 冷却值 | 最高支持超频 |
|------------|------------------|--------------|
| 水 / 水转蒸汽 | 1500 | 2 级 |
| 钠 | 5000 | 6 级 |
| 凛冰溶液 | 12000 | 9 级 |

冷却液配方可在 JEI / EMI 中查看。

### 使用网络拉取卡自动补液

<RecipeFor id="neoecoae:network_pull_card" />

将 <ItemLink id="neoecoae:network_pull_card" /> 插入 <ItemLink id="neoecoae:input_hatch" /> 右侧槽位，输入仓会切换为类似 ME 接口的上下双槽。卸卡后恢复原布局，已有缓存保留。

- **上槽（标记）**：手持流体容器右键，或从 JEI/EMI 拖入目标冷却液。标记不消耗流体，空手左键清除。点击上方齿轮设置目标存量，单位是桶（B），支持小数，可超过原有的 16 桶上限。
- **下槽（缓存）**：每 20 tick 从所连 ME 网络补足缺口。调低目标或更换液体时，多余或不再使用的液体会退回网络；网络拒收时保留并重试。

## F9 集群组网与虚拟合成

<Row>
  <BlockImage id="neoecoae:crafting_network_switch" scale="2"></BlockImage>
  <BlockImage id="neoecoae:crafting_high_energy_network_switch" scale="2"></BlockImage>
</Row>

<ItemLink id="neoecoae:crafting_network_switch" /> 和 <ItemLink id="neoecoae:crafting_high_energy_network_switch" /> 可把多台 **F9 主机**连成一个逻辑合成网络。

- **安装位置**：面向主机正面，替换其右侧紧邻的中央外壳；镜像结构替换左侧。仅支持 F9。
- **组网条件**：同一维度、同一在线 ME 网络、相同逻辑频道（1～4），每组最多 **8 台**，与计算系统分开分组。至少 **2 台**带模块的主机才会生效（单台维持 x1）。

### 共享规则与网络倍率

- **统一倍率**：普通交换主机每台贡献 `2`，高能交换主机每台贡献 `8`，混用时全组倍率为 **`M = 2a + 8b`**（`a`、`b` 分别是两类主机数量）。有限组网时，各 FX 通道的单槽批量统一为 **`512 × M`**，合成耗时为 **1 tick**，通道数量不变。
- **资源共享**：所有成员的样板合并使用，任务分配至有合适空闲通道的成员；全组共用控制界面、超频与主动冷却开关，以及冷却值池。
- **能耗**：有限组网时，每台主机按全部可用工作核心持续消耗额定功率；供电不足时任务会暂停。

### 解锁虚拟合成

同时满足下面的条件，整组进入**虚拟合成模式**：

1. 同一逻辑组凑齐 **8 台 F9 主机**，每台都达到配置允许的最大长度。默认每台 11 条 FX 通道，全组 88 条。
2. 8 台全部安装 <ItemLink id="neoecoae:crafting_high_energy_network_switch" />。
3. 同时开启**超频**和**主动冷却**。

- **无限批量、1 tick 完成**：界面批量显示为**无限**。每条 FX 通道可一次接收单个配方的全部剩余数量（支持有符号 64 位数量），在供电和冷却充足时于首个工作 tick 返回全部产物。
- **能耗与冷却**：工作时全组每 tick 支付一次 **10,000,000 AE** 基础加工费（遵循 AE2 用电倍率），不按合成次数或占用槽位另计加工费。每条工作通道每 tick 从共享池消耗 **10,000 冷却值**，且必须由支持 **9 级超频**的冷却液提供。默认使用凛冰溶液，支持列表见界面右侧信息图标。冷却不足时通道等待，不会退回有限模式。

高速合成系统同样需要 CPU 及时喂单，派单机制见 [ECO 计算系统](neoecoae:neoecoae_intro/computation_system.md)。
