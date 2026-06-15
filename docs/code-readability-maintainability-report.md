# NeoECOAEExtension Code Readability And Maintainability Report

Date: 2026-06-01

Scope:
- `src/main/java/cn/dancingsnow/neoecoae`
- Current focus areas: EMI multiblock preview, Native UI screens, network packets, block entities, multiblock clusters, storage/crafting/computation service paths.
- This report is analysis only. No recommendations below were implemented as part of task 2.

Validation observed before this report:
- `./gradlew compileJava` passed.
- `./gradlew build` passed.
- `./gradlew runClient --no-daemon` completed without a new crash report or fatal mod-loading error.
- `runClient` still logs repeated EMI/JEMI `AbstractMethodError` from the JEI bridge. That appears to be a dependency compatibility issue, not a direct failure in the multiblock preview changes.

## Executive Summary

The codebase is functional but has rising maintenance cost in three areas:

1. Several classes are too large and mix UI rendering, state management, business rules, networking, and persistence concerns.
2. The UI layer uses many hard-coded strings, colors, coordinates, and ad hoc layout calculations, which makes repeated visual fixes fragile.
3. Server block entity code has improved dirty/revision patterns, but `setChanged`, `markForUpdate`, UI sync, persistence, and visual update semantics are still scattered and easy to misuse.

The highest leverage next step is not a broad rewrite. It is to introduce small boundaries around existing hotspots: split large UI widgets into state/layout/render/control units, clarify the current sync boundaries by feature, and standardize dirty/update method names for block entities.

## Large Class Hotspots

| File | Approx. lines | Concern |
| --- | ---: | --- |
| `src/main/java/cn/dancingsnow/neoecoae/all/NEBlocks.java` | 1378 | Registration and block builder declarations are difficult to scan or review. |
| `src/main/java/cn/dancingsnow/neoecoae/all/NEItems.java` | 1173 | Same registration-scale issue as blocks. |
| `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity.java` | 896 | Combines multiblock build terminal, crafting UI state, stats cache, coolant/config state, and server behavior. |
| `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOIntegratedWorkingStationBlockEntity.java` | 853 | Combines inventories, recipe cache, IO strategy, ticking, NBT, and UI sync. |
| `src/main/java/cn/dancingsnow/neoecoae/gui/ldlib/support/NELDLibStateCodecs.java` | 481 | Current source does not contain the old `NENetwork` boundary; this file is one of the current LDLib state sync boundaries. |
| `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECOStorageSystemBlockEntity.java` | 731 | Storage stats, multiblock construction, controller UI state, and persistence concerns share one class. |
| `src/main/java/cn/dancingsnow/neoecoae/gui/nativeui/screen/NEStorageControllerScreen.java` | 579 | Complex layout and animation logic in one screen class. |
| `src/main/java/cn/dancingsnow/neoecoae/compat/emi/MultiblockEmiRecipe.java` | 490 | Recipe model, mutable preview state, layout, drawing, input handling, material paging, and tooltips are mixed. |

## Findings

### P0: EMI Preview Widget Is Still Too Monolithic

Files:
- `src/main/java/cn/dancingsnow/neoecoae/compat/emi/MultiblockEmiRecipe.java`
- `src/main/java/cn/dancingsnow/neoecoae/client/multiblock/preview/NEMultiblockSceneRenderer.java`

Evidence:
- `MultiblockEmiRecipe` owns recipe identity, inputs/outputs, mutable preview state, rebuild logic, layout constants, rendering, material paging, button handling, drag state, and tooltips.
- `PreviewWidget` starts at `MultiblockEmiRecipe.java:198` and contains most of the interaction/rendering behavior.
- Layout is driven by many local constants and methods such as `sceneH`, `materialTitleY`, and `slotsY`.

Risk:
- Small layout changes can break hit testing, tooltips, and rendering order.
- It is hard to verify behavior without manual screenshots because layout and state are tightly coupled.

Recommendation:
- Extract `MultiblockPreviewState` for `expand/layer/formed/materialPage/scene`.
- Extract `MultiblockPreviewLayout` for all calculated rectangles.
- Extract `MaterialStripWidget` or a small helper for slot rendering and hit testing.
- Keep `MultiblockEmiRecipe` mostly as an EMI adapter.

### P0: Manual 3D Camera Math Needs A Narrower Boundary

Files:
- `src/main/java/cn/dancingsnow/neoecoae/client/multiblock/preview/NEMultiblockSceneRenderer.java`

Evidence:
- Camera fitting now uses manual 8-corner projection in `calculateScale`.
- Scissor calculation depends on translating GUI local coordinates to absolute screen coordinates.
- Rendering, fitting, scissor handling, and block rendering are all in one class.

Risk:
- Future pitch/yaw/scaling changes can reintroduce clipping or blank previews.
- Bugs only appear under particular GUI scales, structure lengths, and layer states.

