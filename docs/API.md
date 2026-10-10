# API integration guide

[简体中文](API_ZH_CN.md) · [Documentation index](README.md) · [ECO planner](ECO_PLANNER.md)

Scope: **Minecraft 1.21.1 / NeoForge**, current source reviewed **2026-10-10**. Read the [index](README.md) for build versions and the distinction between working-tree behavior and released artifacts.

## 1. Dependency setup and API boundaries

The API is in the full mod JAR. There is no separate `-api` artifact or declared long-term API compatibility policy. The build publishes to the local `repo/` directory; it does not declare a public NeoECOAE Maven endpoint. Pin the supplied artifact and its Minecraft/NeoForge/AE2 dependencies. The project license is GPLv3.

In an existing NeoForge Java 21 addon project, copy the matching JAR to `libs/`:

```groovy
dependencies {
    compileOnly files("libs/neoecoae-21.2.1.jar")
    // ModDevGradle development runs; omit when the mod is already installed by another mechanism.
    localRuntime files("libs/neoecoae-21.2.1.jar")
}
```

This snippet assumes the addon already configures NeoForge and AE2. It does not bundle NeoECOAE into your addon. Declare a required dependency if the addon cannot load without ECO:

```toml
[[dependencies.examplemod]]
modId = "neoecoae"
type = "required"
versionRange = "[21.2.1]"
ordering = "AFTER"
side = "BOTH"
```

Replace `examplemod` and the version range with the actual addon and tested artifact. For an optional integration, use `type = "optional"` and load the class containing ECO references only after confirming that `neoecoae` is present. Optional metadata alone does not prevent Java linkage errors.

| Need | Preferred entry point | Source |
| --- | --- | --- |
| Explicit ECO calculation | `ECOPlanningService.begin` | [planning service](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOPlanningService.java) |
| Mark an AE2 calculation | `ECOPlannerRequester`, `ECOPlannerOptions` | [requester](../src/main/java/cn/dancingsnow/neoecoae/api/me/planning/ECOPlannerRequester.java) |
| Read grid settings | `ECOCraftingNetworkSettings.of(grid)` | [settings](../src/main/java/cn/dancingsnow/neoecoae/api/me/network/ECOCraftingNetworkSettings.java) |
| Observe and extend jobs | Lifecycle listeners and attachment factories | [lifecycle](../src/main/java/cn/dancingsnow/neoecoae/api/me/lifecycle/ECOCraftingLifecycle.java), [attachments](../src/main/java/cn/dancingsnow/neoecoae/api/me/attachment/ECOCraftingJobAttachmentRegistry.java) |
| Provider integration | Parallel or FastPath contract | [parallel](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProvider.java), [FastPath](../src/main/java/cn/dancingsnow/neoecoae/api/me/provider/ECOFastPathDispatchProvider.java) |
| Dynamic output delivery | `ECOCraftingOutputClaimSink` | [output claims](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputClaimSink.java) |
| Route encoded patterns | `IECOPatternStorageService` | [pattern service](../src/main/java/cn/dancingsnow/neoecoae/api/IECOPatternStorageService.java) |
| Storage integration | `ICellHost`, `IECOCellHandler`, exact insertion | [cell host](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ICellHost.java), [storage helper](../src/main/java/cn/dancingsnow/neoecoae/api/storage/ECOBigIntegerStorage.java) |

Classes under `crafting.planner` expose some version-sensitive entry points used below. Solver, runtime, worker, persistence, and compatibility-loader internals are implementation details. The deprecated `api.me.ECOCraftingCPU`, `ECOCraftingCPULogic`, and `ExecutingCraftingJob` preserve existing addons' binary/Mixin targets; do not construct or subclass them for a new integration.

## 2. Thread and ownership rules

Call planning entry points on the **owning server thread** so inventory capture is safe. They return a `Future`; poll `isDone()` from a tick and call `get()` only after completion. Submission, provider commits, storage mutations, and output bookkeeping also run on the server thread.

Authenticate the player/machine and check access to the grid before starting and before submitting. The low-level ECO interfaces do not implement those checks for you. Keep the same grid, action source, and intended requester throughout one calculation. Cancel abandoned futures when a menu, machine, or network is removed. Handle `ExecutionException`, cancellation, and executor `RejectedExecutionException`; the ECO executor has a bounded queue.

