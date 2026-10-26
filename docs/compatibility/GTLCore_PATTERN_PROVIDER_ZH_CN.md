# GTLCore 样板供应器兼容

## 派发归属

ECO 驱动的合成任务由 ECO 决定批量、增长、拒绝后的回退、材料与能量事务以及产物记账。
供应器与 MAE2 样板 P2P 继续负责实际传输、阻挡模式、锁定和发送缓冲。
GTL 的自动倍增开关及 `gtlcore$getMaxPatternOperations` 不参与 ECO 任务的批量决策。
原版 CPU 和 GTL 自己驱动的任务继续使用其原有策略。

普通加工供应器按实际接受情况执行 ECO 的智能翻倍与回退。GT 样板缓冲仓仍在扣料前检查
`gtlcore$graphCapacity`，避免内部 long 数量溢出；这属于传输容量约束。
编程电路继续按批发送一次，此类批次先由 ECO 根据现有材料与任务预算选定完整批量，
拒绝时由 ECO 缩小重试，防止冷启动单份试投提前耗尽唯一的配置电路。

## 崩溃原因与修复

GTLCore 从 `1.2.3.2-fix6` 起要求 MAE2 `1.6.1`。MAE2 1.x 以默认优先级 1000
重写 `PatternProviderLogic.pushPattern`。ECO 原来的诊断 Mixin 同样是 1000，
Mixin 会拒绝把它的 `INVOKE` 注入点应用到同优先级模组重写的方法中。

`PatternProviderDiagnosticsMixin` 改为优先级 1100。Mixin 在合并方法后准备注入，
因此钩子观察的是 MAE2 的实际传输方法。所有必需的诊断钩子和发送缓冲观察仍然启用，
保留原方法的返回值和运输行为。

## 核对来源

- GTLCore 官方仓库：`GNSW/GTLCore`，分支 `gtl-1431-skyblock`。
- 本次同步提交：`a4e51deb2dc098b6bb3ea9115be30f424a9eefa3`，版本 `1.2.3.3`。
- MAE2 仓库：`Omicron-Industries/MAE2`；同时核对其 `1.6.1` 发布 JAR，不能用另一个
  包名为 `cn.qiuye.betterae2` 的 MakeAE2Better 项目代替。

## 验证范围

通过隔离 Forge 启动探针加载发布 JAR 中的原始供应器 Mixin，覆盖：

| AE2 / 附属组合 | 检查 |
| --- | --- |
| 标准 AE2 15.4.11 | ECO 必需钩子完整 |
| AE2 + MAE2 1.6.1 | MAE2 传输重写和 ECO 必需钩子共存 |
| AE2 + MAE2 1.6.1 + GTLCore 1.2.3.2-fix6 | 三方供应器 Mixin 共存 |
| AE2 + MAE2 1.6.1 + GTLCore 1.2.3.2-fix7 | 三方供应器 Mixin 共存 |
| AE2 + MAE2 1.6.1 + GTLCore 1.2.3.3 | 三方供应器 Mixin 共存 |

对照探针仅把诊断 Mixin 优先级改回 1000，可复现原日志中的 `observeSendList`
注入失败；恢复 1100 后保留全部七个必需钩子。

构建、JAR 检查及 114 项相关回归全部通过（0 失败、0 跳过）。
派发回归覆盖 GTL 倍增关闭或异常时 ECO 仍然翻倍、实际供应器忙碌时停止投料、拒绝批次退款、
配置电路按批发送、原生缓冲容量和任务记账。探针在 EULA 与存档加载前退出，
这些检查不代表完整 GTL 整合包启动或游戏内 P2P、机器交互验收。

本地证据位于 `build/diagnostics/gtl-pattern-provider/`。
