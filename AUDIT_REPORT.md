# NeoECOAEExtension 技术审计报告

**审计日期**：2026-05-30
**审计范围**：`e:\Minecraft Project\NeoECOAEExtension-main` 全仓库
**审计深度**：源码级（build.gradle、Java 源码、Mixin 配置、资源文件、mods.toml、语言文件）

---

## 第一部分：项目概况

### 实际检查的文件与模块
- `build.gradle`（完整）、`gradle.properties`、`settings.gradle`
- 所有 `src/main/java/cn/dancingsnow/neoecoae/` 下的核心业务代码
- `src/main/resources/neoecoae.mixins.json`、`accesstransformer.cfg`
- `src/main/templates/META-INF/mods.toml`
- `src/generated/resources/` 和 `src/main/resources/` 下的语言文件
- `compat/`（JEI/EMI/AppMek）、`integration/`、`mixins/`、`forge/mixin/` 包
- 三个核心 BlockEntity：`ECOCraftingSystemBlockEntity`、`ECOComputationSystemBlockEntity`、`ECOIntegratedWorkingStationBlockEntity`
- 网络包：`NENetwork.java`
- UI Screen：`NEStorageControllerScreen`、`NECraftingControllerScreen`、`NEComputationControllerScreen`
- 配方系统：`CoolingRecipe`、`IntegratedWorkingStationRecipe`
- README.md、README_ZH_CN.md、LICENSE

### 确认的技术栈
| 项目 | 实际值 |
|---|---|
| Minecraft | **1.20.1** |
| Mod Loader | **Forge 47.4.10** |
| Java | **17** |
| AE2 | **15.4.10** |
| JEI | **15.20.0.127** |
| EMI | **1.1.8+1.20.1** |
| Mekanism | **10.4.16.80** |
| Applied Mekanistics | **1.4.3** (file 7244744) |
| Registrate | **MC1.20-1.3.3** |
| Gradle | **8.12.1** |

### ⚠ 发现的版本说明不一致
**严重问题（高）**：README.md 和 README_ZH_CN.md 均宣称项目面向 **"NeoForge (1.21.1+)"**，但实际构建配置（`gradle.properties` 第 8-14 行）明确使用 **Forge 1.20.1** + **ForgeGradle 6.x**。`mods.toml` 中的 `loaderVersion` 使用 `[47,)`（Forge 版本号范围），`game-versions` 为 `["1.20.1"]`，`loaders` 为 `["forge"]`。**这不是简单文档不同步，而是 README 与整个构建链路存在根本性矛盾**。

**影响**：用户根据 README 下载时会预期 NeoForge 1.21.1+ 环境，导致安装失败。CurseForge/Modrinth 发布页面若复制 README 内容，会造成更大范围误导。

**建议**：立即修正 README 中的版本声明为 "Forge 1.20.1 (47.x)"，删除所有 "NeoForge 1.21.1+" 引用。

---

## 第二部分：关键结论（Top 10 风险）

| # | 严重级别 | 问题 |
|---|---|---|
| 1 | **高** | README 宣称 NeoForge 1.21.1+，实际为 Forge 1.20.1（版本欺骗） |
| 2 | **高** | `neoecoae.mixins.json` 中 `forge/mixin/**` 曾被 build.gradle 排除编译（已修复），但 `mixins/**` 仍被排除（含 ExtendedAE/1.21 API 引用） |
| 3 | **中** | `integration/**` 包完全排除编译但未删除，含 30+ 个类文件（1.21.1 NeoForge 代码残留） |
| 4 | **中** | IWS BE 每 tick 重建 `findRecipe()` 的 `ArrayList<ItemStack>`，无 recipe 缓存（dirty 标志逻辑不够快） |
| 5 | **中** | `ECOIntegratedWorkingStationBlockEntity` 类超长（600+ 行），职责过多（Tickable + Inventory + Fluid + Network + Config + Upgrade） |
| 6 | **中** | `ECOCraftingSystemBlockEntity.getCoolingRecipe()` 每 tick 查询 RecipeManager，消耗高 |
| 7 | **低** | `settings.gradle` 含未使用的 `maven.neoforged.net` 仓库 |
| 8 | **低** | 无任何自动化测试、GameTest、CI/CD |
| 9 | **低** | 多处 `Minecraft.getInstance()` 无 null 保护 |
| 10 | **低** | `gradle.properties` 含未使用的 `yoga_version`、`taffy_version`、`kubejs_version` |