The following Java examples are complete integration skeletons with imports. Machine transactions supplied by abstract methods and caller-provided callbacks still need implementation.

## 3. Start a calculation, inspect it, and submit once

`ECOPlanningService.begin(level, grid, source, goal, amount, strategy, options)` directly chooses ECO. It does not invoke `ICraftingService.beginCraftingCalculation`, so other service-entry planner wrappers do not choose this calculation. The example follows the confirmation screen's eligibility rule and consumes each future once.

Use `CalculationStrategy.REPORT_MISSING_ITEMS` to keep the original requested amount and its complete shortage report. `CRAFT_LESS` probes smaller amounts when the original request cannot execute; read the returned `finalOutput().amount()` rather than assuming it still equals your request. Those probes share the session's snapshot and budget. When a non-`CRAFT_LESS` calculation already produced a report plan, the service returns it without repeating the same calculation just to build a simulation report.

```java
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.planning.*;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningService;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import net.minecraft.server.level.ServerLevel;
import java.util.concurrent.*;

public final class EcoPlanningExample {
    private final ServerLevel level;
    private final IGrid grid;
    private final IActionSource source;
    private Future<ICraftingPlan> pending;
    private ECOPlanningResult lastResult;

    public EcoPlanningExample(ServerLevel level, IGrid grid, IActionSource source) {
        this.level = level;
        this.grid = grid;
        this.source = source;
    }

    public void begin(AEKey goal, long amount) {
        checkThread();
        if (amount <= 0 || pending != null) throw new IllegalStateException("Invalid request");
        var settings = ECOCraftingNetworkSettings.of(grid);
        if (settings == null || !settings.neoecoae$isFastPlannerEnabled()
                || !settings.neoecoae$hasComputationHost(source)) {
            throw new IllegalStateException("No eligible ECO planning host");
        }
        lastResult = null;
        pending = ECOPlanningService.begin(level, grid, source, goal, amount,
            CalculationStrategy.REPORT_MISSING_ITEMS, ECOPlannerOptions.from(settings));
    }

    // Call from a server tick, after rechecking access to the original grid.
    // Null means no submission: still pending, cancelled, or a diagnostic-only result.
    public ICraftingSubmitResult poll(ICraftingRequester requester, ICraftingCPU target)
            throws InterruptedException, ExecutionException {
        checkThread();
        var future = pending;
        if (future == null || !future.isDone()) return null;
        pending = null; // Consume once: repeated ticks cannot submit the same plan twice.
        if (future.isCancelled()) return null;
        var plan = future.get();
        if (plan == null) return null;
        lastResult = diagnostics(plan);
        if (plan.simulation() || lastResult != null && lastResult.status() != PlanningStatus.SUCCESS) {
            return null;
        }
        var contract = ECOPlanningResultRegistry.resolveContract(plan, lastResult);
        if (lastResult != null && (contract == null || !contract.executable())) return null;
        return ECOPlanningResultRegistry.withSubmissionAlias(plan, lastResult,
            () -> grid.getCraftingService().submitJob(plan, requester, target, false, source));
    }

    public ECOPlanningResult lastResult() { return lastResult; }

    public void cancel() {
        checkThread();
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    private void checkThread() {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
    }

    public static ECOPlanningResult diagnostics(ICraftingPlan plan) {
        if (plan instanceof ECOCraftingPlanDiagnostics bridge) {
            var attached = bridge.neoecoae$getPlanningResult();
            if (attached != null) return attached;
        }
        return ECOPlanningResultRegistry.find(plan);
    }
}
```

Construct one instance per request owner. After validating access, call `begin(goal, amount)`; call `poll(requester, target)` from subsequent server ticks. A nullable requester means a standalone task. A nullable target lets AE2/ECO select a CPU. Inspect `ICraftingSubmitResult.successful()` and its error information; a plan can become invalid after the snapshot because materials, capacity, or topology changed.

Read `lastResult()` to display `status()`, `trace().diagnostics()`, `exactMissingItems()`, and `theoreticalBytes()`. The diagnostic result can be absent for a foreign plan. Keep diagnostic collections read-only. `executionPlan()` can throw when no phased plan exists; query `resolveContract` instead of assuming that every successful DAG owns phases.

