# 20.2.10 联动存储盘检查

本次按 `porting/source-lock.json` 固定的 main 提交
`dc8344f48ee866e57e02c285fdda5e0fb5da9421` 恢复联动存储功能，版本改为 `20.2.10`。
大集成工作站仍保持常规、AAE、EAE、AE2LT 四类。

## 清单与检查范围

共检查 **73 种条件注册的联动盘**。每种盘都核对组装、空盘拆解、外壳配方、依赖条件、物品模型及中英文名称。
逐盘 ID、依赖、配方路径和运行验证范围见
[`optional-storage-cell-audit.json`](../porting/optional-storage-cell-audit.json)。

| 类别 | 数量 | 完整生产专服写入与冷启动读回 |
| --- | ---: | --- |
| 魔力，含 MEGA | 4 | 通过 |
| FE，含 MEGA、Bigger AE2 | 6 | 通过 |
| 化学品，含 MEGA、Bigger AE2、放射性盘 | 9 | 通过 |
| 魔源，含 MEGA、Bigger AE2 | 6 | 通过 |
| 空气，含 MEGA | 4 | 通过 |
| 物品 MEGA、Bigger AE2、长整型压缩盘 | 4 | 通过 |
| 流体 MEGA、Bigger AE2 | 3 | 通过 |
| 雷电 | 3 | 通过 |
| Omni、复杂 Omni、量子 Omni | 9 | 通过 |
| 乏核废料 | 3 | 通过 |
| 经验，含 MEGA、Bigger AE2 | 6 | 当前安装缺少前置，仅完成静态核对与缺依赖不注册检查 |
| 灵魂，含 MEGA | 4 | 同上 |
| 物质，含 MEGA | 4 | 同上 |
| 应力，含 MEGA | 4 | 同上 |
| 数据，含 MEGA | 4 | 同上 |
| **合计** | **73** | **51 种完成实际存取与冷启动，22 种尚无正向运行验收** |

缺少的五个前置是 `appex`、`appliedsoul`、`apprep`、`appliedcreate`、`data_energistics`。
已更新并核对对应官方源码中的资源键；其中 AppliedSoul 的 Forge 1.20.1 分支使用
`appliedsoul:soulkey`。`ifsoulsdisks` 不等同于 AppliedSoul。
这些前置未装入本次验证安装，不能据此宣称对应 22 种盘已通过实际运行。

## 修复内容

- 恢复魔力、FE、化学品、魔源、空气、经验、灵魂、物质、应力、数据、MEGA、Bigger AE2
  和 Omni 乏核废料的条件注册及存储适配。
- 恢复并转换 160 份配方资源，包括组装、拆解及外壳；补齐普通与复杂 Omni 的外壳和拆解配方，
  以及 MEGA 长整型压缩盘使用的基础散装盘外壳配方。
  所有材料、数量和消耗以锁定的 main 为基准，转换路径和哈希保存在
  [`optional-storage-resource-conversion.json`](../porting/optional-storage-resource-conversion.json)。
- 魔源外壳改用 Ars Nouveau 1.20 原生的 `reagent` 数组和 `output` 字段，保留 5000 Source
  及原有八个基座材料；魔力外壳保留 100000 Mana。
- 适配 MEGA Cells 2.4 的压缩链、化学品规则和 Forge API；不启用 main 中未启用的压缩卡功能、
  L4/L6 MEGA 基础盘或解压服务注册。
- 用 1.20 原生 NBT 保存 Bigger AE2 Digital Singularity 的 `BigInteger` 数量，并让精确读取、提取
  和宿主统计使用实际内容；修复 MEGA 长整型压缩盘的精确访问及空标签与无标签键的冷加载差异。
- 原先六个失败测试来自 Mockito 跳过字段初始化；改用已初始化的测试夹具，保留生产存储逻辑。

## 验证

使用 Java 17 和 `build-local.ps1`，在独立工作树中串行运行 Gradle。
最终 `build --offline` 通过：**1561 项测试，1549 通过、12 跳过、0 失败、0 错误**。
JAR 内版本为 `20.2.10`，1687 个类均为 Java 17 字节码；73 种盘的物品模型、中英文名称
以及全部配置的 Mixin 类均齐全。

实际安装官方 Forge **1.20.1-47.4.28**，直接加载发布 JAR 和校验过的官方依赖 JAR，
使用隔离的服务器目录及新存档。验收包括注册与缺依赖不注册、外壳/组装/拆解配方、
驱动器模型映射、模拟插入不改变内容、实际插入与提取、持久化及退出进程后的冷加载。
Digital Singularity 另存入 `10^45 + 17` 个单位并核对精确数量。

最终三个安装组合的结果和 JAR 哈希见
[`verification.json`](storage/evidence/optional-cells-20.2.10/verification.json)。
完整组合包含 AE2、MEGA、AppBot、AppFlux、AppMek、Omni、Bigger AE2、AppliedPneumatics、
ArsEnergistique、AE2LT 及其必要前置。

`storageAll` 开发专服会触发上游 AppBot 1.5.2 的开发环境客户端 lambda 校验错误；
最终完整组合改用实际生产安装验证，未修改上游 JAR，也未关闭联动功能。
日志中仍有原有 LDLib 服务端扫描及上游可选联动诊断；验收以服务器进入世界后的逐盘 PASS
报告为准，不把进程退出码当作功能通过依据。

本次没有验证客户端实际画面、玩家手动操作或普通整合包覆盖安装。
报告中的模型检查证明资源及注册映射存在，不代表客户端视觉验收。

## 复现完整组合

先运行 `python -X utf8 tools/porting/prepare_dependencies.py` 校验锁定的依赖，再运行
`build-local.ps1 build --offline`。生产服务器安装的依赖清单见
[`production-dependencies.json`](storage/evidence/optional-cells-20.2.10/production-dependencies.json)。

只在隔离测试存档内创建文件 `neoecoae-optional-storage-acceptance`，
并以 JVM 参数 `-Dneoecoae.optionalStorageSmoke=write` 启动该存档。
检查 `optional-storage-write.json` 的 `result` 为 `PASS`，服务器自动停止后，
改用 `-Dneoecoae.optionalStorageSmoke=read` 启动同一存档，检查读回报告。
写入阶段要求新的验收存档，避免覆盖已有测试夹具。
普通启动不运行此探针。
