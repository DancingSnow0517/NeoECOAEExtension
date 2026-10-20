# 1.20.1 移植实施进度

本文件记录实际实施状态；初始调查文档及 `evidence/` 原始证据保留其历史含义。工作目录为 `E:\NeoECOAEExtension-1.20.1`，分支 `port/forge-1.20.1`。不修改参考工程的业务源码，不自动推送。

## 当前结论

最小 Forge 环境、锁定 main 主体导入、范围裁剪及首批平台/配方/NBT 适配已经落地。当前业务源码尚不能完整编译，不能启动或作为可用版本交付。最新完整编译为 **1,505 errors、12 warnings**，日志 `validation/compile-data-checkpoint-final.log`。前两个检查点分别为 1,957 和 1,560 errors，均使用 10,000 的显示上限；更早的 1,000 是截断上限，不能用于计算进度百分比。

`portingPlatformTest` 的 46 项和 `portingContractsTest` 的 4 项共 **50 项隔离 Java 测试通过，0 失败、0 跳过**，另有 **8 项 Python 资源转换/账本检查通过**。这些验证指定生产适配类、真实附属配方对象、原生 Forge/AE2 编解码及静态资源，未验证完整 main、全业务测试、游戏交互或 UI 视觉效果。机器记录见 `porting/validation-state.json`、`porting/data-resource-validation.json`；本轮细节见 [DATA_BOUNDARY_STATUS.md](DATA_BOUNDARY_STATUS.md)。

## 输入与范围