`withSubmissionAlias` preserves metadata only during this synchronous submission. It never replaces the submitted plan. Do not copy just `finalOutput()`, rescale the task counts, or associate another plan with a result because their output matches. The registry checks the complete execution identity and expires metadata after ten minutes; it is not persistent job storage.

### Options and grid defaults

| Option | Meaning |
| --- | --- |
| `cyclePlanningEnabled` | Allow unavoidable cyclic components to enter the cycle solver. |
| `ignorePatternSubstitutions` | Plan using encoded primary inputs instead of considering substitutions. |
| `fuzzyPlanningItemIds` | Item IDs selected for component-insensitive intermediate planning; not an unrestricted fuzzy recipe guarantee. |
| `planningLogEnabled` | Legacy compatibility field; logging now follows server debug configuration. |

`ECOPlannerOptions.from(settings)` captures current defaults. `from(null)` disables cycle planning and substitution ignoring and uses an empty fuzzy set. Explicit options can be supplied with `new ECOPlannerOptions(true, false, Set.of())`.

Grid settings expose `neoecoae$setFastPlannerEnabled`, `neoecoae$setCyclePlanningEnabled`, and `neoecoae$setIgnoringPatternSubstitutions`. Changes affect the shared network and its computation hosts, so perform them after permission checks. A host may accept only player or machine requests: use the source-aware `neoecoae$hasComputationHost(source)`.

### Enter through AE2's calculation service

If another integration needs the normal AE2 entry point, wrap its existing simulation requester:

```java
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.*;
import net.minecraft.server.level.ServerLevel;
import java.util.concurrent.Future;

public final class EcoRequesterExample {
    public static Future<ICraftingPlan> begin(ServerLevel level, IGrid grid,
            ICraftingSimulationRequester original, AEKey goal, long amount,
            ECOPlannerOptions options) {
        var marked = new ECOPlannerRequester(original, options);
        return grid.getCraftingService().beginCraftingCalculation(
            level, marked, goal, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }
}
```

The wrapper delegates `getActionSource()` and marks one calculation. If AE2 creates its `CraftingCalculation`, ECO's Mixin recognizes the marker. A different service wrapper can return its own future before that happens; ECO does not cancel or replace that future. Check the returned plan's provenance before applying ECO rules.

The vanilla confirmation **menu** routes to the direct ECO service when fast planning is enabled and an eligible computation host exists. Merely enabling a grid flag does not globally convert every unmarked addon/AE2 request into ECO planning. Explicit ECO requests return ECO diagnostic results for unsupported cases; they do not automatically retry the same request with the native planner.

## 4. Exact parent orders and menu admission

A parent can keep a positive exact amount beyond `Long.MAX_VALUE`. Current validation accepts at most 1024 decimal digits. Pass a `BigInteger` to the request; do not narrow it through `longValue()`.

For a server-side integration that wants ECO to choose a bounded child segment first, use the asynchronous segment probe:

```java
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.ECOBigOrderPlanner;
import java.util.concurrent.*;

public final class EcoSegmentExample {
    // Begin on the owning server thread; maximum and bytes describe a single segment.
    public static Future<ECOBigOrderPlanner.Answer> begin(IGrid grid, AEKey goal,
            long maximum, long bytes, ECOPlannerOptions options) {
        return ECOBigOrderPlanner.begin(grid, goal, maximum, bytes, options);
    }

    // Poll from the server tick. The caller must consume a completed answer only once.
    public static ECOBigOrderPlanner.Answer completed(Future<ECOBigOrderPlanner.Answer> future)
            throws InterruptedException, ExecutionException {
        if (!future.isDone() || future.isCancelled()) return null;
        return future.get();
    }
}
```

`ECOBigOrderPlanner.begin` captures fresh inventory for this invocation. The `maximumChildCrafts` and `cpuBytes` arguments describe one bounded segment. To create a parent ledger that coordinates successive segments, submit an `ECOBigOrderRequest`:

