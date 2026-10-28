<p align="center"><img src="images/logo.png" alt="Neo ECO AE Extension 标志"></p>
<h1 align="center">Neo ECO AE Extension</h1>
<p align="center">面向 Minecraft 1.20.1 / Forge 的 ECO 合成、存储与计算扩展。</p>

[![Modrinth](https://img.shields.io/modrinth/dt/udZtKfzP?style=flat&logo=modrinth&label=Modrinth)](https://modrinth.com/project/udZtKfzP) [![CurseForge](https://img.shields.io/curseforge/dt/1460639?style=flat&logo=curseforge&label=CurseForge)](https://www.curseforge.com/projects/1460639)

[English](README.md) | 简体中文

## 概述

Neo ECO AE Extension 是 [ECO AE Extension](https://github.com/sddsd2332/NovaEngineering-ECOAEExtension) 经授权的高版本移植，将[新星工程：世界](https://www.mcmod.cn/modpack/784.html)中的 MMCE 多方块设计带到现代 Applied Energistics 2。项目以 [GPLv3](LICENSE) 发布。

本分支适用于 **Minecraft 1.20.1 / Forge / Java 17**，拥有对应的平台适配、依赖与发布产物。下载时请选择 1.20.1 的 Forge 版本。

## 提供了什么？

- 将合成、存储与计算拆分为独立多方块，并提供多档位核心与部件。
- ECO 合成规划、循环处理、供应器并行与精确大数量订单。
- ECO 存储矩阵、精确数量和内置大宗压缩；Bigger AE2 的数字奇点物品存储矩阵可接入 ECO 大宗压缩。
- 集成工作站与大型集成工作站，以及 AdvancedAE、ExtendedAE、AE2LT 对应的配方联动。
- ExtendedAE Plus 的可选联动，以及 JEI/EMI 配方展示。

## 安装

客户端和服务端均需安装本模组及必要前置，并使用**相同的 NeoECOAE 构建**。同名版本被替换后也应两端一起更新，菜单与网络协议修订可能不同。

| 环境 / 前置 | 支持范围与推荐版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 及以上的 47.x；本工程构建使用 47.4.28 |
| Java | 17 |
| Applied Energistics 2 | 15.4.10 至 15.x；推荐 15.4.11 |
| LDLib | 1.0.33.b 至 1.0.x；推荐 1.0.52.a |
| GuideME | 20.1.14 及以上、低于 20.2.0；推荐 20.1.15 |

可选联动需要安装对应模组及其前置。依赖声明见 [模组元数据](src/main/resources/META-INF/mods.toml)，版本变化见 [更新说明](docs/releases/20.5.2.md)。

## 文档与开发

- API 接入文档：[简体中文](docs/API_ZH_CN.md) · [English](docs/API.md)。
- 版本下载：[GitHub Releases](https://github.com/DancingSnow0517/NeoECOAEExtension/releases)，以及上方的 Modrinth、CurseForge。
- 问题反馈：[本项目 Issues](https://github.com/DancingSnow0517/NeoECOAEExtension/issues)。请提供 Minecraft、Forge、本模组及相关前置版本、复现步骤和必要日志。
- Windows 下使用 `./build-local.ps1 build` 选择 Java 17 并构建。干净检出后，先运行 `python tools/compat/bootstrap.py` 恢复固定版本的构建依赖。

## 项目关系

Neo ECO AE Extension 与 ECO AE Extension 之间仅存在经授权的移植关系，由不同团队独立维护。请将本模组的问题、Bug 和支持请求提交到本仓库，不要提交到原 ECO AE Extension 项目，以免干扰对方维护工作。

ECO AE Extension 由 **Hikari_Nova** 领导开发；Neo ECO AE Extension 由 **DancingSnow0517** 领导开发。项目贡献者见模组元数据与 Git 历史。
