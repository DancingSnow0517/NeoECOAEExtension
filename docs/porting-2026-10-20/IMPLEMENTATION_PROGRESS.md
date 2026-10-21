# 1.20.1 移植实施进度

本文件记录实际实施状态；初始调查文档及 `evidence/` 原始证据保留其历史含义。工作目录为 `E:\NeoECOAEExtension-1.20.1`，分支 `port/forge-1.20.1`。不修改参考工程的业务源码，不自动推送。

## 当前结论

截至 2026-10-21，已恢复 **完整 main 源码编译，0 errors、82 warnings**，并完成 JAR 打包。最终命令通过 `build-local.ps1` 使用 Java 17 串行执行 `compileJava jar portingPlatformTest portingContractsTest --offline --rerun-tasks`，日志 `validation/compile-package-final.log`：34 秒、13 个任务全部重新执行成功。包内逐一找到 926 个非 package-info 主源码文件对应的类，共 1,524 个类，均为 Java 17 字节码；5 份 Mixin 配置、58 个已声明 Mixin、存在性插件、refmap、AT、嵌套依赖和必要界面资源检查通过。记录见 `porting/package-validation.json`。

`portingPlatformTest` 的 95 项和 `portingContractsTest` 的 6 项共 **101 项隔离 Java 测试通过，0 失败、0 跳过**，另有 **8 项 Python 资源转换/账本检查通过**。本轮增加 18 项布局、事件、槽位、滚动、资源包样式、同步和 Java 17 集合语义验证。完整 `test` 已另行试跑，但原测试源码仍引用 1.21 API 及已裁剪联动，停在 `compileTestJava`，达到 1,000 条错误显示上限，尚无全业务测试运行结果。**这不是完整 `build`、业务客户端/专服、真实交互或普通安装验收通过。** 本轮细节见 [FULL_COMPILATION_STATUS.md](FULL_COMPILATION_STATUS.md)，机器记录见 `porting/full-compilation-adaptation.json`、`porting/validation-state.json`。此前 1,454 errors、83 项测试仅是历史检查点，保留在 [PERSISTENCE_AND_FASTPATH_STATUS.md](PERSISTENCE_AND_FASTPATH_STATUS.md)。

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
5. main 中保留的 LDLib2/Taffy 调用改由项目命名空间 `platform.ui.runtime` 适配，底层使用 LDLib1 原生菜单、Widget、槽位、流体传输和场景组件。补齐实际布局、样式、资源包读取、数据绑定、RPC、事件捕获/冒泡、滚动、背包槽、幽灵槽及 JEI/EMI 包装，未创建伪装上游的包名。保留界面业务类和 JSON；单测已覆盖代表性行为，仍需逐界面游戏内验收，不能据此声明任意 LDLib2 UI 都等效兼容。
6. 结构终端物品、完整界面、预览/拆除及 host 契约现已接入当前共享搭建服务；31 个旧版必要来源文件与原始哈希记入 `porting/structure-terminal-imports.json`。三个系统控制器持有独立终端预览状态并接入保存入口，搭建会话不在读档时无主恢复。机器 UI 的自动搭建按钮仍移除。真实材料守恒、权限、拆除掉落和大工作站交互仍待游戏内验收。
7. 更新指南中的结构终端入口和四类大集成说明，清理机器 UI 搭建入口文本。指南写的是目标行为，终端功能尚未验收。两个资源包 `pack_format` 改为 15；621 份原始数据文件已做目录/条件/标签/配方结构转换，最终保留 428 份、裁掉 193 份，来源与修订哈希见 `porting/data-resource-conversion.json`。还未执行完整 datagen 或游戏数据重载。
8. 冷却、小工作站、大工作站改用 1.20 配方 serializer 与只读 Container 输入；输入限制分别保留 9/12 项，独立同步整数产物数量以避免 byte Count 截断，保留流体/NBT 与能耗。AAE 与 AE2LT 接真实 1.20 配方对象，大工作站仍保留来源、去重、精确数量与模块门禁。
9. EAE 缺失的水晶装配器配方已落成 ECO 自有类型及实际匹配契约，输入/产出和 2,000 AE 沿用 main，常规模块不能解锁。EAE 升级卡中三个不存在的 1.21 物品按文档映射到 1.20 可用物品，数量和总能耗保留。完整机器执行仍待运行验收。
10. 增加 ECO 自有存储盘拆解元数据，保留物品使用动作中的服务端/空盘检查。9 条现有静态装配/拆解账本已核对；闪电盘原来分别投入 1/2/4 个组件却返还 4/16/64 个，现修正为返还 1/2/4，保持原返还物品种类，不额外返还加工耗材。
11. 普通盘内容、模糊模式、无限盘迁移标记、恢复回执、控制器域和精确 CPU 恢复物品改接 1.20 NBT；记忆卡自动导出侧向设置使用实际 AE2 15 NBT 钩子。存储编码在实际 Forge key registry 上测试 long/NBT 往返与未知 key 拒绝；业务存储异常状态及完整恢复流程仍待验证。
12. 无限存储接入 1.20.1 `DimensionDataStorage` 的反序列化函数/构造函数和 `SavedData.save(File)`。保留 main 的 v3 快照、旧日志重放、迁移回执、恢复分配及原文件保护。服务端保存周期同步写临时压缩文件并原子替换，写入或清理失败保持待重试状态；不会每次物料转移都写盘。原子替换不支持时明确失败，不退回覆盖旧文件。大规模世界保存耗时仍待实际测量。
13. FastPath 可复用物品状态改为原生 NBT，物品比较包含 Forge capability 一致性；原版专用字段变化不作为自定义线性计数器。保留二次证明、耐久与催化剂组合、返回物和溢出规则；1.20 原生配方直接使用完整 3×3 网格，返回物不再套用新版压缩网格坐标。
14. 精确虚拟账本、普通/精确 CPU 库存、执行计划和工作站批次的编解码调用改接 AE2 15；BE、线程及 CPU 的保存入口改为目标签名，任务状态通知改用 AE2 15 原生网络处理器。AE2/AAE 原生任务补入暂停状态、NBT 和状态包桥接，FastPath NBT 注入参数按实际 1.20 方法签名修正。独立账本和过滤器已有测试；完整 CPU/BE、终止任务账本及第三方 Mixin 链仍未运行验收。
15. 冷却余数按 Forge 原生 FluidStack NBT 保存，保留流体标签及按配方隔离的分数；存储筛选使用实际注册 key 解码，缺失通道/物品时仍拒绝放行，不把已有未知过滤项当作空过滤器。
16. 补齐 1.20 的区块固定几何渲染入口、光照、滚动条/图标和文本绘制接口。大工作站模式不通过 FE 适配绕过成型后的输入门禁。原 `order=500/2000` 的敏感 Mixin 挂点拆成高/低优先级伴随 Mixin，其余逻辑保留原优先级；第三方任意优先级组合仍需在真实加载器中确认。
17. 大工作站样板供应器菜单使用 NeoECOAE 自己的 DeferredRegister，沿用 AE2 原生 locator/open 网络协议，避免 1.20 builder 强加 AE2 命名空间及重复排队。客户端初始化移至基础内容与联动初始化之后。
18. Forge 开发配置和发布清单登记全部 5 份 Mixin 配置，补上附属和终端大数量配置的加载入口。AdvancedAE/ExtendedAE 配置通过资源存在性插件判断附属是否存在，避免基于原生 AE2 类的挂点在缺少附属时引用外部类型。

