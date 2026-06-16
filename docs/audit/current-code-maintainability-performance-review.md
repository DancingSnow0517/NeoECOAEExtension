# NeoECOAEExtension 当前代码可维护性与性能审计

## 1. 审计范围与方法

审计基于当前 `perf/server-compute-cache` 分支完成，范围包括：

- `src/main/java/cn/dancingsnow/neoecoae`
- `src/main/resources`
- `src/generated/resources`
- `build.gradle`
- `src/main/resources/META-INF/mods.toml`
- `src/main/resources/*.mixins.json`
- `src/main/resources/**/*mixins*.json`

方法：

- 使用 `rg` 扫描 `saveChanges`、`markForUpdate`、`requestUpdate`、`@Mixin`、`ModList`、`TODO`、network packet limits、UI literal/layout 等风险点。
- 检查大类与职责边界，重点看 storage/crafting/computation/IWS/native UI/network/mixin/compat。
- 运行 `./gradlew build`，确认当前构建通过。
- 检查 `build/libs/neoecoae-1.3.5.jar`，确认包含 `neoecoae.mixins.json` 与 active forge mixin classes；未发现单独 refmap 文件。

本阶段只新增本报告，未修改源码、资源、配置、mixin json、build.gradle 或 generated resources。

## 2. 总体结论

当前分支已经吸收了几类关键修复：IWS recipe null cache、F/C 统计 dirty 化、UI state revision、ECO CPU 状态表格适配、创造栏显式排序、ECO worker input snapshot/recovery、Storage save/provider refresh debounce。这些方向是正确的，能降低 tick 与 insert 热点，并补上几个生产崩溃/数据丢失窗口。

剩余主要风险集中在四类：

- 生产环境稳定性仍缺普通客户端 jar smoke test，尤其是 mixin refmap/obfuscation 行为。
- ECO crafting 与 Storage 的新保护逻辑缺自动化/可重复测试，当前依赖手动场景验证。
- Native UI 与网络状态同步代码增长较快，硬编码布局、颜色、文字和重复绘制 helper 已经影响维护。
- compat 边界的历史包路径残留已清理：`integration/**` 旧源码和失效 `kubejs.plugins.txt` 已移除，当前活跃路径为 `compat/**`。

## 3. P0 问题清单

1. **生产 jar mixin smoke test 仍是硬缺口**

   当前 jar 包含 `neoecoae.mixins.json` 和 `cn/dancingsnow/neoecoae/forge/mixin/*120.class`，但未发现独立 refmap 文件。`build.gradle` 只在 run configs 中设置 `mixin.env.remapRefMap`，生产 jar 行为需要普通客户端验证。风险点包括 `AbstractContainerMenuMixin120`、AE2 menu targets、MixinExtras wrap operations。

   影响：生产客户端可能出现 `No refMap loaded`、target remap 失败或 critical injection failure。

   建议：建立最小 production-client smoke 流程，固定用 `build/libs/neoecoae-1.3.5.jar` 放入普通 Forge 客户端，并记录启动日志断言。

2. **ECO crafting in-flight 数据恢复缺自动验证**

   `ECOCraftingThread` 已新增 input snapshot/job id/recovery，但替换样板、取消、网络满、worker reload、多线程并发仍没有测试 harness。该路径涉及 AE2 `CraftingCpuHelper.extractPatternInputs`、ECO worker state、CPU cancel 和 network insert，任何一个边界错都会导致材料复制、材料丢失或等待项卡死。

   影响：这是玩家资产级数据安全问题。

   建议：优先写一个可运行的 server-side debug scenario 或 GameTest 风格 harness，覆盖替换样板取消、网络满、重载恢复。

3. **Storage debounce 需要 Spark 与数据持久化复测**

   `ECOStorageCell.saveChanges` 已按 host 类型分支，ECO drive 使用 batched provider；`ECODriveBlockEntity` 缓存 cell inventory 并 once-per-tick flush。风险在于 AE2 terminal cache、cell NBT flush、取出 cell、chunk unload、server stop 这些路径必须全部覆盖。

   影响：优化目标是高频写入，但错误会表现为终端不同步或 cell 内容丢失。

   建议：用同一 Spark 场景确认 `updateStorageProviderState/ProviderState.mount` 不再进入每次 insert 热点，同时手动验证取出 cell 与重进世界内容不丢。

4. **optional compat 无 AppMek/Mekanism 环境需要 dedicated server smoke**

   `compat/appmek` 已做 integration gating，旧的 `integration/**` 源码树和失效 KubeJS 插件入口已清理。后续风险主要来自 AppMek/Mekanism 类引用较多，维护时仍需避免把 optional compat 类引入主加载路径。

   影响：无 AppMek/Mekanism 的 dedicated server 可能因 classloading 边界回归而崩溃。

   建议：增加 `runServer` 或普通 server jar 的 no-appmek smoke，检查 `NoClassDefFoundError`。

