# 存储、AE2 兼容与多方块修复分批提交复核

本轮从 `a09e0181c9f78af38f1c4cb446e2c2d3eeed2079` 整理全部源码、测试、脚本及文档改动，分为四批代码和一批验证文档进行本地提交。主体仍固定为 `porting/source-lock.json` 的已批准 main 提交，未推送。

## 改动分组

1. `96947b9`：修复 Forge 发布环境的 AE2 菜单与终端 Mixin 挂点，并补齐 MixinExtras 注解处理器。
2. `2b2516a`：统一普通盘与无限域的 SavedData 持久化、精确数量、挂载所有权和迁移恢复，补充失败取盘边界与隔离专服探针。
3. `897f037`：支持 AE2 15.4.10，回移已知字节单位表修复，补充版本隔离的真实提示生成探针。
4. `bd0cf4f`：恢复模型注册、配置生命周期、客户端重绘与初始同步，动态读取结构长度，并补充多方块定向测试和运行探针。
5. 保存上述改动已有的历史验收证据和本轮复核结果；根目录 `logs/` 为测试运行产物，保留在本地并加入忽略规则。

共用的 `build.gradle` 和 `ClientStartupProbe.java` 按所属功能拆分暂存。分批过程中对捕获的 79 个工作区文件逐个校验 SHA-256，内容均保留；每批检查暂存路径和 `git diff --cached --check`。

## 本轮实际验证

验证对象是完整工作区，未分别构建四个中间提交。通过 `build-local.ps1` 使用 Java 17，Gradle 串行运行：

```powershell
.\build-local.ps1 compileJava portingContractsTest portingPlatformTest multiblockAlignmentTest jar --init-script tools/porting/multiblock_alignment.init.gradle --offline --console=plain
python tools/porting/audit_package.py --output validation/batch-commit-package-audit.json
```

Gradle 返回 `BUILD SUCCESSFUL in 10s`，15 项任务中 1 项执行、3 项从缓存恢复、11 项无需重新执行。本轮没有使用 `--rerun-tasks`，编译及部分专项测试复用有效结果。

| 检查 | 结果 |
| --- | --- |
| `compileJava` 与 `jar` | 通过；完整主源码，无新增生产源码排除 |
| `portingContractsTest` | 6 项，0 失败、0 错误、0 跳过 |
| `portingPlatformTest` | 139 项，0 失败、0 错误、0 跳过 |
| `multiblockAlignmentTest` | 6 项，0 失败、0 错误、0 跳过；包含与平台任务重叠的同步用例 |
| JAR 静态审计 | 947 个主源码对应类齐全；1,557 个类均为 Java 17；60 个 Mixin、资源及内嵌依赖无缺失或重复 |
| 主体输入基线 | `porting/source-lock.json` 与本轮起始提交一致 |
| 历史运行证据复核 | AE2 与多方块记录中的 30 份运行日志、截图及其他非构建工件均存在且 SHA-256 一致 |

构建产物为 `build/libs/neoecoae-20.6.0-dev.jar`，SHA-256 为 `9fd16dc833b8fdc27f664f6280af07e41be54e78c523e646852496de11f6e252`。当前机器记录见 [`porting/commit-batches-followup-validation.json`](../../porting/commit-batches-followup-validation.json)。

## 历史结果与验收边界

存储专服 write/read、AE2 三个客户端提示检查、多方块 base/combined 的 288 组夹具与截图均保留为已有检查点，分别见 [SavedData 架构](../storage/SAVED_DATA_ARCHITECTURE_ZH_CN.md)、[AE2 兼容记录](AE2_15_4_10_COMPATIBILITY.md)和[多方块修复记录](MULTIBLOCK_ALIGNMENT.md)。历史测试数量、JAR 哈希和提交状态描述各自记录时的工件，不改写为本轮结果；25 项历史构建输出未与当前可重建文件逐一比较。

完整 `test` 仍受既有 114 处未适配测试 API 编译错误阻断，见 `porting/multiblock-alignment-validation.json` 和 `validation/multiblock-alignment-fixed-base.log`。本轮未重新执行完整测试集，专项通过不代表完整 `test` 或 `build` 通过。本轮未重新启动客户端、专服或执行普通整合包安装验收。
