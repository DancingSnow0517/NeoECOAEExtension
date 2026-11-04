# CI 构建与缓存

日常 `compat` 工作流只构建生产 JAR、运行兼容性工具自身的 Python 测试和现有 KubeJS 配方 schema 回归，并检查生产 JAR。发布工作流继续执行完整 `build` 与生产 JAR 检查。

`libs/pinned` 使用 `tools/compat/build-dependencies.json` 的哈希作为独立缓存键。每次恢复后仍执行 `bootstrap.py`，重新校验全部 SHA-256；验证通过后才保存依赖缓存。

Gradle 用户目录由 `setup-gradle` 统一缓存，包括 `caches/neoformruntime` 和本地构建缓存。`1.20.1` 分支和默认分支的可信运行可写缓存，PR 和其他分支只读。发布标签允许写入自身作用域，供同一版本重试使用。GitHub 的缓存有分支和标签隔离，发布标签不能直接读取 `1.20.1` 分支的缓存。

## 手动选项

- `build_compat_probe`：默认关闭；开启后，在同一次 Gradle 调用中额外构建、混淆、检查兼容性探针，单独上传 `neoeco-compat-probe`。本选项只生成探针，不启动 GTL 或游戏。
- `cold_cache`：默认关闭；开启后，跳过 Gradle 和固定 Mod 依赖缓存的恢复与保存，用于测量冷构建。

两条工作流均保留 `CI=false`，以确保 ModDevGradle 生成适用于 Java 17 的 Minecraft 编译产物。Gradle 使用 Java 17，串行执行，最多两个 worker。`ci.init.gradle` 提供固定 EMI 依赖替换；构建探针时同时加载 `compat.init.gradle`。

原有单独使用 `-I tools/compat/compat.init.gradle` 的命令也可继续使用，该脚本会自动加载公共依赖配置。

## 比较冷构建和热构建

1. 在同一提交、同一分支上手动运行一次正常构建，完成缓存预热。
2. 开启 `cold_cache` 运行一次，再关闭该选项运行一次，保持 `build_compat_probe` 的选择一致。
3. 比较工作流总耗时、`Setup Gradle` 缓存恢复耗时，以及 Job Summary 中的 Gradle 耗时、任务状态和 Minecraft 阶段记录。
4. 从验证报告 artifact 中读取 `build-summary.json`、`build.log`、Gradle profile 报告和测试报告。缓存已恢复不代表所有任务命中缓存；Minecraft 阶段未出现在日志中也不能单独证明命中缓存。

`compat` 的手动运行始终检出 `1.20.1` 分支。比较期间应保持该分支提交不变，并使用相同的工作流分支运行，以免缓存作用域改变。冷构建不会修改已有缓存。

生产 JAR 上传为 `neoecoae-ci-jars`。构建、单元测试和 JAR 检查不能替代真实游戏交互验证。
