# 源码、发布工件与调查证据索引

**调查日期：2026-10-20（Asia/Shanghai）。** 本索引与主方案、依赖矩阵和验收计划一起使用。范围为核心及ExtendedAE、AdvancedAE、ExtendedAE Plus、AE2LT；只纳入这些项目所需基础库。

## 1. 基准和证据层级

主体实现/算法/资源输入是DancingSnow0517/NeoECOAEExtension远端main提交 **dc8344f48ee866e57e02c285fdda5e0fb5da9421**，版本21.2.9-beta。起始已fetch；原调查交付前再次fetch，远端仍为此提交。原工作区后来新增本地提交和未提交修改，未作为本次移植源，也没有被调查覆盖。最终观察记录见[final-main-recheck.json](evidence/final-main-recheck.json)。用户后来明确要求保留1.20.1结构终端，该特性作为范围例外，实施前须单独核对并锁定其来源与必要实现；不扩展为导入整个旧工程。

本次没有读取/复制已有NeoECOAE 1.20.1工程或分支。源main快照在`E:\Minecraft Project\_comparison_sources\neoecoae-main-dc8344f48-20261020`，第三方明确目标版本快照在`E:\Minecraft Project\_comparison_sources\porting-20261020`。仓库先从`E:\Minecraft Project`查找，读取前更新远端引用；缺失仓库才从官方GitHub取得。

最新范围决定来自用户需求，**不作为已完成的源码或运行证据**：保留1.20.1结构终端；机器UI移除自动搭建按钮；大集成仅常规、AAE、EAE、AE2LT四种，AE2LT三类配方归一台，其余机型及专属内容移除。原始main统计、API缺口与JAR证据保留用于追溯；不把它们直接当成调整后的目标内容数量。结构终端来源尚未在本次补充中读取/复制或锁定，后续核对后增补，不虚构提交或验收结果。

证据强度分别记录：官方发布证明存在相应游戏/Loader文件；目标提交证明该提交的源码能力；实际JAR元数据/字节码证明发布工件中的ABI；依赖解析证明解析成功。**以上均不能替代目标编译、Mixin实际应用、游戏运行或发布物验收。** 对源码分支比发布更新的项目，以发布JAR检查补足，未声称有可复现二进制构建。

## 2. 锁定源码

完整机器可读记录：[source-lock.json](evidence/source-lock.json)。下列短标签也是证据中的索引；所有SHA为完整提交号。

