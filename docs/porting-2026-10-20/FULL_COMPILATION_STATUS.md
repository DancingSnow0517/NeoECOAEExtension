# Forge 1.20.1 完整主源码编译检查点

日期：2026-10-21。工程：`E:\NeoECOAEExtension-1.20.1`，分支：`port/forge-1.20.1`。

后续已完成真实业务客户端进入世界及新的资源/配方修复，见 [RUNTIME_STARTUP_STATUS.md](RUNTIME_STARTUP_STATUS.md)。本文件保留首次恢复零错误编译时的历史状态及工件哈希。

## 已确认结果

完整 main 源码已达到零编译错误，并完成 JAR 打包。没有批量排除 main 源码，没有加入空业务实现，也没有关闭核心功能以绕过编译。冻结的 main 输入、业务算法和此前批准的范围保持不变。

| 验证层级 | 本轮结果 | 证据 |
| --- | --- | --- |
| 完整 main `compileJava` | 0 errors、82 warnings；强制重新执行通过 | `validation/compile-package-final.log` |
| JAR 打包 | 成功；926 个主源码文件的顶层类全部存在 | `porting/package-validation.json` |
| 字节码与包内容 | 1,524 个类全部 major 61；5 份配置的 58 个 Mixin、插件、refmap、AT、嵌套依赖及必要资源齐全 | 同上 |
| 平台/契约 Java 测试 | 95 + 6 = 101 项，0 失败、0 错误、0 跳过 | `porting/validation-state.json` |
| Python 资源转换/材料账本测试 | 8 项通过 | `python -m unittest discover -s tools/porting -p 'test_*.py' -v` |
| 原全量业务测试 | `compileTestJava` 失败；显示 1,000 errors，已到编译器显示上限，测试尚未运行 | `validation/verify-business-tests.log` |
| 完整业务客户端/专服启动 | 本轮未运行 | 不沿用最小入口检查点的启动结论 |
| 真实交互、普通 Forge 安装 | 尚未验证 | 不作为本轮验收成果 |

最终执行命令（Gradle 始终串行）：

```powershell
.\build-local.ps1 compileJava jar portingPlatformTest portingContractsTest --offline --rerun-tasks --console=plain '-Dorg.gradle.jvmargs=-Xmx6G -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
python tools/porting/audit_package.py
python -m unittest discover -s tools/porting -p 'test_*.py' -v
python tools/porting/record_validation.py --log validation/compile-package-final.log
```

最终 Gradle 日志为 `BUILD SUCCESSFUL in 34s`，13 个任务全部重新执行。不是一次只命中缓存的检查，也不是完整 `build` 已通过；后者仍受旧业务测试源码的迁移工作阻塞。

## 本轮适配内容

### 主体与 API

