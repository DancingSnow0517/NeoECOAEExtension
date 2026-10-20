# 1.20.1 移植实施进度

本文件记录实际实施状态；初始调查文档及 `evidence/` 原始证据保留其历史含义。工作目录为 `E:\NeoECOAEExtension-1.20.1`，分支 `port/forge-1.20.1`。不修改参考工程的业务源码，不自动推送。

## 当前结论

最小 Forge 环境、锁定 main 主体导入、范围裁剪和第一批平台适配已经落地。当前业务源码尚不能完整编译，不能启动或作为可用版本交付。上次完整编译为 **1,957 errors、8 warnings**，日志 `validation/compile-full-inventory.log`；较早的 1,000 是 javac 显示上限，不能用于计算错误减少比例。

`portingPlatformTest` 的 15 项和 `portingContractsTest` 的 4 项共 **19 项隔离测试通过，0 失败、0 跳过**。这只验证指定生产适配类和 Java17 helper，未验证完整 main、业务测试、游戏交互或 UI 视觉效果。机器可读检查点见 `porting/validation-state.json`。

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
5. LDLib1 UI 桥接目前覆盖 `FluidHatchUI` 这一条垂直切片：保留原 JSON、实际布局节点、真实流体槽及 36 个玩家背包槽。其余 UI 尚未迁移，标题文字溢出行为仍需补齐；不能据此声明任意 LDLib2 UI 可兼容。
6. 导入结构终端 18 个来源文件，增加实际物品注册及生成模型入口；从固定来源提取 6 个语言文件共 216 个语言键，并纳入 datagen。终端后端契约、完整 UI 和大工作站支持尚未接通。
7. 更新指南中的结构终端入口和四类大集成说明，清理机器 UI 搭建入口文本。指南写的是目标行为，终端功能尚未验收。根包和经典资源包 `pack_format` 改为 15，资源目录和条件格式尚待转换。

独立平台测试任务编译实际生产适配类；未批量排除 main 源码。测试 JVM 没有 ModLauncher 变换器，少量 fixture mock 仅隔离启动器边界，不能替代 Forge 内运行。

## 后续必做

- 1.21 数据组件改为 1.20 NBT；迁移配方输入、serializer、数据生成和资源格式。
- 适配 AE2 15 合成、菜单、存储与执行 API，恢复完整业务编译及已有业务测试。
- 按实际组件补齐 LDLib1 UI 桥接并逐界面迁移；验证槽位、同步、手势、缩放和多人行为。
- 结构终端接入当前共享搭建服务，补齐预览/拆除和大工作站支持；拆除须保护实际物品与库存。
- 完成 EAE 大集成可执行配方，验证四附属独立 profile 和组合，不能禁用或留空代替完成。
- 转换 1.21 单数资源目录和 `neoforge:mod_loaded` 条件，检查 GuideME、Mixin AP/refmap、AT 与发布 JAR。
- 重新验证业务客户端/专服、普通安装、真实功能交互、持久化恢复及数量守恒。验收清单见 `EXECUTION_AND_ACCEPTANCE.md`。