Recommendation:
- Extract a pure `CameraFit` helper that accepts bounds/yaw/pitch/viewport and returns scale.
- Add lightweight unit-style tests for projected bounds if the project test setup allows it.
- Keep the renderer focused on Minecraft rendering calls.

### P0: Hard-Coded UI Text And Layout Values Are Widespread

Files:
- `src/main/java/cn/dancingsnow/neoecoae/compat/emi/MultiblockEmiRecipe.java`
- `src/main/java/cn/dancingsnow/neoecoae/gui/nativeui/screen/NEStructureTerminalScreen.java`
- `src/main/java/cn/dancingsnow/neoecoae/gui/nativeui/screen/NEStorageControllerScreen.java`
- `src/main/java/cn/dancingsnow/neoecoae/gui/nativeui/screen/NECraftingControllerScreen.java`

Evidence:
- EMI preview uses `Component.literal("方块数量需求")`, `Component.literal("切换结构长度")`, and similar literals.
- Structure Terminal and Storage Controller also contain many direct Chinese `Component.literal(...)` calls.

Risk:
- Text cannot be localized consistently.
- String width changes are hard to audit because layout and text live together.

Recommendation:
- Move user-facing UI text to lang keys.
- Keep literals only for dynamic symbols such as `+`, `-`, `/`, or debug-only text.
- Put repeated dimensions/colors in per-screen layout constants or a shared native UI layout helper.

### P1: Sync Boundaries Need Clearer Ownership

File:
- `src/main/java/cn/dancingsnow/neoecoae/gui/ldlib/support/NELDLibStateCodecs.java`
- `src/main/java/cn/dancingsnow/neoecoae/gui/ldlib/widget/NELDLibSyncedStateWidget.java`
- AE2 `CraftingStatusPacket` related mixins
- BE `writeUiSyncTag` / `readUiSyncTag`
- AE2 native packets and recipe serializers

Evidence:
- Current source does not contain `src/main/java/.../network/NENetwork.java`.
- Sync is now spread across LDLib state codecs, synced widgets, AE2 native packets, Crafting Status mixins, BE update tags, and recipe serializers.

Risk:
- Sync changes are hard to review when feature ownership is not explicit.
- Dedicated server safety and client-only access are harder to reason about.

Recommendation:
- Document and keep feature-local sync boundaries: `storage`, `crafting`, `structure`, `iws`, AE2 status integration.
- Keep field order and packet compatibility stable when touching LDLib codecs or AE2-native packet paths.

### P1: BlockEntity Classes Mix Multiple Ownership Boundaries

Files:
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity.java`
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/computation/ECOComputationSystemBlockEntity.java`
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECOStorageSystemBlockEntity.java`
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOIntegratedWorkingStationBlockEntity.java`

Evidence:
- Crafting system BE includes structure terminal build state, crafting controller UI state, dirty stats cache, coolant/config toggles, and live server behavior.
- IWS BE includes inventories, recipe cache, ticking, IO strategy, and UI persistence.

Risk:
- Performance fixes tend to touch unrelated UI/build/persistence paths.
- It is easy to trigger `markForUpdate` or `setChanged` from a path that only needed a UI revision or cache invalidation.

Recommendation:
- Extract small internal collaborators first, not a rewrite:
  - `CraftingSystemStatsCache`
  - `ComputationSystemStatsCache`
  - `StructureBuildState`
  - `IwsRecipeCache`
  - `IwsIoConfig`

### P1: Dirty/Revision/Visual Update Semantics Need Standard Names

Files:
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECODriveBlockEntity.java`
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECOStorageSystemBlockEntity.java`
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity.java`
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/computation/ECOComputationSystemBlockEntity.java`
- `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOIntegratedWorkingStationBlockEntity.java`

Evidence:
- The project now has `storageStatsDirty`, `structureStatsDirty`, `computationStatsDirty`, `uiRevision`, content save flags, and many direct `markForUpdate()` / `setChanged()` calls.
- Some places still pair `setChanged()` and `markForUpdate()` together for state changes that may not affect client rendering.

Risk:
- New code may regress performance by treating persistence, UI sync, provider refresh, and visual update as the same event.

Recommendation:
- Document and standardize method names:
  - `markPersistenceDirty`
  - `markUiStateDirty`
  - `markStatsDirty`
  - `markVisualDirty`
  - `markProviderDirty`
- Avoid direct `markForUpdate()` outside methods that explicitly explain the client-visible model/render reason.

### P1: Null Sentinels Reduce Readability In Public-Looking APIs

Files:
- `src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOStorageCells.java`
- `src/main/java/cn/dancingsnow/neoecoae/api/ECOComputationModels.java`
- `src/main/java/cn/dancingsnow/neoecoae/compat/ae2/StorageCellDisassemblyRecipe.java`
- `src/main/java/cn/dancingsnow/neoecoae/gui/nativeui/menu/*`

Evidence:
- Several methods return `null` for "not handled", "not available", or "not found".

