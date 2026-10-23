# 1.20.1 默认构建修复与验证（2026-10-23）

默认入口 `build-local.ps1 build` 已通过。完整 `src/test/java` 继续由默认 `test` 编译和执行。

## 失败原因与修复

最初复现停在 `compileTestJava`，共 84 个编译错误。生产源码与 JAR 任务当时已能完成，导入测试仍引用新版 Minecraft、NeoForge、AE2、配方序列化及已裁剪联动的接口。

- 将测试适配到 Java 17、Forge 1.20.1 和锁定依赖的真实签名，包括 NBT、配方 JSON/网络往返、网络版本校验、库存、界面和反射契约。
- 为测试补齐 Minecraft 客户端资源和与当前操作系统匹配的 LWJGL 原生库；客户端资源路径使用 Gradle 的实际 `buildDirectory`，兼容独立输出目录。
- 完善独立 JVM 的 Forge 能力、配方材料序列化器和 AE 键注册表初始化；键注册表只安装一次。
- 修正 CPU 列表 Mixin：AE2 15 的 `InfoBar.add` 接收文本、颜色和缩放三个参数。字节码契约与格式归属测试覆盖该调用。
- 修正空物品显示编码：`NEStacks.save` 对空栈生成空 CompoundTag，保留空样板页压缩及交互请求契约，并增加空栈往返回归。
- 已裁剪的联动改为验证缺席或拒绝行为；存储盘拆解测试同时覆盖客户端、占用、不可用、只读及空盘状态。

## 验证结果

| 任务 | 通过 | 条件跳过 | 失败 |
| --- | ---: | ---: | ---: |
| 默认 `test` | 1506 | 12 | 0 |
| `portingPlatformTest` | 189 | 0 | 0 |
| `portingContractsTest` | 6 | 0 | 0 |
| `upstreamSyncTest` | 324 | 9 | 0 |

以上按 JUnit XML 报告统计。专项与默认测试存在重叠，不相加作为去重后的测试数量。跳过项包括当前依赖未提供的 AE2LT 精确 FX 合约、未安装的可选联动、JECharacters，以及未启用的场景/性能验收。

本轮先完成独立输出目录的 `clean build` 和三组专项，耗时 3 分 19 秒，19 个任务全部执行，关闭 Gradle 构建缓存。随后使用默认输出目录执行：

```powershell
.\build-local.ps1 build --console=plain
```

结果为 `BUILD SUCCESSFUL in 59s`。随后针对并行会话更新的诊断探针补跑 `compileJava jar`，结果为 `BUILD SUCCESSFUL in 9s`。最终打包期间核对了 3695 个构建输入，未发现文件变更或新增源码。

独立验证通过 `--init-script .gradle/build-fix-2026-10-23/isolated.init.gradle` 和独立项目缓存执行；该脚本只将构建输出设置为 `.porting/build-fix-validation-20261023`，任务与源码范围保持标准配置。完整任务序列为 `clean build portingPlatformTest portingContractsTest upstreamSyncTest --no-build-cache`。

## 产物与记录

- [最终 JAR](../../build/libs/neoecoae-20.6.0-dev.jar)：7,040,183 字节。
- SHA-256：`3242d3bec2797c46583acd874d557fd4da4ca04313d8b19028a5232b11fdedcb`。
- [机器验证记录](../../porting/build-fix-validation-2026-10-23.json)。
- [默认构建日志](../../build/reports/build-fix-2026-10-23/default-build.log)、[完整清理构建日志](../../build/reports/build-fix-2026-10-23/clean-build.log)、[最终打包日志](../../build/reports/build-fix-2026-10-23/package-refresh.log)。
- [JAR 检查记录](../../build/reports/build-fix-2026-10-23/package-audit.json)：974 个生产源码入口均有对应类，1603 个打包类均为 Java 17 字节码；Mixin、refmap、内嵌依赖和必要资源检查通过。

本任务的验收范围为编译、自动化测试与 JAR 内容检查；未执行游戏内交互或普通启动器安装验收。