```java
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import java.math.BigInteger;
import java.util.Objects;

public final class EcoBigOrderExample {
    // ecoTarget must be an eligible ECO CPU or the grid's idle ECO placeholder.
    public static ICraftingSubmitResult submit(IGrid grid, IActionSource source,
            ICraftingRequester requester, ICraftingCPU ecoTarget, AEKey goal,
            BigInteger amount, ECOPlannerOptions options) {
        Objects.requireNonNull(ecoTarget, "Select an ECO CPU");
        var request = new ECOBigOrderRequest(goal, amount, false, options);
        return request.submit(carrier -> grid.getCraftingService().submitJob(
            carrier, requester, ecoTarget, false, source));
    }
}
```

Call this on the server thread after permission and CPU eligibility checks. The explicit target must be an existing ECO CPU or the idle ECO placeholder advertised by a computation host; a native CPU cannot interpret the parent carrier. Do not use automatic selection here unless your integration proves the parent will reach ECO.

`request.submit(...)` creates a carrier and binds the exact request to that **same object** during the synchronous callback. Submitting `carrier()` later, returning an asynchronous callback, or copying the carrier loses that binding. The carrier's bounded final-output amount is not the exact order quantity.

The parent plans bounded child segments from fresh stock, waits for materials/capacity, and credits completed child delivery. Read `ECOBigOrderProgress` through `ECOCraftingProgressView.bigOrder()` when exposed by the CPU. States include `PLANNING`, `RUNNING_CHILD`, `WAITING_MATERIALS`, `WAITING_CAPACITY`, `COMPLETED`, `CANCELLED`, and `FAILED`.

`ECOBigOrderAdmission.allows(result, forced)` is for converting eligible confirmation diagnostics: it allows `PLANNED_BUT_AMOUNT_UNREPRESENTABLE`, or forced `MISSING_ITEMS`, subject to component checks. It rejects unresolved, unsupported, and solved-but-unemitted components. `ECOBigOrderRequest.fromPlanningResult` takes the goal amount from the plan's long projection; use the constructor above when the requested quantity itself exceeds long. Forced mode does not create materials or make an unknown route executable.

The current confirmation menu has a separate exact-order path. Its big-order action checks admission, the selected ECO CPU, and, in non-forced mode, the current stock, then submits an internal `ECOExactCraftingPlan` directly to that CPU. The adapter retains the complete exact task vector and deferred stock/emission amounts, with bounded long windows for AE2 calls. This menu action does not bind an `ECOBigOrderRequest` or create the segmented parent described above. The adapter is an implementation detail; use the parent API when you want its segment planning and parent progress contract.

Source: [parent request](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderRequest.java), [admission](../src/main/java/cn/dancingsnow/neoecoae/api/me/bigorder/ECOBigOrderAdmission.java), [segment planner](../src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ECOBigOrderPlanner.java), [confirmation menu](../src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuMixin.java), [exact adapter](../src/main/java/cn/dancingsnow/neoecoae/crafting/adapter/ae2/ECOExactCraftingPlan.java).

## 5. Job events, attachments, and policies

### Observe jobs

Register one listener during common setup and retain it for teardown:

```java
import cn.dancingsnow.neoecoae.api.me.lifecycle.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EcoLifecycleExample implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("examplemod.eco");
    private final ECOCraftingLifecycleListener listener = new ECOCraftingLifecycleListener() {
        @Override public void onJobStarted(ECOCraftingJobContext job) {
            LOG.info("Job {} started", job.craftingJobId());
        }
        @Override public void onPatternDispatched(ECOCraftingDispatchEvent event) {
            LOG.debug("Job {} accepted {} crafts",
                event.job().craftingJobId(), event.exactDispatchedCrafts());
        }
        @Override public void onJobFinished(ECOCraftingJobContext job, ECOCraftingJobResult result) {
            LOG.info("Job {} ended: {}", job.craftingJobId(), result.status());
        }
    };

    public EcoLifecycleExample() { ECOCraftingLifecycle.register(listener); }
    @Override public void close() { ECOCraftingLifecycle.unregister(listener); }
}
```

Events describe ECO job start, **accepted** provider dispatch, and terminal results. Use `exactDispatchedCrafts()` for exact dispatch counts; the compatibility long field may be bounded. Job-context and terminal-result amount fields remain long views. Listen without extracting inputs, pushing providers, or changing task state. Listener runtime exceptions are logged and isolated.

