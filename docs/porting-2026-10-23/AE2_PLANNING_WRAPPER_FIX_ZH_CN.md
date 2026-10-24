# AE2 合成规划包装兼容修复

现场日志时间为 2026-10-24 09:45:13、09:52:00。Project Infinity 使用 AE2 15.4.10，Applied Enhancements 从 1.0.7 更新到 1.1.4 后，仍在 `CraftConfirmMenu.planJob(...) @0: astore` 报 `VerifyError: Operand stack underflow`。

原因是 NeoECOAE 在 Mixin `postApply` 中直接调用 `InsnList.set` 替换规划指令，MixinExtras 延迟执行的 `@WrapOperation` 仍持有旧的注入节点。后续包装因此生成了失去原方法主体的无效字节码。

## 修复方式

- 插件在加载时注册 `RouteCraftingPlanning` 注入器，由 Mixin 自身的 `RedirectInjector` 替换调用并维护共享注入节点，供 MixinExtras 继续包装。
- 合并阶段结束后，按实际方法名和完整描述符选择包含规划调用的实例方法。这样可以覆盖名称未知的附属入口，避开 Mixin 0.8.5 通配选择器忽略已合并方法的问题。
- 分别适配 `ICraftingService` 和具体 `CraftingService` 调用；长数量、原生回退、ECO 规划器选择和诊断状态继续通过原有 `CraftConfirmPlanningBridge` 处理。

## 验证

所有 Gradle 验证通过 `build-local.ps1` 使用 Java 17 串行运行。

| 场景 | 结果 |
| --- | --- |
| 原实现与独立包装样本 | 复现同一 `planJob` 栈下溢 |
| 修复后独立样本，开发 AE2 与实际安装 AE2 | JVM 校验通过，接口及具体服务新增入口均保留 ECO 路由 |
| 实际 Applied Enhancements 1.0.7、1.1.4 JAR | 原实现复现故障，修复后加载与规划入口检查通过 |
| 最终发行 JAR + 实际 AE2 15.4.10 + Applied Enhancements 1.1.4 + MixinExtras 0.5.5 | 原实现复现故障，修复后 JVM 校验及包装链检查通过 |
| 原有未知附属规划入口样本 | 修复前后的路由对照验证通过 |
| 合成确认及可用性单元测试 | 9 项通过，0 失败、0 跳过 |
| 发行 JAR 审计 | 952 个源码文件、1570 个类、64 个 Mixin；缺失项及重复项均为 0 |

整合包实际选择的 MixinExtras 0.5.5 来自 `ae2-crafting-time-1.2.4-forge-1.20.1.jar`。包装验证显式使用提取的实际核心 JAR，并记录生产类来自最终发行包的资源地址。验证会调用 `getDeclaredMethods()` 触发 JVM 校验，避免仅凭 ASM 导出文件存在就判为成功。

可复查的结果位于 `porting/planning-wrapper-compatibility-validation.json` 和 `evidence/planning-wrapper-compatibility/results.txt`；完整本地日志位于 `build/validation/planning-wrapper/`。

最终包 SHA-256：`dc92d45ee8ed6c6fd71282597b0291a446a2a92fbe5c7d0ea8eea70b5ebd61cb`。

已备份并替换 Project Infinity 的 `mods/neoecoae-20.6.0-dev.jar`，安装后哈希与上述发行包一致。旧包位于该版本目录的 `mod-backups/neoecoae-planning-wrapper-fix/neoecoae-20.6.0-dev.before.df2d90834249.jar`。

本次验证为真实依赖的隔离 Mixin 加载、JVM 校验、单元测试、发行包审计及文件安装检查；尚未启动完整整合包客户端、进入存档或执行实际合成。