| 索引 | 仓库与明确ref | 提交 | 用途/注意 |
|---|---|---|---|
| NeoECOAE-main | [origin/main](https://github.com/DancingSnow0517/NeoECOAEExtension/tree/dc8344f48ee866e57e02c285fdda5e0fb5da9421) | `dc8344f48ee866e57e02c285fdda5e0fb5da9421` | 主体移植业务/资源/算法源；排除工作区修改；结构终端特性例外另锁定 |
| AE2-15 | [forge/v15.4.11](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/1c2f96e70ca6b61e8120482923e9775d772fd9e7) | `1c2f96e70ca6b61e8120482923e9775d772fd9e7` | 目标Forge15.4.11 API/内部实现 |
| AE2-19 | [neoforge/v19.2.18](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/db17504a86128fdf3dae31f5fb7a112a646e0b93) | `db17504a86128fdf3dae31f5fb7a112a646e0b93` | main严格依赖19.2.18的对照 |
| LDLib1 | [origin/1.20.1](https://github.com/Low-Drag-MC/LDLib-MultiLoader/tree/1d4e4390ae58d16038845c651e122185c29f334b) | `1d4e4390ae58d16038845c651e122185c29f334b` | 目标1.20.1分支；版本1.0.52.a |
| LDLib2 | [origin/1.21](https://github.com/Low-Drag-MC/LDLib2/tree/ae11d71607ada1364de154634a2652a73b66ba0c) | `ae11d71607ada1364de154634a2652a73b66ba0c` | 已更新1.21分支的背景参考；main实际2.2.40以依赖图为准 |
| Registrate20 | [origin/1.20](https://github.com/tterrag1098/Registrate/tree/075509f797fdb98b75a82f3f3590f870b91709a3) | `075509f797fdb98b75a82f3f3590f870b91709a3` | 目标分支背景；最终1.3.11接口以同版sources JAR为准 |
| GuideME20 | [v20.1.15](https://github.com/AppliedEnergistics/GuideME/tree/c2bc0b0c1996cf11fb71c4de348a9cbb004fc0a3) | `c2bc0b0c1996cf11fb71c4de348a9cbb004fc0a3` | 目标20.1.15 |
| AE2LT20 | [1.20.1-2.1.2-beta2](https://github.com/AE2-Lightning-Tech-Reborn/AE2-Lightning-Tech-Reborn/tree/b36bec80ca30f326afa119cd5e2e964fa51f3a86) | `b36bec80ca30f326afa119cd5e2e964fa51f3a86` | 目标2.1.2-beta2 |
| Thunderbolt20 | [1.20.1-2.0.3-beta2](https://github.com/AE2-Lightning-Tech-Reborn/Thunderbolt-Core-Reborn/tree/43730db8a4be55dc398ac58543ddd4c13aedc846) | `43730db8a4be55dc398ac58543ddd4c13aedc846` | 仅AE2LT所需前置2.0.3-beta2 |
| AdvancedAE20 | [1.3.6-1.20.1-forge](https://github.com/pedroksl/AdvancedAE/tree/3137d63403dd35211c886744f094886b6a81e76a) | `3137d63403dd35211c886744f094886b6a81e76a` | 目标1.3.6-1.20.1 |
| ExtendedAE20 | [origin/1.20.1-forge](https://github.com/GlodBlock/ExtendedAE/tree/827a5368dd48a43aab5a5d3c8c3d0914df3bf816) | `827a5368dd48a43aab5a5d3c8c3d0914df3bf816` | 更新后的1.20.1分支，用于检查后续差异 |
| ExtendedAEPlus20 | [origin/develop/1.20.1](https://github.com/GaLicn/ExtendedAE_Plus/tree/030f340fe0abbd94867afc07d5d9f9a400e88d1a) | `030f340fe0abbd94867afc07d5d9f9a400e88d1a` | 目标1.20.1分支；关键ABI再核对Forge文件HNyEHPOp |
| MDG | [origin/main](https://github.com/neoforged/ModDevGradle/tree/e9c127db51cd59d8d19e238af6a01e071d769661) | `e9c127db51cd59d8d19e238af6a01e071d769661` | 2.0.148发布时期官方Legacy源码/文档；非游戏Mod不按Minecraft分支 |
| ExtendedAE20Pinned | [4a815ff9](https://github.com/GlodBlock/ExtendedAE/tree/4a815ff97b3aefe2a0524dcb97aa4bc29701677b) | `4a815ff97b3aefe2a0524dcb97aa4bc29701677b` | 1.4.21目标版本提交，结合发布JAR使用；不用异常tag源码 |
| AE2AddonLib20 | [1.0.4-1.20.1-forge](https://github.com/pedroksl/AE2AddonLib/tree/7980b3b05f99f809db5cafb489d98ae1087426a0) | `7980b3b05f99f809db5cafb489d98ae1087426a0` | AdvancedAE内嵌1.0.4对应目标tag/IngredientStack |
| GeckoLib20 | [origin/1.20.1](https://github.com/bernie-g/geckolib/tree/7d6f32db9168a24e14b327c0ad9f8f87dfc204de) | `7d6f32db9168a24e14b327c0ad9f8f87dfc204de` | 明确1.20.1分支，gradle.properties为4.8.4；实际JAR另核对 |

### 排除的版本标记

ExtendedAE的发布标签`1.20-1.4.21-forge`解析到`4d28c36b370c42f6614db6719366b00f0bdd941f`，该源码`gradle.properties`却声明Minecraft26.1.2和Mod26.1-1.0.4-neoforge。因此不把它当1.20.1 API依据。保留其官方发布JAR，源码改看表中的明确目标提交。该提交时间为2026-09-19 16:15:41 +0800，发布JAR Manifest构建时间为2026-09-19T16:16:21+0800；时间对应只提供关联线索，不证明二进制完全一致。

依据：[标签实际源码](https://github.com/GlodBlock/ExtendedAE/blob/4d28c36b370c42f6614db6719366b00f0bdd941f/gradle.properties)、[记录](evidence/rejected-version-refs.json)、[实际JAR API](evidence/four-addon-published-api.json)。

## 3. 选定发布文件

以下日期使用官方记录的原始UTC时间；调查日期为北京时间2026-10-20。[selected-releases.json](evidence/selected-releases.json)保留游戏版本、Loader、状态、项目要求、文件URL和官方校验值。只看显示版本号不足以确定Loader。

| 项目 | 精确版本与文件ID | 发布状态 / 日期 | 官方文件 |
|---|---|---|---|
| ae2 | `15.4.11` / `jYgSnxye` | release / 2026-09-27T15:05:00.958592Z | [appliedenergistics2-forge-15.4.11.jar](https://modrinth.com/mod/ae2/version/jYgSnxye) |
| guideme | `20.1.15` / `i7Tp1AHw` | release / 2026-06-06T09:47:01.233648Z | [guideme-20.1.15.jar](https://modrinth.com/mod/guideme/version/i7Tp1AHw) |
| ldlib | `mc1.20.1-1.0.52.a-forge` / `YSOP0G6O` | release / 2026-09-07T14:39:24.039563Z | [ldlib-forge-1.20.1-1.0.52.a.jar](https://modrinth.com/mod/ldlib/version/YSOP0G6O) |
| advancedae | `1.3.6-1.20.1` / `d83Wdhdn` | release / 2026-08-02T20:28:03.763034Z | [AdvancedAE-1.3.6-1.20.1.jar](https://modrinth.com/mod/advancedae/version/d83Wdhdn) |
| extendedae-plus | `1.6.3` / `HNyEHPOp` | release / 2026-09-28T08:36:57.783448Z | [extendedae_plus-1.6.3.jar](https://modrinth.com/mod/extendedae-plus/version/HNyEHPOp) |
| ae2-lightning-tech | `2.1.2-beta2-forge.1.20.1` / `eS1qyjYH` | beta / 2026-10-11T17:19:47.761212Z | [ae2lt-forge-1.20.1-2.1.2-beta2.jar](https://modrinth.com/mod/ae2-lightning-tech/version/eS1qyjYH) |
| thunderbolt-core | `2.0.3-beta2-forge.1.20.1` / `tqklE20T` | beta / 2026-10-11T17:15:19.303935Z | [thunderbolt-forge-1.20.1-2.0.3-beta2.jar](https://modrinth.com/mod/thunderbolt-core/version/tqklE20T) |
| glodium | `1.20-1.5-forge` / `eoUaDkZf` | release / 2025-02-04T13:08:25.693981Z | [Glodium-1.20-1.5-forge.jar](https://modrinth.com/mod/glodium/version/eoUaDkZf) |
| ExtendedAE | `1.20-1.4.21-forge` / `574383398` | release / 2026-09-19T08:21:54Z | [ExtendedAE-1.20-1.4.21-forge.jar](https://github.com/GlodBlock/ExtendedAE/releases/tag/1.20-1.4.21-forge) |
| geckolib | `4.8.4` / `aC5KMoNg` | release / 2026-06-20T11:07:31.386330Z | [geckolib-forge-1.20.1-4.8.4.jar](https://modrinth.com/mod/geckolib/version/aC5KMoNg) |

### 必需依赖闭包

[selected-dependency-closure.json](evidence/selected-dependency-closure.json)提取实际JAR的必需依赖和内嵌JAR。基础为AE2+GuideME+LDLib+明确的Registrate/MixinExtras策略；ExtendedAE另需Glodium；Plus另需ExtendedAE；AE2LT另需指定Thunderbolt。AdvancedAE内嵌AE2AddonLib1.0.4，且仍需GeckoLib4.8.4。

特别是AdvancedAE：实际Forge JAR的`mods.toml`漏列GeckoLib，但[官方版本API](https://api.modrinth.com/v2/version/d83Wdhdn)把项目`8BmcQJ2H`（GeckoLib）列为required；[QuantumArmorBase.java第64行](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/items/armors/QuantumArmorBase.java#L64)直接实现GeoItem。不能仅据Gradle或mods.toml单一来源删除此前置。

AE2AddonLib的IngredientStack在嵌套JAR中，而不在AdvancedAE根JAR。[嵌套证据](evidence/ae2addonlib-embedded.json)、[javap](evidence/AE2AddonLib20-embedded-IngredientStack.javap.txt)和明确目标tag核对其真实类型及Java17字节码。

## 4. 实际下载JAR与哈希

JAR留在研究缓存`E:\Minecraft Project\_comparison_sources\porting-20261020\published-artifacts`，不放进目标项目作为移植产物。下表SHA-256由本地重新计算；来自Modrinth的文件另与官方SHA-512校验一致。GitHub资产和作者Maven工件的SHA-256为本地追踪值，不冒称官方签名。

完整元数据和路径：[selected-artifact-audit.json](evidence/selected-artifact-audit.json)。

| 工件 | 字节数 | SHA-256 | class major集合 |
|---|---:|---|---|
| Registrate20-sources | 87704 | `3f158e2eb7b569fb4251b54dc15a4dda4fa382004f55fe2b15b46e1dc4178888` | [] |
| Registrate20-release | 170445 | `81c5041b9a0017c5ac61c46655e984f78d711a6981330567d6fddfbde0385449` | [61] |
| AE2LT20-release | 6672167 | `654e9e8eb13249588b82e9e667f7d4fd18e6eceb8bd2eab821d4a539442b0695` | [61] |
| Thunderbolt20-release | 5343502 | `f3b58a63ed6fad9149d8d85fba4cdd5036170369694aa8cec8f8785eb19bff51` | [61] |
| AdvancedAE20-release | 2486306 | `e216f95ac73eb4c1e8f3db22cc3d30f37f2612a7cb1762b9ebce148d0d5657b2` | [61] |
| ExtendedAEPlus20-release | 2315478 | `d8634db4ca56fe725799084b3c2c67b17874db50377ea497670379221d374764` | [61] |
| ExtendedAE20-release | 2972843 | `30fc8abe66d4b96919f0fd7b33badd0bb2013b31e2ee57f6aeaa63ffae37df4b` | [61] |
| AE2-15-release | 8501309 | `f2ff9ee5122b037d4e93537425ab9fbe49860fc9c3977db13b20a50ca7d30f9e` | [61] |
| GuideME20-release | 9414827 | `cf8052ab3da121012efc59a20e731c24bfe7488dfa556c9b88809f42dea3b24a` | [51, 52, 55, 61] |
| LDLib1-release | 3125867 | `a9b0827d264e0d15f90482de0d4783a28fc2dad9dfb477f344dc5e959168838d` | [52, 61] |
| Glodium20-release | 60550 | `fc419cb5bf511750c0a2da498b925f5122ac4f78617aca6630fe80abf0b6f460` | [61] |
| GeckoLib20-release | 1038209 | `4518779a31b6c2abb92c98b5ad17ef3d60177df68626bfc86c44de2764913c2b` | [61] |

AE2AddonLib嵌套JAR SHA-256：`47bbd147a155c24aa58944a6b52eec7935cc4135bbf2e68f89208993c7538501`，class major为61。其他老版本class major小于61的库仍可由Java17读取；此检查没有证明运行语义。

## 5. 构建与平台的官方依据

| 结论 | 官方来源及已保存证据 |
|---|---|
| Legacy支持Forge/Vanilla1.17至1.20.1；普通插件能力仍可用 | [锁定LEGACY.md](https://github.com/neoforged/ModDevGradle/blob/e9c127db51cd59d8d19e238af6a01e071d769661/LEGACY.md)；[插件发布页](https://plugins.gradle.org/plugin/net.neoforged.moddev.legacyforge) |
| 官方最新Legacy为2.0.148，不能停留2.0.147示例 | [官方Maven版本表](https://maven.neoforged.net/releases/net/neoforged/moddev/legacyforge/net.neoforged.moddev.legacyforge.gradle.plugin/maven-metadata.xml)；mdg-legacy-metadata.xml、mdg-legacy-2.0.148.pom |
| Forge1.20.1 latest47.4.28、recommended47.4.10 | [官方发布列表](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)、[promotions](https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json)；forge-promotions.json |
| Java17、构建与运行基础 | [Forge1.20.1 Getting Started](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)；forge-getting-started.html |
| SimpleChannel消息注册/线程/服务端验证 | [Forge SimpleImpl](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/)；forge-simpleimpl.html |
| AT使用目标SRG成员，生产工件需重混淆 | [Forge AT](https://docs.minecraftforge.net/en/1.20.1/advanced/accesstransformers/)、上述LEGACY的Reobfuscating artifacts；forge-access-transformers.html |
| Mixin AP/refmap和Manifest；Mod配置非传递 | 上述LEGACY的Mixins/Remapping Mod Dependencies；不能把开发成功等同普通安装成功 |
| Forge能力生命周期 | [Capabilities](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/)；forge-capabilities.html |
| NeoForge1.20.1备选入口使用enable.neoForgeVersion | [LegacyForgeExtension](https://github.com/neoforged/ModDevGradle/blob/e9c127db51cd59d8d19e238af6a01e071d769661/src/legacy/java/net/neoforged/moddevgradle/legacyforge/dsl/LegacyForgeExtension.java)、对应LegacyForgeModdingSettings；neoforge-1.20.1-metadata.xml |

已实际取得的目标POM：

- `appeng:appliedenergistics2-forge:15.4.11`：[Modmaven](https://modmaven.dev/appeng/appliedenergistics2-forge/15.4.11/appliedenergistics2-forge-15.4.11.pom)。main的`org.appliedenergistics:appliedenergistics2`不能照搬。
- `org.appliedenergistics:guideme:20.1.15`：[Maven Central](https://repo.maven.apache.org/maven2/org/appliedenergistics/guideme/20.1.15/guideme-20.1.15.pom)。不依赖本次返回404的Modmaven路径。
- `io.github.pedroksl:ae2addonlib:1.0.4-1.20.1`：[Maven Central](https://repo.maven.apache.org/maven2/io/github/pedroksl/ae2addonlib/1.0.4-1.20.1/ae2addonlib-1.0.4-1.20.1.pom)。
- `com.lowdragmc.ldlib:ldlib-forge-1.20.1:1.0.52.a`：[作者仓库](https://maven.firstdarkdev.xyz/snapshots/com/lowdragmc/ldlib/ldlib-forge-1.20.1/1.0.52.a/ldlib-forge-1.20.1-1.0.52.a.pom)。
- `com.tterrag.registrate:Registrate:MC1.20-1.3.11`：[作者仓库](https://maven.tterrag.com/com/tterrag/registrate/Registrate/MC1.20-1.3.11/Registrate-MC1.20-1.3.11.pom)。

抓取结果/日期/HTTP状态见[build-official-sources.json](evidence/build-official-sources.json)、[supplementary-official-sources.json](evidence/supplementary-official-sources.json)、[corrected-coordinate-probes.json](evidence/corrected-coordinate-probes.json)。错误路径的404和非必要镜像的超时也保留；它们不被当作Mod不支持1.20.1的证据。

## 6. 主业务源码索引

下面是各子系统主要实现入口，链接固定main提交；完整文件位置与声明行号见[core-source-index.csv](evidence/core-source-index.csv)。Mixin的53个入口和目标声明单独见[MIXIN_AUDIT.md](MIXIN_AUDIT.md)。

| 子系统 | main文件与声明位置 |
|---|---|
| 构建与平台 | [NeoECOAE.java:L62](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/NeoECOAE.java#L62)；[NERegistries.java:L10](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/all/NERegistries.java#L10)；[NEBlockEntities.java:L51](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/all/NEBlockEntities.java#L51)；[NEDataComponents.java:L12](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/all/NEDataComponents.java#L12)；[AutoExportSides.java:L14](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/api/components/AutoExportSides.java#L14)；[ECOStorageLifecycleEvents.java:L17](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/event/ECOStorageLifecycleEvents.java#L17) |
| 网络 | [ECONetwork.java:L7](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/network/ECONetwork.java#L7)；[MenuDataTransport.java:L20](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/network/MenuDataTransport.java#L20)；[MenuStreamAssembler.java:L4](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/network/MenuStreamAssembler.java#L4)；[MenuDataCodecs.java:L15](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/network/MenuDataCodecs.java#L15) |
| 规划与样板 | [PlannerAmount.java:L15](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/crafting/amount/PlannerAmount.java#L15)；[ECOBigOrderPlanner.java:L11](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOBigOrderPlanner.java#L11)；[AE2PatternIntrospection.java:L19](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/ae2/AE2PatternIntrospection.java#L19)；[ECOProcessingComponentMatching.java:L16](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/crafting/ECOProcessingComponentMatching.java#L16)；[ECOExternalCpuSupport.java:L10](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/crafting/execution/ECOExternalCpuSupport.java#L10) |
| 执行 | [ECOCraftingCPULogic.java:L25](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic.java#L25)；[ECOCraftingCPULogic.java:L74](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/crafting/execution/ECOCraftingCPULogic.java#L74)；[ECOExternalCpuFastPath.java:L21](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/crafting/execution/ECOExternalCpuFastPath.java#L21)；[ECOExternalCpuJob.java:L11](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/crafting/execution/ECOExternalCpuJob.java#L11)；[ECOCraftingProviderDispatcher.java:L32](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/crafting/execution/ECOCraftingProviderDispatcher.java#L32) |
| 存储 | [ECOStorageCell.java:L39](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/impl/storage/ECOStorageCell.java#L39)；[SavedDataInfiniteStorageEngine.java:L22](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/impl/storage/infinite/SavedDataInfiniteStorageEngine.java#L22)；[ECOInfiniteStorageData.java:L36](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/impl/storage/infinite/ECOInfiniteStorageData.java#L36)；[ECOInfiniteStorageDomains.java:L21](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/impl/storage/infinite/ECOInfiniteStorageDomains.java#L21)；[LegacyInfiniteStorageReader.java:L28](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/impl/storage/infinite/LegacyInfiniteStorageReader.java#L28) |
| 多方块 | [NEMultiBlocks.java:L28](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/all/NEMultiBlocks.java#L28)；[LargeWorkstationPatternProvider.java:L44](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/blocks/entity/LargeWorkstationPatternProvider.java#L44)；[LargeWorkstationRecipes.java:L49](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipes.java#L49)；[ECOLargeIntegratedWorkingStationInterfaceBlockEntity.java:L47](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOLargeIntegratedWorkingStationInterfaceBlockEntity.java#L47) |
| 四附属 | [ECOAdvancedAEPatternScaling.java:L19](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/advanced_ae/ECOAdvancedAEPatternScaling.java#L19)；[ECOExtendedAEUnboundedCells.java:L9](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/extendedae/ECOExtendedAEUnboundedCells.java#L9)；[ECOExtendedAEPlusScaling.java:L8](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/extendedaeplus/ECOExtendedAEPlusScaling.java#L8)；[EAEPForcedCrafting.java:L11](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/extendedaeplus/EAEPForcedCrafting.java#L11)；[ECOExtendedAEPlusVirtualCrafting.java:L6](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/extendedaeplus/ECOExtendedAEPlusVirtualCrafting.java#L6)；[ECOExtendedAEPlusMatrixBridge.java:L26](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/extendedaeplus/ECOExtendedAEPlusMatrixBridge.java#L26)；[ECOAe2LtBatchCapability.java:L15](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/ae2lt/ECOAe2LtBatchCapability.java#L15)；[ECOAe2LtDirectDispatch.java:L15](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/ae2lt/ECOAe2LtDirectDispatch.java#L15)；[ECOAe2LtCraftingAdapter.java:L12](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/ae2lt/ECOAe2LtCraftingAdapter.java#L12)；[ECOOverloadCpuAccountingBridge.java:L23](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/thunderbolt/ECOOverloadCpuAccountingBridge.java#L23)；[ECOAe2LtCancellationBridge.java:L17](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/compat/ae2lt/ECOAe2LtCancellationBridge.java#L17) |

### 高风险差异的直接依据

- 两套外部job没有`suspended`：[AE2目标ExecutingCraftingJob](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/crafting/execution/ExecutingCraftingJob.java)、[AdvancedAE目标ExecutingCraftingJob](https://github.com/pedroksl/AdvancedAE/blob/3137d63403dd35211c886744f094886b6a81e76a/src/main/java/net/pedroksl/advanced_ae/common/logic/ExecutingCraftingJob.java)。主Mixin仅改Accessor名不能补足暂停/恢复/NBT语义。
- 暂停菜单：[CraftingCPUMenu目标](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingCPUMenu.java)无toggleScheduling；[CraftingStatus目标](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/me/crafting/CraftingStatus.java)无暂停位，构造为五参；暂停状态须由ECO通道重接。
- 供应器缓存：[CraftingService目标](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/me/service/CraftingService.java)保留节点供应器生命周期，缺三种全局供应器方法；本轮无需为范围外调用者重建全局服务。
- LDLib跨度：[ldlib-api-compare.json](evidence/ldlib-api-compare.json)从main导入74种LDLib2类型，对照目标LDLib1，47种缺同名类。它是适配规模线索，不意味着其余27种接口兼容，也不意味着所有界面都必须逐个重写。这些类型还包括 UI 之外的接口，不能把数量直接当成 UI 兼容层的待实现清单；应按本轮实际使用的方法和行为收敛。
- AE2LT exact输出缺口：[AE2LT发布Ae2LtCraftingIntegration的javap](evidence/AE2LT20-release-Ae2LtCraftingIntegration.javap.txt)。long接口存在，BigInteger方法不存在；按指定工件限定结论。
- 原调查工作站两个缺失配方类型与四个已有目标能力的配方类型：[four-addon-deep-api.json](evidence/four-addon-deep-api.json)、目标仓库配方注册和实际JAR精确token/类检索。此处是配方类型统计，不是机型数量。最新要求中EAE大集成必留，缺口须补齐并实测；Plus大集成移出范围，其余Plus联动保留。AE2LT三类配方归同一大集成。

### LDLib UI 转接层的源码依据

此前 UI 兼容层调查在更新检查上游后核对以下文件，本节涉及的 main UI 以 `dc8344f48ee866e57e02c285fdda5e0fb5da9421` 为锁定输入；LDLib1/2 的提交与第 2 节相同。LDLib2 的 `ae11d716...` 用于架构参考，实施时仍须对照 main 实际依赖 **2.2.40** 的对应 API，不能把更新分支的方法一律作为 main 的需求。结构终端的1.20.1特性保留按第1节另行核对来源，不属于本表已完成的源码调查。

| 依据 | 已确认的源码事实 | 对方案的含义 |
|---|---|---|
| [LDLib2 UIElement](https://github.com/Low-Drag-MC/LDLib2/blob/ae11d71607ada1364de154634a2652a73b66ba0c/src/main/java/com/lowdragmc/lowdraglib2/gui/ui/UIElement.java) | 控件树关联布局/样式、事件、同步值和 RPC 注册 | 单独包装可见控件不足以覆盖现有 UI；兼容层需要相应行为 |
| [LDLib1 Widget](https://github.com/Low-Drag-MC/LDLib-MultiLoader/blob/1d4e4390ae58d16038845c651e122185c29f334b/common/src/main/java/com/lowdragmc/lowdraglib/gui/widget/Widget.java)、[WidgetGroup](https://github.com/Low-Drag-MC/LDLib-MultiLoader/blob/1d4e4390ae58d16038845c651e122185c29f334b/common/src/main/java/com/lowdragmc/lowdraglib/gui/widget/WidgetGroup.java) | 提供控件/分组、尺寸/位置、输入回调，以及 writeUpdateInfo/readUpdateInfo、writeClientAction/handleClientAction 等更新/动作接口 | 可作为兼容层后端；仍需适配语义，不能把同名或近似功能当成等价证明 |
| [LDLib2 UIEventDispatcher](https://github.com/Low-Drag-MC/LDLib2/blob/ae11d71607ada1364de154634a2652a73b66ba0c/src/main/java/com/lowdragmc/lowdraglib2/gui/ui/event/UIEventDispatcher.java) | 事件存在捕获、目标、冒泡及停止传播处理 | 需要明确事件路由与动作的唯一发送责任，不能只转发一次 mouseClicked |
| [LDLib2 UISyncManager](https://github.com/Low-Drag-MC/LDLib2/blob/ae11d71607ada1364de154634a2652a73b66ba0c/src/main/java/com/lowdragmc/lowdraglib2/gui/sync/UISyncManager.java) | 管理同步值、初始数据、变化数据及 RPC 登记和发送 | 兼容层必须覆盖使用到的同步方向、初始化和生命周期；协议仍按 Forge1.20.1 重接 |
| [LDLib1 SlotWidget](https://github.com/Low-Drag-MC/LDLib-MultiLoader/blob/1d4e4390ae58d16038845c651e122185c29f334b/common/src/main/java/com/lowdragmc/lowdraglib/gui/widget/SlotWidget.java) | 持有真实 Slot 并向 ModularUI 挂接/移除原生槽位 | 物品槽可复用其基础，但仍需真实手势、菜单索引和库存数量验证 |
| [main NeoUiJsonLoader](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/gui/json/NeoUiJsonLoader.java)、[NeoUiJsonContext](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/gui/json/NeoUiJsonContext.java) | 项目自定义 JSON 格式集中创建控件/子节点、应用布局与纹理，context 提供自定义创建和配置入口 | 可保留现有 JSON 格式并替换底层实现；Java 自定义组件仍需单独适配 |
| [main StoragePanelSync](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/gui/storage/StoragePanelSync.java)、[StorageResourceFilterGrid](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/java/cn/dancingsnow/neoecoae/gui/storage/StorageResourceFilterGrid.java) | 现有 UI 使用 Tag S2C、分页 C2S、RPC 和自定义 ItemSlot 绘制 | 原型应同时覆盖状态、动作、自定义组件以及真实槽与过滤槽的区别 |

以上事实支持“项目所需 API 子集兼容层值得做原型”的工程判断，**未证明转接层已完成、界面已兼容、某个复用比例成立或工期一定缩短**。方案见[主方案第 6.9 节](../../PORTING_1_20_1_PLAN_ZH_CN.md)，原型交付与实际验收见[阶段计划](EXECUTION_AND_ACCEPTANCE.md)的 P3、A12/A13。

## 7. 证据文件用途与边界

| 证据组 | 文件 | 如何使用 |
|---|---|---|
| main完整基线 | main-inventory.json、main-imports.json、main-mixins.json、main-porting-symbols.csv/json | 原main全量计数/定位，包含范围外源痕迹以追踪排除入口；不作为额外附属兼容性调查 |
| 实际声明与解析 | main-mod-dependency-scopes.csv/json、main-resolved-dependencies.json、main-dependency-resolution.log、main-four-addon-jar-metadata.json | 区分声明坐标、strictly、测试/dev/可选、真实JAR版本；解析成功不等于客户端能共装 |
| 隐式/可选入口 | main-optional-probes.json、main-runtime-guards.txt、four-addon-scope.json、scope-boundary-references.csv | 检查反射、mod ID、资源/注册入口和共享类引用；指导范围收敛 |
| 目标API | ae2-15-missing-imports.json、ldlib-api-compare.json、scoped-mixins.json、mixin-target-audit.json、scoped-mixin-missing-identifiers.json | 缺失成员表是词法筛查加人工核实的线索，并非完整二进制兼容性证明；不能以同名即认为相容 |
| 实际发布ABI | four-addon-published-api.json、four-addon-deep-api.json、各保留的release-*.javap.txt | 核对指定工件中的类/方法、数组/接口、泛型桥接、record和私有字段；不是Mixin应用日志 |
| 版本/依赖闭包 | selected-releases.json、selected-artifact-audit.json、selected-dependency-closure.json、ae2addonlib-embedded.json | 指定Loader、文件ID、哈希、必需与内嵌前置；发布页和实际元数据交叉检查 |

main依赖图解析在独立快照执行，审计配置均为0 unresolved；未编译main或目标Mod。主源统计为911个Java主文件、111,905行、262个Java测试文件、63个原始配置Mixin；原调查候选保留53个，结构终端/四种大集成调整后须复核，不作为最终数量。原调查的数量、链接、JSON和文件存在性检查见`report-validation.json`，该历史记录不证明后续需求已实现。

范围外早期调查证据已移出目标交付目录，放入研究目录的`superseded-before-four-addons/evidence`归档；不进入最终矩阵、预算或验收。保留main完整原始基线的原因是复现范围裁剪，不能把其中出现的附属名当作本轮支持承诺。

本轮四个附属均找到了官方Forge1.20.1发布，故没有“未发现1.20.1项目”的推断结论。指定版本缺失API的事实仍按原证据保留，如何处理由最新范围决定：EAE大集成必须补齐，Plus及其他范围外大集成不再交付；AE2LT exact边界仍按已验证能力处理。没有以搜索无结果、时间线或默认分支版本判断不支持。

没有实施目标工程、源码复制、提交、发布，也没有启动Minecraft作功能验收。后续完成判据以[EXECUTION_AND_ACCEPTANCE.md](EXECUTION_AND_ACCEPTANCE.md)的A01–A22为准。
