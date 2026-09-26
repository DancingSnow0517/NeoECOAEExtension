# ECO 主 Mod 内部源码模块

本模块位于 `src/main/java/cn/dancingsnow/neoecoae/crafting`，由现有 main 源集编译。
不新增 Gradle 源集、子工程、独立 Mod、核心 jar 或发布任务。Java 21 与 1.21.1 的现有构建继续使用。

## 功能与边界

| 包 | 职责 |
| --- | --- |
| `crafting.planner` | Planner 服务、编译图、路线选择、循环求解、精确材料校验、计划与诊断快照 |
| `crafting.execution` | CPU、执行运行时、调度、能量及输入事务、任务账本、持久化、输出交付和恢复 |
| `crafting.execution.fastpath` | 普通/精确批次、配方分类、状态转换、可复用输入与批次上限 |
| `crafting.execution.worker` | worker 工作、生命周期、输出与恢复 |
| `crafting.execution.bigorder` | 精确父订单账本 |
| `crafting.amount` | PlannerAmount、ExactAmount 和饱和算术 |
| `crafting.display.format` | 大数缩写、完整数量、字节单位与分组 |
| `crafting.display.terminal` | 终端精确数量采集、缓存、排序及来源接口 |
| `crafting.graph` | CraftingTree 图模型、树投影、循环聚合、布局、连线路由、空间索引和布局缓存 |
| `crafting.graph.client` | CraftingTree Screen、绘制器与帧统计 |
| `crafting.adapter.ae2` | ECO 精确计划及强制计划的 AE2 表示 |

原有入口、方块实体、网络、菜单、Mixin 和可选兼容代码通过更新后的导入调用这些实现。
外部 Provider、生命周期、进度等契约继续位于 `api.me`；`ECOFastPathFacade` 入口保留。
没有引入第二份算法、运行时注册容器或反射路由。

## 当前依赖规则

- `amount` 和 `display.format` 只依赖 JDK 及内部数量类型。
- Planner、执行器与 AE2 计划适配不得依赖 Minecraft 客户端、屏幕或绘制器。
- 图投影和布局不得调用 `graph.client`。布局接收图，返回 `GraphLayoutSnapshot`。
- 展开/折叠只变换展示投影，不修改原始规划图。
- CPU 拥有资源与账本；Provider 拒绝必须保持未转移状态，结果未知时进入对账。
- 规划结果不转移资源；世界、Provider 和库存变更仍在服务端所属线程发生。
- 精确父订单与有限子计划继续使用原有分段规则；显示舍入不能回流执行账本。
- 源码模块采用包边界，现有 `test` 中的 `CraftingModuleBoundaryTest` 检查关键依赖方向。

## 迁移位置

| 原位置 | 新位置 |
| --- | --- |
| `impl.crafting.planner` | `crafting.planner`，其中 PlannerAmount 位于 `crafting.amount` |
| `impl.crafting.fastpath` | `crafting.execution.fastpath` |
| `impl.crafting` 下 AE2 计划包装 | `crafting.adapter.ae2` |
| `api.me` 下 CPU/调度/运行时实现 | `crafting.execution` |
| `api.me.worker` | `crafting.execution.worker` |
| `api.me.bigorder.ECOBigCraftingOrder` | `crafting.execution.bigorder.ECOBigCraftingOrder` |
| `client.craftinggraph` | `crafting.graph` 与 `crafting.graph.client` |
| `terminal.bigamount` | `crafting.amount`、`crafting.display.format`、`crafting.display.terminal` |
| 数量相关 `util` | `crafting.amount` 或 `crafting.display.format` |

内部实现的完全限定类名发生变化。主工程、Mixin 和测试引用已迁移。
依赖这些实现类或含这些类型的方法签名的第三方代码需要重新编译；本次不承诺旧二进制 ABI。
NBT 字段、协议字段、Mod ID 与注册 ID 没有因包迁移而更名。

## 1.21.1 限定

本次完成的是主 Mod 内部的源码模块化，不是四版本通用内核。
Planner/执行计划仍使用 AEKey、IPatternDetails；CPU/worker 仍调用 AE2 和 NBT；
图快照仍有 1.21.1 网络编码，图模型仍使用 AE2 key。它们目前是明确保留的 1.21.1 依赖。
后续若启动跨版本移植，再在这些边界提取资源身份、样板语义、持久化和网络适配。

## 验证

沿用现有命令：

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

新增边界检查直接使用 JDK 编译数量和格式化代码，刻意不给 Minecraft/AE2 classpath。
新增 CraftingTree 测试覆盖无 Screen 的投影/布局、连线路由、空间查询和缓存失效。
游戏内字体、图标、菜单同步以及保存恢复仍应在客户端/服务端集成环境验收。