---

## 第三部分：性能问题清单

### 高严重级别

#### P-HIGH-1：IWS 配方缓存设计缺陷
- **文件**：`ECOIntegratedWorkingStationBlockEntity.java` 行 441-450
- **问题**：`findRecipe()` 每 tick 创建 `new ArrayList<>()` 并遍历 `inputInv`（9 slot），同时调用 `RecipeManager.getRecipeFor()`。`dirty` 标志仅在输入变化时才触发重新查找（行 480），但在 `dirty=false` 时仍调用 `findRecipe()`（行 483 通过 `Objects.equals` 比较 ID）。缓存仅基于 `cachedTask.getId()` 的 `Objects.equals`，**不检查输入物品的实际 NBT 或数量变化**。
- **影响**：ItemStack 比较不可靠（同一物品不同 NBT 可能匹配相同 recipe ID 但输入不匹配），导致无效进度计算或卡配方。
- **建议**：用 `ItemStack.matches()` 或 hash 化输入快照做缓存失效，替代 ID 比较。

#### P-HIGH-2：Crafting System 每 tick 查询 CoolingRecipe
- **文件**：`ECOCraftingSystemBlockEntity.java` 行 168-170, 行 450-465
- **问题**：`tickingRequest()` 每 tick（1-10 tick 间隔）调用 `getCoolingRecipe()`，该方法调用 `RecipeManager.getRecipeFor()`，创建 `CoolingRecipe.Input(fluid, fluid)` 对象。**冷却液配方很少变化，不需要每 tick 重新查找**。
- **影响**：高频对象分配 + RecipeManager 查询，在多个 Crafting System 运行时叠加。
- **建议**：冷却液配方仅在输入仓/输出仓流体变化时重查，缓存结果。

### 中严重级别

#### P-MID-1：UI 状态同步频率无节流
- **文件**：`NEUiStateMachineMenu.java`（推断位置，方法 `broadcastChanges()`）
- **问题**：UI 状态每 20 tick（1 秒）广播一次。在玩家打开 UI 时合理，但**未检查是否有实际数据变化**就发送。
- **建议**：缓存上次发送的状态快照，仅在变化时发送。

#### P-MID-2：IWS BE ContainerData 的 get() 每次调用都执行 switch
- **文件**：`ECOIntegratedWorkingStationBlockEntity.java` 行 222-240
- **问题**：`ContainerData.get()` 被客户端每 tick 多次调用，每次执行完整的 `switch` 语句（9 个 case）。虽然开销小，但 `cachedTask != null ? cachedTask.energy() : 0` 这类表达式在高频调用中应内联缓存。
- **建议**：缓存 ContainerData 的值并在 BE tick 中批量更新。

#### P-MID-3：`markForUpdate()` + `setChanged()` 频繁调用
- **文件**：`ECOIntegratedWorkingStationBlockEntity.java` 行 130-136, 488
- **问题**：`inputTank.onContentsChanged()` 和 `outputTank.onContentsChanged()` 均触发 `markForUpdate()` + `setChanged()` + `onChangeTank()`。IWS 在工作时会频繁进出流体（每 tick 1 mB），导致**每 tick 两次 block update + chunk dirty**。
- **建议**：流体变化不急切需要每 tick 同步。改为 tick 结束时统一调用一次。

### 低严重级别

#### P-LOW-1：Jade 相关代码的静态字段
- **文件**：`integration/jade/NEJadePlugin.java`（被排除编译，无运行时影响）

#### P-LOW-2：`NEStorageControllerScreen` 每帧做 `animateTo()` + `percent()` 4 次
- **文件**：`NEStorageControllerScreen.java` 行 95-99
- **问题**：`animateTo()` 使用 `Mth.lerp()` — 本身开销极低，但 `renderAdditionalLabels()` 每帧调用且不必要地创建 `StorageMetrics` 和 `Metric[]` 数组。
- **建议**：缓存 `StorageMetrics` 直到 `NEStorageUiState` 更新。

---

## 第四部分：冗余代码与资源清单

