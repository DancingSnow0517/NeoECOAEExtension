# Neo ECO AE Extension documentation / 开发文档

English | 简体中文

These guides cover the Minecraft 1.21.1 / NeoForge source tree. Build versions are defined in [`gradle.properties`](../gradle.properties), with dependencies and the Java toolchain in [`build.gradle`](../build.gradle). Use documentation from the same revision as your source or JAR.

本文档适用于 Minecraft 1.21.1 / NeoForge。构建版本见 [`gradle.properties`](../gradle.properties)，依赖与 Java 工具链见 [`build.gradle`](../build.gradle)。接入时请使用与源码或 JAR 对应版本的文档。

| Read about / 内容 | English | 简体中文 |
| --- | --- | --- |
| API calls and integration contracts / API 调用与接入约定 | [API guide](API.md) | [API 调用指南](API_ZH_CN.md) |
| Addon contracts: power, structures, parts, cells and workstations / 附属扩展契约 | — | [附属 API v1](ADDON_API_ZH_CN.md) |
| ECO planner behavior and diagnostics / ECO 规划器行为与诊断 | [ECO planner](ECO_PLANNER.md) | [ECO 规划器说明](ECO_PLANNER_ZH_CN.md) |
| Host panel appearance extensions / 主机面板外观扩展 | — | [主机面板 API](HOST_PANEL_API_ZH_CN.md) |
| JSON UI resources / JSON 界面资源 | — | [UI JSON](UI_JSON_ZH_CN.md) |
| AE2LT and Thunderbolt crafting integration / 天枢与闪电合成互通 | — | [合成互通约定](AE2LT_THUNDERBOLT_INTEROP_ZH_CN.md) |
| Crafting dispatch input resolution / 合成派发取料性能 | — | [优化与基准](CRAFTING_DISPATCH_PERFORMANCE_ZH_CN.md) |
| Workstation recipe overlap and shared upgrade cards / 工作站配方重合与多卡共享方案 | — | [联动配方审计](WORKSTATION_RECIPE_OVERLAP_AUDIT_ZH_CN.md) |
| Workstation processing time and network FE / 加工时间与网络 FE | — | [实施与游戏验收](WORKSTATION_PROCESSING_TIME_AND_FE_ZH_CN.md) |
| Bigger AE2 storage cells / Bigger AE2 存储矩阵 | — | [存储矩阵联动](BIGGER_AE2_INTEGRATION_ZH_CN.md) |
| Storage scanning and terminal synchronization / 存储扫描与终端同步优化 | — | [性能与网络优化](STORAGE_NETWORK_OPTIMIZATION_ZH_CN.md) |
| Bulk storage and compression / 大宗存储与压缩 | — | [大宗重构说明](ECO_BULK_STORAGE_ZH_CN.md) |
| GuideME page corrections and validation / 游戏指南修订与验证 | — | [GuideME 更新说明](GUIDEME_UPDATE_ZH_CN.md) |

Start with the API guide to request a calculation, submit a job, or integrate a provider/storage cell. Read the planner guide for routes, byproducts, seeds, reusable inputs, cycles, exact amounts, and diagnostics. Java examples are integration skeletons; provide machine transactions and permission checks, register integrations once, and mutate gameplay state on the owning server thread.

调用规划器、提交任务或接入供应器与存储盘，请先读 API 指南。路线、副产物、种子、可复用输入、循环、精确数量及诊断结果见规划器说明。Java 示例用于说明接入方法；机器事务与权限检查由接入方提供，注册操作只做一次，游戏状态修改在所属服务器线程执行。

Gameplay instructions live in the [English guidebook](../guidebook/neoecoae_intro/index.md) and [Chinese guidebook](../guidebook/_zh_cn/neoecoae_intro/index.md). For workstation cards, parallelism, coolant, temperature, and overheating, see the [large workstation guide](../guidebook/neoecoae_intro/large_integrated_working_station.md).

玩法说明统一维护在[中文游戏指南](../guidebook/_zh_cn/neoecoae_intro/index.md)中。升级卡、并行、冷却液、温度和过热恢复见[大型集成工作站](../guidebook/_zh_cn/neoecoae_intro/large_integrated_working_station.md)。

Project introduction / 项目介绍：[English](../README.md) · [简体中文](../README_ZH_CN.md)