### Persist a per-job attachment

An attachment belongs to one job and owns a private NBT payload:

```java
import cn.dancingsnow.neoecoae.api.me.attachment.*;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class EcoLabelAttachment implements ECOCraftingJobAttachment {
    private static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath("examplemod", "label");
    private String label;

    private EcoLabelAttachment(String label) { this.label = label; }

    public static void register() {
        ECOCraftingJobAttachmentRegistry.register(ID,
            context -> new EcoLabelAttachment(context.craftingJobId().toString()));
    }

    @Override public ResourceLocation id() { return ID; }
    @Override public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("label", label);
        return tag;
    }
    @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
        label = tag.getString("label");
    }
    @Override public void clear(ECOCraftingJobResult result) { label = ""; }
}
```

Call `EcoLabelAttachment.register()` once during common setup. Registration IDs must be unique, and each factory result's `id()` must match its registration. ECO invokes `save`, `load`, and `clear`; terminal statuses are `SUCCESS`, `FAILURE`, or `CANCELLED`. Unknown/unbound saved payloads are retained for later resolution. Factory and load failures do not authorize dropping the job's physical inventory. Attachment creation/binding methods are ECO internals.

### Pause dispatch

```java
import cn.dancingsnow.neoecoae.api.me.dispatch.*;
import java.util.function.BooleanSupplier;

public final class EcoPolicyExample {
    public static ECOCraftingDispatchPolicy install(BooleanSupplier enabled) {
        var policy = new ECOCraftingDispatchPolicy() {
            @Override public boolean mayTick(ECOCraftingCpuContext cpu) {
                return enabled.getAsBoolean();
            }
        };
        ECOCraftingDispatchPolicyRegistry.register(policy);
        return policy; // Unregister this same instance during teardown.
    }
}
```

All installed policies must permit the tick/provider. Override `isProviderAvailable(cpu, provider)` for additional selection rules; its default respects `provider.isBusy()`. Exceptions deny the current operation. These callbacks must not debit inputs, push patterns, or mutate tasks. Use `ECOCraftingDispatchPolicyRegistry.unregister(policy)` to remove the same installed instance.

## 6. Provider integration

### Ordinary parallel intake

```java
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import java.util.UUID;

public abstract class EcoParallelProviderExample
        implements ICraftingProvider, ECOParallelCraftingProvider {
    @Override public int eco$getAvailableParallelSlots() { return freeLanes(); }

    @Override public boolean eco$pushPatternBatch(IPatternDetails pattern,
            KeyCounter[] inputTotal, long craftCount, UUID jobId) {
        if (craftCount <= 0 || craftCount > freeLanes()) return false;
        return acceptWholeBatch(pattern, inputTotal, craftCount, jobId);
    }

    protected abstract int freeLanes();
    // Implement one atomic reservation/acceptance of the full batch in your machine.
    protected abstract boolean acceptWholeBatch(IPatternDetails pattern,
        KeyCounter[] inputTotal, long craftCount, UUID jobId);
}
```

Implement the abstract methods using your machine's queue transaction. `inputTotal` contains **all inputs for `craftCount` copies**, in AE2 key units. Return true only after owning the entire batch. On false, leave inputs untouched; partial acceptance cannot be reported as clean rejection. The job ID may be null.

For a foreign provider class, register `ECOParallelCraftingProviders.register(providerClass, adapter)` during setup. The adapter returns an `ECOParallelCraftingProvider` for that instance, or null. Resolve contracts with `ECOParallelCraftingProviders.find(provider)` to support both direct implementations and adapters. Cached wrappers must query live capacity. Avoid overlapping class registrations: adapter iteration has no declared priority order. Registration persists for the session.

### FastPath provider contract

`ECOFastPathDispatchProvider` is the separate contract for verified atomic FastPath execution. A preparation probe must not move resources. Providers validate the concrete recipe, return/container semantics, capacity, and commit target.

