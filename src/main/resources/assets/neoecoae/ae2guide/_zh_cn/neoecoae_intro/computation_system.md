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

ECO 计算系统是 ME 网络的高性能合成 CPU，提供**线程**、**并行（加速器）**和**合成存储**。想同时跑更多订单就加线程，想接下更大的合成单就增加存储。

上图为带普通网络交换模块的最小结构（长度 1）。

## 搭建结构

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

- **主机**：决定最高等级，可使用同级或更低级的线程与并行核心。
- **传输总线与驱动器**：<ItemLink id="neoecoae:computation_transmitter" /> 连接内部模块；<ItemLink id="neoecoae:computation_drive" /> 用来安装闪存晶阵。
- **线程核心与并行核心**：线程核心决定同时运行的任务数；上下两排并行核心提供合成加速。
- **通讯接口与冷却控制器**：<ItemLink id="neoecoae:computation_interface" /> 接入 ME 网络；冷却控制器装在末端负责散热。
- **外壳**：<ItemLink id="neoecoae:computation_casing" /> 构成外部框架。

搭建步骤（也可以使用[自动搭建](multiblock_builder.md)预览和放置）：

1. 放置主机（正面朝外），搭出周围的外壳框架。
2. 在主机左后方放置通讯接口。
3. 从主机右侧外壳往右水平延伸，铺设传输总线。
4. 在传输总线后方放置线程核心，总线上下各放一排驱动器。
5. 在线程核心上下各放一排并行核心。
6. 站在结构右侧、面朝主机，在总线末端放置冷却控制器。
7. 用外壳补齐剩余部分。

扩建时成组追加传输总线、驱动器、线程核心和并行核心。

## 等级规格与闪存晶阵

| 等级 | 主机 | 每个并行核心的加速器 | 每个线程核心的线程数 | 单枚晶阵存储 |
|------|------|----------------------|----------------------|--------------|
| C4 | <ItemLink id="neoecoae:computation_system_l4" /> | 64 | 4 | 64 MiB |
| C6 | <ItemLink id="neoecoae:computation_system_l6" /> | 192 | 16 | 256 MiB |
| C9 | <ItemLink id="neoecoae:computation_system_l9" /> | 576 | 64 | 1 GiB |

将 CE4、CE6 或 CE9 闪存晶阵装入驱动器，为 CPU 提供合成存储：

<ItemGrid>
  <ItemIcon id="neoecoae:eco_computation_cell_l4" />
  <ItemIcon id="neoecoae:eco_computation_cell_l6" />
  <ItemIcon id="neoecoae:eco_computation_cell_l9" />
</ItemGrid>

结构成型并接入 ME 网络后即可作为合成 CPU 使用。主机界面可查看线程、存储与并行数，并切换允许的请求来源：

| 选择模式 | 可接单来源 |
|----------|------------|
| 任意 | 玩家与自动化设备 |
| 仅玩家 | 仅玩家手动下单 |
| 仅机器 | 仅自动化设备下单 |

主机必须在线且允许对应来源，才能响应合成请求。

## C9 集群组网与极限模式

<Row>
  <BlockImage id="neoecoae:computation_network_switch" scale="2"></BlockImage>
  <BlockImage id="neoecoae:computation_high_energy_network_switch" scale="2"></BlockImage>
</Row>

<ItemLink id="neoecoae:computation_network_switch" /> 和 <ItemLink id="neoecoae:computation_high_energy_network_switch" /> 可把多台 **C9 主机**连成一个逻辑计算网络。

- **安装位置**：面向主机正面，替换其右侧紧邻的中央外壳；镜像结构替换左侧。仅支持 C9。
- **组网条件**：同一维度、同一在线 ME 网络、相同逻辑频道（1～4），每组最多 **8 台**，与合成系统分开分组。
- **生效规则**：至少 **2 台**带模块的主机才会生效（单台维持 x1）。两种模块可混用，每台先按自身模块计算资源，再汇总到同一个逻辑 CPU。

