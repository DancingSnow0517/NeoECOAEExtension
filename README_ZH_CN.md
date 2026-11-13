<p align="center"><img src="/images/logo.png" alt="Logo"></p>
<h1 align="center">Neo ECO AE Extension</h1>
<p align="center">为 Applied Energistics 2 带来可扩展的合成、存储与计算。</p>

<p align="center">
  <a href="https://modrinth.com/mod/neoecoae"><img src="https://img.shields.io/modrinth/dt/udZtKfzP?style=for-the-badge&amp;logo=modrinth&amp;logoColor=white&amp;label=Modrinth&amp;color=1bd96a" alt="Modrinth downloads"></a>
  <a href="https://www.curseforge.com/projects/1460639"><img src="https://img.shields.io/curseforge/dt/1460639?style=for-the-badge&amp;logo=curseforge&amp;logoColor=white&amp;label=CurseForge&amp;color=f16436" alt="CurseForge downloads"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/LICENSE-GPL--3.0-f97316?style=for-the-badge" alt="License: GPL-3.0"></a>
</p>
<h1 align="center">

[English](/README.md) | 简体中文 | [繁體中文](/README_ZH_HK.md) | [文言文](/README_LZH.md)

</h1>

<h2 align="center" id="supported-versions">版本支持与下载</h2>

<table align="center">
  <thead>
    <tr><th align="center">Minecraft</th><th align="center">加载器</th><th align="center">下载</th></tr>
  </thead>
  <tbody>
    <tr>
      <td align="center">1.20.1 (GTL)</td>
      <td align="center">Forge</td>
      <td align="center"><a href="https://modrinth.com/mod/neoecoae/versions?g=1.20.1"><img src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fapi.modrinth.com%2Fv2%2Fproject%2FudZtKfzP%2Fversion%3Fgame_versions%3D%255B%25221.20.1%2522%255D&amp;query=%24%5B0%5D.version_number&amp;label=release&amp;color=007ec6" alt="1.20.1 release"></a></td>
    </tr>
    <tr>
      <td align="center">1.21.1</td>
      <td align="center">NeoForge</td>
      <td align="center"><a href="https://modrinth.com/mod/neoecoae/versions?g=1.21.1"><img src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fapi.modrinth.com%2Fv2%2Fproject%2FudZtKfzP%2Fversion%3Fgame_versions%3D%255B%25221.21.1%2522%255D&amp;query=%24%5B0%5D.version_number&amp;label=release&amp;color=007ec6" alt="1.21.1 release"></a></td>
    </tr>
    <tr>
      <td align="center">26.1.2</td>
      <td align="center">NeoForge</td>
      <td align="center"><a href="https://modrinth.com/mod/neoecoae/versions?g=26.1.2"><img src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fapi.modrinth.com%2Fv2%2Fproject%2FudZtKfzP%2Fversion%3Fgame_versions%3D%255B%252226.1.2%2522%255D&amp;query=%24%5B0%5D.version_number&amp;label=release&amp;color=007ec6" alt="26.1.2 release"></a></td>
    </tr>
    <tr>
      <td align="center">1.20.1-GTO</td>
      <td align="center">Forge · GTO</td>
      <td align="center"><a href="https://github.com/Yang120231/NeoECOAEExtension/releases"><img src="https://img.shields.io/github/v/release/Yang120231/NeoECOAEExtension?label=release" alt="1.20.1-GTO release"></a></td>
    </tr>
    <tr>
      <td align="center">1.7.10-GTNH</td>
      <td align="center">Forge · GTNH</td>
      <td align="center"><a href="https://github.com/Yang120231/GTNH-ECO-1.7.10/releases"><img src="https://img.shields.io/github/v/release/Yang120231/GTNH-ECO-1.7.10?label=release" alt="1.7.10-GTNH release"></a></td>
    </tr>
  </tbody>
</table>

## 概述

API 文档：[简体中文](docs/API_ZH_CN.md) · [English](docs/API.md)。

Neo Eco AE Extension 是 [Eco AE Extension](https://github.com/sddsd2332/NovaEngineering-ECOAEExtension)的高版本移植, 添加了来自[新星工程：世界](https://www.mcmod.cn/modpack/784.html)中的 MMCE 多方块结构。在获得原作者授权的前提下，本项目以 GNU 通用公共许可证第 3 版（GPLv3） 发布。

## Neo Eco AE Extension提供了什么?

Neo Eco AE Extension为 Applied Energistics 2引入了三种大型核心组件，将合成、存储与计算拆分为三个独立的多方块结构。 每种结构均包含三个等级，每一等级都相比前一级带来显著的性能提升。

此外，本模组还提供了一系列高性能组件，大幅提升自动化系统的易用性与操作舒适度，让你的自动化之旅如同呼吸一般简单。 ~~核电，轻而易举啊~~

AE2LT 的一次性外层神秘盘、`infinite_storage_cell` 大容量存储盘，以及 ExtendedAE Plus 的 BigInteger 存储盘仍按有限库存处理。无限来源不等于无限大小的单次网络调用：AE2 提取接口仍使用 `long`，超出该范围的需求由 ECO 的精确订单库存与分批提取处理；实际提交及提取仍经过 ME 网络。

## 重要提示

Neo Eco AE Extension与Eco AE Extension之间仅存在经授权的移植关系，两者在项目层面不存在任何直接的隶属关系或依赖关系。
两个项目均由不同的开发团队独立维护与开发。

请不要将有关Neo Eco AE Extension的问题、Bug报告或技术支持请求提交至Eco AE Extension项目，否则相关问题将无法得到有效处理，并可能对Eco AE Extension团队的维护与开发工作造成不必要的干扰。

项目维护与开发信息:   
Eco AE Extension由**Hikari_Nova**领导开发   
Neo Eco AE Extension由**DancingSnow0517**领导开发