| Method/value | Meaning |
| --- | --- |
| `eco$prepareFastPath(context)` | Null declines support; otherwise returns positive capacity and a dispatch callback. |
| `ECOBatchDispatchContext` | Concrete input slots, outputs, and remainders for **one pattern copy**. |
| `Preparation.statefulCalculator` | Optional calculator for changing/reused material state; honor `statefulCalculatorRequired`. |
| `Batch.inputTotal/outputTotal/remainingTotal` | Complete totals of the accepted batch. |
| `supportsExactInputs`, `Batch.exactInputTotal` | Explicit opt-in to exact debit; never truncate exact inputs for a legacy provider. |
| `eco$prepareExactFastPath(context, requested)` | Separate exact-count contract; its default declines support. |
| `ECOBatchCapacityProvider` | Deprecated bridge; use `ECOFastPathDispatchProvider` for new integrations. |

A provider returns true only after full acceptance. False or an ordinary exception guarantees no acceptance, allowing CPU rollback. If acceptance may have happened, throw `ECOIndeterminateBatchException`; resources remain owned by the transaction and the job must stop for reconciliation. Never refund and retry an uncertain acceptance.

### External CPU using ECO's transaction facade

```java
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import net.minecraft.world.level.Level;
import java.util.UUID;
import java.util.function.Consumer;

public final class EcoFastPathExample {
    // oneCopyInputs/Outputs/Remainders are previews; inputs are still in cpuInventory.
    public static boolean dispatch(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] oneCopyInputs, KeyCounter oneCopyOutputs, KeyCounter oneCopyRemainders,
            ListCraftingInventory cpuInventory, long maxCrafts, double powerPerCraft,
            IEnergyService energy, Level level, UUID jobId,
            ECOFastPathFacade.EnergyAccount energyAccount,
            Consumer<ECOFastPathFacade.PreparedBatch> accountAccepted) {
        var prepared = ECOFastPathFacade.prepare(provider, pattern,
            oneCopyInputs, oneCopyOutputs, oneCopyRemainders, cpuInventory,
            maxCrafts, powerPerCraft, energy, level, jobId);
        if (prepared == null || !prepared.submit(energyAccount)) return false;
        // Provider acceptance already happened. Do not retry if accounting throws.
        accountAccepted.accept(prepared);
        return true;
    }
}
```

Prepare and submit in the same server-thread tick. Physical inputs stay in the CPU inventory until the facade extracts them. The CPU must not extract/refund those inputs separately. The CPU's `EnergyAccount.reserve` returns a reservation whose `commit()` cannot throw and whose `refund()` retains energy the network cannot receive as persistent CPU credit.

`PreparedBatch` is single-use, including rejection. Null preparation or false submission permits clean fallback after rollback. An indeterminate exception requires stopping the job. After true, the CPU accounts task counts, outputs, and remainders **once** using the returned totals; an accounting failure cannot undo provider acceptance.

`prepareParallel` adapts the ordinary parallel contract. `prepareAllocated` is restricted to already allocated, stateless uniform copies without returned inputs; its ownership rules differ from inventory-backed preparation. Read the [facade contract](../src/main/java/cn/dancingsnow/neoecoae/api/me/ECOFastPathFacade.java) before using either.

`api.fastpath.EcoFastpathHost` is a separate inspect/submit capability protocol. Requests carry its capability ID, API version, nonce, processing definition, and per-craft inputs. Obtain an actual host implementation from your integration; this interface does not provide a global capability lookup or replace CPU transactions.

## 7. Output delivery, virtual completion, and progress

```java
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.output.*;
import java.util.UUID;

public final class EcoOutputExample {
    public static ECOCraftingOutputClaimResult deliver(ECOCraftingOutputClaimSink sink,
            UUID jobId, AEKey expected, AEKey actual, long offered) {
        return sink.claimCraftingOutput(new ECOCraftingOutputClaimRequest(
            jobId, expected, actual, offered, Actionable.MODULATE));
    }
}
```

Obtain the sink through the integration's CPU/logic bridge; the legacy ECO CPU exposes `getOutputClaimSink()`. Expected and actual keys may differ for a dynamic output. The claim operation validates job identity, consumes reserved demand, routes the actual product, and updates completion together.

Remove only `claimedAmount()` from your producer's physical buffer after a modulating call. Retain the unclaimed remainder. Simulation reports matching demand and projected routing without consuming resources. A claim can be accepted into the CPU even when the destination is full; inspect `deliveredToRequester()`, `deliveredToNetwork()`, and `storedInCpu()` separately. Never insert the claimed amount again through another route.

