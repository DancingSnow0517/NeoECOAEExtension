# GuideME 更新说明与验证

适用：Minecraft 1.21.1 / NeoForge，Neo ECO AE Extension 21.2.9。

本轮沿用原有中英文各 10 页，在现有段落、表格和操作说明中就地修订。保留原页面路径、主要导航、结构场景、截图和章节顺序。

## 已落实的修改

| 原有页面 | 修订内容 |
| --- | --- |
| 盈能水晶母岩 | 修正品质与生长概率的关系；水晶修复器首级输入改为水晶方块，并补充该方块的指南归属 |
| 集成工作站 | 加工费用改为 AE；输入、输出储罐各为 64,000 mB |
| 存储系统 | 明确矩阵字节容量与每个能量元件容量；简要补充 IO 方向、27 格过滤、两个转移开关及无限存储启用与退出条件 |
| 计算系统 | 线程核心改为 4 / 16 / 64；修正交换组频道、8 台极限条件及旧冷却门槛；简要补充规划诊断与组件标记 |
| 合成系统 | 补充总线页数、通讯接口检索及插入替换；说明频道分组和虚拟组固定加工费 |
| 大型集成工作站 | 可见样板行数改为 7；区分界面温区 4 / 6 / 9 与冷却等级 2 / 6 / 9 |
| 兼容存储矩阵 | 保留现有表格，补充量子矩阵、装配拆解及其他资源系列的简短说明；原生工作站配方费用改为 AE |
| 多方块自动搭建 | 采用简短的功能说明，补充 JEI / EMI 的分层、长度、成型外观与左右键切换 |

入口页保持原样。合成控制器保留原图文流程，标题改为功能说明。原有三个结构文件、六张合成教程截图继续复用。

数字奇点按全网盘数分档的原指南表格保持不变；[Bigger AE2 开发说明](BIGGER_AE2_INTEGRATION_ZH_CN.md)同步修正旧的固定 25 AE/t 表述。各模组资源单位、配方和数值依据当前实现校对。

## 源码依据

- [工作站](../src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOIntegratedWorkingStationBlockEntity.java)：AE 结算及储罐容量。
- [等级](../src/main/java/cn/dancingsnow/neoecoae/api/ECOTier.java)、[网络分组](../src/main/java/cn/dancingsnow/neoecoae/multiblock/network/NELogicalNetworkManager.java)：线程、容量和交换组。
- [IO 与过滤](../src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOMachineInterfaceBlockEntity.java)、[无限存储](../src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECOInfiniteStorageModeController.java)：模式、过滤、成员和迁移资格。
- [计算网络](../src/main/java/cn/dancingsnow/neoecoae/multiblock/cluster/NEComputationNetworkCluster.java)、[合成网络](../src/main/java/cn/dancingsnow/neoecoae/multiblock/cluster/NECraftingNetworkCluster.java)：极限条件与虚拟运行费用。
- [温区标签](../src/main/java/cn/dancingsnow/neoecoae/gui/workstation/WorkstationTextures.java)、[样板视口](../src/main/java/cn/dancingsnow/neoecoae/gui/workstation/WorkstationPatternScroll.java)：温区名称与 7 行视口。
- [规划器说明](ECO_PLANNER_ZH_CN.md)、[加工时间与网络 FE](WORKSTATION_PROCESSING_TIME_AND_FE_ZH_CN.md)：既有实现的配套说明。

## 验证范围

检查中英文页面、物品归属、相对链接、结构与图片引用，以及源码、复制资源和 JAR 的内容一致性。在实际客户端使用项目当前 GuideME 21.1.1 检查页面编译、渲染、链接跳转与搜索。

验证完成：中英文共 20 页编译及实际显示均通过，页面错误为 0；10 项搜索、2 项入口链接跳转和 2 项自动搭建页滚动检查通过。已检查更新后的中文材料消耗与搭建中断说明截图。

67 个本地引用、3 个结构文件和 6 张原有截图检查通过。29 个指南文件在源目录、复制目录及 JAR 中逐字节一致，JAR 包含 20 页。编译、资源处理、JAR 构建及差异格式检查通过。

构建产物为 `.gradle/guideme-update-20261024/build/libs/neoecoae-21.2.9.jar`；客户端记录及截图位于 `.gradle/guideme-update-20261024/client/`。本轮未改动玩法实现，工作区原有循环求解与工作站遮挡修改的文件哈希保持一致。