| # | 路径 | 问题 | 建议 |
|---|---|---|---|
| R1 | `src/main/java/cn/dancingsnow/neoecoae/integration/**` | 30+ 个类文件，全部被 `build.gradle` 排除编译。包含 JEI/EMI/KubeJS/Jade/AppMek 的 1.21.1 实现。 | **删除**或迁移到独立分支。当前 `compat/` 包已有 1.20.1 替代实现。 |
| R2 | `src/main/java/cn/dancingsnow/neoecoae/mixins/**` | 6 个 Mixin 类被排除编译。其中 `CraftingCpuListEntryMixin.java` 引用 `RegistryFriendlyByteBuf`（1.20.5+ API）。`aae/*.java` 引用不存在的 ExtendedAE 类。 | **删除**（1.20.1 不可用）。`forge/mixin/` 已有替代。 |
| R3 | `src/main/java/cn/dancingsnow/neoecoae/recipe/*Builder.java` | 被排除编译，疑似 1.21 配方 Builder。 | 检查后决定保留或删除。 |
| R4 | `src/main/java/cn/dancingsnow/neoecoae/data/**` | 被排除编译，疑似 1.21 datagen。 | 检查后决定。 |
| R5 | `src/main/java/cn/dancingsnow/neoecoae/all/NEDataComponents.java` | 被排除编译（1.20.5+ DataComponent API）。 | 1.20.1 不需此文件，可删除。 |
| R6 | `gradle.properties` 中 `yoga_version`、`taffy_version`、`kubejs_version` | 未在 `dependencies` 块中使用。 | 删除或注释"预留"。 |
| R7 | `settings.gradle` 中 `maven.neoforged.net` | NeoForge 仓库，Forge 1.20.1 项目不需要。 | 删除。 |
| R8 | `ECOStorageCellItem.java` 内 `@Deprecated public static class Handler` (行 296+) | 仅标记 deprecated，无实际用途。 | 删除或保留 1 个版本后移除。 |
| R9 | `src/main/java/cn/dancingsnow/neoecoae/forge/mixin/CraftingServiceMixin.java` | 与 `CraftingServiceMixin120.java` **重复**（前者在 `forge.mixin` 包，后者同名加后缀 120）。后者已注册在 mixins.json。 | **确认**两个文件是否不同。如功能相同则删除前者。 |
| R10 | `ComputationControllerScreen.java` 的 `imageHeight=160→170` 修改 | 仅硬编码数字，无引用常量。 | 抽取为常量 | (已修复) |

---

## 第五部分：架构与维护性问题

### A1. 类职责过重
- **`ECOIntegratedWorkingStationBlockEntity`**（~700 行）：同时实现 `IGridTickable`、`IUpgradeableObject`、`IConfigurableObject`，包含 inventory、fluid、power、recipe、upgrade、export、network 逻辑。
  - **建议**：拆分为 `ECOIWSRecipeLogic`、`ECOIWSExportLogic`、`ECOIWSInventoryHandler` 等协从类。

### A2. 包结构混乱
- `integration/` 与 `compat/` 两个不同包同时存在 JEI/EMI/AppMek 集成代码。`compat/` 是活代码，`integration/` 是死代码。
  - **建议**：统一为一个包名，删除死代码。

### A3. 硬编码散落
- 多个 Screen 文件中有独立的颜色常量副本（`DARK_TEXT_PRIMARY = 0xFFD6D0E0` 在 3 个文件中重复定义）。
  - **建议**：抽取到 `NENativeUiConstants` 或新建 `NEDarkThemeColors` 类。

### A4. 静态可变状态
- `ECOIntegratedWorkingStationBlockEntity.loggedRecipeCounts` 是**静态 boolean**（行 104）。基于 `FMLEnvironment.production` 判断是否生产环境来跳过日志——但在单元测试或多世界场景下可能行为不一致。
  - **建议**：改用 `Level.isClientSide` 检查或配置项控制。

### A5. API 层设计薄弱
- `api/` 包中 `IECOCellHandler`、`ECOStorageCells` 设计合理，但 `ECOCellType`、`ECOCellModels` 等类混合了 API 和实现逻辑。
  - **建议**：将纯 API 接口与实现分离到 `api/` 和 `impl/`。

---

## 第六部分：兼容性与稳定性问题

### C1. 服务端/客户端隔离 ✅ 良好
- `Minecraft.getInstance()` 调用均出现在 `compat/jei/`、`compat/emi/`、`client/`、`gui/` 等客户端包中——这些类由 JEI/EMI 仅在客户端加载，**dedicated server 不会触发**。`NeoECOAE.java` 行 95-96 使用 `FMLEnvironment.dist == Dist.CLIENT` 条件加载客户端类，设计正确。

