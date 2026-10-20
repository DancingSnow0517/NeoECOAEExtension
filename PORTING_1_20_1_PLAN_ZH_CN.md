# Neo ECO AE Extension：Minecraft 1.20.1 移植调查方案

**调查日期：2026-10-20（Asia/Shanghai）。本次只调查和整理方案，没有实施移植。**

按用户最终范围，本报告只考虑 **main 核心功能、ExtendedAE、AdvancedAE、ExtendedAE Plus、AE2LT**。构建/GUI 必需库继续纳入；**Thunderbolt 仅作为 AE2LT 必需前置、Glodium 仅作为 ExtendedAE 前置、AE2AddonLib 与 GeckoLib 仅作为 AdvancedAE 的基础库**处理。不再评估其他附属的目标版本、替代方案、排期或验收。

本轮新增的目标约束：**保留 1.20.1 的结构终端，结构搭建入口统一由结构终端承接；移除机器 UI 中的自动搭建按钮。大集成只保留常规、AAE（AdvancedAE）、EAE（ExtendedAE）、AE2LT 四种机型，其余大集成全部移出范围。** 四种机型与四个附属是不同的范围清单；ExtendedAE Plus 的其他联动继续保留，但不保留其独立大集成机型。具体内容见第 6.8 节。

新工程目录：`E:\NeoECOAEExtension-1.20.1`。

移植主体源：`DancingSnow0517/NeoECOAEExtension` 远端 main，提交 **`dc8344f48ee866e57e02c285fdda5e0fb5da9421`**，版本 **21.2.9-beta**，提交时间 **2026-10-20 19:02:23 +0800**。**结构终端是用户明确要求保留的 1.20.1 特性例外**：实施时允许针对性核对其 1.20.1 功能契约与必要实现，并记录来源版本/提交；其余功能仍从锁定 main 移植，不扩展为复制整个旧工程。本次补充只修改文档，尚未读取或复制旧结构终端实现。

配套交付物：

- [四个附属与必需库的兼容性矩阵](docs/porting-2026-10-20/DEPENDENCY_MATRIX.md)
- [53 项候选 Mixin 的逐项调查](docs/porting-2026-10-20/MIXIN_AUDIT.md)
- [阶段、工作量与实际验收清单](docs/porting-2026-10-20/EXECUTION_AND_ACCEPTANCE.md)
- [源码提交、工件、哈希与证据索引](docs/porting-2026-10-20/SOURCE_AND_EVIDENCE.md)

## 1. 结论与推荐版本

建议采用 **Minecraft 1.20.1 + Forge 47.4.28 + Java 17 + ModDevGradle Legacy Forge 2.0.148 + AE2 15.4.11**。四个选定附属均已找到 Forge 1.20.1 发布工件；保留并适配它们是可行方向，但存在具体 API/功能缺口，不能直接降版本号交付。

主要成本是 **LDLib2→LDLib1 的 GUI/同步适配、AE2 内部 Mixin、物品/NBT/Recipe/网络边界**。GUI 优先验证“覆盖本项目用法的 LDLib2 UI 兼容层 + LDLib1 后端”，集中处理控件、布局、事件和绑定差异，尽量复用现有界面源码与 JSON；原型通过后推广，具体边界见第 6.9 节。main 的精确数值、独立规划、路由与循环求解、FastPath 决策、存储账本和多方块业务规则应保留。

| 层次 | 建议锁定 | 核实依据与选择理由 |
|---|---|---|
| 游戏 / Loader | Minecraft 1.20.1 / Forge **47.4.28** | 官方截至调查日 latest；官方 recommended 为 **47.4.10**，可作为基础兼容验证点 |
| Java / Gradle | Java **17** / Gradle **8.12.1** | 游戏目标 Java17；先沿用 main wrapper，避免同时升级 Gradle 大版本 |
| MDG | **net.neoforged.moddev.legacyforge 2.0.148** | 官方 Maven latest/release 均为此版本；2.0.147 已不是最新候选 |
| AE2 | **15.4.11 Forge** | 官方发布及 `forge/v15.4.11`；其自身已用 MDG Legacy，第一轮严格锁定 |
| Registrate | **MC1.20-1.3.11** | 作者 Maven 有正式工件，已取得同版本 sources JAR |
| LDLib | **1.0.52.a Forge 1.20.1** | 发布列表及目标分支版本一致；API 与 LDLib2 不直接兼容，优先验证项目所需 API 子集的 UI 兼容层 |
| GuideME | **20.1.15 Forge** | 正式发布，满足 AE2/ExtendedAE 的 `[20.1.7,20.2.0)` |
| ExtendedAE | **1.20-1.4.21-forge** | 实际发布 JAR 已核对；mod ID 为 **expatternprovider** |
| AdvancedAE | **1.3.6-1.20.1** | Forge 发布 JAR、目标标签与字节码核对；内部含 AE2AddonLib 1.0.4-1.20.1 |
| ExtendedAE Plus | **1.6.3，Forge 文件 HNyEHPOp** | 发布 JAR 与 1.20.1 分支 API 核对，不能只按显示版本选文件 |
| AE2LT | **2.1.2-beta2-forge.1.20.1** | 官方 Beta，发布 JAR 已核对批量/时间轮 API |
| AE2LT 前置 | **Thunderbolt 2.0.3-beta2-forge.1.20.1** | AE2LT 元数据要求 `[2.0.3-beta2,2.1.0)`，必须成对锁定 |
| ExtendedAE 前置 | **Glodium 1.20-1.5-forge** | 官方目标工件；满足 ExtendedAE 的 Glodium 最低范围 |
| AdvancedAE 前置 | **GeckoLib 4.8.4 Forge 1.20.1** | 官方版本d83Wdhdn声明required；量子护甲直接实现GeoItem；目标工件aC5KMoNg |
| Mixin / MixinExtras | Loader 运行时 + Mixin AP候选 **0.8.5:processor**；MixinExtras **0.5.3 Forge** | MDG 官方 Legacy 配置；满足 main 对 WrapOperation 等和 MixinExtras 最低版本的要求 |

关键结果：

