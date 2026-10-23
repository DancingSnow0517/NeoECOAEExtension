# AE2 附属共装环境合成规划页面修复

## 故障及修复

用户在数量页面点击“下一页”，无法进入合成规划。实际整合包使用 AE2 15.4.10、GTLCore 1.2.3.2-fix4，以及 NeoECOAE 20.6.0-dev。

修复遵循 AE2 的通用菜单同步和资源规则：ECO 字段使用独立编号段，ECO 报告使用独立样式文件名。生产实现没有按 GTLCore 是否安装切换行为，也不依赖其类。验证工具统一命名为 `Ae2AddonCompatibility`，第三方类名和字段名放入可替换的运行样本 JSON，样本不参与发行包。另用独立的模拟附属占用整个旧编号段，检验同一修复对其他附属的适用性。

### 确认菜单的同步编号冲突

在运行中的整合包里读取合并后的 `CraftConfirmMenu`，并调用真实菜单构造方法，复现了：

```text
IllegalStateException: Class class appeng.menu.me.crafting.CraftConfirmMenu declares the same sync id twice: 100
```

GTLCore 的 `gtlcore$missingCraftAvailable`、`gtlcore$planningFailure` 分别使用编号 100、101，和 ECO 的计算用时、规划状态冲突。AE2 的 `DataSynchronization` 构造方法拒绝重复编号，菜单无法创建。

将该菜单的 12 个 ECO 字段统一移到 `12000..12011`，保留所有字段与原有同步类型。AE2 原生字段和 GTLCore 字段保持各自编号。客户端与服务端必须使用同一新版 JAR。

### ECO 报告样式被 GTLCore 误替换

临时修正当前会话的同步编号后，用户再次操作，出现 `crash-2026-10-23_23.48.18-client.txt`：

```text
Description: Rendering screen
IllegalStateException: Screen is missing required image: cycleItem
```

实际 GTLCore JAR 的 `StyleManagerMixin.loadStyleDocHooks` 使用 `path.contains("craft_confirm.json")`，把 `/screens/eco_craft_confirm.json` 也重定向到 `/screens/modify_craft_confirm.json`。后者没有 ECO 循环列表需要的图像和控件。源码和已安装 ECO JAR 中原本均包含这些资源，故不能仅凭资源存在判断能够打开界面。

将 ECO 报告样式改名为 `/screens/eco_crafting_report.json`，并更新调用。GTLCore 的原生合成确认页继续使用其原有重定向，ECO 报告读取自己的完整样式。

## 验证范围

- 17 项相关单元测试通过，包括通过 AE2 实际样式加载器加载报告、构造循环列表渲染器、检查全部必要控件和图像，以及 CPU 显示与菜单数据传输回归。通用同步测试用不依赖任何第三方模组的模拟附属占用 99..112 和 23456，验证 ECO 字段与外部字段的完整、增量同步，并保留 AE2 对其他重复字段的正常拒绝行为。
- 使用真实 Mixin 和整合包实际 GTLCore JAR，分别对 AE2 15.4.10、15.4.11 验证菜单字段。修复前两处撞号均可复现，修复后 12 个 ECO 字段和 3 个 GTLCore 字段全部保留且唯一。
- 命名映射环境验证 AE2 `DataSynchronization` 的完整及增量数据包，ECO 的计算用时、规划状态和 GTLCore 的失败提示、缺料合成状态互不覆盖。发布版 AE2 在隔离测试中验证真实同步器构造；其数据包测试在命名环境和真实发布运行时分别记录。
- 在用户原先运行的完整整合包中，仅临时修改 ECO 字段的注解编号缓存；随后真实客户端、服务端菜单构造均成功，并通过发布运行时的真实同步器完成数据包往返。未重定义游戏类，测试菜单没有安装到玩家上，未修改世界数据。此证据不等于完整规划页已通过验收。
- 对两种 AE2 版本使用 GTLCore 的实际样式注入：旧路径被误替换且没有循环图像；新路径保留并包含所有 ECO 必要图像和控件。原生确认页的重定向继续存在。
- 回归之前的 CPU 逻辑及 CPU 列表注入修复，并检查重混淆 JAR 的类、资源、Mixin、引用映射及内嵌依赖。

单元测试与变换日志位于忽略的 `build/crafting-confirm-*.log`。持久证据见同目录的 `evidence/crafting-plan-navigation/`，汇总见 `porting/crafting-confirm-navigation-validation.json`；最终包审计见 `porting/crafting-confirm-navigation-package-validation.json`。

**真实客户端的两处菜单构造和同步已验证；修复样式后的最终 JAR 仍需重新启动游戏并完成“下一页 → ECO 规划列表”的交互验收。**

## 重跑关键兼容性检查

```powershell
.\build-local.ps1 ae2AddonCompatibilityTest `
  --init-script tools/porting/ae2_addon_compatibility.init.gradle `
  '-Pae2AddonCompatibilityTarget=craft-confirm' `
  '-Pae2AddonCompatibilityJar=<整合包 GTLCore JAR>' `
  '-Pae2AddonCompatibilityAe2Jar=<整合包 AE2 JAR>' `
  --offline --console=plain
```

目标改为 `crafting-style` 检查报告样式路径，改为 `selection-list` 检查 CPU 列表，改为 `logic` 检查 CPU 逻辑。不传 AE2 JAR 参数时，使用工程锁定的开发版 AE2 15.4.11。测试使用的第三方 JAR 不进入发行包，也不改变依赖锁定。

默认样本为 `tools/porting/fixtures/ae2-addon-runtime-sample.json`；可以通过 `-Pae2AddonCompatibilitySample=<样本 JSON>` 配合其他附属 JAR 重跑对应行为检查。旧故障记录中的 GTLCore 名称仅用于说明实际复现环境。
