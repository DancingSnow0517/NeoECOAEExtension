# 20.4.2 旧存档格式兼容

**2026-10-23 实际玩家文件复核：** 截图中的盘确实因旧主文件缺少身份字段而无法挂载，另有历史无限域记录/增量/提交标记读取缺口。本轮补充读取契约，导出 3 个库存逐标签相等的补头副本，并通过实际文件副本的独立专服升级与新 JVM 重读。18 份原始文件哈希保持不变；尚未写回原世界，也未完成完整玩家世界或客户端验收。详见 [实际文件复核与修复副本](PLAYER_STORAGE_COMPATIBILITY_2026_10_23_ZH_CN.md)。下文 2026-10-22 的格式 fixture 结果继续作为独立历史证据。

日期：2026-10-22。源版本为正式标签 `20.4.2`，提交 `f1739d1f1d23b889649aa2804a3d7e493c919c4e`；目标为本工程的 Forge 1.20.1。主业务继续使用 `porting/source-lock.json` 锁定的 main 及当前 SavedData 后端，本次只增加格式读取与转换契约。

## 已实现的转换

| 内容 | 旧格式 | 当前处理 |
| --- | --- | --- |
| 普通盘身份与库存 | 物品 `eco_cell_id`；`data/neoecoae_cells/<uuid>.dat`，`format=1`、身份字段 `cell`、逐键 long 数量 | 保留 UUID、完整 AEKey/NBT 和数量，转换到当前格式 2；先验证并保存旧文件原件，再原子替换。当前引用优先于滞后的旧标签。 |
| 历史物品内库存 | `eco_cell_contents` GenericStack 列表 | 无权威文件时导入，先归档完整物品并持久化库存，再替换引用及删除物品内库存；已有权威文件、备份或转换归档时不重放旧物品库存。 |
| 配置与成员身份 | 原生 AE2 过滤/升级数据、`FuzzyMode`/`fuzzyMode`、`neoecoae_infinite_matrix_id` | 保留原物品配置，读取旧模糊模式；旧矩阵身份映射到当前成员身份，结束成员生命周期时清理旧身份。 |
| 无限域 | `data/neoecoae_infinite/domain_<uuid>.dat`，`format=2`；`amount_long`/规范 BigInteger 字节数组 `amount_wide` | 校验域 UUID、版本、数量和重复键后转换到当前 `_v3` 文件；精确数量不截断，旧域文件保持原字节。已有当前文件时不回退旧库存。 |
| 迁入回执 | `transfer_receipts` 中的 UUID 与可选 `contents_sha256` | 保留原回执及证明，核对旧逐键、按位置整盘和按矩阵身份整盘回执；只有证明匹配的资源才跳过重复导入。当前事务将兼容身份及源快照一并持久化，跨中断重试只退休一次源盘。 |
| 控制器 | `priority`、`infiniteComponentSlot`、既有域及模式字段 | 恢复优先级、Forge 元件槽、域归属和模式。组件数据无法解码时保留原始载荷并停止模式转换。 |
| 智能总线 | `patternInventory`、`patternInventoryPages` | 原槽位、完整样板 NBT 和页数一起恢复；支持第八页和旧拆卸物品。保留旧页数并同步到客户端，恢复后由现有样板目录向网络发布。 |

普通盘原件位于世界的 `data/neoecoae_20_4_2_archive/cells/<uuid>.dat`，物品内库存原件位于 `data/neoecoae_20_4_2_archive/items/<uuid>.dat`。这些归档用于保留原件，不作为缺失当前库存时的自动回退来源。

## 故障及保留边界

- 文件缺失、损坏、UUID 不匹配、重复条目、未知资源或写入失败时保留原件，拒绝转换成可写空库存。归档或快照写入失败可以重试；世界自动保存先补写成功的情况也能在随后挂载时接管。
- 总线存在未知物品、重复或越界槽位、异常页数时保留整份原始样板载荷，锁定总线插入和合成发布；拆卸携带原始载荷，再放置仍保留。不会只导入能识别的部分再丢弃其余样板。
- **旧版已经部分回填到普通盘的退出无限模式事务尚不自动恢复。** `ecoRestoreReceipts` 及相关库存会被保留，盘的阻断标记写入当前 SavedData 和物品；宿主发现该状态时将域的只读屏障持久化。重启、盘或宿主移动不会解除该屏障。本轮没有实现这些旧回填回执与当前恢复计划的自动核账和解锁。
- 早期 File/WAL 数据若尚未被 20.4.2 转成其 SavedData 格式，本轮没有新增对应文件后端转换器。仅有旧 UUID 而缺少可识别快照时会报错并锁定，需单独核对这些历史数据。
- 本轮覆盖上述存储和总线契约；其他机器运行中任务、第三方 Mod 自有存档及移植范围外物品不能据此认定兼容。`20.4.2` 玩家世界副本、客户端人工交互和普通整合包安装仍需分别验收。

