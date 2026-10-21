# 大集成成型模型的叠层及 Forge 材质适配

## 现象与定位边界

用户截图中的机身、灯带和顶面出现密集条纹。当前基础环境和四附属组合环境中，原模型都能正常成型，11 个部件的渲染状态均为 `INVISIBLE`，没有复现截图同等严重的大面积闪纹。因此不能把这次本地截图检查当作原运行环境已经完全修复的证明。

源码检查确认了两处需要处理的模型问题：灯带面与机身外表面完全共面；发光面的数据仍使用 `neoforge_data`，而本工程的 Forge 1.20.1 模型解析器读取 `forge_data`。PNG 中机身和灯带的不透明像素互补，不能仅凭共面就断言原图必然由这两个图层引起。

## 修改

- 在 `large_integrated_working_station_off.json` 和 `large_integrated_working_station_on.json` 中，将与灯带共面的机身外表面向内退让 `0.01` 模型单位，即 `1/1600` 方块。灯带仍位于原来的结构边界；所有元素坐标保留在 1.20.1 模型允许的 `[-16, 32]` 范围内。
- 将这两份模型的发光面数据改为 `forge_data`。待机模型有 5 个灯带面，运行模型另有 2 个屏幕面；亮度及禁用环境光遮蔽的原始数值保留。
- 常规、AdvancedAE、ExtendedAE、AE2LT 四类外观共用这两份基础几何，6 份附属模型继续通过父模型继承修复。PNG、UV 和结构判定没有改动。
- 增加仅在开发诊断模式启用的 `WorkstationModelClientProbe`。普通开发启动与生产环境不会自动运行此检查。

主体基线仍为 `porting/source-lock.json` 中的 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`。本轮更新参考仓库远端仅用于核对，没有切换移植基线。

## 验证内容

完整主源码编译与 JAR 构建通过。包检查逐字节比较 8 份基础及附属模型，检查原生材质字段和模型坐标范围；最终 JAR 的 SHA-256 记录在 `porting/workstation-formed-rendering-validation.json`。

开发客户端使用 Forge `47.4.28`、AE2 `15.4.11`，并加载 AdvancedAE、ExtendedAE、ExtendedAE Plus、AE2LT。在新建隔离世界中实际摆放 4 座 `3×2×2` 结构，覆盖四个朝向及水平区块边界。安装真实模块卡后，检查 4 类模块 × 2 种模型状态 × 4 个朝向 × 2 个观察方向，共 64 个视角。

每个视角检查模型仅使用 `cutout` 渲染层、恰有 36 个面、5 个灯带面保留原生满亮度且没有环境光遮蔽；同时保存游戏帧缓冲截图。最后实际拆除一个外壳，检查主机解除成型以及剩余部件恢复独立模型。最终运行的 64 个视角及拆机检查全部通过，客户端正常保存退出，日志记录 `WORKSTATION_MODEL PASS` 和 `BUILD SUCCESSFUL`。

运行状态通过客户端视觉夹具选择，用于验证模型和动画外观；没有借此宣称实际加工、配方消耗或热量机制已经通过。普通启动器安装及用户截图原环境也未在本轮验证。

验证材料位于 [`validation/workstation-formed-rendering-20261021/`](../../validation/workstation-formed-rendering-20261021/)，包含原始截图、[四类外观总览](../../validation/workstation-formed-rendering-20261021/gallery.png)、[修改前后对照](../../validation/workstation-formed-rendering-20261021/before-after.png)、日志和包检查报告。总览仅缩放、排列游戏原图，没有重绘模型像素。

## 复测

Gradle 命令串行执行：

```powershell
.\build-local.ps1 compileJava jar --offline --console=plain
.\build-local.ps1 runClient -PportingProfile=combined -PclientSmoke=true -PworkstationModelSmoke=verified --offline --console=plain
```

中间两次探针失败也保留在验证目录：第一次是待机服务器同步覆盖视觉样本的工作状态；第二次是拆机断言错误地要求已移除位置的空气显示模型。它们是验证夹具的问题，未计为最终检查通过，也没有为此改变机器业务逻辑。