5. **mixin 配置默认 require=1 仍偏激进**

   `src/main/resources/neoecoae.mixins.json` 的 `injectors.defaultRequire` 为 1。新增生产安全 mixin 单点用了 `require = 0`，但其他 mixin 仍可能因 AE2 小版本方法签名变化导致启动失败。

   影响：依赖 AE2 patch 版本或生产 remap 变化时，客户端直接无法启动。

   建议：对非核心、可降级 mixin 明确 `require = 0`，并在代码路径中做到无注入时功能降级。

## 4. P1 问题清单

1. **Native UI screen 职责过重**

   `NEStorageControllerScreen`、`NECraftingControllerScreen`、`NEStructureTerminalScreen` 同时持有布局常量、绘制 primitive、文本格式化、hover hit test、动画、业务 state fallback。任何视觉修复都容易影响数据逻辑。

   建议：抽出 `GaugeRenderer`、`MetricFormatter`、`PanelLayout`、`TooltipRegion` 等小工具，先从 Storage/Crafting 共用绘制 helper 开始。

2. **硬编码中文 literal 与 lang key 混用**

   多个 screen 仍有 `Component.literal("物品")`、`Component.literal("所需方块")`、`"已用线程: "` 这类 UI 文本。当前中文可用，但多语言和后续文案替换成本高。

   建议：逐步迁移到 lang key，保留动态数值 formatter。

3. **IWS 类过大且职责混杂**

   `ECOIntegratedWorkingStationBlockEntity` 同时处理 AE grid power、item/fluid inventory、recipe cache、auto export、GUI data、capability、guide integration。虽然已有 `recipeCacheValid`，但后续改动容易误触 markForUpdate/saveChanges。

   建议：拆分 recipe/runtime state、I/O tanks/export、UI sync DTO 构造。

4. **Network packet 类集中在一个大文件**

   `NENetwork` 中 packet 注册、多个 S2C/C2S record、encode/decode/handle 全在一个类中。当前已有 `MAX_STORAGE_UI_TYPES` 和 `MAX_STRUCTURE_TERMINAL_MATERIALS`，但 packet 增多后审查边界困难。

   建议：按 feature 拆分 packet record 文件，`NENetwork` 只保留注册表。

5. **Storage controller UI stats 与 AE terminal content update 语义需更清楚**

   现在有 `contentDirty/storageStatsDirty/providerDirty/visualDirty` 的方向，但命名仍分散在 drive/controller/cell。后续维护者可能重新把 content change 绑回 provider remount。

   建议：用注释或小型接口明确四类 dirty 的触发边界，并补测试。

## 5. P2 问题清单

1. **ECOCraftingCPULogic 与 AE2 internals 耦合深**

   当前逻辑直接使用 AE2 execution classes 和字段，后续 AE2 升级会比较脆弱。短期不要重写核心执行逻辑；长期应考虑 adapter 层封装 AE2 internal touch points。

2. **多方块 calculator/cluster 仍有可观复杂度**

   F/C/Storage cluster 已有缓存，但 calculator、cluster、controller 之间的 dirty/revision 传播还缺统一模型。应在 Spark 证明瓶颈后再抽象。

3. **GUI render 分配与字符串格式化可继续优化**

   `NumberFormat`、`String.format`、`Component.literal` 在 render path 仍较多。这个不是当前最大服务端热点，建议只在客户端 profile 证明后再做缓存。

4. **旧 package 残留已清理**

   `src/main/java/cn/dancingsnow/neoecoae/integration/**` 已删除，active compat 实现保留在 `compat/**`。后续新增 optional compat 时应继续放在 `compat/**`，并同步检查资源入口，避免重新出现未编译旧包。

## 6. 可精简/可抽象代码区域

- **Native UI 绘制 helper**：Storage/Crafting/Computation/Structure Terminal 都有 `drawDarkInsetRect`、`drawLine`、`drawPairLine`、颜色常量、居中绘制逻辑。可抽出 `NENativeUiDraw`。
- **容量与百分比格式化**：`formatStorageBytes`、`formatMetricNumber`、`formatPercent` 可以统一到 formatter，避免 UI 类各自演化。
- **Tooltip region 管理**：Storage 柱状图、Structure material slots、Crafting buttons 都有 hover rect 逻辑，可抽象成轻量 `TooltipRegion` 列表。
- **Cell handler 判定**：item/fluid/chemical 的 strict `cellType + keyType` 判定应集中成 helper，减少未来新增类型时复制错。
- **Creative tab order**：当前已集中到 `NECreativeTabOrder`，后续可以把 group 注释和 compat 插入点规范化，但不要回到 registry 遍历。
- **Multiblock material calculation**：Structure Terminal 的材料显示和 build plan 统计可以通过单一 DTO 输出，避免 screen 端处理过多状态。