## 验证证据

机器记录见 `porting/legacy-20-4-2-validation.json`，JAR 检查与专服报告见本目录 `evidence/legacy-20-4-2/`。

| 验证层 | 本轮结果 |
| --- | --- |
| 完整 `compileJava` 与 JAR | 通过；951 个主源码对应类齐全，1,561 个类均为 Java 17 |
| `portingPlatformTest` | 176 项通过，0 失败、0 跳过；其中新增旧格式兼容测试 37 项 |
| `portingContractsTest` | 6 项通过，0 失败、0 跳过 |
| 基础专服升级 → 新 JVM 重进 | 通过，包含旧回填只读屏障持久化 |
| 四附属组合专服升级 → 新 JVM 重进 | 通过，覆盖常规存储及总线转换 |
| 既有新版存储世界专服回归 | 通过，含普通盘、大数、迁入中断恢复和缺失文件保护 |
| 客户端人工交互、玩家旧世界、普通安装 | 本轮未执行 |

2026-10-22 验证工件为 `build/libs/neoecoae-20.6.0-dev.jar`，当时 SHA-256 为 `6ca459db8b1a3e1c350a5e5158e33573318abe709788becc34246709ffea1972`。源码、测试及分层证据纳入本地提交，不自动推送。

**2026-10-23 提交前复核：** 通过 `build-local.ps1` 使用 Java 17 串行强制重新执行 `compileJava portingPlatformTest portingContractsTest jar --offline --rerun-tasks --console=plain`，13 个任务全部执行成功。176 项平台测试和 6 项契约测试共 182 项通过，0 失败、0 跳过，其中旧格式兼容测试 37 项。重新生成的 JAR 通过静态检查，951 个主源码对应类齐全、1,561 个类均为 Java 17，资源、Mixin 和内嵌依赖未发现缺项。新工件 SHA-256 为 `a7a509a5ac4641eadcbfc1fc2aa412639e6ff0d655b12ecf3a94b35d2358b6f5`；报告见 [提交前 JAR 检查](evidence/legacy-20-4-2/precommit-package-audit-2026-10-23.json)，日志 `validation/precommit-20261023.log`。本次未重新启动客户端或专服，前表中的运行结果仍为 2026-10-22 的旧格式 fixture 验证，不能解释为本次复测或玩家旧档验收。

本轮使用按正式标签源码构造的旧格式 fixture，运行于实际 Forge/AE2/LDLib 方块实体及世界中；没有运行旧版 JAR 生成玩家世界，不能把该结果写成真实玩家旧档验收。

验证覆盖：

1. 原生 `DimensionDataStorage` 保存/重读、旧 UUID/物品库存导入、完整 AEKey/NBT、旧文件原件、滞后物品、归档/写入失败重试、未知资源与损坏文件保护。
2. 180–240 位无限数量、旧域路径、旧回执与摘要不匹配拒绝、已迁入资源的重复加载，以及当前迁入事务所有中断阶段的恢复。
3. 真实总线的第 0 和第 449 槽、八页、两份可解码合成样板及网络供应目录；旧拆卸物品导入、再次拆卸时的掉落守恒，以及未知样板载荷的拆装保护。
4. 实际驱动器取盘/重插、控制器 64 个元件/域/优先级、升级后正常保存退出和新 JVM 冷启动。基础及四附属组合分别记录。
5. 基础专服另验证旧回填回执的只读盘和域在冷启动后仍受保护；这只证明屏障持久化，不代表旧回填事务已经恢复。

运行方式（Java 17，Gradle 串行）：

```powershell
./build-local.ps1 compileJava portingPlatformTest portingContractsTest jar --offline --console=plain
./build-local.ps1 -Plegacy2042Smoke=upgrade -PstorageAcceptanceWorld=legacy-2042-acceptance-<新的测试名> runServer --offline --console=plain
./build-local.ps1 -Plegacy2042Smoke=read -PstorageAcceptanceWorld=legacy-2042-acceptance-<同一个测试名> runServer --offline --console=plain
python tools/porting/audit_package.py --output docs/storage/evidence/legacy-20-4-2/package-audit.json
```

四附属组合可使用下列参数复用已配置的本地开发服务端目录，同时选择独立的测试世界：

```powershell
./build-local.ps1 -GradleArguments @('--init-script', 'tools/porting/legacy_2042_acceptance.init.gradle', '-PportingProfile=combined', '-Plegacy2042Smoke=upgrade', '-PstorageAcceptanceWorld=legacy-2042-acceptance-<新的组合测试名>', 'runServer', '--offline', '--console=plain')
# 首次升级正常退出后，使用同一世界名将 upgrade 改为 read，进入新 JVM 验证。
```

探针仅在开发环境和显式属性下启用，要求独立的 `legacy-2042-acceptance-*` 世界。`upgrade` 拒绝复用已有 fixture；禁止指向玩家正在使用的世界。
