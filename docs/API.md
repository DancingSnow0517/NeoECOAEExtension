# NeoECOAE API integration guide

[简体中文](API_ZH_CN.md) · [Project](../README.md) · [API sources](../src/main/java/cn/dancingsnow/neoecoae/api)

For addon authors targeting this **Minecraft 1.21.1 / NeoForge / Java 21** branch. The mod version is `mod_version` in [gradle.properties](../gradle.properties); dependencies and publishing are configured in [build.gradle](../build.gradle). Use documentation from the same revision as the target JAR.

## Dependencies and compatibility

The API ships in the full mod JAR, without a separate `-api` artifact. Publishing targets a local `repo/` directory; the build declares no public Maven endpoint. In an addon already configured for NeoForge, AE2 and Java 21:

```groovy
dependencies {
    compileOnly files("libs/neoecoae-21.2.11.jar")
    localRuntime files("libs/neoecoae-21.2.11.jar")
}
```

Replace the version with the tested artifact. `localRuntime` is for ModDevGradle development runs; omit it if the mod is supplied another way. Do not bundle another copy of NeoECOAE into the addon. Declare the dependency in your addon's `neoforge.mods.toml`:

```toml
[[dependencies.examplemod]]
modId = "neoecoae"
type = "required"
versionRange = "[21.2.11]"
ordering = "AFTER"
side = "BOTH"
```

Replace `examplemod` and the version range. Optional integrations use `type = "optional"` and isolate ECO references in classes loaded only after checking that the mod is present. Optional metadata alone does not prevent Java linkage errors. See [LICENSE](../LICENSE).

[ECOAddonApi](../src/main/java/cn/dancingsnow/neoecoae/api/extension/ECOAddonApi.java) currently has `VERSION == 1`. Its `supports(feature)` checks only the addon extension families below, not the entire crafting API. Within one API major version on the same Minecraft/loader branch, those contracts retain existing signatures and add overloads/default methods. JARs from different Minecraft versions are not interchangeable. Other public implementation classes do not automatically carry that compatibility policy.

[ECOPlanningService](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOPlanningService.java) is the public planning boundary used here, but lives in an implementation package; pin the mod version. Solvers, Workers, CPU mutable internals and persistence engines are not addon extension points. Deprecated `api.me.ECOCraftingCPU`, `ECOCraftingCPULogic` and `ExecutingCraftingJob` preserve existing binary/Mixin targets; do not construct or subclass them for new integrations.

## Entry points