独立平台测试任务编译实际生产适配类；未批量排除 main 源码。测试 JVM 没有 ModLauncher 变换器，少量 fixture mock 仅隔离启动器边界，不能替代 Forge 内运行。

## 后续必做

- 将原全量测试源码的新版 API、fixture 与范围断言迁至目标版本；不能通过删除有效断言或恢复未批准联动来制造测试通过。当前独立存储/状态测试不等同于整个 CPU/BE、未知数据只读状态及完整恢复链验收。
- 在真实 Forge 加载器中验证 Mixin 注入、初始化、菜单注册、保存/恢复入口及四类附属；编译器和静态包检查不执行 Mixin 变换。
- 逐界面验证 LDLib1 桥接的实际布局、字体、槽位、同步、手势、缩放和多人行为。
- 验证结构终端预览/搭建/拆除与大工作站支持；拆除须保护实际物品与库存。
- 验证 EAE 配方在实际大集成中的消耗/产出、四附属独立 profile 和组合；不能用本轮适配测试代替机内运行证据。
- 恢复完整 datagen 并重新生成装配/拆解资源（包括 main 中尚未生成的 Bulk Item 三等级），核对剩余未注册内容的外观/指南资源；检查 GuideME、Mixin AP/refmap、AT 与发布 JAR。
- 重新验证业务客户端/专服、普通安装、真实功能交互、持久化恢复及数量守恒。验收清单见 `EXECUTION_AND_ACCEPTANCE.md`。
