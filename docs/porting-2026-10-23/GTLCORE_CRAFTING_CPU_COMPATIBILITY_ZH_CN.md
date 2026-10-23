# GTLCore 合成 CPU 注入兼容修复

## 问题与改动

GTL 猪咪整合包在 2026-10-23 22:21:53 加载 `kubejs:void` 中坐标 `(-156, 75, -135)` 的 `gtlcore:crafting_storage` 时退出。实际环境为 AE2 15.4.10、GTLCore 1.2.3.2-fix4、NeoECOAE 20.6.0-dev。

GTLCore 的 `CraftingCpuLogicMixin` 以优先级 1100 覆盖 `executeCrafting`；NeoECOAE 的 `Ae2CraftingCpuFastPathMixin` 使用默认优先级 1000，因而其 `@WrapOperation` 在准备阶段被 Mixin 拒绝，尚未进入合成业务执行。

将 NeoECOAE 这个 Mixin 的优先级明确设为 1200。已更新 GTLCore 官方仓库的远端引用，并核对上游源码及整合包实际 JAR：覆盖后的方法仍调用相同描述符的 `ICraftingProvider.pushPattern`，因此可以保留既有任务 UUID 传递、批量执行、暂停、完成/取消回收及能量账本。原有注入的强制命中要求保持有效。

## 验证

- Java 17 下完成生产代码编译、57 项外部 CPU/供应器单元测试及 `jar`/`reobfJar`，没有测试失败或跳过。
- 使用实际 Mixin 0.8.5、MixinExtras 0.5.3 和安装实例的 GTLCore JAR 做独立字节码转换回归，分别覆盖实例中的 AE2 15.4.10 与日常开发的 15.4.11。
- 每个版本执行三种场景：在临时副本中恢复优先级 1000 并复现原始异常；以修复后的优先级和实际 GTLCore 覆盖共装；使用没有 GTLCore 覆盖的原生 AE2。六种场景全部通过。
- 检查转换后的 `executeCrafting`，确认 GTLCore 方法归属、ECO 批量执行入口和投料包装调用均保留，而且没有遗留未经包装的原始投料调用。
- 发布 JAR 静态审计通过：1603 个类均为 Java 17 字节码，源码对应类、资源、Mixin 和内嵌依赖完整；发布类中的优先级为 1200。

回归通过 LaunchWrapper 提供独立的 Mixin 转换环境，未启动 Forge 游戏、打开玩家存档或运行完整整合包。它验证具体崩溃点及转换后的挂钩，不代表完整合成任务和真实存档已验收。

可选验证入口如下；测试源码和第三方输入不会打进发布 JAR：

```powershell
.\build-local.ps1 ae2AddonCompatibilityTest `
  --init-script tools/porting/ae2_addon_compatibility.init.gradle `
  '-Pae2AddonCompatibilityJar=<实例的 GTLCore JAR 绝对路径>' `
  '-Pae2AddonCompatibilityAe2Jar=<实例的 AE2 JAR 绝对路径>' `
  --offline --console=plain
```

省略 `ae2AddonCompatibilityAe2Jar` 时使用当前工程开发依赖中的 AE2。结果分别位于 `build/ae2-addon-compatibility/installed-ae2` 和 `build/ae2-addon-compatibility/development-ae2`。完整记录见 `porting/gtl-cpu-compatibility-validation.json` 和 `porting/gtl-cpu-compatibility-package-validation.json`。

## 安装实例

已将修复包替换到原实例的 `mods/neoecoae-20.6.0-dev.jar`，并核对 SHA-256：`b65a3988c8de16f694ad391369b0274651198bd5fb41f409147272e4222b9665`。

原 JAR 保存在实例目录的 `neoecoae-backups/gtl-cpu-20261023-224144/neoecoae-20.6.0-dev.jar`。备份目录另含替换记录 `replacement.json`。

日志中 GTL EnhancedCore 的卫星工厂结构校验失败，以及 ExtendedAE Plus 1.6.1 的目标方法缺失，是独立问题。本次修改针对造成退出的 NeoECOAE/GTLCore CPU 注入冲突。
