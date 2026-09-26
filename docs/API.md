# API integration guide

> Source snapshot: Neo ECO AE Extension `21.2.0-beta4`, 2026-09-21. This describes the current source tree, not a permanent compatibility promise.

## 1. Integration status

The project has a substantial Java API under `cn.dancingsnow.neoecoae.api`. It covers storage cells, ECO tiers and cell types, pattern storage, crafting lifecycle and job state, provider dispatch, output routing, progress, planning settings, and client model registration.

Current distribution constraints:

- There is no separate API source set or `-api` artifact. The full mod JAR contains the API.
- `java-library` and `maven-publish` are enabled, but the checked-in publishing target is only the local `repo` directory. No public Maven repository is declared.
- API types directly reference Minecraft, NeoForge, AE2, and occasionally ECO implementation types. Consumers must compile against matching versions.
- The mod is GPLv3. Review the license implications before distributing linked derivative work.
- The version is beta and no semantic API compatibility policy is declared. Pin the exact mod version and test upgrades.

Recommended Gradle setup when consuming a locally supplied JAR:

```groovy
repositories {
    flatDir { dirs "libs" }
}

dependencies {
    implementation("org.appliedenergistics:appliedenergistics2:19.2.17")
    compileOnly(name: "neoecoae-21.2.0-beta4")
    localRuntime(name: "neoecoae-21.2.0-beta4") // only for the development run
}
```

Use the actual file name without `.jar`. If a published Maven repository is added later, replace the `flatDir` dependency with its documented coordinate. Declare a required or optional `neoecoae` dependency in `neoforge.mods.toml` according to whether your code can load without ECO.

## 2. Stability levels

| Level | Surface | Guidance |
| --- | --- | --- |
| Preferred | `api.storage` contracts; lifecycle listener and attachment registries; dispatch policy; progress view; pattern storage service; provider contracts | Intended integration boundaries. Still pin the beta version. |
| Conditional | `IECOTier`, custom registries, model registries, planning/network settings, output claims, `ECOFastPathFacade` | Usable when the documented ownership and lifecycle rules are followed. Usually version-sensitive. |
| Read-only bridge | diagnostics, menu interfaces, capability snapshots, output routers | Mostly implemented onto AE2/ECO objects by mixins. Obtain with `instanceof`; do not implement unless the contract explicitly asks you to. |
| Internal | `ECOCraftingCPU`, `ECOCraftingCPULogic`, execution/runtime/persistence/worker classes, integration loader internals | Public for implementation access, not a stable third-party boundary. Do not construct, subclass, or persist these types. |

`@ApiStatus.Internal` currently marks event firing and attachment creation methods, but not every implementation-facing public class is annotated. Package placement alone is therefore not a complete stability guarantee.

## 3. Registration and lifecycle rules

All mutable registries are process-wide. Register once during mod construction/common setup, unregister listeners and policies during teardown when a test or reloadable host can install them repeatedly. Gameplay callbacks and provider commits run on the owning server thread unless a contract explicitly says otherwise.

Never call client model APIs from a dedicated server class path. Never mutate an ECO job, provider inventory, or AE grid from asynchronous planner work.

### Lifecycle observer

```java
private static final ECOCraftingLifecycleListener LISTENER = new ECOCraftingLifecycleListener() {
    @Override
    public void onPatternDispatched(ECOCraftingDispatchEvent event) {
        long crafts = event.dispatchedCrafts();
        UUID jobId = event.job().craftingJobId();
        // Observe only. Do not mutate the job from this callback.
    }
};

public static void register() {
    ECOCraftingLifecycle.register(LISTENER);
}
```

Use `register`/`unregister`. Deprecated `addListener`/`removeListener` are binary bridges scheduled for removal. Listener exceptions are logged and isolated from the job.

### Persistent per-job attachment

```java
ECOCraftingJobAttachmentRegistry.register(
    ResourceLocation.fromNamespaceAndPath("examplemod", "audit"),
    context -> new AuditAttachment(context.craftingJobId())
);
```

An attachment instance belongs to exactly one job. Its `id()` must equal the registration id. `save`/`load` own a private `CompoundTag`; `clear` receives the terminal `SUCCESS`, `FAILURE`, or `CANCELLED` result. Factories that throw, return null, or return a mismatched id are ignored and logged. Do not call `create` or `createAll`; those methods are internal.