| Interface | Call and responsibility |
| --- | --- |
| `ECOCraftingOutputClaimSink` | `claimCraftingOutput(request)`; read status and actual claimed amount. |
| `ECOCraftingOutputRouter` | `neoecoae$insertIntoCpuForJob(jobId, key, amount, mode)`; route outputs to their owner and retain the unaccepted remainder. |
| `ECOJobOutputReceiver` | `neoecoae$insertWorkerOutput(...)`; CPU-side receipt, including surplus. |
| `ECOVirtualCraftingCompletionSink` | `tryCompleteVirtualCrafting(pattern, completedCrafts)`; accepted logical executions do not necessarily finish the entire job. |
| `ECOCraftingProgressSink` | `recordCompletedCraftingWork(amount, keyType)`; only report work already completed. |
| `ECOCraftingProgressView` | Read `progress()`, `elapsedTimeNanos()`, work by key type, and optional parent-order progress. |

Progress accounting does not deliver physical resources. Do not independently report completion when the output-claim path already updated it. Keep simulation, actual delivery, and virtual completion separate.

Source: [claim result](../src/main/java/cn/dancingsnow/neoecoae/api/me/output/ECOCraftingOutputClaimResult.java), [virtual completion](../src/main/java/cn/dancingsnow/neoecoae/api/me/completion/ECOVirtualCraftingCompletionSink.java), [progress](../src/main/java/cn/dancingsnow/neoecoae/api/me/progress/ECOCraftingProgressView.java).

## 8. Pattern routing and container ownership

```java
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.*;
import net.minecraft.world.item.ItemStack;

public final class EcoPatternExample {
    public static ECOPatternInsertion insert(IGrid grid, ItemStack encoded,
            IPatternDetails decoded) {
        var service = grid.getService(IECOPatternStorageService.class);
        var prepared = new ECOPreparedPattern(encoded, decoded, AEItemKey.of(encoded));
        return service.insertPreparedPatternReporting(prepared);
    }
}
```

Use already decoded details that match the encoded stack. The grid service handles routing and duplicate detection:

| Result | Caller action |
| --- | --- |
| `INSERTED` | Commit the source transfer according to the reporting result. |
| `ALREADY_PRESENT` | The recipe already exists; do not assume the source item was moved. |
| `NO_SPACE` | Keep the source and retry when capacity changes. |
| `INCOMPATIBLE`, `NO_TARGET` | Keep the source and report the unsupported/missing destination. |

When the destination stores the encoded **item**, no blank is owed. When `consumedSource()` is true, a container absorbed the recipe; `blankReplacement()` must be non-empty. Secure delivery of the complete replacement before clearing the source. Track a partially completed migration transaction so retry does not duplicate insertion or blanks.

`IECOPatternStorage` is an AE2 node service for writable destinations. Register it on the managed node. Its `KnownUnique` methods require a prior duplicate proof; they are not general insertion shortcuts. `canAcceptIntoAuxiliary` must be side-effect-free. Hosts indexed by the catalog expose `PatternStorageHost` and `AuxiliaryPatternHolder`: use real slot inventories, cheap revision tokens, stable auxiliary ordering, and refreshed advertised patterns.

External migration uses owner UUID claims from `claimExternalPatternCandidates`. Candidates can be processed before the scan's `ready` flag becomes true. Release claims on every exit; release one candidate for a temporary no-space result and remove a candidate after its source is emptied. Do not retain source-slot references through topology changes.

Source: [reporting result](../src/main/java/cn/dancingsnow/neoecoae/api/ECOPatternInsertion.java), [writable storage](../src/main/java/cn/dancingsnow/neoecoae/api/IECOPatternStorage.java), [catalog host](../src/main/java/cn/dancingsnow/neoecoae/api/PatternStorageHost.java).

## 9. Storage calls and cell integration