1. **main 没有接入独立 `:core` 模块**。`settings.gradle` 无 include，锁定提交无 `core/build.gradle`。算法可复用不等于已经具备一个原样拷贝的跨版本模块。
2. **LDLib2 不是 LDLib1 的兼容升级，但可以为本项目编写 UI 转接层**。main 引用的 74 种 LDLib2 类型中，47 种在目标源码连同名类型都没有；其余也需核对方法。这个统计说明不能直接替换依赖，不代表所有界面都必须逐个重写。优先在兼容层实现实际使用的控件、布局、事件、数据绑定和菜单/RPC 语义，允许调整 import 与部分自定义组件，并重新编译；可复用比例尚待原型验证。
3. **ExtendedAE 的 mod ID/资源命名空间改变**：`extendedae`→`expatternprovider`。Loader 判断、资源与配方、Mixin 条件必须一起处理。
4. **AdvancedAE 的方向样板接口改变**：main 用 `IAdvPatternDetails`，目标发布版本用 **`AdvPatternDetails`**；仍有方向映射能力，应适配接口，不应删除定向投料。
5. **EAE 大集成必须保留，配方缺口须单列解决**：所核对目标 ExtendedAE 未提供 main 的 `crystal_assembler`；需补齐并验证明确的目标配方来源与适配方案，不能把空壳机型或禁用模块作为完成交付。Plus 的 `crystal_assembler_plus` 仍缺失，但其大集成机型按本轮要求整体移除，不再保留禁用入口；Plus 的供应器、矩阵、倍率等其他联动继续适配。
6. **AE2LT 的部分接口存在、部分缺失**：发布 JAR 有 LightningBatchProvider、TianshuSynthesizer、普通输出和过载登记 API，但没有 `insertExactCpuOutput(BigInteger)`。只限制该子路径；ECO 自身精确大订单功能不能因此整体降级。
7. **一个 ExtendedAE 发布标签不能作为目标源码依据**：`1.20-1.4.21-forge` 实际指向的源码声明 26.1.2。本文排除该标签作为 1.20.1 API 依据，改用明确的目标提交与 Forge 发布 JAR，详见证据索引。

## 2. 调查基准与证据边界

调查开始时已执行 `git fetch origin main` 并锁定上述提交；交付前再次fetch确认远端main仍为该SHA。本地后来出现的提交和未提交改动不纳入此基准，详见`final-main-recheck.json`。原工作区存在未提交改动，调查从锁定提交导出独立快照，未纳入脏工作区内容。只读快照位于：

`E:\Minecraft Project\_comparison_sources\neoecoae-main-dc8344f48-20261020`

上游先在 `E:\Minecraft Project` 查找，更新远端引用后读取目标版本；缺失仓库从官方 GitHub 取得。没有强制覆盖第三方仓库的工作区。本报告使用目标分支、标签或提交，并对关键接口再查发布 JAR；不是用默认分支推断 1.20.1。

main 全量基准有 911 个主 Java 文件（111,905 行）、262 个测试 Java 文件、63 个配置启用的 Mixin。此前按核心与四附属范围调查得到 **53 个候选 Mixin**：核心、终端、AdvancedAE、ExtendedAE、AE2LT/前置桥；剔除其他附属专属项。53 是原调查对象数，不表示已经可直接在目标加载，也不是结构终端与四种大集成实施后的最终数量；实施前复核新增/移除的入口并更新清单。

对原 main 扫描还得到：500 个文件引用 AE2、132 个引用 NeoForge、97 个引用 LDLib2；16 个命中 DataComponent、20 个命中 StreamCodec/RegistryFriendlyByteBuf、58 个命中 HolderLookup.Provider。它们是**原全量源码的耦合统计**，不是最终要改的文件数。范围收缩后的共享代码/调用边界见 `evidence/four-addon-scope.json`、`scope-boundary-references.csv`。

本次已做：源码锁定/检索、官方发布查询、main 依赖图解析、关键 JAR 元数据和 javap/类版本检查、报告一致性验证。**没有编译或运行目标 Mod，也没有游戏功能验收**。下面的配置和关卡是实施方案，不能作为已移植成功的证据。

## 3. main 的实际构建基线

核对文件：[gradle.properties](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/gradle.properties)、[build.gradle](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/build.gradle)、[settings.gradle](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/settings.gradle)、[Mod元数据](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/dc8344f48ee866e57e02c285fdda5e0fb5da9421/src/main/templates/META-INF/neoforge.mods.toml)。

| 项目 | main 声明 | 实际引用/解析 |
|---|---|---|
| Minecraft | 1.21.1 | NeoForm 1.21.1-20240808.144430、Minecraft dependencies 1.21.1 |
| NeoForge | 21.1.251 | `neoForge.version=project.neo_version`，实际选 21.1.251；FML loader 4.0.44 |
| Java | toolchain 21 | 编译目标21；Gradle启动JVM与JavaCompile toolchain须区分，本次未重编译main |
| Gradle / MDG | wrapper8.12.1 / 普通插件2.0.78 | 当前并非Legacy；解析日志提示有Gradle9不兼容的弃用用法 |
| AE2 | 19.2.18 | strictly约束，compile/runtime都选19.2.18；Mod元数据最低值19.2.17不是实际开发版本 |
| Registrate | MC1.21.1-1.4.8 | api + jarJar；实际编译/运行选该版本 |
| LDLib2 | 2.2.40 | `ldlib2-neoforge-1.21.1:2.2.40:all`，transitive=false |
| GuideME | 21.1.1 | compileOnly `:api` + runtimeOnly完整工件 |
| ExtendedAE | Curse项目892005，文件8811572 | 真实JAR版本 **1.21-2.2.36-neoforge**，mod ID extendedae |
| AdvancedAE | Curse项目1084104，文件8564177 | 真实JAR版本 **1.6.12-1.21.1** |
| ExtendedAE Plus | 测试依赖文件8755074 | 真实JAR版本 **1.6.2**；主业务还有反射桥，默认未安装不等于无联动 |
| AE2LT | Modrinth AosdXlfS | 默认 **2.1.2-beta3**；compileOnly/test/dev三处；`ae2lt_local_jar`可覆盖 |
| Thunderbolt | Modrinth oyVnlG5A | **2.0.3-beta2**，同样在编译/测试/dev配置 |
| GeckoLib / KubeJS | 4.8.3 / 2101.7.2-build.353 | 前者implementation，后者compileOnly。主Java无直接GeckoLib import，但目标AdvancedAE仍要求GeckoLib4.8.4，作为其必需基础库保留；KubeJS可选入口不进入本轮范围 |
| Yoga / Taffy | 1.0.0 / 1.1.3 | compileOnly，但布局代码有直接引用；在 UI 兼容层核对实际布局语义与 Java17 可用实现，是否保留依赖由原型决定 |