### Scheduler policy

```java
ECOCraftingDispatchPolicy policy = new ECOCraftingDispatchPolicy() {
    @Override
    public boolean mayTick(ECOCraftingCpuContext cpu) {
        return !maintenanceMode;
    }

    @Override
    public boolean isProviderAvailable(ECOCraftingCpuContext cpu, ICraftingProvider provider) {
        return !provider.isBusy();
    }
};
ECOCraftingDispatchPolicyRegistry.register(policy);
```

Every policy is a veto, policies fail closed, and an exception denies the tick/provider. Callbacks must not extract inputs, push providers, or mutate tasks.

## 4. Storage API

### Cell discovery

Implement `IECOCellHandler` and register the singleton from enqueued common setup:

```java
event.enqueueWork(() -> ECOStorageCells.register(MyCellHandler.INSTANCE));
```

The first handler returning a non-null inventory wins. `isCell`, `getCellInventory`, release, and runtime-cache clearing must be mutually consistent. A handler may cache by stack/host, but must release host-bound state in `releaseCellInventory` and discard transient state in `clearRuntimeState`.

Implementations return `IECOStorageCell`, which extends AE2 `StorageCell`. Implement `IECOStorageMigrationCell` only when contents can be enumerated, cleared, persisted, and reinserted losslessly for resumable migration into the infinite storage domain.

Cell items can implement:

- `IECOStorageCellItem`: tier, cell type, and accepted `AEKeyType`s.
- `IBasicECOCellItem`: the standard finite-cell contract, including bytes, bytes per type, total types, idle drain, and blacklist checks.

### Tiers and cell types

The synchronized custom registries are:

- `neoecoae:eco_tier`, key `NERegistries.Keys.ECO_TIER`, value `IECOTier`.
- `neoecoae:cell_type`, key `NERegistries.Keys.CELL_TYPE`, value `ECOCellType`.

Built-ins are `l4`, `l6`, `l9` and cell types `items`, `fluids`. External registration should use NeoForge `RegisterEvent` after the custom registries exist. A custom tier must supply all crafting, computation, storage, power, and overlay values. `supportsComponentTier` uses tier ordering. Registry ids and values are synchronized, so registration must be identical on client and server.

Treat `NERegistrate` and its builders as project implementation conveniences, not the cross-mod contract.

## 5. Pattern storage API

Get the grid-owned service through AE2:

```java
IECOPatternStorageService service = grid.getService(IECOPatternStorageService.class);
ECOPatternInsertionResult result = service.insertPreparedPattern(prepared);
```

`PatternCatalog` is the grid source of truth. Prefer `ECOPreparedPattern` when pattern details have already been decoded. Result values distinguish insertion, duplicate, no-space, and incompatibility conditions.

Implement `IECOPatternStorage` on an `IGridNodeService` to expose a writable destination. Respect the `KnownUnique` methods: they permit the implementation to skip a duplicate scan only because the grid catalog already proved uniqueness. `checksLogicalDomainForDuplicates()` must describe actual behavior.

The external-pattern index claim/release methods are server-thread coordination primitives. Always release an owner UUID's claims on success, cancellation, or failure. Do not retain `PatternContainer` or slot references across topology changes.

## 6. Crafting provider APIs

There are two separate contracts. Do not mix them.

### Ordinary parallel dispatch

Implement `ECOParallelCraftingProvider` for providers that atomically accept multiple ordinary processing crafts:

- `eco$getAvailableParallelSlots()` returns current capacity.
- `eco$pushPatternBatch(...)` receives total input counters for the complete `craftCount`, not one-copy inputs.
- Return `true` only after taking ownership of the complete total.
- Return `false` without changing any input or provider state.

### Verified FastPath dispatch

Implement `ECOFastPathDispatchProvider` for synchronous verified ECO/F9 execution. `eco$prepareFastPath(context)` must only inspect and prepare; it must not consume resources. Return a `Preparation` with positive capacity and a commit predicate.

