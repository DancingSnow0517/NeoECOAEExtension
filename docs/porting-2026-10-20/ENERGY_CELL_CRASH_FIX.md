# 能源元件加载崩溃修复

日期：2026-10-21。main 移植基线仍为 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`。

## 原因与修复

用户本次报告为 `crash-2026-10-21_19.05.52-server.txt`。表面上是在准备 `storage_casing` 方块实体，实际是机壳的 AE2 多方块扫描加载相邻能源元件时，LDLib 1 无法为 `energyStored: appeng.me.energy.StoredEnergyAmount` 创建受管字段引用：`No payload found for class appeng.me.energy.StoredEnergyAmount`。

`StoredEnergyAmountMixin` 已提供 NBT 序列化方法，但只声明了 Forge 的 `INBTSerializable<DoubleTag>`。LDLib 1 的 `ITagSerializableAccessor` 通过自身的 `ITagSerializable` 接口匹配类型，因此这些已有方法没有被识别。补上 `ITagSerializable<DoubleTag>`，继续使用已有的 `energyStored` double NBT 和 AE2 的 `getAmount` / `setStored`；容量、充放电和业务持久化实现没有改写。

参考源码在阅读前已更新远端：LDLib 1.20.1 的本地与 `origin/1.20.1` 同为 `1d4e4390ae58d16038845c651e122185c29f334b`；AE2 源码核对的是已锁定依赖 `forge/v15.4.11`，没有变更依赖锁或 main 基线。

## 实际验证

新增显式启用的开发客户端探针 `ManagedEnergyClientProbe`。它从真实注册表创建机器实体并初始化受管字段，随后通过客户端 `gameMode.useItemOn`、预测放置和网络路径放置 L4/L6/L9 能源元件及相邻存储机壳；最后退出到标题界面并重新打开同一个存档。

| 检查 | base | combined |
| --- | --- | --- |
| 全部受管机器字段初始化 | 20 种方块、135 个引用，通过 | 20 种方块、135 个引用，通过 |
| 三档能源元件和相邻机壳的客户端预测及服务端放置 | 通过 | 通过 |
| 模拟充放电不改变能量、实际充放电数量正确 | 通过 | 通过 |
| 原 double NBT 保存、实体重建、档位容量保留 | 通过 | 通过 |
| 实际保存退出并重新进入相同世界 | 通过 | 通过 |
| 重进后能量 | 1200.25 / 2200.5 / 3200.75 AE | 1200.25 / 2200.5 / 3200.75 AE |
| 再次正常保存退出 | 通过 | 通过 |
| Gradle 结果 | `BUILD SUCCESSFUL in 1m 38s` | `BUILD SUCCESSFUL in 1m 33s` |

组合日志明确记录 AdvancedAE、ExtendedAE、ExtendedAE Plus 和 AE2LT 均已加载。两种 profile 串行运行，最终均未出现受管字段缺少 payload 或致命错误。

原工程完整主源码编译及打包首次为 `BUILD SUCCESSFUL in 28s`。最后再次执行 `compileJava jar`，结果为 `BUILD SUCCESSFUL in 3s`，全部任务复用已更新输出；原工程的 `StoredEnergyAmountMixin.class` 与独立副本实际运行通过的编译类逐字节一致，两份重混淆 JAR 内的对应类也逐字节一致。

首次新增探针对普通机壳误用了 `NEManagedBlockEntity` 类型断言，三档能源元件检查已经通过后在机壳断言停止；修正为检查实际方块和实体存在。随后一次原工作目录复测与另一个 `runClient` 的重新编译重叠，出现 `ECOInfiniteStorageMember` 暂时缺类。该次不计为通过；最终两次验证在 `.porting/energy-runtime-20261021` 独立副本中执行，受影响源码与原工程逐字节核对一致。中间失败日志也予以保留。

## 证据与复测

原始崩溃、主工程构建日志、两次最终运行日志及中间失败日志位于 `validation/energy-crash-20261021/`。机器记录为 [`porting/energy-crash-validation.json`](../../porting/energy-crash-validation.json)，包含哈希、逐项断言、两种环境的测试世界和已验证 JAR 路径。

在独立、无其他构建改写其输出的工作目录中，串行执行：

```powershell
.\build-local.ps1 compileJava jar --offline --console=plain
.\build-local.ps1 runClient -PportingProfile=base -PclientSmoke=true -PclientSmokeEnergy=true --offline --console=plain
.\build-local.ps1 runClient -PportingProfile=combined -PclientSmoke=true -PclientSmokeEnergy=true --offline --console=plain
```

探针仅在显式开启开发 smoke 时运行，使用独立新世界；普通 `runClient` 不会自动创建、重开或修改用户存档。本轮验证覆盖真实开发客户端及单人服务端的放置、能量持久化与重进；没有据此宣称成型机器运行、原生鼠标手势、全量业务测试、独立专服或普通 Forge 安装通过。
