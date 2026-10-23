# 20.4.2 玩家存储文件复核与修复副本

日期：2026-10-23。结论：**当前不能认定完整兼容 20.4.2；截图中的存储盘确实加载失败。** 本轮定位了玩家实例中的实际文件，并验证了独立修复副本。原世界和普通安装实例的 Mod JAR 均未修改。

## 截图故障的直接原因

玩家实例日志记录盘 `0bb1e818-0aa9-4888-aee4-325fa7fcf8fd` 的失败原因：

```text
Invalid ECO disk format or identity; original file retained
```

该盘当前 `.dat` 的 `data` 只有 `format=1` 和空的 `entries`，没有 `cell` UUID 和 `revision`。正式 `20.4.2` 普通盘格式要求这些身份、修订字段；当前文件也没有新版 `id`。加载器因此拒绝挂载，盘呈现不可写状态，界面同时出现“存储容量已满”和“存储数据尚未成功保存”。不能据此把该盘解释为正常的空盘或容量不足。

该文件最后写入时间为 2026-10-18 19:48:39，同目录 `.dat.20.4.2.bak` 的时间为 2026-10-18 15:45:22。保留的备份含匹配 UUID 和合法修订号。现有证据不能确定哪次历史运行移除了当前文件头，不能把这份损坏文件当作正式标签原始输出格式。

实际文件审计结果：

| 文件 | 结果 |
| --- | --- |
| 9 个普通盘主文件 | 6 个具备合法旧文件头，3 个缺失身份和修订字段 |
| 3 个缺头盘对应的 `.20.4.2.bak` | UUID 与文件名匹配；库存列表逐标签、逐类型相等，可以只恢复文件头 |
| 2 个旧无限域主文件 | 记录编码为 `neoecoae_record_encoding=1`，分别带 `.commit` 文件 |
| 1 个旧无限域主文件 | 非 GZIP；按原始 NBT 解析也失败，保留损坏文件并阻断挂载 |
| 1 个既有新版 `_v3.dat` | 继续使用既有新版来源，保持对旧文件的优先级 |

三个缺头盘为 `0bb1e818-0aa9-4888-aee4-325fa7fcf8fd`、`326ce289-1d25-4683-974e-91b05cb215b7`、`a25174d0-8c3a-4741-be8d-ed851585e99a`。9 个普通盘主文件及其匹配旧备份的库存均为空；本轮实际文件验证不证明带有库存的其他玩家世界已经验收。

损坏历史域为 `69c1104e-21ff-4f17-8d87-d370779a3110`，在本世界目录中未发现对应可用备份。本轮没有确认它仍被当前控制器引用，不能据此断言玩家正在使用的无限库存丢失。专服验证只确认它不会被当作可写空域覆盖。

## 本轮实现

1. 新增 `Legacy2042DomainSnapshot`，读取旧域的原生 SavedData、逐记录编码、`.delta.dat` 增量及 `.commit` 高水位。验证记录 CRC32、完整 NBT、可选 key 指纹、域身份、版本、修订和增量基线，按旧文件契约处理替换、删除及已经合并的旧增量。仅剩历史侧文件且不能证明当前快照完整时停止导入。
2. 未知编码、记录损坏、重复键、提交修订领先于快照等情况均拒绝导入，保留源文件。正常加载不选择旧备份作为库存来源；已有新版文件依然优先。
3. 新增只读审计工具 `tools/porting/audit_legacy_storage.py`。只有当前文件恰好缺头、备份身份正确、没有额外待核账状态且库存逐标签相等时，才允许向**世界外新目录**导出修复副本。工具不会写回玩家世界，不覆盖已有导出目录，并核对主文件和侧文件哈希。
4. 新增显式开发专服探针 `LegacyPlayerStorageProbe`，用导出的实际数据文件验证真实驱动器挂载、存取、拔盘、重插、保存和新 JVM 重读。探针仅复用已有开发诊断入口；发布环境不会执行。

普通盘与正式旧域格式参照 `20.4.2` 提交 `f1739d1f1d23b889649aa2804a3d7e493c919c4e`；记录、增量、提交标记契约参照历史 `1.20.1` 提交 `a6b3d776d677c3093954081d40d4871592864c3b`。两者用于兼容读取，不替换锁定 main 的业务、算法和当前持久化后端。