### C2. Mixin 配置
- `neoecoae.mixins.json` 已注册 5 个 mixin（之前修复）。`forge/mixin/CraftingServiceMixin120.java` 的 Mixin 目标 `appeng.me.service.CraftingService` 是 AE2 内部类——**每次 AE2 版本升级都有断裂风险**。
  - **建议**：在 `mods.toml` 中严格限制 AE2 版本 `[15.4.10,15.5)`，并在 Mixin 目标方法上添加 `@Debug(export=true)` 以追踪兼容性。

### C3. AccessTransformer 风险
- `accesstransformer.cfg`（8 条规则）全部针对客户端内部类（`AbstractContainerScreen` 字段、`BakedQuad.direction`）。这些是 Forge/MC 内部 API，**在 MC 1.20.2+ 必然断裂**。
  - **当前 Forge 1.20.1 范围内安全**。

### C4. 可选依赖隔离
- `AppMekIntegration` 使用 `@Integration("appmek")` 注解控制系统，仅在 AppMek 加载时实例化。**设计良好**。
- `NEAppMekItems` 的 recipe 使用 `.save(prov)` 无条件注册（未加 `ModLoadedCondition`）。虽然物品仅在 AppMek 加载时注册，但 recipe JSON 生成后会被打包进 jar——未安装 AppMek 的客户端会看到无法合成的配方。
  - **建议**：recipe JSON 添加 `forge:mod_loaded` 条件。

### C5. 数据保存风险
- `ECOCraftingSystemBlockEntity.saveAdditional()`（行 163）保存 `overclocked`、`activeCooling`、`coolant`、`coolantMaxOverclock`、`selectedBuildLength`。**未保存 `buildInProgress`、`buildSession`、预览状态** —— 区块卸载后重建状态会丢失。
  - **影响**：服务端重启后 multiblock builder 进度丢失（已注释为 transient，有意设计但用户体验差）。
  - **建议**：`buildSession` 确实应 transient，但 `buildInProgress` 应持久化以在重启后通知玩家。

---

## 第七部分：构建与依赖问题

### B1. mavenLocal() 污染
- `settings.gradle` 和 `build.gradle` 均包含 `mavenLocal()`。**CI 构建时本地仓库可能包含不同版本的依赖**，导致构建不可复现。
  - **建议**：移除 `mavenLocal()` 或仅在开发环境使用（通过 `if (System.getenv("CI") == null)` 条件）。

### B2. 过多 Maven 仓库
- `build.gradle` 声明了 **17 个 Maven 仓库**，其中多个可能未使用：
  - `maven.ithundxr.dev`、`maven.firstdarkdev.xyz`、`maven.blamejared.com`、`maven.createmod.net`、`maven.latvian.dev`、`maven.terraformersmc.com`、`jitpack.io`、`maven.shedaniel.me`
  - **建议**：审计后移除未使用的仓库。每个额外的仓库都会增加构建时间（Gradle 按顺序解析依赖）。

### B3. 依赖范围问题
- `geckolib` 使用 `implementation` 但被 `NENativeAe2StyleRenderer` 和 `ECOComputationCoolingControllerRenderer`（被排除）引用——如果不再使用 GeckoLib，应降为 `compileOnly` 或移除。
  - **需要进一步验证**：当前 `main` 分支是否有实际运行中的 GeckoLib 渲染代码。

### B4. 构建排除列表 `forge120JavaExcludes` 分析
| 排除项 | 必要性 | 说明 |
|---|---|---|
| `integration/**` | **高** | 含 1.21 NeoForge 代码，暂时排除合理，但应迁移或删除 |
| `mixins/**` | **高** | 含 1.20.5+ API 和不存在依赖的引用，排除合理 |
| `client/model/**` | **中** | 模型加载代码可能未完成 |
| `client/renderer/...CoolingControllerRenderer.java` | **低** | 依赖 GeckoLib，当前未使用 |
| `client/all/NEBuiltinModels.java` | **低** | 同上 |
| `data/**` | **中** | 1.21 datagen 代码 |
| `recipe/*Builder.java` | **中** | 1.21 recipe builder |
| `all/NEDataComponents.java` | **高** | 1.20.5+ API 绝不兼容 |
| `api/components/**` | **低** | 同上 |

### B5. 无配置缓存
- `gradle.properties` 显式设置 `configuration-cache=false`。Gradle 8.x 的配置缓存可显著加速增量构建，关闭原因未知。
  - **建议**：尝试启用 `configuration-cache=true` 并解决可能的兼容问题。

---

## 第八部分：优先级修改路线图

