# Forge 1.20.1 完整业务客户端启动检查点

日期：2026-10-21。工作目录：`E:\NeoECOAEExtension-1.20.1`。

后续的放置崩溃与 UI 绘制问题已另行修复并扩大验证，详见 [CLIENT_CRASH_AND_UI_FIXES.md](CLIENT_CRASH_AND_UI_FIXES.md) 和 `porting/client-crash-ui-validation.json`。以下保留此前的启动检查点，工件哈希、类数量和测试数量也是该历史检查点的数据。

完整业务树已在真实 Forge 开发客户端进入单人世界。基础环境和四附属组合均有玩家进入世界的日志与游戏帧缓冲截图；组合环境另外验证了小工作站菜单打开、显示、关闭，以及所有维度正常保存退出。

## 已确认的结果

| 检查 | 结果与证据 |
| --- | --- |
| 基础环境进入世界 | `validation/business-client-03.log`；截图 `validation/runtime-startup-20261021/base-world.png` |
| 四附属组合进入世界 | `validation/business-runtime-final.log` 明确记录 AdvancedAE、ExtendedAE、ExtendedAE Plus、AE2LT 全部加载 |
| 联动配方读取 | 两条 AAE 反应配方、一条 EAE 切割配方、三档 AE2LT 存储盘装配配方均在真实服务端 RecipeManager 中存在 |
| 工作站菜单 | 通过真实服务端方块交互、LDLib 菜单与客户端网络路径打开；截图 `validation/runtime-startup-20261021/combined-workstation.png`；随后关闭回到世界 |
| 保存与退出 | 组合客户端正常退出；日志记录四维度全部保存，Gradle `BUILD SUCCESSFUL` |
| 主源码与打包 | 0 errors、82 warnings；928 个主源码对应类全部入包，1,526 个类均为 Java 17 字节码 |
| 专项测试 | 95 + 6 = 101 项 Java 测试，0 失败、0 跳过；10 项 Python 资源转换与账本检查通过 |

最终串行命令：

```powershell
.\build-local.ps1 compileJava jar portingPlatformTest portingContractsTest runClient -PportingProfile=combined -PclientSmoke=true -PclientSmokeMenus=true --offline --console=plain '-Dorg.gradle.jvmargs=-Xmx6G -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
python tools/porting/audit_package.py
python -m unittest discover -s tools/porting -p 'test_*.py' -v
python tools/porting/record_validation.py --log validation/business-runtime-final.log
python tools/porting/record_runtime_startup.py
```

日志为 `BUILD SUCCESSFUL in 2m 11s`，17 个任务中 6 个执行、11 个已是最新输出。专项 Java 测试实际重新运行。本轮没有重新执行完整业务 `test`。

## 修复内容

- AE2 15 没有新版 `toggleScheduling` 方法。改由 ECO 自有菜单动作和工具栏按钮提供服务端权威的暂停/继续，保留原有 ECO 任务暂停状态。
- 样板编码终端的动作注册改在构造器结束时执行，兼容目标 Mixin 0.8.5；此前的后续注册错误为这类 Mixin 失败的连锁结果。
- 计算系统复合模型和冷却液桶改用 Forge 1.20 模型加载器。内置 ECO 驱动器模型接入旧版实际加载队列及依赖缓存；明确声明空/满驱动器模型依赖。
- 工作站使用的三张 AE2 通用贴图在 AE2 15 中不存在；保留其预期图案并导入 ECO 自己的命名空间，官方来源提交与哈希见 `porting/runtime-resource-adaptation.json`。
- 104 份进度资源转换为目标版本的物品数组/独立 tag 条件。AAE 配方使用 `#c` 通道与 AE2AddonLib `fluidStack` NBT；EAE 切割配方补足输入数量默认值；AE2LT 自有 serializer 的物品/流体输出恢复 `id` 字段。数量、能耗及材料账本保留。
- 新增显式启用、仅开发环境注册的客户端启动探针。普通启动不自动创建世界；探针使用新建隔离世界、真实方块菜单路径和游戏自身截图接口。

资源修订保留原始来源哈希和逐次修改记录；没有改变锁定 main 基线或恢复范围外联动。

## 验收边界

全量业务测试仍未通过测试源码编译；上一轮实测为 29 个文件、115 个错误，见 `validation/progress-check-20261021.log`。原测试迁移改动保留，本轮未借助测试排除、空实现或关闭业务功能来启动客户端。

组合环境还存在第三方可选内容的数据错误：AAE 未安装 AE2WTLib 时的无线终端配方/Curios 标签，以及 EAE 未安装 ProjectE 时的 EMC 接口掉落表。它们没有阻断进入世界，保留在日志中；本工程自有配方与模型的前述加载错误已经消除。

Windows 窗口控制插件本轮无法连接，菜单通过游戏 API 的真实服务端交互与网络路径打开，截图来自实际客户端帧缓冲。这证明菜单可加载显示，尚不覆盖真实鼠标手势、库存数量守恒、多人同步、全部 UI 或四种大集成的完整机器运行。

独立业务专服、普通 Forge 安装、完整 datagen、结构终端材料守恒、持久化故障恢复及完整功能验收仍待完成。本轮结果不能替代这些验收。

此前检查点工件：`build/libs/neoecoae-20.6.0-dev.jar`，6,747,062 字节；SHA-256：`dcd0671fc1b49e3cca042f99c452fe6f43b99caac4fd2e40ad38f6e901d2722b`。最新工件见后续修复记录。

普通开发启动：

```powershell
.\build-local.ps1 runClient -PportingProfile=combined --offline
```