| 模块 | 线程 / 并行 / 存储倍率 | 耗电倍率 |
|------|------------------------|----------|
| 普通网络交换 | **x2** | **x4** |
| 高能网络交换 | **x8** | **x16** |

### 解锁极限模式

同时满足以下条件时，整个计算网络解锁极限容量（无需额外升级冷却控制器）：

1. 同一逻辑组凑齐 **8 台 C9 主机**，每台都达到配置允许的最大长度。
2. 8 台全部安装 <ItemLink id="neoecoae:computation_high_energy_network_switch" />。
3. 8 台主机上下两排驱动器的**每一个槽位**都装有 <ItemLink id="neoecoae:eco_computation_cell_l9" />。空槽、无效物品或低级晶阵（CE4/CE6）都会让整组退回普通汇总。

| 项目 | 极限模式上限 |
|------|--------------|
| CPU 存储 | 9,223,372,036,854,775,807 字节（有符号 64 位上限） |
| 线程数 | 2,147,483,647（有符号 32 位上限） |
| 并行数 | 2,147,483,647（有符号 32 位上限） |

运行中的任务仍会占用并扣除可用字节数。悬停主机界面的信息图标可查看未满足的条件。

## 规划模式与实用功能

### 默认规划与深度规划

- **默认（快速规划）**：每种材料固定走一条样板路线，不回溯其他配方或使用替代输入。样板明确原样返还的单份输入只需备足一次合成的全部相关槽位；消耗料仍按合成次数计算，实际副产物可供后续需求复用。
- **深度规划模式**：在主机界面手动开启。可搜索替代路线、考虑替代输入及容器和工具返还，并显示完整诊断与估算提示；适合复杂配方，但可能耗时更长。

### 自增殖循环与自动留种

遇到晶种增殖这类配方时，打开规划器的**“循环”开关**即可求解循环并保留启动种子：

- **库存充足**：若库存不少于“启动种子 + 本次需求”，直接使用库存，不动种子。
- **库存不足**：临时借用种子跑循环补齐缺口，结束后把种子还回网络。
- **缺种子或原料**：若缺少启动种子，或燃料等材料不足以完成增殖，规划器会提示缺料，而不会耗尽种子。

### 忽略 NBT 匹配

手持样本，**左键**点击 <ItemLink id="neoecoae:computation_interface" />，可将该物品标记为忽略组件/NBT 差异；**右键**清空标记，不消耗样本。

- 标记作用于支持的处理样板输入，实际机器也必须能接受该变体。合成配方和自定义样板仍按自身规则校验，最终成品仍严格匹配。
- 兼容的 **AE2LT / Thunderbolt** 规划器也能使用这些标记，无需切换规划器；ECO 规划器也会读取 AE2LT 过载样板自带的忽略 NBT 设置。
- 匹配规则在下单时锁定，之后修改接口或重载存档不会影响已有任务。

### 高速连续派单

与原版 AE2 的平滑派单不同，只要当前配方有足够的材料且供应器能接收，ECO CPU 就会持续优先派发该配方，减少 [ECO 合成系统](crafting_system.md) 高速工作核心的等待时间。

普通单份派单每台 CPU 每 tick 最多 **64 次**，服务器可调低该上限。处理样板批量派单有单独的预算，每台 CPU 每 tick 最多 **64 次尝试**，单个配方最多 **32 次**；整批计一次尝试，不按份数计数。ECO 工作核心的快速批量路径不占用普通单份额度。仅因预算耗尽而暂停时，会在下一 tick 继续派单，不会视作供应器阻塞。

### 合成图与清单导出

- **精确数值**：在确认页或合成图中按住 **Shift** 悬停材料或节点，可查看完整数字。缺少启动种子与缺少常规原料会分开提示。
- **合成递归图**：点击左侧工具栏打开合成图，展开递归模块可查看每层补料、启动种子和剩余产物。大型循环会折叠重复步骤；“未知”表示该分支尚未算完。
- **导出清单 PNG**：材料清单载入后，点击左侧图片导出按钮，可将完整清单保存到 `screenshots` 文件夹；长清单采用多栏排版，点击聊天栏文件名即可打开。