- 主体固定为 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`。更新参考远端不改变已批准基线；实际原样导入 3,818 个文件，见 `porting/main-import.json`。
- 结构终端单独锁定 `a6b3d776d677c3093954081d40d4871592864c3b`，旧版代码只用于终端及其必要契约，见 `porting/source-lock.json`、`structure-terminal-import.json`。
- 大集成仅常规、AAE、EAE、AE2LT 四类；AE2LT 三类配方归同一种机型。ExtendedAE Plus 的其他联动保留，不增加其大集成配方。EAE 实际可执行配方仍是必做项。
- 保留结构终端，移除机器 UI 的自动搭建按钮及专用界面，保留共享搭建服务。存储、计算、合成系统各自的 L4/L6/L9 保留。

## 最小环境检查点

提交 `e24ea3b` 使用 Java17.0.10、Gradle8.12.1、Legacy Forge 插件2.0.148、MC1.20.1/Forge47.4.28；基础依赖 AE2 15.4.11、GuideME20.1.15、LDLib1.0.52.a。Registrate MC1.20-1.3.11 和 MixinExtras0.5.3 通过 JarJar 打包。

该检查点的 `build` 通过，真实开发客户端到达标题界面，开发专服完成世界初始化，两者均正常退出。记录见 `porting/environment-validation.json`。它当时只有最小入口，业务单测为 `NO-SOURCE`。后续导入提交 `9eaf7de` 已替换该入口；这些旧成功记录不能证明当前业务树已能运行，也不能证明普通 Forge 安装验收通过。

## 已实施的适配

1. 按冻结范围删除附属实现、入口、Mixin、独立外观资源及专属配方；`scope-cleanup.json` 记录 182 个删除文件与来源哈希。大集成保留四类、atlas 原列 0/1/2/4；工作站 blockstate 删除 64 个排除机型变体，共用 atlas 保留。
2. 注册入口改用 Forge，创建原生自定义 Registry，接入 Registrate 的 Forge 泛型。`ForgeBlockCapabilities` 发布真实方向能力、处理缓存/失效/复活与成型切换，邻居查询不强制加载区块。
3. LDLib1 管理字段/持久化/同步/RPC 接入项目适配层。初次区块同步恢复原增量脏标记；C2S RPC 使用专用通道并校验玩家、菜单、距离和区块。Registry accessor 延迟取已创建的原生 Registry。
4. 业务网络改为 Forge `SimpleChannel`，五种业务包按明确方向注册；项目 codec/context 包装替代新版网络类型。JEI bookmark 数量有上限校验。
5. LDLib1 UI 桥接目前覆盖 `FluidHatchUI` 这一条垂直切片：保留原 JSON、实际布局节点、真实流体槽及 36 个玩家背包槽；标题改用 LDLib1 的实际悬停滚动文字渲染器，客户端字体在绘制时延迟访问。其余 UI 尚未迁移，视觉和交互仍待游戏内验收；不能据此声明任意 LDLib2 UI 可兼容。
6. 导入结构终端 18 个来源文件，增加实际物品注册及生成模型入口；从固定来源提取 6 个语言文件共 216 个语言键，并纳入 datagen。终端后端契约、完整 UI 和大工作站支持尚未接通。
7. 更新指南中的结构终端入口和四类大集成说明，清理机器 UI 搭建入口文本。指南写的是目标行为，终端功能尚未验收。两个资源包 `pack_format` 改为 15；621 份原始数据文件已做目录/条件/标签/配方结构转换，最终保留 428 份、裁掉 193 份，来源与修订哈希见 `porting/data-resource-conversion.json`。还未执行完整 datagen 或游戏数据重载。
8. 冷却、小工作站、大工作站改用 1.20 配方 serializer 与只读 Container 输入；输入限制分别保留 9/12 项，独立同步整数产物数量以避免 byte Count 截断，保留流体/NBT 与能耗。AAE 与 AE2LT 接真实 1.20 配方对象，大工作站仍保留来源、去重、精确数量与模块门禁。
9. EAE 缺失的水晶装配器配方已落成 ECO 自有类型及实际匹配契约，输入/产出和 2,000 AE 沿用 main，常规模块不能解锁。EAE 升级卡中三个不存在的 1.21 物品按文档映射到 1.20 可用物品，数量和总能耗保留。完整机器执行仍待编译恢复后验收。
10. 增加 ECO 自有存储盘拆解元数据，保留物品使用动作中的服务端/空盘检查。9 条现有静态装配/拆解账本已核对；闪电盘原来分别投入 1/2/4 个组件却返还 4/16/64 个，现修正为返还 1/2/4，保持原返还物品种类，不额外返还加工耗材。
11. 普通盘内容、模糊模式、无限盘迁移标记、恢复回执、控制器域和精确 CPU 恢复物品改接 1.20 NBT；记忆卡自动导出侧向设置使用实际 AE2 15 NBT 钩子。存储编码在实际 Forge key registry 上测试 long/NBT 往返与未知 key 拒绝；业务存储异常状态及完整恢复流程仍待验证。

独立平台测试任务编译实际生产适配类；未批量排除 main 源码。测试 JVM 没有 ModLauncher 变换器，少量 fixture mock 仅隔离启动器边界，不能替代 Forge 内运行。

## 后续必做

- 继续处理 FastPath 的组件状态语义、BE/AE2 保存接口、未知数据的业务只读状态及其他持久化 API；不能把本轮 NBT helper 测试等同于完整存储恢复验收。
- 适配 AE2 15 合成、菜单、存储与执行 API，恢复完整业务编译及已有业务测试。
- 按实际组件补齐 LDLib1 UI 桥接并逐界面迁移；验证槽位、同步、手势、缩放和多人行为。
- 结构终端接入当前共享搭建服务，补齐预览/拆除和大工作站支持；拆除须保护实际物品与库存。
- 验证 EAE 配方在实际大集成中的消耗/产出、四附属独立 profile 和组合；不能用本轮适配测试代替机内运行证据。
- 恢复完整 datagen 并重新生成装配/拆解资源（包括 main 中尚未生成的 Bulk Item 三等级），核对剩余未注册内容的外观/指南资源；检查 GuideME、Mixin AP/refmap、AT 与发布 JAR。
- 重新验证业务客户端/专服、普通安装、真实功能交互、持久化恢复及数量守恒。验收清单见 `EXECUTION_AND_ACCEPTANCE.md`。
