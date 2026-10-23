# AE2 菜单规划与 CPU 选择兼容修复

本次修复解决两个独立问题：确认报告中的规划图按钮不可用，以及手选 ECO CPU 后任务仍被其他附属的自动选择逻辑接管。生产实现只依赖 AE2 标准接口和 ECO 自身契约；第三方类名仅保留在可替换的验证样本 JSON 和现场证据中。

## 现场原因

- 实际报告页已能打开，客户端和服务端容器编号一致，诊断流版本也一致；但两端图节点均为 0，服务端结果来自另一个规划器，ECO 报告就绪状态为 false。因此按钮被禁用，并非点击坐标或分包同步问题。证据：[graph-empty-before.txt](porting-2026-10-23/evidence/ae2-menu-routing/graph-empty-before.txt)。
- 旧实现枚举规划入口方法名，遗漏附属新增的长数量入口。真实 Mixin 测试进一步证实：改用 `method = "*"` 也不足够，Mixin 0.8.5 会跳过其他 Mixin 合并的方法。
- 从实际游戏 JVM 捕获的提交代码显示：附属在原生自动扫描后的回调先执行并返回，ECO 回调随后才会执行。证据：[submission-order-before.txt](porting-2026-10-23/evidence/ae2-menu-routing/submission-order-before.txt)。

## 实现

`CraftingMenuPlanningMixinPlugin` 在所有 Mixin 完成合并后，扫描确认菜单实例方法对标准 `beginCraftingCalculation` 的调用，通过 `CraftConfirmPlanningBridge` 交给菜单自己的规划入口。调用保留原服务、请求者、物品、long 数量和策略；未开启 ECO 或没有接受该请求来源的计算主机时，继续调用原服务。路由方法本身保留原始调用，避免递归。图和报告状态在每次真实计算开始时清理，不再绑定外部方法名。

`CraftingServiceEarlyMixin` 将 ECO 提交检查移到 `findSuitableCraftingCPU` 调用前。模拟计划仍先被拒绝，显式原生 CPU 仍走原生直接提交分支；显式 ECO CPU 的成功或失败结果会直接返回，不再让随后的自动选择改换目标。无合适 ECO 候选时继续原服务和其他附属的处理。

## 验证

- 使用 `build-local.ps1` 和 Java 17，在基于 `212cc702015f27603de363c524bdced206f90afd` 的独立检出目录编译和打包，未合入工作区正在进行的结构终端改造。
- 全量 `build --offline --console=plain` 通过。JUnit 报告汇总：1,528 项，0 失败、0 错误、12 跳过。
- 单元测试覆盖手选 ECO CPU 的原计划、请求者和来源转发，忙碌时不转交其他 CPU，其他显式 CPU 保留路径，以及模拟计划不执行。
- 可执行字节码测试使用未知名称的新增入口和超过 int 上限的数量，验证真实调用进入桥接方法且仅执行一次；重复处理也不产生递归。
- 通用变换验证覆盖独立模拟附属和实际安装附属，分别使用开发 AE2 15.4.11 与安装 AE2 15.4.10；同时回归菜单同步编号、报告样式、CPU 列表和执行器注入。最终结果见 [验证记录](../porting/ae2-menu-routing-validation.json)。
- 发布 JAR 静态审计通过：976 个源码类检查、1,605 个打包类均为 Java 17 字节码、64 个注册 Mixin 完整、插件和桥接类齐全；未打包验证工具或带 GTL 名称的本工程类。

发布包：`build/release/ae2-menu-routing/neoecoae-20.6.0-dev.jar`，7,039,545 字节，SHA-256：`bd51fd4d65b345fc0b407ea77175c492f0c123d0d1d776a2049b67577a8c7c0c`。

## 安装与真实交互

目标仍为用户指定整合包的 `mods/neoecoae-20.6.0-dev.jar`。替换前需确认游戏完全退出，并在实例的 `neoecoae-backups` 中保存旧包；安装路径、备份路径和哈希记录在验证记录中。

本轮新包尚未完成重启后的真实规划图点击和真实任务 CPU 归属验收。隔离变换和单元测试不代替这两项游戏内验收；诊断过程中未代用户创建、取消或提交真实合成任务。
