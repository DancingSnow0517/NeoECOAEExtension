# 合成终端“合成状态”跳转修复

## 运行时原因

用户点击原生 AE2 合成终端右上角的“合成状态”按钮后，服务端已经打开 `CraftingStatusMenu`，客户端仍停在 `CraftingTermScreen`。按钮请求和服务端定位器有效；失败发生在客户端构造合成状态页面时。

在用户实际运行的 GTL 猪咪整合包中，通过游戏线程上的只读检查及已注册的原生界面工厂调用，抓到 `CpuSelectionListMixin.onDrawBackgroundLayer` 的 `InjectionError`：必需注入命中数为 `0/1`。AE2 发布 JAR 中的调用是 `PoseStack.m_85849_()`，原注入目标却是开发环境的 `PoseStack.popPose()`；Mixin 类整体关闭重映射，发布包原来没有这条调用映射。

仅在这个 Minecraft 调用的 `@At` 上启用 `remap = true`，AE2 自身的 `drawBackgroundLayer` 名称继续保持原样。发布 JAR 的 `neoecoae.refmap.json` 现在同时在默认映射和 `searge` 上下文中包含：

```text
Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V
  -> Lcom/mojang/blaze3d/vertex/PoseStack;m_85849_()V
```

CPU 贴图叠加仍为必需注入；没有通过允许零命中绕过错误。容量、协处理器和提示包装继续保留。

## 验证与实际点击

- 11 项 CPU 列表契约及格式归属测试通过，生产编译和重混淆打包成功。
- 扩展已有独立 Mixin 回归入口：AE2 15.4.10 实际安装 JAR、15.4.11 开发依赖分别验证原生与 GTLCore 共装，共四组通过，检查最终绘制和提示方法确实调用四个 ECO 挂钩。
- 原有合成 CPU 执行回归的三个场景继续通过，包含优先级旧错误复现及修复后的两种环境。
- 发布 JAR 静态检查通过，且核对了实际打包的两份方法映射。
- 在用户正在运行的客户端中，临时向已加载的 Mixin 引用映射补入与新 JAR 完全相同的一条映射，再通过当前合成终端的 `mouseClicked`、`mouseReleased` 点击真实“合成状态”按钮。随后确认实际界面为 `CraftingStatusScreen`，客户端和服务端菜单均为 `CraftingStatusMenu`。这一步覆盖了按钮、AE2 网络包、服务端打开和客户端界面构造。

当前会话的在线验证没有重定义游戏类，也未使用未就绪的 mcpfabric 桥接。临时诊断文件位于 `build/live-terminal-probe`，不会打进发布包；关键文本证据保存在 `evidence/crafting-status-navigation`。

四组列表转换检查的入口如下，省略 `ae2AddonCompatibilityAe2Jar` 即验证开发依赖：

```powershell
.\build-local.ps1 ae2AddonCompatibilityTest `
  --init-script tools/porting/ae2_addon_compatibility.init.gradle `
  '-Pae2AddonCompatibilityTarget=selection-list' `
  '-Pae2AddonCompatibilityJar=<GTLCore JAR 绝对路径>' `
  '-Pae2AddonCompatibilityAe2Jar=<AE2 发布 JAR 绝对路径>' `
  --offline --console=plain
```

## 实例安装

确认游戏正常保存并退出后，已替换原实例的 `mods/neoecoae-20.6.0-dev.jar`。SHA-256 为 `9c6d841056acd802caa749c52827d216844eb0cd1dcb93229bf4fa97dd1e44f8`。

旧包保存在实例目录的 `neoecoae-backups/crafting-status-20261023-231924/neoecoae-20.6.0-dev.jar`。新旧文件和备份均校验哈希；替换记录见该目录的 `replacement.json`。新 JAR 替换后的下一次启动尚未重新验收。

机器记录为 `porting/crafting-status-navigation-validation.json` 与 `porting/crafting-status-navigation-package-validation.json`。