### P0（必须立即修复的崩溃/构建/严重错误）
| # | 问题 | 文件 |
|---|---|---|
| P0-1 | README 版本声明不实 | `README.md`, `README_ZH_CN.md` |
| P0-2 | 确认 `CraftingServiceMixin.java` vs `CraftingServiceMixin120.java` 是否重复 | `forge/mixin/` |
| P0-3 | 移除或标记 `settings.gradle` 中的 `mavenLocal()` 为 dev-only | `settings.gradle` |

### P1（短期应修）
| # | 问题 |
|---|---|
| P1-1 | 删除 `integration/**` 死代码或迁移到独立分支 |
| P1-2 | IWS BE 职责拆分（≥3 个协从类） |
| P1-3 | 冷却液配方查询缓存化 |
| P1-4 | `NEAppMekItems` recipe JSON 添加 `mod_loaded` 条件 |
| P1-5 | 移除 `CraftingSystemBlockEntity` 中的 tick 级 recipe 查询 |

### P2（长期优化）
| # | 问题 |
|---|---|
| P2-1 | 抽取共享颜色常量到 `NEDarkThemeColors` |
| P2-2 | UI 状态同步改为变化驱动（delta sync） |
| P2-3 | 添加 GameTest 框架（至少 multiblock 形成/破坏测试） |
| P2-4 | 启用 Gradle Configuration Cache |
| P2-5 | CI/CD pipeline（GitHub Actions） |
| P2-6 | 依赖明确化（移除未使用 Maven 仓库） |

---

## 第九部分：需要人工确认的问题

| # | 问题 | 需要确认的事项 |
|---|---|---|
| Q1 | 项目目标版本 | 到底是要发布 Forge 1.20.1 还是 NeoForge 1.21.1+？当前两套代码并存。 |
| Q2 | `integration/` 包的未来 | 是否计划迁移到 1.21.1？如是，当前 1.20.1 分支应清理以免混淆。 |
| Q3 | GeckoLib 依赖 | 当前是否有运行中的 GeckoLib 渲染代码？如没有，`implementation` 应降级。 |
| Q4 | `mavenLocal()` | 是否仅在本地开发使用？发布构建前必须移除。 |
| Q5 | 原 `Eco AE Extension` 授权 | README 声称已获授权，但 LICENSE 文件仅含 GPLv3 模板文本，未注明原始作者/项目。确认授权范围是否覆盖代码、贴图、模型、文档。 |
| Q6 | `CraftingServiceMixin120` 的稳定性 | AE2 15.x 内部 API 变化频繁——是否有监控方案？ |

---

## 第十部分：最终评分

| 维度 | 评分 (100) | 扣分原因 |
|---|---|---|
| **性能** | 62 | IWS 配方缓存弱、CoolingRecipe 每 tick 查询、UI 同步无节流、tick 路径有过多对象分配 |
| **架构** | 55 | 类职责划分不清（IWS BE 700 行）、`integration/` vs `compat/` 双重代码、硬编码散落、API 层薄弱 |
| **可维护性** | 45 | 死代码 30+ 类未清理、跨版本代码混杂、排除列表过大、无文档注释的标准、无测试 |
| **兼容性** | 70 | 客户端/服务端隔离良好、AppMek 隔离设计好。但 AE2 Mixin 目标脆弱、AT 未来断裂风险、recipe 缺少 mod_loaded 条件 |
| **构建质量** | 50 | 17 个 Maven 仓库、mavenLocal() 污染、exclude 规则过多、配置缓存关闭、无 CI |
| **资源规范性** | 78 | 语言文件较完整、模型/贴图无缺失。但 en_us/zh_cn 有微小不一致（format specifier `%d` vs `%s`） |
| **发布准备度** | 40 | README 版本不实、无 CI/CD、无测试、classic_pack 资源包未完成（TODO 注释）、buildInProgress 不持久化 |

### 总评：55/100 — 功能可用但技术债较重

项目核心功能（多方块存储/合成/计算）基本可工作，JEI/EMI/AppMek 集成已打通。但跨版本代码残留、性能优化不足、架构层面技术债严重影响长期可维护性。建议优先处理 P0 版本声明问题和 P1 死代码清理，在发布 CurseForge/Modrinth 前完成。

---

*报告结束。本报告基于静态源码分析生成，未进行运行时 profiling 或多人测试。标记为"需要进一步验证"的项目建议在实际游戏环境中确认。*