```java
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.ISaveProvider;
import net.minecraft.world.item.ItemStack;
import cn.dancingsnow.neoecoae.api.storage.*;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import java.math.BigInteger;

public final class EcoStorageExample {
    public static void register(FMLCommonSetupEvent event, IECOCellHandler handler) {
        event.enqueueWork(() -> ECOStorageCells.register(handler));
    }

    public static IECOStorageCell inventory(ItemStack cell, ISaveProvider host) {
        return ECOStorageCells.getCellInventory(cell, host);
    }

    public static void release(ItemStack cell, ISaveProvider host) {
        ECOStorageCells.releaseCellInventory(cell, host);
    }

    public static void setPriority(Object host, int priority) {
        if (host instanceof IECOStoragePriorityHost target) target.setStoragePriority(priority);
    }

    public static BigInteger insertExact(MEStorage storage, AEKey key,
            BigInteger offered, IActionSource source) {
        return ECOBigIntegerStorage.insert(storage, key, offered, Actionable.MODULATE, source);
    }
}
```

Register each `IECOCellHandler` once in enqueued common setup. The first non-null inventory wins. Keep recognition, inventory creation, host-bound cache release, and runtime-state clearing consistent. Implement `IECOStorageCell` for the inventory and `IECOStorageCellItem`/`IBasicECOCellItem` for item metadata.

To use the handler facade directly, pass the host that owns the cell when one exists and release it when the host is discarded:

`EcoStorageExample.inventory` resolves the host-bound inventory, and `release` releases that binding when the host is removed.

The facade is synchronized. It does not transfer item ownership or authenticate the caller; those are host/terminal responsibilities.

### Cell slots and priority

Query `blockEntity instanceof ICellHost`. `getCellStack()` returns null for empty and may return a live stack: treat it as read-only. To install, pass one valid cell; to remove, pass **null**. `ItemStack.EMPTY` is rejected. Check `isItemValid`, `canExtractCell`, and the optional `getCellExtractionBlockReasonText()`. The void setter can reject a request silently, so re-read the slot before moving either stack. Remove and account for the old cell before inserting its replacement.

`IECOStoragePriorityHost.setStoragePriority(int)` accepts a signed configured priority, persists/synchronizes changes, and refreshes storage mounts. It is a server-thread mutation after caller-side permission checks; client or detached hosts ignore it.

### Exact insertion and migration

`ECOBigIntegerStorage.insert` returns the **inserted amount**, not the remainder. Subtract it from the offered amount. Within long range it calls AE2's usual insertion. Above long range it uses an exact implementation when available, otherwise offers one `Long.MAX_VALUE` slice. It does not guarantee a full exact insertion or loop through all slices. Respect `Actionable.SIMULATE` versus `MODULATE`.

Implement `IECOStorageMigrationCell` only when enumeration, clearing, simulated insertion, persistence, and restoration can preserve contents through resumable migration. `IECOBulkMarkableCellItem` opts an item into manual compression markers; it does not register a backend or enable automatic transfers. `IECOBulkDisplayCell` independently controls compression display cutoff.

### Registries and client rendering

Custom tiers and cell types use synchronized `neoecoae:eco_tier` and `neoecoae:cell_type` registries exposed by `NERegistries.Keys`. Register matching IDs on both sides through NeoForge registry events. Supply every required `IECOTier` value; tier comparison governs compatible components.

Client integrations may register storage/computation cell models with `ECOCellModels.register` and `ECOComputationModels.registerCellModel/registerCableModel`. Keep client GUI/render references in client-only setup. Do not call global deferred-registration internals or edit model maps during gameplay.

## 10. Diagnostics and integration limits

Use `ECOCraftingServiceDiagnostics.neoecoae$describeCpuSelection(plan, source)` for CPU eligibility and `ECOPatternPushDiagnostics.neoecoae$getPushDiagnostics()` for the most recent push failure. These are observations; they never authorize replay or refund.

A simulation shell may exist for missing, unsupported, unresolved, or unrepresentable results. A non-null plan alone is insufficient for submission. Read the [planner status table](ECO_PLANNER.md#8-results-and-diagnostics) and preserve blocked execution metadata.

For a reproducible report, include exact artifact versions, action source, settings, planning ID/status/diagnostics, CPU submission result, and provider/output logs. Planning, CPU reservation, provider acceptance, and final delivery are separate stages. This guide's examples are compile-time integration examples; they do not establish an in-game compatibility result.