## 7. 性能热点与后续 Spark 验证计划

已处理方向：

- IWS recipe null cache：`recipeCacheValid` 避免无配方输入每 tick 查 recipe manager。
- F/C 系列 dirty stats 与 UI revision：避免 UI state 创建触发结构遍历。
- Storage insert debounce：避免每次 `ECOStorageCell.insert` 都 remount provider。

后续 Spark 计划：

1. **Storage 写入复测**
   - 场景：大量 ECO crafting 输出写入 item/fluid/chemical cell。
   - 指标：`ECOStorageCell.saveChanges`、`ECODriveBlockEntity.notifyPersistence`、`StorageService.refreshMountedStorageProvider`、`ProviderState.mount`。
   - 预期：provider remount 不再出现在每次 insert 热路径。

2. **ECO crafting cancel/replace 场景**
   - 场景：替换样板、多线程、取消、网络满。
   - 指标：worker tick、NetworkStorage insert、CPU waitingFor。
   - 预期：无永久 waitingFor，无材料消失。

3. **IWS 无配方输入**
   - 场景：无匹配输入静置 60 秒。
   - 指标：RecipeManager `getRecipeFor`。
   - 预期：输入不变时不重复查询。

4. **Native UI idle**
   - 场景：打开 F/C/Storage UI 静置。
   - 指标：state create、packet send、screen render allocation。
   - 预期：revision skip 生效，Storage 保持必要实时刷新。

## 8. 生产环境稳定性风险

- **refmap 风险**：jar 内目前能看到 `neoecoae.mixins.json` 与 mixin classes，但未发现 `*.refmap.json`。需要确认 Forge/Mixin 在普通客户端生产环境是否能稳定 remap。
- **mixin target 风险**：AE2 menu/status mixin 依赖 AE2 15.4.10 结构。升级 AE2 时需要重新跑 production jar smoke。
- **client-only classloading**：`NENetwork` 使用 `DistExecutor` 隔离 client handler 是正确方向，但 compat 和 UI 类仍需 dedicated server smoke。
- **optional compat 边界**：AppMek/Mekanism 类必须只在 integration loaded 后触达。`AppMekCompat` 注释已说明风险，但维护时仍需警惕主类强引用。
- **generated/main resources 冲突**：当前查询未发现 `src/generated/resources/assets/neoecoae/lang/zh_cn.json`，但 build 将 main/generated/guidebook resources 合并且 duplicatesStrategy 为 EXCLUDE。生成资源恢复后需要检查覆盖优先级。

## 9. 后续任务建议

P0：

- 建立普通客户端 jar smoke checklist，验证 mixin/refmap、AE2 Crafting Status、创造栏、Storage insert。
- 为 ECO crafting thread recovery 写替换样板取消测试或 debug harness。
- 用 Spark 复测 Storage insert 场景，确认 provider remount 消失。
- 跑 no-appmek dedicated server smoke，确认 chemical compat 不被错误加载。
- 检查并决定是否显式生成/打包 refmap。

P1：

- 抽离 Native UI 绘制/formatter/tooltip helper。
- 拆分 `NENetwork` packet records。
- 拆分 IWS recipe/runtime/export/UI sync 职责。
- 对 `markForUpdate` 建立使用规范：外观变化才调用。
- 保持 inactive `integration/**` 不再回流；新增 compat 只放在 `compat/**` 并同步资源入口。

P2：

- 在 AE2 升级前封装 `ECOCraftingCPULogic` 对 AE2 execution internals 的访问。
- 基于 Spark 再决定是否继续增量化 Storage UI stats。
- 客户端 render allocation profile 之后再做字符串/Component 缓存。

## 10. 不建议做的事情

- 不建议重写 `ECOCraftingCPULogic.executeCrafting` 核心调度语义。
- 不建议迁移 1.12.2 virtual CPU 注入方案。
- 不建议把 Spark、GuideME、LDLib 改成硬依赖。
- 不建议用 provider remount 作为普通 storage content change 通知。
- 不建议在未验证 AppMek/Mekanism classloading 的情况下从主类直接引用 compat-only 类型。
- 不建议先做大规模 UI 重构；应先补 smoke/test/profile，再做小步抽象。
> Current branch note: source search did not find `src/main/java/.../network/NENetwork.java`.
> Treat current sync boundaries as `NELDLibStateCodecs`, `NELDLibSyncedStateWidget`,
> AE2 `CraftingStatusPacket` related mixins, BE `writeUiSyncTag/readUiSyncTag`,
> AE2 native packets, LDLib state codecs, and recipe serializers.
> `NELDLibStateCodecs` is one current LDLib state codec boundary, not a replacement
> for reviewing feature widgets, BE update tags, AE2 status sync, or serializers.
> Older `NENetwork` mentions in this document are stale references for the current branch.
