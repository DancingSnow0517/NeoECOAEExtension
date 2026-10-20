# 数据能源工作站升级卡：机器清单与兼容说明

核对源码：`E:/Minecraft Project/DataEnergistics`，`1.21` 分支，提交 `4766d9c52d50693ed3010985307634b753b6eb92`；已执行远端同步。

已注册数据能源集成工作站升级卡、添加合成配方，并接入大型工作站的卡槽、配方筛选、界面和成型外观。原始两帧贴图保留，每帧显示 1 秒。

## 当前机器与设备

| 分类 | 机器或设备 | 与工作站配方的关系 |
| --- | --- | --- |
| 加工 | 数据重组器 `data_reassembler` | 使用物品、流体和通用资源键；部分配方有概率或多个输出，需要完整保留这些语义 |
| 加工 | 数据充能台 `data_charger`、扩展数据充能台 `extended_data_charger` | 数据充能配方还需要数据流和 AE 能量 |
| 加工 | 数据充压一体机 `data_integrated_charger` | 集成压粉、增产、压印和充能；模板、数据流和直接充能分别处理 |
| 加工 | 数据异步处理工厂 `data_asynchronous_processing_factory` | 多通道执行重组配方，也能读取已安装的部分第三方配方；不是另一个独立配方族 |
| 特殊加工 | ME 数位化铁砧、数位化改造研究站 | 具有专用交互或改造逻辑，接入时需分别评估 |
| 数据生产 | 数据提取仪、数据拟生场 | 提取仪记录目标并生产数据流；拟生场读取载体并模拟产出 |
| 能量与资源生产 | ME 太阳能板、ME 数据太阳能、天文观测台、干涉阵列核心 | 根据环境和结构生产能量或资源；观测镜、镜面板、星能波导为配套结构 |
| 加速 | 数据撕裂器 `data_ripper` | AE2 部件，通过数据流加速设备和随机刻 |
| 存储 | 数位化存储仓、数据 IO 端口、数据能量元件 | 存储和转移功能 |
| 网络连接 | 数据均分塔、数据传送锚、ME 光束成型器、巨型 ME 光束成型器、全向 ME 光束成型器 | 网络分配、传送或连接功能 |
| 样板与供给 | 自适应样板供应器、数位化封包样板供应器、数位化供给接口、通用终端 | 配方供给和网络交互；部分同时提供方块与部件形态 |
| 维度设备 | 维度解离总站、极限承载接口 | 维度解离、资源生产和维度传送 |
| 轨道设备 | 耀星抹除装置控制终端、轨道上行信标 | 轨道武器端点和控制 |
| 多方块合成 | 三位一体数位化核心及其数位化存储核心、样板核心、复合输入输出仓、ME 样板缓冲器、信息交换仓 | 大型合成、并行和资源接入系统 |

源码依据：[`DEBlocks.java`](../../DataEnergistics/src/main/java/com/fish_dan_/data_energistics/registry/DEBlocks.java)、[`DEItems.java`](../../DataEnergistics/src/main/java/com/fish_dan_/data_energistics/registry/DEItems.java)、[`DERecipes.java`](../../DataEnergistics/src/main/java/com/fish_dan_/data_energistics/registry/DERecipes.java) 和该模组的中文游戏内指南。

## 升级卡合成

在大型集成工作站合成，输出 1 张数据能源集成工作站升级卡，耗能 **16,384,000 AE**，加载条件为 `data_energistics`。

| 材料 | 数量 | 物品 ID |
| --- | ---: | --- |
| 空白集成工作站升级卡 | 1 | `neoecoae:integrated_working_station_module_empty` |
| ECO - SA 超导处理器 | 16 | `neoecoae:superconducting_processor` |
| 盈能超导锭 | 64 | `neoecoae:energized_superconductive_ingot` |
| 水晶晶格 | 16 | `neoecoae:crystal_matrix` |
| 奇点 | 16 | `ae2:singularity` |
| 数据重组器 | 4 | `data_energistics:data_reassembler` |
| 数据充压一体机 | 4 | `data_energistics:data_integrated_charger` |
| 数曜记忆合金 | 64 | `data_energistics:digisidian_memorize_ingot` |
| 数据处理器 | 128 | `data_energistics:data_processor` |

基础成本与现有工作站升级卡一致；两类加工机器各消耗 4 台，与水晶科技升级卡的双机器成本结构相同。数据充压一体机覆盖普通充能台的能力，异步处理工厂沿用重组配方，因此这两台适合作为主要机器材料。

