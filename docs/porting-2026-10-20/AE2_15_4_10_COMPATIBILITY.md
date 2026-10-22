# AE2 15.4.10 兼容

最低运行版本改为 `15.4.10`，依赖范围为 `[15.4.10,16)`。推荐版本、日常开发依赖及 `porting/dependencies.lock.json` 仍采用 `15.4.11`。

## 修复依据

用户日志 `Project Infinity 0.1 new/logs/latest.log` 显示，Forge 在依赖检查阶段因 NeoECOAE 声明 `[15.4.11,16)` 而拒绝实际安装的 AE2 `15.4.10`。

已更新官方 AE2 参考仓库远端，并比较以下固定标签：

| 标签 | 提交 |
| --- | --- |
| `forge/v15.4.10` | `b4b08d9941e3faecb520d76be617629bb56661e1` |
| `forge/v15.4.11` | `1c2f96e70ca6b61e8120482923e9775d772fd9e7` |

15.4.10 的 `Tooltips.BYTE_NUMS` 只有四项，而且第四项重复了 GiB，缺少正确的 TiB/PiB/EiB。`getByteAmount` 在数量达到 `1000 GiB`，即 `1,073,741,824,000` 字节时会越过数组末尾。NeoECOAE 的 `ComputationTaskCards.tooltipLines` 会通过 `Tooltips.ofBytes` 显示 CPU 容量，因此放宽版本前需要补齐此修复。

`TooltipsByteUnitsMixin` 在 AE2 `Tooltips` 静态初始化结束时，检查是否恰好存在这张错误表，并替换为与 15.4.11 相同的六项单位表。已修复的表不被替换；Mixin 放在公共配置中。此处只回移单位表修复，其余 15.4.11 修复仍由推荐升级获得。

## 验证入口

`tools/porting/ae2_compatibility.init.gradle` 提供独立验证入口，校验实际发布 JAR 的 SHA-256，再通过 `modImplementation` 声明交给 ModDevGradle 映射。常规构建不加载此脚本。

15.4.10 发布 JAR 放在 `libs/ae2-compatibility/AE2-15.4.10.jar`，可以使用已有整合包中的官方文件。其 SHA-512 已与 `evidence/modrinth-ae2.json` 中官方版本 `7KVs6HMQ` 的记录一致；SHA-256 为 `fbfee05c6674cb6b00fe425e945ca4818d41eb34069b60dcb4a36221a0390fcc`。15.4.11 使用原有 `libs/pinned/AE2-15-release.jar`。

在 PowerShell 中逐条运行，版本参数保留引号：

```powershell
.\build-local.ps1 runClient --init-script tools/porting/ae2_compatibility.init.gradle '-Pae2CompatibilityVersion=15.4.10' '-PportingProfile=base' --offline --console=plain
.\build-local.ps1 runClient --init-script tools/porting/ae2_compatibility.init.gradle '-Pae2CompatibilityVersion=15.4.11' '-PportingProfile=base' --offline --console=plain
.\build-local.ps1 runClient --init-script tools/porting/ae2_compatibility.init.gradle '-Pae2CompatibilityVersion=15.4.10' '-PportingProfile=combined' --offline --console=plain
```

每个验证客户端使用独立的 `run/ae2-compatibility/<版本>/<profile>` 目录。实际到达标题画面后，开发探针调用经过 Forge/Mixin 转换的 AE2 类，验证 14 个容量样本：零、普通容量、旧版越界边界前后、TiB/PiB/EiB、`Long.MAX_VALUE - 1` 和 `Long.MAX_VALUE`。随后构造最大容量的 ECO 任务条目，调用实际的任务卡提示生成器。成功日志为 `AE2_BYTE_TOOLTIP PASS version=... cases=14 taskTooltip=true`，之后客户端自动正常退出。普通启动不启用探针。

Thunderbolt 的实际发布 JAR 包含 `TooltipsByteAmountMixin`，将恰好等于 `Long.MAX_VALUE` 的容量视为无限并显示 `∞`。组合环境保留这一行为；`Long.MAX_VALUE - 1` 仍验证为有限的 `8E`。基础环境的两个最大值都显示 `8E`。

本次验证覆盖开发客户端加载及提示组件生成。整合包内鼠标悬停、普通安装和完整合成任务属于独立验收项。

## 2026-10-21 验证结果

Java 17、Minecraft 1.20.1、Forge 47.4.28 下串行完成以下检查：

| AE2 | 环境 | 结果 |
| --- | --- | --- |
| 15.4.10 | base | 实际客户端到达标题画面；14 个容量样本及最大容量任务卡提示通过 |
| 15.4.11 | base | 实际客户端到达标题画面；14 个容量样本及最大容量任务卡提示通过 |
| 15.4.10 | combined | AdvancedAE、ExtendedAE、ExtendedAE Plus、AE2LT 及必要前置共装；14 个容量样本及最大容量任务卡提示通过，保留 Thunderbolt 的无限容量显示 |

共验证 42 个容量样本和 3 次任务卡提示生成，三个客户端均正常退出。最终常规 `compileJava jar` 成功，JAR 静态审计确认最低版本元数据、新 Mixin 注册及类文件均已打包，全部 1,555 个类采用 Java 17 字节码；没有缺失资源、未打包 Mixin 或重复条目。

机器记录为 `porting/ae2-15-compatibility-validation.json`，其中保存各次日志位置、运行日志哈希、JAR 哈希及静态审计结果。构建产物为 `build/libs/neoecoae-20.6.0-dev.jar`。
