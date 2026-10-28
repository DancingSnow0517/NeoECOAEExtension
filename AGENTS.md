# AGENTS.md

本文件适用于本仓库的协作与自动化代理。项目介绍见 [README_ZH_CN.md](README_ZH_CN.md)，附属接入见 [中文 API](docs/API_ZH_CN.md) / [English API](docs/API.md)。`docs/` 只维护 API；不新增开发流程、机制介绍、性能报告或历史记录。玩法内容维护在 `guidebook/`，版本更新维护在 `CHANGELOG.md`。

## 开始工作

- 先检查 `git status --short --branch` 与相关 diff，保留已有和无关修改。确认当前仓库、分支、Minecraft/加载器版本，不套用其他版本工作区的结论。
- 本仓是 Minecraft 1.21.1 / NeoForge、Java 21 项目。实际版本、依赖与任务分别以 [gradle.properties](gradle.properties)、[build.gradle](build.gradle)、[settings.gradle](settings.gradle) 为准，不在文档中维护另一份依赖版本表。
- Mod 源码仓库统一位于 `E:\Minecraft Project`。查找外部 Mod 时先确认远端与分支；没有本地仓库则从 GitHub 克隆，有远端更新则先拉取再阅读。已有未提交修改或分支分叉时不得强制覆盖；可使用隔离检出读取远端。
- 接入以本仓实际依赖和目标实例 JAR 为准。最新版源码不等于编译/运行版本；必要时读取匹配 tag 或用 `javap` 核对签名、Mixin 目标。
- 先用 `rg` 定位实际入口，再读取相关代码和测试。按任务查阅下表，不在每次开始时加载全部源码或文档。

## 任务路由

Java 包路径均相对于 `src/main/java/cn/dancingsnow/neoecoae/`。

