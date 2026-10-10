# Neo ECO AE Extension documentation / 开发文档

English | 简体中文

This documentation describes the **Minecraft 1.21.1 / NeoForge** source tree reviewed on **2026-10-10**. The checked-in build declares NeoECOAE `21.2.1`, NeoForge `21.1.251`, AE2 `19.2.18`, and Java `21`. It includes the current working-tree planner changes; the version in `gradle.properties` does not establish that every documented behavior is in a published JAR. Use the matching source/build when integrating.

本文依据 **2026-10-10** 的 **Minecraft 1.21.1 / NeoForge** 当前源码编写。构建声明版本为 NeoECOAE `21.2.1`、NeoForge `21.1.251`、AE2 `19.2.18`、Java `21`。文档包含当前工作区的规划器改动；`gradle.properties` 中的版本号不代表这里描述的所有行为已经发布。接入时请使用对应源码构建的版本。

| Read about / 内容 | English | 简体中文 |
| --- | --- | --- |
| API calls and integration contracts / API 调用与接入约定 | [API guide](API.md) | [API 调用指南](API_ZH_CN.md) |
| ECO planner behavior and implementation / ECO 规划器行为与实现 | [ECO planner](ECO_PLANNER.md) | [ECO 规划器说明](ECO_PLANNER_ZH_CN.md) |

Start with the API guide to request a calculation, inspect its result, submit a job, or integrate a provider/storage cell. Read the planner guide to understand route selection, byproducts, seeds, reusable inputs, cycles, exact amounts, execution phases, and diagnostic results. Each guide links to the implementation that defines its contract.

调用规划器、查询结果、提交任务或接入供应器与存储盘，请先读 API 指南。路线选择、副产物、种子、可复用输入、循环配方、精确数量、执行阶段及诊断结果，请读规划器说明。两份指南都附有相应源码链接。

The Java examples are integration skeletons. Supply your own machine transactions and permission checks, register integrations once, and keep gameplay mutations on the owning server thread. The old investigation notes and subsystem snapshots are intentionally not part of this rebuilt documentation.

Java 示例用于说明接入方法；机器事务与权限检查由接入方提供。注册操作只做一次，游戏状态修改在所属服务器线程执行。本次重建集中维护上述中英文指南，不恢复已删除的历史调查记录和旧子系统快照。

Project introduction / 项目介绍：[English](../README.md) · [简体中文](../README_ZH_CN.md)