## 验证及工件

| 层级 | 本轮结果 |
| --- | --- |
| 完整主源码编译、JAR | 通过；JAR 静态检查通过 |
| `portingPlatformTest` | 189 项通过，0 失败、0 跳过；包含新增 9 项记录/增量/提交标记回归 |
| `portingContractsTest` | 6 项通过，0 失败、0 跳过 |
| Python 修复导出工具 | 5 项通过，覆盖库存不等、错误 UUID、额外状态、源文件变化和输出边界 |
| 基础专服首次升级 | 通过：9 个实际文件副本均可写，3 个补头盘可正常存取及拔盘重插 |
| 新 JVM 冷启动重读 | 通过：9 个盘保持可用；损坏域仍被拒绝写入，旧域原件保持原字节 |
| 玩家原文件完整性 | 18 份文件 SHA-256 全部与导出前一致 |
| 完整玩家旧世界、客户端界面、四附属组合、本机普通安装 | 本轮未执行 |

静态名称对照中，正式 `20.4.2` 的 66 个方块状态名、156 个物品模型名均仍存在。这只证明资源名称存在，不能代替注册、NBT 恢复、渲染或交互验收。

机器记录为 [本轮验证记录](../../porting/legacy-player-storage-2026-10-23.json)。实际文件审计、升级/重读及包检查见 [证据目录](evidence/legacy-player-2026-10-23/)。

修复副本目录为 `.porting/legacy-player-world-20261023-verified/`：

- `originals/data/` 保留审计时的原始文件，包括损坏主文件与旧备份。
- `prepared/data/` 仅替换 3 个已证明库存相同的普通盘文件，其余导出文件保持原字节。
- `manifest.json` 记录每份文件的哈希及修复条件。

独立构建工件为 `.porting/legacy-validation-build-20261023/libs/neoecoae-20.6.0-dev.jar`，SHA-256 为 `f9a981560a824d229a16efbd66e043293182b4cd0b969d5bcfc50141d93ef613`。这是本轮工作区的开发工件，尚未替换玩家实例中的 JAR，也未进行普通安装客户端验收。

## 复现方式

审计/导出使用 Python 的 `nbtlib`。默认仅审计；导出目录必须新建且在原世界之外。

```powershell
python tools/porting/test_audit_legacy_storage.py
python tools/porting/audit_legacy_storage.py '<玩家世界路径>' --report '.porting/player-storage-audit.json'
python tools/porting/audit_legacy_storage.py '<玩家世界路径>' --prepare '.porting/<新的导出目录>'
```

本轮检测到其他 Gradle 任务使用共享 `build/`；首次共享输出构建因缓存打包文件消失而失败，随后全部验证串行切换到 `.porting/legacy-validation-build-20261023/`。本轮使用的独立输出 init 脚本为：

```groovy
gradle.beforeProject { project ->
    if (project.path == ':') {
        project.layout.buildDirectory.set(project.file('.porting/legacy-validation-build-20261023'))
    }
}
```

```powershell
# 将上述片段存为 .porting/legacy-validation-output.init.gradle。
./build-local.ps1 -GradleArguments @('--init-script', '.porting/legacy-validation-output.init.gradle', 'compileJava', 'portingPlatformTest', 'portingContractsTest', 'jar', '--offline', '--no-build-cache', '--console=plain')

# 首次选择新的独立测试世界；第二次沿用同一世界并将 player-upgrade 改为 player-read。
./build-local.ps1 -GradleArguments @('--init-script', '.porting/legacy-validation-output.init.gradle', '--init-script', 'tools/porting/legacy_2042_acceptance.init.gradle', '-Plegacy2042Smoke=player-upgrade', '-PlegacyPlayerSnapshotDirectory=.porting/legacy-player-world-20261023-verified', '-PstorageAcceptanceWorld=legacy-2042-acceptance-player-20261023', 'runServer', '--offline', '--no-build-cache', '--console=plain')
```

专服导入的是单独复制的数据文件和新建驱动器，未加载完整玩家世界及全部整合包。实际玩家世界应用修复副本、缺失历史无限域恢复、旧部分回填事务核账、未转为 SavedData 的历史 File/WAL 导入，以及其他机器/任务/UI 的旧档验收仍待完成。