本次 main 的 compileClasspath/runtimeClasspath/testCompileClasspath/testRuntimeClasspath/devMods/独立dev配置/annotationProcessor 解析均无 unresolved。证据：`main-resolved-dependencies.json`、`main-dependency-resolution.log`。这只证明**依赖图解析**，不证明原 main 的所有开发 Mod 可同时启动。四个附属的真实文件版本另见 `main-four-addon-jar-metadata.json`。

## 4. 先收敛源码范围，再迁移平台

目标是保留核心及四个附属的行为，而不是只在Gradle删掉其余依赖。主源码存在跨包调用和静态链接：

- 保留 `compat/ae2`、`compat/advanced_ae`、`compat/extendedae`、`compat/extendedaeplus`、`compat/ae2lt`、AE2LT所需`compat/thunderbolt`、`compat/storage`及`integration/ae2lt`。
- `ECOPatternIntegrations`、`ECOProcessingPatternDispatcher`、工作站配方登记、GUI工具按钮、数据生成、测试fixture需同步去掉非目标附属入口。
- `integration`目录内可能有被核心预览/界面复用的DTO和工具，必须提取/保留共享部分，不能盲删整目录后补编译错误。
- 删除非目标Mixin配置/声明，并从核心配置移除仅服务非目标附属的条目；**StoredEnergyAmountMixin仍是核心NBT/同步桥，不能误删**。
- 删除无效可选依赖元数据、模块卡/配方生成和运行JAR复制任务；目标开发目录只装选定集合。
- 大集成的注册、机型/模块入口、配方、资源、指南与测试只保留常规、AAE、EAE、AE2LT 四组；AE2LT 的三类配方归入同一机型，其余机型直接移除，不留下禁用卡或空页面。
- 保留 1.20.1 结构终端并接入最终保留结构；删除机器 UI 自动搭建按钮及其专用绑定/动作。若搭建底层由结构终端复用，则保留公共后端，不随按钮一起删除。
- 测试范围相应收敛，核心算法/存储/GUI测试仍保留；不能为了减少依赖而删掉验证核心行为的场景。

初扫118个候选非目标联动包文件，并生成跨边界引用清单。该清单是收敛任务的输入，不是本次已删除的文件，也不是可直接一键排除的最终列表。

## 5. ModDevGradle Legacy 构建迁移

### 5.1 工具适用范围