The commit predicate receives total inputs, outputs, and remainders. It returns `true` only when the whole batch is accepted. `false` or an ordinary exception means nothing was accepted and ECO rolls inputs/energy back. If the acceptance state is unknowable, throw `ECOIndeterminateBatchException`; ECO retains custody and stops fallback to prevent duplication.

`ECOBatchCapacityProvider` is deprecated. Implement `ECOFastPathDispatchProvider` directly.

`ECOFastPathFacade` is the advanced boundary for a non-ECO CPU. Prepare and submit synchronously on that CPU's owning server thread in the same tick. A `PreparedBatch` is single-use, including rejection. The caller owns exactly-once accounting after success; the facade owns extraction/rollback during submission. Do not use this API unless the caller has a durable energy reservation and reconciliation strategy.

### Adaptive batch dispatch (internal behavior, no new API)

All CPU dispatch lanes now use `crafting.execution.batch`. Linear and single-copy adapters collect live limits in `ECOBatchPlanner`, acquire physical inputs through `ECOBatchMaterializer`, and submit through `ECOBatchExecutor`. `ECOBatchProvider#eco$dispatchBatch` returns an ownership receipt: rejection refunds the whole batch, a valid partial acceptance refunds only the unaccepted linear suffix, and uncertain acceptance retains resources and suspends the job. The planner also respects waiting-output headroom and startup seeds reserved for other execution phases.

Verified FastPath and stateful recipes use `ECOStatefulBatchPlanner`; arbitrary-precision orders use `ECOExactBatchPlanner`. Both submit through the same executor/materializer using prepared totals, so reusable tools are not multiplied and exact quantities are not narrowed to `long`. `ECOStatefulBatchProvider#eco$dispatchPreparedBatch` is the internal atomic commit adapter. CPU task/output accounting runs only after successful resource settlement. These are internal execution contracts, not new third-party registration APIs. Existing provider APIs below/above are adapted into this chain; there is no old/new dispatcher setting.

Adaptive/dynamic batching for ordinary processing patterns is currently performed by the ECO CPU's internal executor. It does not add a registration point or a new provider interface. Successful probes can keep doubling within a visit and resume growth on the next tick. Once earlier chunks have filled a target, rejection or buffering ends that visit while preserving its proven batch size. Initial rejection has bounded recovery retries and a growth cooldown. Separate per-visit and shared per-CPU tick attempt budgets bound small-batch work, failed attempts, and ordinary fallbacks after scaled dispatch. The ordinary fallback remains available during scaled cooldown. Recipe quantity tables use fastutil primitive counters and are reused within a visit, while materials, protected seeds, energy and waiting-output headroom are checked for every offer. Eligibility checks are also cached briefly. When an ECO CPU dispatches, ECO owns this multiplier decision; EAEP and AE2LT smart-doubling policies are not consulted.

Use `ECOParallelCraftingProvider` for ordinary batch processing. Use `ECOFastPathDispatchProvider` only for verified synchronous FastPath execution, never for ordinary processing patterns. Internal adaptive scaling supports multiple inputs while preserving sparse input order. It checks observable provider logic/send buffers, external inventory push support, and compatible blocking/directional modes. Remainders and reusable tools require verified stateful execution or single-copy fallback.

AE2LT uses a direct counted transport adapter for normal and wireless providers. ECO chooses the count; the adapter preserves blocking, locks, transfer energy, success callbacks and durable overflow custody, without calling native adaptive ramps or consulting their switches, configured multiplier limits or history. Directional patterns use one-copy routed transactions, with each copy consuming an ECO attempt. Unsupported transport versions fall back to single-copy dispatch. Generic Thunderbolt adaptive batch contracts are no longer preferred by the ECO CPU. Mek-Energistics remains the exception: its smart queue accepts the ECO batch when enabled and receives only one copy when disabled.

Imported EAEP 1.6.x automatically scaled plans are normalized to base recipes and exact task counts after validating input/output scaling. Encoded amounts and unrelated wrappers are preserved. ECO's own verified execution plans are already expressed in base crafts. Execution-only processing wrappers are created by ECO independently of EAEP smart-doubling settings.