| Need | Entry points |
| --- | --- |
| Planning and grid settings | [ECOPlanningService](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOPlanningService.java), [ECOPlannerRequester](../src/main/java/cn/dancingsnow/neoecoae/api/me/planning/ECOPlannerRequester.java), [ECOCraftingNetworkSettings](../src/main/java/cn/dancingsnow/neoecoae/api/me/network/ECOCraftingNetworkSettings.java) |
| Exact parent orders | [ECOBigOrderRequest](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderRequest.java), [ECOBigOrderAdmission](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderAdmission.java) |
| Events, attachments, dispatch policies | [ECOCraftingLifecycle](../src/main/java/cn/dancingsnow/neoecoae/api/me/lifecycle/ECOCraftingLifecycle.java), [ECOCraftingJobAttachmentRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/attachment/ECOCraftingJobAttachmentRegistry.java), [ECOCraftingDispatchPolicyRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/dispatch/ECOCraftingDispatchPolicyRegistry.java) |
| Parallel and FastPath providers | [ECOParallelCraftingProvider](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProvider.java), [ECOFastPathDispatchProvider](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOFastPathDispatchProvider.java), [ECOFastPathFacade](../src/main/java/cn/dancingsnow/neoecoae/api/me/ECOFastPathFacade.java) |
| Output, virtual completion, progress | [ECOCraftingOutputClaimSink](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputClaimSink.java), [ECOVirtualCraftingCompletionSink](../src/main/java/cn/dancingsnow/neoecoae/api/me/completion/ECOVirtualCraftingCompletionSink.java), [ECOCraftingProgressView](../src/main/java/cn/dancingsnow/neoecoae/api/me/progress/ECOCraftingProgressView.java) |
| Pattern semantics, optimization, insertion | [ECOPatternSemanticsRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/pattern/ECOPatternSemanticsRegistry.java), [ECOExecutionOptimizationRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/pattern/ECOExecutionOptimizationRegistry.java), [IECOPatternStorageService](../src/main/java/cn/dancingsnow/neoecoae/api/IECOPatternStorageService.java) |
| Cells, slots, exact insertion | [ECOStorageCells](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOStorageCells.java), [ICellHost](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ICellHost.java), [ECOBigIntegerStorage](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOBigIntegerStorage.java) |
| Tiers, structures, workstations | [Addon extension registration](#addon-extension-registration) |
| Host panel appearance | [IECOTier](../src/main/java/cn/dancingsnow/neoecoae/api/IECOTier.java), [IHostPanelTextureProvider](../src/main/java/cn/dancingsnow/neoecoae/api/IHostPanelTextureProvider.java); [panels and models](#panels-and-models) |

## Crafting fluid hatches

[ECOFluidInputHatchBlockEntity](../src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOFluidInputHatchBlockEntity.java) and [ECOFluidOutputHatchBlockEntity](../src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOFluidOutputHatchBlockEntity.java) expose a stable `public FluidTank getFluidTankHandler()`. New addons should use this method for panels and handler integration; its name, parameters and return type are retained within the same Minecraft/loader branch. The input hatch also exposes `public long getStoredFluidAmount()` for the exact amount in mB. Its ordinary int-sized handler view saturates at `Integer.MAX_VALUE`; never overwrite exact stock with that projection.

Builds fixing #143 retain both input-hatch binary read signatures: 21.2.10's `tank: FluidTank` and 21.2.11's `tank: LongFluidTank`. Both reference the same buffer. A compatibility superclass supplies the older descriptor and is not an addon extension point. The fields only support existing binaries: callers may read and operate on the buffer, but must not replace either field reference. Adding a getter alone cannot repair existing JARs, so both field signatures remain available. The output hatch also retains its original `tank: FluidTank` field.

Handler operations and quantity reads follow the server-thread, simulation/execution and accepted-quantity rules below. The output hatch's registered fluid capability remains drain-only; third-party capability queries should use NeoForge capabilities rather than bypassing that restriction through the getter.

## Threads, registration and ownership

Begin planning on the owning server thread: it captures grid inventory before handing work to the executor. Submission, provider acceptance, storage mutations and output claims also run on that thread. The caller checks player/machine access before calculation and again before submission; these low-level APIs do not authenticate callers.

Poll `Future.isDone()` from a later tick before calling `get()`. Clear the stored request reference before consuming it so a plan can be submitted only once. Cancel requests when their menu, machine or grid disappears. Handle cancellation, `ExecutionException`, interruption and executor `RejectedExecutionException`. Never block a server tick waiting for a result.

Register once during initialization, using stable addon IDs. Process-wide registrations survive world changes; retain listener/policy instances for removal. Description and preparation callbacks are read-only. Pattern semantic callbacks may run on planning threads and must not access mutable world state or move resources. Register client models before baking; keep client-only classes out of common initialization.

Quantities use the corresponding AEKey's units. Simulation changes no resources. Transfer ownership only for the returned accepted amount, retaining rejected resources; requested amounts, previews and progress displays are not receipts.

## Planning and submission

This helper demonstrates explicit ECO planning, execution-contract checks and exact parent-order submission. The caller supplies permission checks, request lifecycle management and CPU selection.

```java
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.*;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import net.minecraft.server.level.ServerLevel;
import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.Future;

public final class EcoApiExample {
    public static Future<ICraftingPlan> begin(ServerLevel level, IGrid grid,
            IActionSource source, AEKey goal, long amount) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Server thread required");
        }
        if (amount <= 0) throw new IllegalArgumentException("Positive amount required");
        var settings = ECOCraftingNetworkSettings.of(grid);
        if (settings == null || !settings.neoecoae$isFastPlannerEnabled()
                || !settings.neoecoae$hasComputationHost(source)) {
            throw new IllegalStateException("No eligible ECO computation host");
        }
        return ECOPlanningService.begin(level, grid, source, goal, amount,
            CalculationStrategy.REPORT_MISSING_ITEMS, ECOPlannerOptions.from(settings));
    }

    // Call once on the server thread after the Future completes and access is rechecked.
    public static ICraftingSubmitResult submit(IGrid grid, IActionSource source,
            ICraftingRequester requester, ICraftingCPU target, ICraftingPlan plan) {
        var result = plan instanceof ECOCraftingPlanDiagnostics bridge
            ? bridge.neoecoae$getPlanningResult() : null;
        if (result == null) result = ECOPlanningResultRegistry.find(plan);
        if (result == null || plan.simulation() || result.status() != PlanningStatus.SUCCESS) {
            throw new IllegalArgumentException("An executable ECO plan is required");
        }
        var contract = ECOPlanningResultRegistry.resolveContract(plan, result);
        if (contract == null || !contract.executable()) {
            throw new IllegalArgumentException("Missing execution contract");
        }
        return ECOPlanningResultRegistry.withSubmissionAlias(plan, result,
            () -> grid.getCraftingService().submitJob(plan, requester, target, false, source));
    }

    // ecoTarget must be an eligible ECO CPU or its idle ECO placeholder.
    public static ICraftingSubmitResult submitBigOrder(IGrid grid, IActionSource source,
            ICraftingRequester requester, ICraftingCPU ecoTarget, AEKey goal,
            BigInteger amount, ECOPlannerOptions options) {
        Objects.requireNonNull(ecoTarget, "Select an ECO CPU");
        return new ECOBigOrderRequest(goal, amount, false, options).submit(
            carrier -> grid.getCraftingService().submitJob(
                carrier, requester, ecoTarget, false, source));
    }
}
```

Consume a completed Future from a server tick, call `submit` once, then check `ICraftingSubmitResult.successful()`. Inventory, capacity and topology can change after the snapshot. For ordinary submission, a null `target` allows CPU selection and a null `requester` creates an independent job.

`ECOPlanningService.begin` bypasses AE2's calculation service entry. To keep the normal service chain, wrap an `ICraftingSimulationRequester` with `new ECOPlannerRequester(originalRequester, options)` and call `beginCraftingCalculation`. ECO does not cancel or replace a Future returned earlier by another planner. Enabling the grid switch alone does not redirect every unmarked third-party request.

| Option | Contract |
| --- | --- |
| `cyclePlanningEnabled` | Allow unavoidable cyclic components into the solver |
| `ignorePatternSubstitutions` | Plan only the encoded preferred input |
| `fuzzyPlanningItemIds` | Ignore components for selected items during planning; actual inputs must still pass pattern validation |
| `planningLogEnabled` | Legacy compatibility field; logging uses the global Calculating debug configuration |

`ECOPlannerOptions.from(settings)` captures current options. `from(null)` disables cycle planning and ignored substitutions and uses an empty fuzzy set. Mutating shared grid settings requires authorization. Check source-specific eligibility with `neoecoae$hasComputationHost(source)`.

`REPORT_MISSING_ITEMS` retains the original request and shortage report. `CRAFT_LESS` may return a smaller amount; read `plan.finalOutput().amount()`. A non-null plan can be a simulation report. Read status, `exactMissingItems()`, `theoreticalBytes()` and `trace().diagnostics()` through [ECOCraftingPlanDiagnostics](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOCraftingPlanDiagnostics.java) or [ECOPlanningResultRegistry.find](../src/main/java/cn/dancingsnow/neoecoae/api/me/planning/ECOPlanningResultRegistry.java); verify `resolveContract(...).executable()` before submission. Do not assume every plan provides stage `executionPlan()` metadata.

`withSubmissionAlias` binds metadata only during a synchronous submission callback. Keep execution identity intact; do not copy or scale task vectors to reuse another plan's metadata. The registry is bounded and expiring, not persistent storage.

### Exact parent orders

`ECOBigOrderRequest` accepts positive `BigInteger` totals, currently limited to 1024 decimal digits. The helper's explicit target must be an eligible ECO CPU or its advertised idle ECO placeholder; ordinary AE2 CPUs cannot interpret the parent carrier.

`request.submit(callback)` binds the request to that exact carrier object for the synchronous callback. Do not submit a separately obtained `carrier()` later, copy the carrier or run the callback asynchronously. Its long output is a bounded projection. `fromPlanningResult` reads a long plan projection; construct the request directly for totals exceeding long.

The parent plans bounded child segments against fresh inventory. [ECOBigOrderPlanner.begin](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOBigOrderPlanner.java) probes one segment without creating a parent. [ECOBigOrderAdmission.allows](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderAdmission.java) checks diagnostic/forced admission; forced mode creates no materials and does not accept unknown or unsupported routes. Read exact parent state through `ECOCraftingProgressView.bigOrder()`.

The confirmation menu's full exact-plan submission and the segmented parent API have distinct contracts. Do not construct parent orders from internal menu adapters.

## Job events, attachments and policies

| Capability | Registration and contract |
| --- | --- |
| Lifecycle | `ECOCraftingLifecycle.register(listener)`; remove that instance with `unregister`. Callbacks: `onJobStarted`, `onPatternDispatched`, `onJobFinished` |
| Persistent attachments | `ECOCraftingJobAttachmentRegistry.register(id, factory)`; implement [ECOCraftingJobAttachment](../src/main/java/cn/dancingsnow/neoecoae/api/me/attachment/ECOCraftingJobAttachment.java) `id/save/load/clear`, with an independent instance per job |
| Dispatch policy | `ECOCraftingDispatchPolicyRegistry.register(policy)`; implement `mayTick(cpu)` and `isProviderAvailable(cpu, provider)`; unregister the same instance |

A dispatch event reports provider acceptance, not completion. Read exact counts with `exactDispatchedCrafts()`. Listener exceptions are isolated; observers must not debit inputs, dispatch again or mutate the job.

Attachment IDs must be unique and match the returned object's ID. ECO owns save/load/clear. Unbound historical data is retained for recovery; a factory failure does not authorize discarding job resources.

Every policy must allow the operation. Default provider availability checks `isBusy()`; policy exceptions reject the operation. Pausing does not change ownership of an accepted batch.

## Providers and FastPath

Ordinary providers implement AE2 `ICraftingProvider`. For atomic parallel acceptance, implement `ECOParallelCraftingProvider`:

- `eco$getAvailableParallelSlots()` returns current free capacity.
- `eco$pushPatternBatch(pattern, inputTotal, craftCount, jobId)` receives the complete batch total; `jobId` may be null.
- Return true only after accepting all inputs. False guarantees unchanged inputs, never partial acceptance.

Adapt external classes with [ECOParallelCraftingProviders.register(providerClass, adapter)](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProviders.java). Return the capability or null. Query live capacity; overlapping adapter types have no priority guarantee.

FastPath uses the separate `ECOFastPathDispatchProvider`:

| Phase/data | Contract |
| --- | --- |
| `eco$prepareFastPath(context)` | Read-only probe; null for unsupported, otherwise positive long capacity and commit callback |
| `ECOBatchDispatchContext.inputs()` | Inputs for one execution |
| `Batch.inputTotal/outputTotal/remainingTotal` | Whole-batch totals; do not modify rollback collections |
| `supportsExactInputs`, `exactInputTotal` | Explicit exact-input support, without truncation for legacy providers |
| `eco$prepareExactFastPath(context, requested)` | Separate exact-count capability, unsupported by default |

Resolution checks direct implementations, external adapters, then built-in bridges. Parallel-only adapters do not gain FastPath automatically. `ECOBatchCapacityProvider` is deprecated; use the new contract.

True means full acceptance. False or an ordinary exception guarantees no acceptance and permits CPU rollback. If acceptance is uncertain, throw [ECOIndeterminateBatchException](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOIndeterminateBatchException.java) and stop for reconciliation; never refund then retry uncertain acceptance. Providers do not debit/refund CPU inputs or energy themselves.

External CPUs call `ECOFastPathFacade.prepare(...)` and single-use `PreparedBatch.submit(energyAccount)` in one server tick. Inputs remain in CPU inventory until the facade debits them. After true, account the returned totals once; later accounting failure cannot undo provider acceptance. `EnergyAccount.commit()` must not throw; persist energy refunds the network cannot accept. `prepareAllocated` has different ownership for preallocated inputs; read the facade Javadoc before using it.

[EcoFastpathHost](../src/main/java/cn/dancingsnow/neoecoae/api/fastpath/EcoFastpathHost.java) is a separate inspect/submit protocol obtained from an actual host integration, without global capability discovery or replacement of CPU transactions.

## Outputs, progress and machine diagnostics

[ECOCraftingOutputClaimSink.claimCraftingOutput](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputClaimSink.java) accepts `ECOCraftingOutputClaimRequest(jobId, expected, actual, amount, mode)`, combining job identity, expected demand, actual output routing and accounting. Expected and actual keys may differ.

Remove only the returned `claimedAmount()` from the producer's physical buffer. Keep unclaimed resources. Inspect `deliveredToRequester()`, `deliveredToNetwork()` and `storedInCpu()`; never deliver an already claimed amount through another path. `SIMULATE` transfers nothing.

| Entry point | Responsibility |
| --- | --- |
| [ECOCraftingOutputRouter](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputRouter.java) | Route output to a CPU by job ID |
| [ECOJobOutputReceiver](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOJobOutputReceiver.java) | Accept Worker outputs and surplus |
| [ECOVirtualCraftingCompletionSink](../src/main/java/cn/dancingsnow/neoecoae/api/me/completion/ECOVirtualCraftingCompletionSink.java) | Accept virtual completion counts, not necessarily finish the whole job |
| [ECOCraftingProgressSink](../src/main/java/cn/dancingsnow/neoecoae/api/me/progress/ECOCraftingProgressSink.java) | Report completed work without delivering resources |
| [ECOCraftingProgressView](../src/main/java/cn/dancingsnow/neoecoae/api/me/progress/ECOCraftingProgressView.java) | Read progress, elapsed time, key-type work and optional parent progress |
| [ECOProcessingStatusRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOProcessingStatusRegistry.java) | Register [ECOProcessingStatusReader](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOProcessingStatusReader.java) under a stable ID; remove with `unregister(id)` |

Do not report progress twice if the output path already recorded it. Machine readers observe without loading chunks or advancing machines; providers can expose processing targets through [ECOProcessingTargetProvider](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOProcessingTargetProvider.java).

[ECOVirtualCraftingCoolantRegistry.register(fluidId)](../src/main/java/cn/dancingsnow/neoecoae/api/me/cooling/ECOVirtualCraftingCoolantRegistry.java) extends the virtual-crafting fluid allowlist. A qualifying `neoecoae:cooling` recipe and normal cooling, structure and power requirements still apply. Registration is idempotent; `unregister` removes custom entries while preserving built-ins. The registry is process-wide, not saved in worlds.

## Pattern semantics and insertion

Standard patterns need only AE2 `IPatternDetails`. Register special [ECOPatternSemanticAdapter](../src/main/java/cn/dancingsnow/neoecoae/api/me/pattern/ECOPatternSemanticAdapter.java) instances with `ECOPatternSemanticsRegistry.register(id, priority, adapter)`:

- Extend the generic analysis while preserving the same physical pattern instance.
- Return `ECOPatternSemantics.unsupported(...)` when accurate analysis is unavailable.
- Higher priority wins, with ID ordering for ties; only the first matching adapter applies. Analysis failure does not fall through to a more permissive adapter.
- Normalized input/feedback amounts stay exact; actual inputs must still satisfy `IInput.isValid`.

Execution optimization requires separate `ECOExecutionOptimizationRegistry` authorization. `VERIFIED_ASSEMBLER` cannot turn arbitrary processing into virtual crafting. Wrappers implement [ECOPatternWrappers.Wrapper](../src/main/java/cn/dancingsnow/neoecoae/api/me/pattern/ECOPatternWrappers.java) or register a specific contract/accessor; delegate names are not guessed. Registration changes invalidate related caches and old optimization credentials; avoid changing contracts mid-calculation.

For encoded items, obtain `grid.getService(IECOPatternStorageService.class)`, construct [ECOPreparedPattern](../src/main/java/cn/dancingsnow/neoecoae/api/ECOPreparedPattern.java) from the item, its matching decoded pattern and `AEItemKey.of(encoded)`, then call `insertPreparedPatternReporting`.

| Result | Ownership |
| --- | --- |
| `INSERTED` | Complete the source transfer according to [ECOPatternInsertion](../src/main/java/cn/dancingsnow/neoecoae/api/ECOPatternInsertion.java) |
| `ALREADY_PRESENT` | The recipe exists; this does not mean the source item was accepted |
| `NO_SPACE` | Retain the source and retry after capacity changes |
| `INCOMPATIBLE/NO_TARGET` | Retain the source and report the missing compatible target |

Physical item storage returns no blank. When a container absorbs the recipe, `consumedSource()` is true: deliver `blankReplacement()` before clearing the source. Persist partially completed transfers to prevent duplicate insertion or blank returns.

Writable node services implement [IECOPatternStorage](../src/main/java/cn/dancingsnow/neoecoae/api/IECOPatternStorage.java); `KnownUnique` methods require proof of uniqueness. Directory hosts use [PatternStorageHost](../src/main/java/cn/dancingsnow/neoecoae/api/PatternStorageHost.java) and [AuxiliaryPatternHolder](../src/main/java/cn/dancingsnow/neoecoae/api/AuxiliaryPatternHolder.java) to expose real slots, revisions and stable content ordering. Opt into migration sourcing with `allowsPatternMigrationSource()`.

Claim external candidates under an owner UUID and release claims on every exit path. Remove candidates after clearing the source. An unfinished scan can still expose usable candidates; discard old slot references after topology changes.

## Storage cells and exact amounts

Register handlers with `ECOStorageCells.register(IECOCellHandler)`. `getCellInventory(stack, saveProvider)` may return null. On cell replacement or host unload, call `releaseCellInventory` so the handler saves/releases owned state. [IECOStorageCell](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOStorageCell.java) defines inventory behavior; [IECOStorageCellItem](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOStorageCellItem.java) and [IBasicECOCellItem](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IBasicECOCellItem.java) expose item capabilities.

For `ICellHost`, install exactly one valid cell; null requests removal, while `ItemStack.EMPTY` does not. Check `isItemValid/canExtractCell`, call void `setCellStack`, then re-read the slot before transferring source/old-cell ownership. Do not mutate its returned live stack. [IECOStoragePriorityHost.setStoragePriority](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOStoragePriorityHost.java) saves and refreshes mounts on the server.

`ECOBigIntegerStorage.insert(storage, key, amount, mode, source)` returns the **inserted amount**, not the remainder. Within long it uses normal AE2 insertion. Beyond long it prefers exact storage; otherwise it attempts one `Long.MAX_VALUE` segment without looping. Exact implementations must return an amount between zero and the offered total.

| Optional capability | Requirement |
| --- | --- |
| [IECOStorageMigrationCell](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOStorageMigrationCell.java) | Lossless enumeration, clearing, simulation, persistence and recovery before migration |
| [IECOBulkMarkableCellItem](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOBulkMarkableCellItem.java) | Enables the marking UI, without registering a backend |
| [IECOBulkDisplayCell](../src/main/java/cn/dancingsnow/neoecoae/api/storage/IECOBulkDisplayCell.java) | Exposes the complete compression chain and effective display cutoff |
| [INativeMEStorageRestrictedItem](../src/main/java/cn/dancingsnow/neoecoae/api/storage/INativeMEStorageRestrictedItem.java) | Defaults to rejecting native ME drive/chest insertion; false resumes normal validation, not guaranteed acceptance |

Native restrictions do not remove existing cells, affect other mods' drives or supply ECO storage. Infinite cells still need complete migration support and explicit eligibility.

## Addon extension registration

Query these feature names with `ECOAddonApi.supports(name)`:

| Feature | Entry point | Capability |
| --- | --- | --- |
| `power_profiles` | [ECOPowerProfiles](../src/main/java/cn/dancingsnow/neoecoae/api/power/ECOPowerProfiles.java) | Host, part and cell idle power |
| `multiblock_definitions` | [ECOMultiblockDefinitions](../src/main/java/cn/dancingsnow/neoecoae/api/multiblock/ECOMultiblockDefinitions.java) | Build definitions by host kind |
| `multiblock_parts` | [ECOMultiblockParts](../src/main/java/cn/dancingsnow/neoecoae/api/multiblock/ECOMultiblockParts.java) | Part roles, drive rows and ordering |
| `multiblock_requirements` | [ECOMultiblockRequirements](../src/main/java/cn/dancingsnow/neoecoae/api/multiblock/ECOMultiblockRequirements.java) | Alternative blocks for structure slots |
| `computation_cells` | [ECOComputationCells](../src/main/java/cn/dancingsnow/neoecoae/api/computation/ECOComputationCells.java) | Independent computation items and per-stack capacity |
| `host_cable_models` | [ECOComputationModels](../src/main/java/cn/dancingsnow/neoecoae/api/ECOComputationModels.java) | Models by tier and host |
| `tier_features` | [IECOTier.supportsFeature](../src/main/java/cn/dancingsnow/neoecoae/api/IECOTier.java) | Switch, virtual crafting and infinite storage eligibility |
| `storage_channel_displays` | [ECOStorageChannelDisplays](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOStorageChannelDisplays.java) | Storage channel names, icons and aliases |
| `native_me_storage_restrictions` | [INativeMEStorageRestrictedItem](../src/main/java/cn/dancingsnow/neoecoae/api/storage/INativeMEStorageRestrictedItem.java) | Native ME drive/chest insertion restrictions |
| `workstation_adapters` | [ECOWorkstationRecipeAdapters](../src/main/java/cn/dancingsnow/neoecoae/api/workstation/ECOWorkstationRecipeAdapters.java) | Complete external recipe conversion |
| `workstation_modules` | [ECOWorkstationModules](../src/main/java/cn/dancingsnow/neoecoae/api/workstation/ECOWorkstationModules.java) | Module cards, source types and appearance |

[ECOExtensionRegistry](../src/main/java/cn/dancingsnow/neoecoae/api/extension/ECOExtensionRegistry.java) sorts by descending priority, then ID. Duplicate IDs throw; unregister before replacing your entry. `entries()` is immutable and successful registration/removal advances `revision()`. This ordering does not apply to every legacy registry, including parallel adapters.

Power callbacks return total AE/t. Zero is valid; negative, NaN and infinity are invalid. Scale `context.defaultPower()` when needed and refresh changed hosts through `NEBlockEntity.refreshIdlePower()`. This controls idle power only.

Build definitions resolve through the registry, `IECOTier.getBuildDefinition(kind)`, then built-ins; kinds separate computation/crafting/storage. Actual formation uses `NEBlockEntity.registerCalculatorFactory(type, factory)`: a build preview alone supplies no formation logic. [ECOMultiblockRequirement](../src/main/java/cn/dancingsnow/neoecoae/api/multiblock/ECOMultiblockRequirement.java) describes default/alternative states, transformed together for rotation/mirroring and checked again before placement. `Context.level` may be null.

Roles provide classification/ordering, not execution backends; cores and drives still require compatible entities/calculators. `IECOComputationCellItem` reports nonnegative bytes per actual stack; adapt external Items without caching dynamic capacity by Item. `IECOTier.supportsFeature(ECOFeature)` declares eligibility without bypassing physical, material or recovery requirements.

`ECOStorageChannelDisplays.Display` aliases merge display channels, never distinct AEKey inventories. Names, colors and texture IDs travel in server snapshots; statistics stay exact. Icons/bars use separate 8×8 images with bar borders.

[AddonApiExample.java](../src/test/java/cn/dancingsnow/neoecoae/api/extension/AddonApiExample.java) provides a compilable registration skeleton; replace IDs, blocks, entities and recipe adapters with registered addon objects.

### Workstation recipes and modules

`ECOWorkstationRecipeAdapters.register(id, priority, sourceRecipeType, adapter)` preserves source ID/type, inputs, outputs, time, AE/FE costs and extra resources. Disable recipes that cannot be represented accurately instead of omitting costs. `LargeWorkstationRecipe.processing(ticks, feEnergy, feAlternative)` specifies time/FE; `energy()` is the AE budget. Nonzero AE and FE with alternative false require both; true makes them alternatives. FE-only recipes set AE to zero.

`ECOWorkstationModules.Module` supplies stable ID, card, source types, names and optional appearance. Read `getInstalledModuleId/getInstalledModuleDescription`; the legacy enum's `OTHER` cannot distinguish custom modules. Icons are 15×15, circuit images 28×28; provide complete north-facing controller models.

[LargeWorkstationRecipes](../src/main/java/cn/dancingsnow/neoecoae/recipe/LargeWorkstationRecipes.java) `getAll` returns display representatives, `getGroups/getGroup` exposes groups and `getAvailable(level, moduleId)` returns legal card-specific variants. `availableVariants(moduleId)` retains original batch sizes/costs. Persist the actual variant ID returned by `findScaled`. A display representative grants no execution permissions or arbitrary smaller batches. Recipe/tag reloads and adapter/module changes invalidate relevant caches.

## Panels and models

Register custom tiers and cell types with matching IDs on both sides in synchronized registries exposed by [NERegistries.Keys](../src/main/java/cn/dancingsnow/neoecoae/all/NERegistries.java). Implement required `IECOTier` values; use `supportsComponentTier` for component compatibility and `supportsFeature` for advanced eligibility.

`IECOTier.getPanelTierName()` supplies names, `getHostMarkerTexture()` supplies markers and `getPanelAtlas(String)` replaces full atlases for that tier. Null preserves defaults. Resolution is tier atlas, global LDLib2 `ui-eco` provider, then original image. Preserve atlas dimensions and crop layout.

| atlas argument, without `ui-eco:` | Default under `assets/neoecoae/textures/guis/` |
| --- | --- |
| `Computation-BACKGROUND_ATLAS` | `eco_cpu_controller.png` |
| `Computation-WIDGETS_ATLAS` | `cpu_widget.png` |
| `Storage-CONTROLLER_ATLAS` | `storage/storage_controller.png` |
| `Storage-ATLAS` | `storage/storage_widget.png` |
| `Crafting-CONTROLLER_ATLAS` | `crafting_controller.png` |
| `Crafting-WIDGETS_ATLAS` | `crafting_widgets.png` |

Blocks implement `IHostPanelTextureProvider.getPanelTexture(BlockState, Part)` for members, tier badges and markers. The five-argument overload adds `index, active, hovered` for status/buttons and delegates to the two-argument method by default. Use actual placed state; null falls back per state.

| Part | Separate image layout |
| --- | --- |
| `COMPUTATION_UPPER` | 10×5 |
| `COMPUTATION_CORE/COMPUTATION_LOWER` | 10×6 |
| `STORAGE_TIER/COMPUTATION_TIER/CRAFTING_TIER` | 13×18 |
| `STORAGE_MARKER/COMPUTATION_MARKER/CRAFTING_MARKER` | 13×16, two stacked 13×8 markers |
| `COMPUTATION_STATUS` | 12×12 |
| `COMPUTATION_SWITCH/CRAFTING_MODE` | 15×26 |

Status indices 0–5 represent connection, substitutions, cycles, fast planning, normal switch and high-energy switch; hovered is false. The high-energy blue state uses column 5, its pink state column 4. Computation switch indices 0–2 are substitutions/cycles/fast planning; crafting indices 0–1 are active cooling/overclock, with active/hovered selecting state images.

When constructing panels such as [StorageHostUI](../src/main/java/cn/dancingsnow/neoecoae/gui/storage/StorageHostUI.java), use overloads accepting `Supplier<IECOTier>` and `Supplier<BlockState>`. The server captures placed-state texture IDs for synchronization; old factory signatures remain available.

Register `ECOCellModels.register` and `ECOComputationModels.registerCellModel/registerCableModel` on the client. Cables support `(tier, hostBlock)` entries. Additional models returned by dynamic providers require `registerAdditionalModel` before baking. Panel providers change images, not button semantics.

## Integration diagnostics

[ECOCraftingServiceDiagnostics.neoecoae$describeCpuSelection](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOCraftingServiceDiagnostics.java) describes CPU eligibility; [ECOPatternPushDiagnostics.neoecoae$getPushDiagnostics](../src/main/java/cn/dancingsnow/neoecoae/api/me/diagnostics/ECOPatternPushDiagnostics.java) reports recent push failure. Diagnostics do not authorize replay, refunds or skipped checks.

Include actual JAR/dependency versions, action source, options, planning status, submission results and provider acceptance/delivery logs in compatibility reports. Signature/contract checks establish API behavior; machine transactions, assets and modpack interoperability require runtime validation.
