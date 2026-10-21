# 2026-10-21 分批提交前复核

本次仅整理 `E:\NeoECOAEExtension-1.20.1` 当前改动并分批本地提交，不推送。主体仍固定为 `porting/source-lock.json` 已批准的 main 提交。既有运行记录保留为各自历史检查点，本轮没有重新启动客户端或专服。

## 改动分组

1. Java 17、Forge、AE2 15 与保留式 UI 的业务测试迁移，以及已批准范围外测试的原样归档。
2. Forge 模型加载器、工作站纹理、进度条件和 AAE、EAE、AE2LT 配方资源适配。
3. AE2 15 任务暂停控制、样板终端动作注册、模型依赖加载及 Plus 的 JEI 查询接口适配。
4. 库存与流体受管字段、AE2LT 持久化签名、保留式 UI 绘制与小窗口定位修复，以及显式启用的开发诊断。
5. 能源元件受管字段修复及保存重进诊断。
6. 大集成成型模型叠层、Forge 发光材质及模型诊断。
7. 启动、界面、能源、成型模型的历史证据与本轮复核记录。

共用的开发启动参数和探针入口随对应修复分批加入，最终工作区内容保持完整。

## 本轮实际检查

检查对象是分批提交前的完整工作区，不是逐个中间提交的独立构建结果。Gradle 串行执行：

```powershell
.\build-local.ps1 compileJava jar portingPlatformTest portingContractsTest --offline --rerun-tasks --console=plain '-Dorg.gradle.jvmargs=-Xmx6G -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
.\build-local.ps1 compileTestJava --offline --console=plain '-Dorg.gradle.jvmargs=-Xmx6G -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
python -m unittest discover -s tools/porting -p 'test_*.py' -v
python tools/porting/audit_package.py --output validation/commit-batches-package-audit.json
python tools/porting/verify_data_resources.py --output validation/commit-batches-data-audit.json
```

| 检查 | 结果 |
| --- | --- |
| 完整主源码编译、JAR 及专项测试 | `BUILD SUCCESSFUL in 40s`，13 项任务全部重新执行 |
| 专项 Java 测试 | 平台 104 项、契约 6 项，0 失败、0 错误、0 跳过 |
| Python 资源与账本测试 | 10 项通过 |
| JAR 检查 | 934 个主源码对应类全部存在；1,536 个类均为 Java 17；59 个 Mixin 及其他包检查无缺失或重复 |
| 数据资源与材料账本 | 428 份目标数据、193 份已裁剪数据、9 条拆解契约，无错误 |
| 全量业务测试源码编译 | **仍失败：29 个文件、114 个错误**；尚未执行业务测试 |
| 归档完整性 | Flux Networks 与 OmniSequence 两份归档和提交前 `HEAD` 原文件逐字节一致，来源哈希吻合 |

完整机器记录见 [`porting/commit-batches-validation.json`](../../porting/commit-batches-validation.json)。构建和失败详情分别位于 `validation/commit-batches-build.log`、`validation/commit-batches-test-compile.log`。

## 验收边界

业务测试仍需继续适配，不能把专项测试通过描述成完整 `test` 或 `build` 通过。没有通过排除保留业务测试、添加跳过或关闭生产功能消除编译阻塞。

此前真实客户端结果分别见 [RUNTIME_STARTUP_STATUS.md](RUNTIME_STARTUP_STATUS.md)、[CLIENT_CRASH_AND_UI_FIXES.md](CLIENT_CRASH_AND_UI_FIXES.md)、[ENERGY_CELL_CRASH_FIX.md](ENERGY_CELL_CRASH_FIX.md) 和 [WORKSTATION_FORMED_RENDERING_FIX.md](WORKSTATION_FORMED_RENDERING_FIX.md)。这些记录的工件哈希和测试数量属于相应检查点；本轮没有重新执行其中的运行与交互检查。完整 datagen、独立业务专服和普通 Forge 安装仍未验收。