配方已写入 `src/main/resources/data/neoecoae/recipe/large_integrated_working_station/integrated_working_station_module_data_energistics.json`，内容如下：

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "data_energistics" }
  ],
  "type": "neoecoae:large_integrated_working_station",
  "inputItems": [
    { "item": "neoecoae:integrated_working_station_module_empty", "count": 1 },
    { "item": "neoecoae:superconducting_processor", "count": 16 },
    { "item": "neoecoae:energized_superconductive_ingot", "count": 64 },
    { "item": "neoecoae:crystal_matrix", "count": 16 },
    { "item": "ae2:singularity", "count": 16 },
    { "item": "data_energistics:data_reassembler", "count": 4 },
    { "item": "data_energistics:data_integrated_charger", "count": 4 },
    { "item": "data_energistics:digisidian_memorize_ingot", "count": 64 },
    { "item": "data_energistics:data_processor", "count": 128 }
  ],
  "itemOutput": {
    "id": "neoecoae:integrated_working_station_module_data_energistics",
    "count": 1
  },
  "energy": 16384000
}
```

## 动画资源

- 原图：`assets/neoecoae/textures/item/integrated_working_station_modules/data_energistics.png`，保留用户原始 PNG 字节，尺寸为 16×32。
- 动画：同名 `.png.mcmeta`，帧尺寸 16×16，顺序 `[0, 1]`，每帧 `20 tick`，即正常速度下 1 秒；完整循环为 2 秒。
- 关闭插值，两幅图标直接切换。

## 机器配方兼容范围

安装升级卡后，大型工作站解锁以下配方族；移除卡片后恢复为常规配方。合成升级卡本身是工作站原生配方，不需要提前安装 Data 卡。

| 配方类型 | 消耗处理 |
| --- | --- |
| `data_energistics:data_reassembler` | 保留物品数量、成分条件、流体和自定义 AE 资源键；上游没有逐次加工 AE 费用，适配配方的该项为 0，工作站仍采用自身运行与待机规则 |
| `data_energistics:data_charger` | 保留 `data_flow` 和 `power`，小数 AE 费用向上取整；数据流可由样板提交，也可从 ME 网络获取 |
| `data_energistics:data_integrated_charger` | 保留多物品输入及输出，每次收取上游机器的 1,600 AE |
| `data_energistics:data_charge_press` | 保留多物品输入及输出，按 `fluid_amount` 消耗数据腐蚀液，每次收取 1,600 AE |

数据异步处理工厂复用重组配方，因此相同配方无需重复接入。工作站沿用自己的处理速度、并行、温度与冷却机制。

目前工作站的显示和输出契约支持一种输入流体、一种物品输出及一种流体输出。包含多种流体、多个物品输出、自定义资源输出或动态输出修饰器（如分配量子频率）的配方会拒绝适配，并记录日志，避免漏算资源或生成错误产物。研究站、铁砧、数据拟生场、世界交互和物品直接充能不属于上述四类静态配方。

额外资源沿用现有持久化账本：成本随样板倍数和批次数量同比增加，缺少资源时暂停，已获取的资源归工作站保管，取消或拆机时按原有流程返还。配方去重将数据流和其他非闪电资源计入材料契约，保留不同资源成本的配方。

## Data 绿色外观

安装 Data 卡后，成型控制器使用独立的 `data_energistics_off/on` 模型，绿色指示灯、运行屏幕和界面图集直接使用美工提供的文件。模型覆盖四种水平朝向及运行、停止状态，并保留外部资源包替换入口。输入仓、输出仓和通讯接口保留原始外观。

美工来源：`C:/Users/hnhjy/Desktop/New`。以下六份文件逐字节复制，未重新着色或修改图片内容：

| 美工文件 | 接入位置 |
| --- | --- |
| `data_energistics.png` | `controller_formed_light_compat/data_energistics.png` |
| `data_energistics_on.png` | `controller_formed_light_compat/data_energistics_on.png` |
| `data_energistics_on.png.mcmeta` | 灯光动画配置 |
| `data_energistics_on (1).png` | `controller_formed_screen_compat/data_energistics_on.png` |
| `data_energistics_on.png (1).mcmeta` | 屏幕动画配置 |
| `integrated_working_station_widget.png` | 工作站界面图集 |

美工图集将 Data 放在第 6 列、其他兼容放在第 7 列。`WorkstationTextures` 显式映射这两列，保留既有枚举序列。升级卡仍使用此前提供的独立两帧原图，每帧 1 秒；灯光和屏幕分别使用美工提供的动画配置。

## 验证结果

- 美工替换后，206 项工作站测试全部通过，覆盖发布版 Data Energistics 3.3.5 的四类配方编解码、卡片隔离、资源费用与倍数、重载、持久化、模型状态及图集列映射，并复核彩色电路线条与界面背景对齐。
- 旧版 `data-card-green-frame-a/b.png` 由临时客户端预览界面调用 `BlockRenderDispatcher.renderSingleBlock` 绘制模型，再由 `Screenshot.grab` 保存。新版 `data-workstation-current-front/back/ports.png` 在独立测试世界实际放置完整结构、安装 Data 卡，通过正常世界渲染后截图。运行状态由测试辅助程序设置，用于检查外观与动画；该检查不等于完整手动生产验收。
- 检查 JAR 中的配方、模型、贴图和动画与当前任务文件逐字节一致；本地预览辅助代码及 Data Energistics 本体类未进入成品。
- 成品：[`neoecoae-21.2.7.jar`](../build/verification/data-energistics/neoecoae-21.2.7.jar)。新版客户端截图：[正面](../build/verification/data-energistics/data-workstation-current-front.png)、[背面](../build/verification/data-energistics/data-workstation-current-back.png)、[接口近景](../build/verification/data-energistics/data-workstation-current-ports.png)、[工作站界面](../build/verification/data-energistics/data-workstation-current-ui.png)。本轮截图与打包使用包含当前工作区渲染修改的独立快照。