Do not depend directly on internal adaptive-dispatch classes, probe sizes, cache lifetimes, or probe intervals. A successful scaled push transfers the entire batch to the provider, even when some inputs remain buffered; a non-empty send buffer is not proof of capacity for a larger batch. Rejection must leave inputs and provider state unchanged.

## 7. Output, progress, and mixin bridges

An ECO CPU exposes progress and output claims through explicit getters:

```java
if (cpu instanceof ECOCraftingCPU ecoCpu) {
    ECOCraftingProgressView view = ecoCpu.getProgressView();
    float progress = view.progress();

    ECOCraftingOutputClaimResult result =
        ecoCpu.getOutputClaimSink().claimCraftingOutput(request);
}
```

This concrete check is the currently supported discovery path when starting from AE2's `ICraftingCPU`; a generic CPU does not directly implement either view/sink interface. Do not construct or subclass `ECOCraftingCPU`.

Output claims are atomic server-thread operations. `expectedKey` is the planned key; `actualKey` is the produced substitution/dynamic key. Inspect the returned status and delivered/stored counts; never edit the CPU waiting inventory directly.

Other bridge interfaces include `ECOCraftingNetworkSettings`, diagnostics interfaces, `ECOCraftingOutputRouter`, `ECOJobOutputReceiver`, menu extensions, and `ECOCraftingProviderRevision`. These are generally discovered with `instanceof` on the documented AE2/integration object and most are injected through mixins. Absence must degrade gracefully because mixin/configuration/version conditions can make an object not implement a bridge.

## 8. Client model registration

Use `ECOCellModels.register(holder, model)` for drive-cell models and `ECOComputationModels` for computation cells/cables. Holder-based registration may occur before registry resolution. ECO drains deferred registrations during `FMLClientSetupEvent` after loading client integrations.

All referenced `ResourceLocation`s are model ids, not texture ids. Keep these calls in client-only code. Late registration after deferred processing updates the live map but may miss earlier model baking; register during client setup/integration loading.

## 9. Optional integration loader

`@Integration("target_mod_id")` classes are discovered from mod scan data. They must have an accessible no-argument constructor and may declare `public void apply()` and/or `public void applyClient()`. The class is only instantiated when the target mod is loaded.

This mechanism is convenient inside this project, but external mods should normally use their own mod lifecycle plus the public registries above. The loader uses reflection/method handles, has no ordering API between integrations, and exceptions can abort loading.

## 10. Recipes and data APIs

Registered recipe types are:

- `neoecoae:cooling`: fields `input`, optional `output`, `coolant`, optional `max_overclock` (default `0`).
- `neoecoae:integrated_working_station`: `inputItems` (0-9), optional `inputFluid`, optional `itemOutput`, optional `fluidOutput`, and required `energy`.

Java builders exist for data generation. KubeJS schemas use the same two ids when KubeJS is present. Recipe classes are public data types, but `NERecipeTypes` owns serializer/type registration; other mods should create JSON/data recipes instead of registering duplicate serializers.

## 11. Networking and compatibility cautions

`cn.dancingsnow.neoecoae.network` is private protocol implementation. Protocol version is currently the literal `"1"` and registers two C2S and one S2C payload. Do not send these packets directly or depend on payload record layout.

Avoid dependencies on `impl`, `mixins`, `blocks.entity`, `multiblock`, or `compat` packages. In particular:

- Never persist ECO implementation class names or internal NBT keys.
- Never call event `fire*` methods or attachment `create*` methods.
- Never assume an AE2 object has an ECO bridge without `instanceof`.
- Never retain mutable `ItemStack`, `KeyCounter`, grid, provider, or block-entity references across ticks unless the API says so.
- Preserve `long` amounts. Do not narrow storage/crafting quantities to `int`.

## 12. Pre-release integration checklist

1. Compile against Java 21, Minecraft 1.21.1, NeoForge 21.1.x, AE2 19.2.17+, and the exact ECO JAR.
2. Test both with and without ECO if the dependency is optional.
3. Test a dedicated server; client-only model/UI classes must never load there.
4. Test server restart, chunk unload/reload, job cancellation, provider rejection, and full output storage.
5. For provider APIs, test atomic rejection and indeterminate acceptance explicitly.
6. Run against the exact optional-mod versions your bridge targets.