| 任务 | 优先读取 |
| --- | --- |
| API 或附属接入 | [API](docs/API_ZH_CN.md)、`api/` 及对应调用点；示例在 `src/test/java/cn/dancingsnow/neoecoae/api/extension/AddonApiExample.java` |
| 注册、依赖、可选联动 | `NeoECOAE.java`、`all/`、`api/integration/`、`integration/`、`compat/`、`build.gradle`、`src/main/templates/META-INF/neoforge.mods.toml` |
| 规划、循环、精确订单 | `api/me/planning/`、`api/me/bigorder/`、`crafting/planner/`、`crafting/adapter/` 和对应测试 |
| CPU、发配、事务、产物 | `api/me/provider/`、`api/me/output/`、`crafting/execution/`；先区分准备、接收、记账与交付 |
| 样板导入或迁移 | `api/IECOPatternStorage*.java`、`api/ECOPatternInsertion.java`、`api/PatternStorageHost.java` 及服务实现 |
| 存储盘、精确数量、迁移 | `api/storage/`、`storage/`、`storage/bulk/` 和对应持久化/恢复测试 |
| 多方块、档位、模型 | `api/IECOTier.java`、`api/multiblock/`、`multiblock/`、`blocks/entity/` 与客户端模型实现 |
| 工作站、配方、温控 | `recipe/`、`api/workstation/`、`blocks/entity/ECOLargeIntegratedWorkingStationBlockEntity.java`、`config/NEConfig.java` |
| UI、同步、面板贴图 | `gui/`、`menu/`、`network/`、`src/main/resources/assets/neoecoae/screens/`；面板扩展见 [API](docs/API_ZH_CN.md#面板与模型) |
| 语言、配方、标签、模型生成 | `data/NEDataGen.java`、`data/lang/`、`data/recipe/`、`data/provider/`、`all/` |
| 玩法指南 | `guidebook/` 与 `guidebook/_zh_cn/`；`copyGuidebook` 的配置见 `build.gradle` |

## 实现约束

- 保持现有架构与命名，不为同一能力新建平行实现。区分公开 API、可选兼容桥和内部实现；API 变更同步维护中英文指南及实际调用点。
- 先定义机制职责、暂停与恢复条件，再调整超频、冷却和温度参数。界面显示、服务端准入与执行使用同一规则。
- 游戏状态变更在所属服务器线程执行，异步规划使用已捕获快照；不在 tick 中阻塞等待 Future。描述/探测回调不修改世界、加载区块或移动资源。
- 输入、能量、产物按实际接收量记账。明确模拟/执行、单份/整批、准备/提交的区别，拒收可回滚，不确定接收停止核对，不能退款后重试造成复制。
- 精确数量路径保持 `BigInteger`；进入 long API 时使用明确的有界投影，不用截断值覆盖精确账本。
- 可选模组类仅在存在且版本适配后加载，公共/服务端入口不提前链接客户端专用类型。Mixin 目标按实际依赖版本检查。
- 持久化或迁移改动验证重载、失败恢复与资源守恒，不能把旧任务中途重新定价或把无法恢复的数据静默丢弃。

## 资源与构建

使用 Gradle wrapper，并先确认 Java 21 环境可用。PowerShell 常用命令：

```powershell
java -version
.\gradlew.bat compileJava --console=plain
.\gradlew.bat test --tests 'cn.dancingsnow.neoecoae.api.*' --console=plain
.\gradlew.bat jar --console=plain
```

按实际改动选择测试范围，不把上述 API 过滤器当成所有任务的完整测试。纯文档变更检查链接、源码引用与 diff 即可，不要求运行 Gradle。当前构建没有 Spotless 插件，不照搬其他项目的格式化任务。

- `src/generated/resources/` 是 datagen 输出。改生成源后运行 `.\gradlew.bat runData`，核对实际生成的 diff；不要手改生成文件作为最终结果。
- 英文语言入口为 `data/lang/NELangGenerator.java`；其他语言源位于 `src/main/resources/assets/neoecoae/lang/`，由 `NELangMergerProvider` 合并。不要照搬其他仓库的繁体自动转换规则。
- `guidebook/` 是指南源，`copyGuidebook` 复制到资源目录，且 `processResources` 已依赖该任务；修改源后验证打包内容。
- `runData` 默认加载开发依赖。遇到可选模组冲突先核对实际加载集与日志；隔离输出、保留无关生成结果，不把无关依赖修复混入当前任务。
- 避免多个 Gradle 进程同时写同一构建/生成目录。缓存完备时可用 `--offline`；缺失依赖时不要将离线失败归为源码错误。
- 按变更选择编译、相关单测、打包、datagen 和真实客户端验证，分别报告。测试通过不等于界面、玩家交互或整合包兼容性通过。

## 游戏内验证与 mcpfabric

仅在任务要求启动游戏或游戏内验证时启动客户端。任何 mcpfabric 请求前，先通过本机进程和桥接端口确认游戏已启动并加载桥接；Gradle daemon 不是 Minecraft 游戏进程。桥接使用 `MCPFABRIC_URL`，默认 `127.0.0.1:25599`。

没有游戏进程或端口未监听时，跳过全部 mcpfabric 请求，不用 `get_status` 反复试探。进程与桥接就绪后最多调用一次 `get_status` 确认已进入世界；未进世界或返回 `no_client_player` 时停止玩家/世界调用，等待存档或服务器加载完成。

出现 `Bridge unreachable` 等连接错误后停止重试；确认游戏启动、重新进入世界、配置修复等状态变化，或用户明确要求重试后，才重新检查。

## 收尾

- 检查 `git diff --check`，报告具体修改、实际验证及尚未完成的必要检查。用户报告运行失败时继续以实际复现结果为准。
- 用户要求提交时，只暂存当前任务的明确路径，检查 `git diff --cached --check` 与暂存内容，使用清晰的中文提交信息；未经要求不提交或推送。
- 不提交缓存、日志、临时测试产物或无关文件，不用破坏性恢复清理用户工作区。
- `docs/` 仅保留中英文 API；长期协作约定在本文件维护，避免新增重复流程说明或一次性验收记录。