- main 仍固定为 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`。参考仓库已更新远端，未把远端更新自动混入移植基线。
- Java 21 集合访问、反向视图和范围调用使用 Java 17 实现；新增测试验证空集合异常、空元素及反向视图的双向修改语义。
- 对齐 Forge 1.20 的激活、能力、tick、世界生成、物品属性、NBT、配方和原生 AE2 库存/数组 API。NBT 与 Component 使用实际结构化编解码，不以显示字符串替代保存数据。
- 大工作站仍按原成型状态拒绝外部 FE 接入，并保留缓存能力适配器的门禁检查；批次、温度、冷却和超频参数未为编译而改写。
- 修正 AE2/AAE FastPath 的 NBT 注入参数，以及原生 ME 箱过滤器的目标类名；AE2/AAE 原生任务的暂停字段、保存和状态同步有实际实现。
- 1.20 Mixin 0.8.5 不支持单个 injector 的 `order`。原来需要提前的四个 CraftingService 挂点及强制开始操作放入 priority 1500 的伴随 Mixin；诊断 RETURN 挂点放入 priority 500 的伴随 Mixin。业务体仍由原 Mixin 中的明确接口方法执行，其余挂点保持原优先级。真实第三方优先级组合未验证。
- Forge 开发配置与发布清单登记全部 5 份 Mixin 配置；AdvancedAE/ExtendedAE 的存在性插件在未安装附属时阻止引用对应外部类型的挂点应用到原生 AE2 类。AE2LT/Thunderbolt 继续使用原有逐目标资源检测。

### 界面与渲染

- 保留 main 的界面业务类和 JSON；`platform.ui.runtime` 在项目命名空间内实现所用的 retained UI API，由 LDLib1 原生 Widget、菜单、槽位、流体传输和场景承载。没有伪装成 LDLib2/Taffy 上游包。
- 实现所用的绝对/百分比、flex、grid 布局，LSS 选择器优先级和显式内联值，资源包样式读取、九宫格纹理、滚动、原生输入、标签、按钮、真实背包槽、幽灵槽、数据绑定和 RPC。
- 新测试覆盖捕获/冒泡只分发一次、阻止默认操作、tick 不重复冒泡、离开悬停、裁剪/遮挡命中、祖先隐藏时禁用原生槽位、幽灵放入不消耗原物、横/纵滚动和闭菜单动作拒绝。
- 流体保留原生同步与操作，绘制顺序调整为先流体后刻度；数量标签恢复右下角、4.5px 字体和真实数量绑定。资源重载使后续构造的界面读取当前资源包样式。
- AE2 样板供应器菜单使用 NeoECOAE 的 DeferredRegister；沿用目标版本的 locator/open 协议，避免新版 builder 调用产生非法命名空间或重复注册。客户端初始化移至基础内容初始化之后。
- 用原版区块重建任务的实际入口补入固定几何渲染，缓冲区生命周期仍由原生编译器管理。光照、滚动条、CPU 图标、槽位高亮及 EditBox 的实际绘制重载均按 1.20 接口适配。

这些是源码、静态包及代表性测试证据，尚不能证明全部界面的视觉、字体尺寸、缩放、菜单槽位重绑或真实手势都已与来源等效。文本自然宽度仍使用近似度量，需结合客户端字体和服务端布局继续验收。

### 结构终端

- 只从批准的旧版提交 `a6b3d776d677c3093954081d40d4871592864c3b` 恢复结构终端及必要依赖闭包，31 个来源路径与哈希记入 `porting/structure-terminal-imports.json`。
- `NELDLibUis`、`NELDLibStateCodecs` 只保留终端方法，没有导入旧工程整套机器业务。
- 三类系统控制器接入终端 host 契约，继续调用 main 的放置服务、计划与会话。终端预览计数/状态通过独立 NBT 子项保存；读档不恢复没有玩家与会话的建造过程。main 原有长度/镜像字段仍是其权威来源。
- 拆除使用方块普通破坏/掉落钩子并收集对应新掉落；实际权限、库存、掉落和大工作站交互仍待运行验收。
- 机器 UI 的自动搭建按钮继续按批准范围移除；保留结构终端提供的操作入口。

## 产物与范围

JAR：`build/libs/neoecoae-20.6.0-dev.jar`，6,736,567 字节。

SHA-256：`919ca62c2edbddc8959e27211dd8eea20cd386536e1524ffd07fcf195c54871b`。

大集成仍仅常规、AdvancedAE、ExtendedAE、AE2LT 四类，ExtendedAE Plus 的其他已批准联动保留。原范围裁剪清单的 182 个排除文件均未重新出现。包内没有项目伪造的 `com.lowdragmc.lowdraglib2` 或 `dev.vfyjxf.taffy` 类；Registrate 和 MixinExtras 仍按 JarJar 打包。

## 后续验收

原 `src/test/java` 中的测试仍使用新版 Component、RecipeHolder、List 输出 API、LDLib2 及已裁剪联动 fixture，不能把这批编译失败描述成测试通过。本轮没有删除有效断言、排除这些测试源码或添加伪造上游实现来消除报错。应在后续阶段按批准范围迁移测试，再执行完整测试任务。

还需在完整业务树上运行开发客户端/专服，确认 Mixin 变换、注册、四类附属组合、UI/JEI/EMI 交互、结构终端、EAE 机内配方、存储恢复与数量守恒，并执行完整 datagen 和普通 Forge 安装验收。历史最小入口的启动成功不能代替这些结果。
