<p align="center"><img src="images/logo.png" alt="Neo ECO AE Extension logo"></p>
<h1 align="center">Neo ECO AE Extension</h1>
<p align="center">ECO crafting, storage and computation for Minecraft 1.20.1 / Forge.</p>

[![Modrinth](https://img.shields.io/modrinth/dt/udZtKfzP?style=flat&logo=modrinth&label=Modrinth)](https://modrinth.com/project/udZtKfzP) [![CurseForge](https://img.shields.io/curseforge/dt/1460639?style=flat&logo=curseforge&label=CurseForge)](https://www.curseforge.com/projects/1460639)

English | [简体中文](README_ZH_CN.md)

## Overview

Neo ECO AE Extension is an authorized port of [ECO AE Extension](https://github.com/sddsd2332/NovaEngineering-ECOAEExtension), bringing the MMCE multiblock concepts from [Nova Engineering: World](https://www.mcmod.cn/modpack/784.html) to modern Applied Energistics 2. The project is distributed under [GPLv3](LICENSE).

This branch targets **Minecraft 1.20.1 / Forge / Java 17**. It has its own platform adapters, dependencies and release artifacts; use the Forge download for 1.20.1.

## Features

- Separate multiblocks for crafting, storage and computation, with multiple component tiers.
- ECO crafting planning, cycle handling, parallel providers and exact large-quantity orders.
- ECO storage matrices, exact amounts and built-in bulk compression; Bigger AE2's Digital Singularity Item Storage Matrix can use ECO bulk compression.
- Integrated and Large Integrated Workstations, with supported recipe integrations for AdvancedAE, ExtendedAE and AE2LT.
- Optional ExtendedAE Plus integrations and JEI/EMI recipe displays.

## Installation

Install Neo ECO AE Extension and its required dependencies on **both the client and server**. Use matching NeoECOAE builds, including when a same-number release has been replaced; menu/network protocol revisions can differ.

| Requirement | Supported range / recommended build |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 or newer within 47.x; build uses 47.4.28 |
| Java | 17 |
| Applied Energistics 2 | 15.4.10–15.x; 15.4.11 recommended |
| LDLib | 1.0.33.b–1.0.x; 1.0.52.a recommended |
| GuideME | 20.1.14 or newer, below 20.2.0; 20.1.15 recommended |

Optional integrations require the corresponding mods and their prerequisites. See [mod metadata](src/main/resources/META-INF/mods.toml) for dependency declarations and [release notes](docs/releases/20.5.2.md) for version-specific changes.

## Documentation and development

- API integration: [English](docs/API.md) · [简体中文](docs/API_ZH_CN.md).
- Releases: [GitHub](https://github.com/DancingSnow0517/NeoECOAEExtension/releases), Modrinth and CurseForge above.
- Bug reports: [this project's issue tracker](https://github.com/DancingSnow0517/NeoECOAEExtension/issues). Include Minecraft, Forge, NeoECOAE and dependency versions, reproduction steps and relevant logs.
- On Windows, use `./build-local.ps1 build` to select Java 17 and build the project. For a clean checkout, first run `python tools/compat/bootstrap.py` to restore the pinned build dependencies.

## Project relationship

ECO AE Extension and Neo ECO AE Extension have an authorized porting relationship and are maintained independently. Please send Neo ECO AE Extension bug reports and support requests to this repository, rather than the original ECO AE Extension project.

ECO AE Extension is led by **Hikari_Nova**. Neo ECO AE Extension is led by **DancingSnow0517**. Project contributors are listed in the mod metadata and Git history.