Risk:
- Callers must remember which `null` meaning applies.
- Defensive checks spread across code instead of being encoded in types or names.

Recommendation:
- For hot paths, avoid `Optional` if allocation matters, but make names explicit: `findHandlerOrNull`, `getCellInventoryOrNull`.
- For non-hot UI/config paths, prefer `Optional` or empty collections/empty states.

### P1: Generated/Main Resource Ownership Is Not Obvious

Files:
- `src/main/resources`
- `src/generated/resources`
- Data generation code under `src/main/java/cn/dancingsnow/neoecoae/data`

Evidence:
- Recent recipe and loot fixes required checking both generated JSON and datagen source.
- Build includes generated resources directly in `sourceSets.main.resources`.

Risk:
- Manual edits to generated JSON can be overwritten or drift from datagen.

Recommendation:
- Add a short contributor note that generated resources must be changed through datagen sources.
- Consider a CI check that fails when `runData` changes generated output unexpectedly.

### P2: Registration Classes Are Hard To Review

Files:
- `src/main/java/cn/dancingsnow/neoecoae/all/NEBlocks.java`
- `src/main/java/cn/dancingsnow/neoecoae/all/NEItems.java`
- `src/main/java/cn/dancingsnow/neoecoae/all/NEBlockEntities.java`

Evidence:
- `NEBlocks` and `NEItems` exceed 1000 lines.

Risk:
- Merge conflicts and accidental registration side effects become more likely.

Recommendation:
- Split by domain without changing IDs:
  - storage
  - crafting
  - computation
  - materials
  - compat/appmek
- Keep a central facade if external references rely on `NEBlocks.X`.

### P2: Duplicate Compatibility Package Boundaries Are Confusing

Files:
- `src/main/java/cn/dancingsnow/neoecoae/compat/emi`
- `src/main/java/cn/dancingsnow/neoecoae/integration/emi`
- `src/main/java/cn/dancingsnow/neoecoae/compat/jei`
- `src/main/java/cn/dancingsnow/neoecoae/integration/jei`

Evidence:
- Both `compat` and `integration` package trees contain EMI/JEI related classes.

Risk:
- Future contributors may add recipes/categories in the wrong integration path.

Recommendation:
- Define one package convention and migrate gradually.
- If both are intentionally separate, document what belongs in each.

## Current Branch Notes

- LDLib compile dependency has been removed, so any source under `compat/ldlib` must stay removed or excluded. A stale `MultiblockPreviewWidget.java` caused `compileJava` to fail until it was removed.
- The EMI preview now has a native implementation, but it would benefit from a smaller adapter/widget split before more UI behavior is added.
- `runClient` surfaced EMI/JEMI `AbstractMethodError` spam related to JEI bridge APIs. It did not create a crash report in this run, but it should be tracked separately because it can hide real EMI errors in logs.

## Suggested Follow-Up Tasks

### P0

1. Split `MultiblockEmiRecipe` into state/layout/material-strip helpers.
   - Expected benefit: safer visual iteration and smaller diffs.
   - Risk: low if behavior is kept behind the same EMI recipe API.
   - Validation: `compileJava`, `build`, manual EMI screenshots at GUI scale 2/3/4.

2. Move EMI preview text to lang keys.
   - Expected benefit: fixes localization drift.
   - Risk: low.
   - Validation: `runData` if lang is generated, then `build`.

3. Add a short resource/datagen ownership note.
   - Expected benefit: avoids generated JSON drift.
   - Risk: low.
   - Validation: documentation-only.

### P1

1. Clarify current sync boundaries by feature; current source does not contain the old `NENetwork` file, and `NELDLibStateCodecs` should be treated as one sync boundary rather than the sole sync boundary.
   - Expected benefit: easier sync review and side-safety auditing.
   - Risk: medium because LDLib codec field order, AE2 packet behavior, BE update tags, and recipe serializers must remain stable.
   - Validation: `build`, client/server connect, each LDLib UI action, AE2 Crafting Status refresh.

2. Extract stats cache classes from F/C/Storage system block entities.
   - Expected benefit: clearer dirty semantics and lower regression risk.
   - Risk: medium.
   - Validation: multiblock form/break, UI state refresh, crafting submit/cancel, storage insert/remove.

3. Standardize update semantics naming.
   - Expected benefit: prevents future `markForUpdate` performance regressions.
   - Risk: medium because behavior is cross-cutting.
   - Validation: Spark before/after plus visual state checks.

### P2

1. Split large registration classes by domain.
   - Expected benefit: easier navigation and conflict reduction.
   - Risk: medium-high because registration order and static init can regress.
   - Validation: data generation, registry load, existing world migration.

2. Rationalize `compat` vs `integration` package structure.
   - Expected benefit: clearer ownership.
   - Risk: medium because plugin discovery annotations and class loading must remain side-safe.
   - Validation: dedicated server start, EMI/JEI present and absent combinations.