[MDG官方LEGACY.md](https://github.com/neoforged/ModDevGradle/blob/e9c127db51cd59d8d19e238af6a01e071d769661/LEGACY.md)明确支持Forge/Vanilla Minecraft 1.17～1.20.1。Legacy是叠加在普通插件能力上的扩展。普通`net.neoforged.moddev`不能通过把`neoForge.version`写成Forge47.x就成为Forge构建。

普通插件的[官方README](https://github.com/neoforged/ModDevGradle/blob/e9c127db51cd59d8d19e238af6a01e071d769661/README.md#L45)示例使用NeoForge21.0.103-beta，并标注面向21.0.x之后的NeoForge版本；本报告不据此推断它支持旧NeoForge47.x。Forge1.20.1和旧NeoForge1.20.1均按Legacy入口配置。

AE2 **15.4.11** 的 `build.gradle/settings.gradle` 已用 Legacy 插件（2.0.86）；GuideME **20.1.15** 也用 Legacy（2.0.93）。这提供了同游戏版本的实际上游构建参考，因此没有改用ForgeGradle的先决要求。新工程建议用已核实正式发布的2.0.148。

### 5.2 可实施配置骨架

以下为实施阶段配置草案，**没有在目标目录创建build.gradle或证明这段配置已完成运行验收**。

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev.legacyforge' version '2.0.148'
    id 'io.freefair.lombok' version '8.7.1'
}
java.toolchain.languageVersion = JavaLanguageVersion.of(17)

repositories {
    mavenCentral()
    maven { url = 'https://maven.tterrag.com/' }
    maven { url = 'https://maven.firstdarkdev.xyz/snapshots' }
    maven { url = 'https://maven.blamejared.com/' }
    maven { url = 'https://modmaven.dev/' }
}

legacyForge {
    version = '1.20.1-47.4.28'
    validateAccessTransformers = true
    runs {
        client { client() }
        server { server(); programArgument '--nogui' }
        data {
            data()
            programArguments.addAll '--mod', 'neoecoae', '--all',
                '--output', file('src/generated/resources').absolutePath,
                '--existing', file('src/main/resources').absolutePath
        }
    }
    mods { neoecoae { sourceSet sourceSets.main } }
}

dependencies {
    modImplementation('appeng:appliedenergistics2-forge:15.4.11') {
        version { strictly '15.4.11' }
    }
    modImplementation 'org.appliedenergistics:guideme:20.1.15'
    modImplementation 'com.lowdragmc.ldlib:ldlib-forge-1.20.1:1.0.52.a'
    modApi 'com.tterrag.registrate:Registrate:MC1.20-1.3.11'
    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'
    // 四个附属、必需前置及jarJar按矩阵显式补齐；先实现base profile。
}
mixin {
    add sourceSets.main, 'neoecoae.refmap.json'
    config 'neoecoae.mixins.json'
    // 其余保留配置同样登记。
}
tasks.named('jar', Jar) {
    manifest.attributes('MixinConfigs': 'neoecoae.mixins.json')
    // 最终值列出本构建所有保留配置，而不是只有上面这一项。
}
```

实施还须完成：`mods.toml`替换表、loaderVersion `[47,)`、目标MC/AE2范围、Java17 Mixin JSON、资源目录、sources JAR、Lombok exclusions、jarJar、依赖锁定/校验与发布元数据。`neoForge.ideSyncTask`迁到对应Legacy能力。先使用Mojang官方映射；若需要Parchment，配置1.20.1/2023.09.03，不能残留main的1.21.1映射版本。

### 5.3 AT、Mixin与SRG

- main有13条有效AT。Forge1.20.1的AT按**目标SRG成员名和描述符**重建。例如AE2目标AT可核对Slot.x=`f_40220_`、Slot.y=`f_40221_`、renderSlot=`m_280092_`、isHovering=`m_97774_`。其他项从目标映射逐条查；启用validateAccessTransformers，检查实际打包路径。
- Legacy配置Mixin AP生成refmap；所有有Mixin的source set都配置。JSON使用JAVA_17并指向正确refmap。Minecraft成员需要映射；AE2/附属未混淆成员通常remap=false，但混合调用的注入点要逐项判定，不能统一替换。
- main的NeoForge `[[mixins]] requiredMods`不能照搬到Forge47。用Manifest加IMixinConfigPlugin/延迟加载处理可选项，在外部类链接前检查Mod、版本与API。`required=false`不能代替类加载隔离。
- MixinExtras0.5.3用Forge工件；检查编译、AP、runtime和jarJar最终选择，不能依赖其他Mod偶然携带兼容版本。
- Legacy为jar自动配置**reobfJar**，将Mojang命名开发代码重混淆成SRG；开发JAR位于build/devlibs。上传/发布引用reobf输出。额外Shadow/JAR任务用`obfuscation.reobfuscate(...)`登记。
- 验证IDE运行后，还要在普通Forge安装中加载同一reobf JAR，检查AT、refmap、MixinConfigs、内嵌依赖和主类major=61；IDE成功不是发布产物成功。

### 5.4 依赖反混淆与运行

Mod依赖使用`modImplementation/modCompileOnly/modRuntimeOnly/modApi`。这些Legacy配置**不传递**：显式补齐GuideME、Glodium；AdvancedAE内嵌AE2AddonLib需要编译接口时配置对应目标库，运行避免重复副本；AE2LT与Thunderbolt一起锁定。测试等自定义配置使用`obfuscation.createRemappingConfiguration(...)`，避免将SRG JAR直接作为Mojang命名测试classpath。

**已验证坐标**：AE2 15使用`appeng:appliedenergistics2-forge:15.4.11`（Modmaven），不能照搬main的group/artifact；GuideME20.1.15从Maven Central取得；LDLib1与Registrate的目标POM也已取得。AdvancedAE所需`io.github.pedroksl:ae2addonlib:1.0.4-1.20.1`在Maven Central存在，运行由其内嵌提供；还须安装GeckoLib4.8.4目标文件。证据见`corrected-coordinate-probes.json`与`selected-dependency-closure.json`。

纯Java库继续使用普通Gradle配置。Registrate/MixinExtras选择明确的jarJar或外部依赖策略，检查最终嵌套清单。开发客户端、专服、数据生成用独立profile/目录，不继续使用原main全量`devMods`复制逻辑。

### 5.5 NeoForge 1.20.1备选

Legacy源码明确支持此旧版本，配置应为：

```groovy
legacyForge {
    enable { neoForgeVersion = '1.20.1-47.1.106' }
}
```

其工件为`net.neoforged:forge:1.20.1-47.1.106`；`legacyForge.version`简写设置的是forgeVersion，不能混用。旧NeoForge仍采用Forge时代的接口/SRG体系，不能保留main的`net.neoforged.neoforge.*`导入。

本次首选Forge，因为四个实际选定工件均按Forge核对。NeoForge1.20.1的共装/Loader范围仍需独立验证，不能仅靠版本号或平台双标签认定兼容；本报告不承诺该备选已通过运行。

## 6. 核心源码迁移清单

下列路径相对于锁定main。完整原始命中位置在证据CSV，Mixin另有逐项附录。

### 6.1 Java 21与原版API

| 涉及位置/边界 | 必须调整 | 可复用 | 主要风险与验证 |
|---|---|---|---|
| 全项目，尤其crafting/amount、planner、GUI | List首尾/反转等新方法、Math.clamp；按接收者区分Java17已有Deque/第三方Pair方法 | record、sealed、switch表达式、instanceof模式、BigInteger可继续使用 | clamp上下界/NaN语义、可变反转视图；必须在JDK17编译/测试 |
| all/NEDataComponents、api/components/AutoExportSides、盘/样板标记 | DataComponent注册与ItemStack读写改NBT；统一键、UUID和精确数量codec | 字段含义、版本号、ID、数量模型 | 复制堆栈共享UUID、默认值、未知键；往返/移槽/重启测试 |
| ECOProcessingComponentMatching、compat/ae2/AE2PatternIntrospection、execution/fastpath | 组件比较改为目标ItemStack/NBT；区分普通、模糊耐久和容器返回 | 模糊策略、快路分类和安全回退 | 不能扩大为同物品任意NBT等价；损坏工具、附魔、返回物实测 |
| recipe/*、NERecipeTypes | RecipeInput/CraftingInput/RecipeHolder降为目标Container/CraftingContainer与Recipe ID；实现fromJson/fromNetwork/toNetwork | 配方消耗、产出、优先级、能量 | 重载缓存、空槽、单位与返回物；目标JSON和实际执行验证 |
| SizedIngredient/SizedFluidIngredient调用处 | 目标Ingredient/流体判断+独立数量DTO | 数量/标签匹配语义 | “能匹配”与“实际扣除量”必须一致，覆盖标签和极值 |
| Block/BE/资源构造 | 原版方法签名、NBT保存参数、ticker/use、ResourceLocation/Component便利方法 | 注册ID、结构与交互意图 | 删除override或参数不能代替正确实现；放置、交互、卸载验证 |

### 6.2 Forge注册、事件与能力

涉及`NeoECOAE.java`、`registration/*`、`all/NERegistries`、`all/NEBlockEntities`、`api/integration/*`、`event/ECOStorageLifecycleEvents`。

构造入口改为Forge47的FMLJavaModLoadingContext/ModLoadingContext方式；事件总线、服务端配置同步、注册时机、生命周期和资源包发现逐项映射。Registrate自定义builder不能继续依赖现代ModContainer.getEventBus。cell type/tier注册表保持ID与语义，按目标RegistryBuilder/DeferredRegister生命周期实现。

集中`RegisterCapabilitiesEvent`改为BE的getCapability/LazyOptional或对应附加能力事件；维护方向、缓存失效、invalidate/revive。AE2 15没有现代AECapabilities，使用目标AE2 Capabilities/网格宿主机制。掉块/卸载/朝向变化后必须让旧引用失效，防止库存仍被管道访问。

IntegrationManager改用Forge加载期接口或显式延迟登记，且只登记四个目标入口。目标缺席时不加载带外部类型的实现类；注册顺序和可选依赖顺序必须可复现。

### 6.3 网络与数据同步

涉及`network/ECONetwork`、`MenuDataTransport`、`MenuStreamAssembler`、`MenuDataCodecs`、`ECOBigOrder*`和GUI菜单。

保留菜单分片/增量、数量codec、超时/清理语义；CustomPacketPayload/RegisterPayloadHandlersEvent转为**SimpleChannel + FriendlyByteBuf**。按最终功能集合重新登记消息ID、方向、协议版本。非目标工具集成消息不进入本轮协议。

服务端处理器验证sender、菜单ID、机器位置/所有权和权限，使用enqueueWork进入主线程。BigInteger用有界字节数组/字符串，校验长度与符号，不经过double/截断long。关窗、断连、跨维度、数据包重载时废弃过期快照/分片。

验证必须包含专服+双客户端同时修改同设备、菜单切换、过期/重复分片、错误协议拒绝。单机正确不足以证明线程和同步正确。

### 6.4 AE2 19.2.18→15.4.11

| main边界 | 目标差异 | 迁移方式 |
|---|---|---|
| AEKey/AEKeyType/GenericStack/KeyCounter/MEStorage | 核心抽象仍在；组件/NBT和序列化参数改变 | 统一key/数量适配边界；外部long接口与内部精确量分开 |
| IPatternDetails.getOutputs | 19.x为List，15.x为GenericStack[]；inputs仍为数组 | 所有自定义Pattern和批量封装适配；保持顺序、身份和副产物 |
| AECraftingPattern | sparseInputs数组、CraftingPatternEncoding NBT；无现代positionedPattern/RecipeHolder | 重做introspection/accessor，保留3×3空位/偏移/替代/返回物 |
| AEComponents | 目标无该组件表 | 用15.x的NBT与pattern helper，不伪造组件 |
| AENetworkedBlockEntity/Powered | 目标AENetworkBlockEntity/AENetworkPowerBlockEntity等旧层次 | 重接节点、能量、能力与保存加载 |
| PowerUnit | 目标PowerUnits | 单位保持；AE/FE边界按实际适用范围验证 |
| ClientboundPacket、clientbound.*Packet | 目标旧core.sync.packets体系 | 本Mod消息走SimpleChannel；AE2确认/状态交互按15.x重接 |
| MenuHostLocator、AE2Button | 类/接口路径不一致 | 对照15.x菜单定位与GUI构造，不复制19.x基础管理器 |
| StorageCellDisassemblyRecipe | 目标缺类 | 自有目标serializer保留空盘检查与拆解返回，不能删除核心拆盘功能 |

额外必须重写的差异：

- AE2 15及AdvancedAE1.3.6的`ExecutingCraftingJob`均没有`suspended`。把main两个Accessor改为ECO持有的暂停字段/接口，接入job NBT读写，并在快路和原生执行入口同时阻止不确定提交后的重试。
- `CraftingCPUMenu.toggleScheduling`不存在；main用它暂停/恢复ECO任务。目标`CPU_SELECTION_MODE`是另一种设置，不能借它代替任务暂停。用本Mod菜单动作实现，暂停位单独同步；15.x `CraftingStatus`是五参构造，没有main的第六个暂停参数。
- `CraftingService`没有三种`*GlobalCraftingProvider`方法。本轮范围收敛后无相应调用者；从Mixin方法列表去掉这些目标，保留`addNode/removeNode/refreshNodeCraftingProvider`驱动providerRevision和游标失效，不为已排除功能重建全局供应器注册。

主要依据：AE2两端精确标签和`evidence/ae2-15-missing-imports.json`。这个文件是全量main导入扫描；其中通配符`appeng.api.stacks.*`不是缺失类型，范围外条目不进入移植任务。

### 6.5 独立规划器、快速规划与精确大订单

涉及`crafting/planner/**`、`crafting/amount/**`、`crafting/graph/**`、`crafting/adapter/ae2/**`、`api/me/planning/**`、订单网络和对应GUI。

保留PlannerAmount的long快路/BigInteger晋升、图/SCC、循环种子、副产物、路由搜索、预算/取消、缓存设计、失败分类、精确计划与分段搜索。`ECOBigOrderPlanner`每次捕获新库存、复用不变供应器结构的设计继续使用。

改写边界：IGrid/ICraftingService库存与供应器快照、Pattern输出数组、ICraftingPlan构造、CPU选择/提交、计划注册、RecipeManager重载与订单NBT恢复。必要时先提取小适配接口，不把移植变成重新设计求解器。

ECOExternalCpuSupport对普通原生/AdvancedAE CPU接收能力的约束保留：精确计划、超long账本、特殊执行模式不能因CPU按钮可点击就无条件提交。无法一次表示的数量必须分段或明确返回不支持，禁止饱和后假装完成全部订单。

复用现有planner/solve测试及ECOBigOrderPlannerTest场景，再用真实AE2验证：多路径、循环种子、副产物、缺料模拟、重载、取消、分段、重启。异步任务只读冻结快照，不直接遍历变化中的世界。快速规划不仅要结果正确，还要能实际执行。

### 6.6 FastPath、原生与AdvancedAE CPU

涉及`crafting/execution/fastpath/**`、`batch/**`、`worker/**`、`ECOExternalCpuFastPath`及相关Mixin。

保留耐久模型、状态转移、安全分类、缓存、输入租约、准备/提交、输出回执和取消回收。配方执行与ItemStack比较改为目标API，批处理必须与逐次执行的消耗/返回物/耐久/能量等价。

AE2 15和AdvancedAE1.3.6均保留`executeCrafting(int,CraftingService,IEnergyService,Level)`、trySubmitJob、tickCraftingLogic和finishJob；可重接main核心挂点。两者的NBT方法仅有CompoundTag参数。TaskProgress、waitingFor、时间统计和持久化字段逐项对应，不因为类名相同就跳过验证。

验收覆盖部分接收、共享输入、输出背压、提交前后机器变化、取消/掉块/卸载、异常和两CPU竞争。未经所有权确认不能退款并重放；不得为吞吐率牺牲数量守恒。AE2LT桥的具体事务语义见兼容矩阵。

### 6.7 超大数量存储和持久化

涉及`impl/storage/ECOStorageCell`、`impl/storage/infinite/**`、`blocks/entity/storage/**`、`api/storage/**`、`crafting/display/terminal/**`。

main使用**SavedDataInfiniteStorageEngine + ECOInfiniteStorageData**。ECOInfiniteStorageDomains使用独立`_v3`数据名，并保留LegacyInfiniteStorageReader/旧journal恢复读取。按该main设计移植，不引入旧NeoECO项目的存储实现。

保留有限容量/类型/字节统计、BigInteger晋升、UUID/域归属、模拟与实际操作、恢复锁、迁移回执、未知条目保留、dirty/revision及失败后只读退化。适配SavedData.Factory/computeIfAbsent/save签名、AEKey NBT、盘上组件和服务端保存生命周期。

终端long可视投影与精确显示通道分开；显示大数不能改变真实抽取规则。所选ExtendedAE/AE2LT的显式无界盘识别单独适配，不能把“很大的有限数”当无限。

验收包括2^31/2^63边界、更大十进制量、模拟不写状态、盘移动/复制、跨维度、正常重启、损坏快照、写失败、恢复中断/重复回执、缺失key后恢复。不直接将1.21.1游戏世界在1.20.1打开作为迁移路径；若要求跨游戏版本存档转换应另立格式任务。

### 6.8 大型多方块和工作站

涉及`multiblock/definition/calculator/cluster/placement`、`NEMultiBlocks`、大型工作站controller/interface、`LargeWorkstationPatternProvider`、`recipe/LargeWorkstation*`。

#### 6.8.1 大集成仅保留四种机型

| 保留机型 | 能力范围 | 实施与验收边界 |
|---|---|---|
| 常规大集成 | 本 Mod 核心工作站能力和常规配方 | base 环境可用；保留核心吞吐、容量、输入/输出与返回物语义 |
| AAE 大集成 | AdvancedAE 关联的工作站能力，含反应室配方适配 | 使用目标 `advanced_ae:reaction` API；核对物品/流体、能量和返回物 |
| EAE 大集成 | ExtendedAE 关联的工作站能力 | 必留交付项；目标 `crystal_assembler` 缺口须补齐配方来源与可验证适配，不能只保留名称/外观或标为不可用 |
| AE2LT 大集成 | AE2LT 关联的工作站能力 | `overload_processing`、`lightning_assembly`、`lightning_simulation` 三类配方归入这一种机型，不拆算成三台 |

**四种机型是产品范围，不是配方类型数量，也不要求它们必须拥有四套重复实现。** 可共享基础结构、控制器/接口、UI 与执行代码，但注册/选择入口、能力归属和验收清单必须明确区分四种机型。附属缺席时按依赖门禁处理相应机型，base 不因此强依赖所有附属。

其余大集成（包括 ExtendedAE Plus 的大集成）不做本轮内容：移除对应机型/模块入口、专属配方与卡、数据生成、指南/配方查看展示及专属测试场景。公共 DTO、执行/预览工具按保留机型的调用关系留下；ExtendedAE Plus 的倍率、强制计划、虚拟合成与超级矩阵等非大集成联动继续保留。

锁定 main 的 `NEMultiBlocks` 有 **10 个原始定义**：工作站 1 个，以及存储/计算/合成系统各 L4、L6、L9 三档。该数是源基线，不再直接作为目标结构/机型数量的验收标准。**本次“四台”只收敛大集成，不删除存储、计算、合成系统的九个等级组合。** 四种工作站机型是否共享结构定义由实施决定，最终逐个列出并验收所有保留结构与机型。

#### 6.8.2 保留结构终端，移除 UI 自动搭建按钮

1. **保留 1.20.1 结构终端特色。** 针对性核对该终端原有功能契约与必要实现，保留终端物品/注册、配方、菜单/UI、网络与资源等实际需要的路径，并将最终保留的结构/机型接入。结构终端的来源与目标行为单独登记，不能因锁定 main 未提供相同入口而删去。
2. **结构搭建的玩家入口统一走结构终端。** 核对原有交互、服务端执行、权限、材料消耗与失败反馈；需要的底层放置/搭建能力允许继续共用，不与控制器 UI 的按钮绑定。
3. **移除机器 UI 中的自动搭建按钮。** 同步删除对应 Java/JSON 控件、点击/RPC 绑定、专用客户端动作和仅为该入口存在的提示/资源；不保留禁用按钮或空白占位。涉及公共搭建服务时，先确认结构终端调用关系再清理。
4. 验收结构终端可覆盖最终保留结构与四种大集成；机器 UI 不再出现自动搭建入口。验证终端交互与机器 UI 打开/关闭、槽位和双玩家同步互不串用，真实放置与扣料由服务端按原契约处理。

结构定义、重复段、方向、级别、部件分工和成型/拆除规则仍保留 main 的适用设计，按四种机型收敛。重接 AE2 节点/cluster、服务端 tick、BE 同步、相邻能力、交互与预览世界；大型结构不能只用一个最小结构验证。

#### 6.8.3 配方适配与 EAE 必留项的缺口

原调查得到的“六类外部配方候选、四类已确认目标 API”只是旧范围的 API 事实：AdvancedAE reaction 与 AE2LT 三类已有目标能力，ExtendedAE/Plus 的两类晶体配方在指定目标中缺失。**本轮按四种机型收敛后，Plus 大集成配方不再纳入；EAE 大集成仍是必做项，其缺口不能作为永久禁用该机型的交付理由。**

实施前为 EAE 明确配方来源、ID/serializer、物料/返回物、能量/耗时与资源生成方案。可评估以 ECO 自有配方/适配桥补齐，但必须先定义并验证与保留功能相符的行为，不假称目标 ExtendedAE 已提供缺失 API，也不擅自用其他机型配方充数。这是待解决的设计与实现项，目前尚无已验证方案；完成 EAE 大集成验收前不能宣称四种机型已交付。

对可接配方，以目标Recipe对象/serializer读取物品、流体、额外资源、能量、催化剂/返回物，不照搬1.21 codec。namespace变更、目标Recipe ID、单位与排序同时处理。对照main业务公式验证吞吐、实际容量和FT/FX，不根据旧调查猜公式。

### 6.9 LDLib2 UI 兼容层与 LDLib1 后端

**可行性结论：可以编写转接层，以 LDLib1 承载本项目使用的 LDLib2 UI API 子集。** 这是基于源码结构的工程判断，尚未实现、编译或运行验证。目标是集中适配两代 UI 的差异，让现有界面源码在调整 import、少量调用和自定义组件后重新编译复用；目前不能承诺具体复用比例或节省工时。

#### 6.9.1 兼容目标与结构

建议采用以下职责边界；包名和文件组织在实施阶段确定，不要求为此新增独立 Gradle 模块：

```text
现有 NeoECOAE 界面源码 / UI JSON / 业务状态
                    ↓
项目 UI 兼容层：控件树、布局、绘制、事件、数据绑定、RPC
                    ↓
LDLib1 Widget / WidgetGroup / ModularUI
       + Forge 1.20.1 菜单、槽位与网络
```

- 兼容层使用项目自己的包名，保留实际需要的 LDLib2 API 语义，允许调用方更换 import；不要求伪装成完整的 `com.lowdragmc.lowdraglib2` 包，也不直接装载 1.21.1 的 LDLib2 JAR。
- API 清单按本轮保留源码的实际调用收敛到方法、参数、返回类型、继承和绘制扩展点。原全量 74 种类型还包括 UI 之外的工具、同步与资源接口，不能直接作为兼容层的实现数量。
- 默认不承诺任意第三方 LDLib2 UI 源码或已编译 Mod 的兼容性。LDLib2 编辑器、完整样式语言等运行时能力不自动进入范围；NeoECOAE 现有规划图、结构预览及其必要绘制能力仍须保留。
- 若要求任意 LDLib2 UI 源码完全不改，布局、事件、同步及平台依赖都需进一步补齐，工程范围会接近回移 LDLib2 的 UI 运行时，应另行评估。

#### 6.9.2 API 与行为映射

| main 机制 | 兼容层与 LDLib1 后端的职责 | 必须保留的行为 |
|---|---|---|
| UI/UIElement、父子关系与生命周期 | 用 Widget/WidgetGroup/ModularUI 承载，适配增删子节点、初始化、挂接与移除 | 动态控件、窗口和菜单生命周期；关闭时解除绑定与监听 |
| Button/TextElement/TextField/Toggle/ScrollerView | 委托对应 Widget 或自定义封装，保留调用方需要的链式配置与扩展点 | 数字输入、禁用状态、滚动、焦点、键盘与鼠标操作 |
| `.layout()`、`.style()`、Taffy/Yoga 调用 | 提取实际用到的布局/样式子集，选择 Java17 可用的布局实现或实现该子集，再输出坐标和尺寸 | 绝对/相对定位、百分比、自动尺寸、Flex、间距、动态重排；不能用固定坐标替代而改变现有行为 |
| UIEvents | 在兼容层实现所需事件分发，再接 LDLib1 输入回调 | 捕获/目标/冒泡、停止传播、焦点、拖动、悬停；一次操作只触发一次业务动作 |
| ItemSlot/FluidSlot/InventorySlots | 分别适配真实菜单槽位、流体能力与仅显示/过滤控件；物品槽优先复用 SlotWidget 和目标 Container slot | Shift点击/拖拽、右键分配、双击；视觉索引与服务端索引一致；过滤槽不得变成真实库存 |
| IDataSource/DataBindingBuilder/SyncStrategy | 将初始数据、变化更新和同步方向映射到 widget 更新/动作通道或自有协议 | getter/setter与通知语义、初始值、S2C/C2S方向、解绑；服务端权威，不在本地GUI修改真实库存 |
| RPCEmitter/RPCEventBuilder | 将所需动作封装到目标更新/动作通道或 SimpleChannel；统一处理菜单生命周期与路由 | 菜单/设备/权限校验、参数上限、关闭后拒绝旧请求，避免控件默认回传与兼容层重复发送 |
| GUIContext/Stylesheet/图集、自定义绘制 | 目标绘制上下文和主题封装，适配自定义组件的绘制扩展点 | 裁剪、Z顺序、透明度、tooltip、精确数量、纹理坐标与性能 |
| Scene/TrackedDummyWorld | 单独适配目标 SceneWidget/预览世界，与普通控件兼容层共用必要资源接口 | 大型结构旋转、着色、遮挡和资源加载 |

`FieldManagedStorage`、holder/accessor、`IContentChangeAware` 等设备同步与持久化接口仍需单独适配 LDLib1 的 syncdata 能力，保持 dirty、保存、desc sync 和内容变化通知生命周期。UI 兼容层通过不等于这些接口已经完成迁移，也不替代 AE2 15 的菜单/屏幕 Mixin 适配。

#### 6.9.3 现有 JSON 界面的复用入口

main 已有自定义的 `NeoUiJsonLoader` / `NeoUiJsonContext`：loader 集中创建控件、应用布局与纹理、构建子节点；context 提供自定义组件和动态配置入口。优先保留现有 JSON 格式与素材，通过改造 loader 的控件创建/布局应用和 context 的接口类型，把后端接到项目 UI 兼容层。继续保留从客户端资源管理器或 classpath 加载资源的能力，确保专服菜单构造和无客户端测试可用。

该入口有助于集中适配，但 JSON 中的布局字段仍需实现等价语义。Java 自定义组件的继承关系、绘制重载、事件与同步调用仍要逐项迁移；不能仅替换解析器的返回类型就宣布所有界面兼容。

本轮界面内容同时执行第 6.8 节约束：机器 UI 的自动搭建按钮节点和专用动作/绑定从 Java 与 JSON 两端移除，避免资源重载后重新出现；其余大集成的专属界面入口一起删除。结构终端 UI 与其公共搭建后端按 1.20.1 保留需求单独接入。

#### 6.9.4 原型、推广与未覆盖项

P3 先完成一个包含 **真实槽位、滚动、精确数字、S2C 状态、C2S 动作及双客户端同步**的可运行界面，包含一次现有 JSON 加载和一个 Java 自定义组件。具体关卡为：

1. 建立实际使用 API 的清单，逐项标明已兼容、待补齐或需要调用方改写；未实现行为必须明确报错或登记，不能静默忽略布局、事件或绑定。
2. 先验证初始状态、变化更新、服务端处理与菜单关闭/重开，再核对布局和绘制；一次操作只产生一次服务端动作。
3. 在 GUI 缩放 1/2/3 和小窗口检查滚动、百分比/自动尺寸、焦点、裁剪、tooltip及精确数字；真实操作 Shift点击/拖拽、右键分配和双击，核对真实库存数量与槽位索引。
4. 专服连接两个玩家操作同一设备，覆盖关闭/重开、切换菜单、断连和旧请求；确认无客户端类泄漏、状态串用或重复扣料/移动。
5. 保存 API 覆盖表、与冻结 main 行为的对照、日志、截图/录像和数量账本。原型通过后，将兼容层推广到其余界面；未覆盖组件逐项评估补齐兼容层或局部改写。

保留核心存储筛选、规划图、合成进度、拖动窗口和工具提示。**编译、截图或 widget 单测不能替代真实鼠标手势与多人同步验收。** 任意 LDLib2 UI 的通用兼容或完整运行时回移不属于当前默认工程量；P3 后根据 API 覆盖与实际改动重估 P7，当前不预先下调预算。

源码依据见[证据索引中的 LDLib UI 转接层条目](docs/porting-2026-10-20/SOURCE_AND_EVIDENCE.md#ldlib-ui-转接层的源码依据)，实施阶段与通过条件见[阶段与验收计划](docs/porting-2026-10-20/EXECUTION_AND_ACCEPTANCE.md)。

### 6.10 注册库、指南、资源与数据生成

Registrate保留DSL和内容定义，改加载总线与目标provider泛型；使用实际1.3.11 sources JAR。GuideME20.1.15有`GuidesCommon.openGuide(Player,ResourceLocation,PageAnchor)`和PageAnchor，main指南入口有直接对应能力；保留guidebook、namespace和内容，核对markup/3D场景。

PNG/语言/模型设计继续用main。按1.20.1处理`recipes/loot_tables/advancements/tags/items`目录、recipe result字段、条件、tag namespace、资源包格式。NeoForge条件不能只改字符串，需按Forge目标schema和datagen生成。

按最终四种大集成清理专属注册、模块卡、配方、模型/贴图引用、语言、指南及配方查看入口；不生成范围外机型的禁用占位内容。为结构终端保留其必要配方与资源，并检查机器 UI 自动搭建按钮及专用动作引用已清除。共享素材按实际引用保留。

按base及四附属profile从空目录生成，检查重复资源、翻译、条件与未加载注册项。非目标可选集成和其测试/资源不纳入本轮。目标JAR检查指南复制、经典资源包、atlas/模型挂点和所有有效资源路径，随后用发布JAR做真实客户端/专服验收。

## 7. 四个附属的实施建议

详细版本、API、文件位置和证据见[兼容矩阵](docs/porting-2026-10-20/DEPENDENCY_MATRIX.md)。总原则如下：

- **ExtendedAE：保留。** 改ID/namespace，适配IOPort与无界盘；EAE 大集成是四种必留机型之一，缺失晶体配方单列为必须解决的适配项，补齐并验证前不能交付空壳机型。
- **AdvancedAE：保留。** 方向接口IAdvPatternDetails→AdvPatternDetails；保留方向图、倍率、反应室、CPU FastPath，NBT与嵌套任务字段按目标版本调整。
- **ExtendedAE Plus：保留其他联动，移除其大集成。** 倍率工厂、强制计划、虚拟合成和超级矩阵入口已在发布JAR确认；与AdvancedAE共同验证方向样板包装及计划元数据。不再保留 Plus 大集成机型、专属配方与禁用模块入口。
- **AE2LT：保留，按能力分层。** 公开inspect/submit、long输出、过载记账与IOPort优先适配；无法由公开API覆盖的main直达路径保留精确字段/描述符核对。BigInteger时间轮输出缺口在ECO侧保持精确账本，以可验证的long分段桥接或明确不接收该路径处理，不能把未完成量当完成。

任何降级只发生于**已经证实缺失的能力**。API名称不同优先适配，不直接认定不能移植。基础AE2/core与四附属缺席场景仍须可运行。

## 8. 工作量与结束条件

按[阶段与验收](docs/porting-2026-10-20/EXECUTION_AND_ACCEPTANCE.md)，上轮估算为 **60–101等效人日**，含阶段验证；加入20%–30%风险预留为 **72–132人日**。本次保留结构终端、移除 UI 自动搭建按钮、收敛四种大集成并要求补齐 EAE 后，尚未重新估算，旧数仅保留为预算基线。P0 核对结构终端与 EAE 配方方案、P3 验证 UI 兼容层后，重估 P7/P8/P9 及总量；这些数字不是实测或交付日期承诺。

完成标准必须同时满足：Java17编译/测试、目标资源生成、重混淆JAR普通安装、客户端/专服/双人、结构终端保留、机器 UI 无自动搭建按钮、仅四种大集成且均有实际功能、其余核心大结构与规划/精确订单/FastPath/存储，以及四个附属的独立和组合交互验收。编译成功、菜单能打开或所有文件已复制都不足以宣布完成。
