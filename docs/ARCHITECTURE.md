# Architecture reference for AI and maintainers

> Snapshot: `21.2.0-beta4`, Minecraft `1.21.1`, NeoForge `21.1.233`, AE2 `19.2.17`. This document is deliberately explicit and redundant enough for an AI coding agent to choose the correct ownership boundary before editing code.

## 1. System purpose

Neo ECO AE Extension is an AE2 addon with three large multiblock families:

- **Storage system**: finite ECO cells plus an infinite storage domain with resumable migration.
- **Crafting system**: pattern buses, parallel workers, cooling, dispatch, and output recovery.
- **Computation system**: crafting-plan computation, CPU threads, byte capacity, parallelism, and logical network pooling.

Integrated workstations provide machine processing and pattern-provider behavior. Optional integrations add storage key families, models, recipe viewers, probes, and high-performance dispatch bridges.

## 2. Hard architectural rules

An edit is wrong if it violates one of these rules, even if it compiles.

1. The AE grid and its grid services own network-wide state. A block entity owns only its local durable state.
2. `PatternCatalog` is the source of truth for pattern locations, counts, free capacity, and external-pattern claims.
3. The CPU owns crafting inputs, energy reservation, task accounting, and accepted-but-undelivered outputs. A provider must not debit or refund CPU resources.
4. A provider acceptance is atomic: `true` means complete ownership transfer; `false` means no transfer. Unknown acceptance must enter reconciliation, never ordinary fallback.
5. Worker inputs and outputs remain durably recoverable across chunk unload, topology rebuild, cancellation, and restart.
6. Server gameplay state is authoritative. Client state is presentation/cache only.
7. Planning may run asynchronously; world, grid, provider, inventory, and block-entity mutation must remain on the owning server thread.
8. Storage/crafting amounts use `long`. Narrow to `int` only at a proven Minecraft API boundary with checked bounds.
9. Optional-mod classes must never be eagerly loaded when that mod is absent.
10. Mixin bridges are conditional capabilities. Always use `instanceof` and provide a normal fallback.

## 3. Source tree map

| Package/path | Responsibility | May depend on |
| --- | --- | --- |
| `NeoECOAE` | Common entry point and registration orchestration | `all`, data, config, network, integration bootstrap |
| `all` | Registry handles for blocks, items, block entities, recipes, menus, tiers, cell types, grid services | API and concrete registrations |
| `api` | Cross-module and third-party contracts | Minecraft/NeoForge/AE2; minimize implementation references |
| `registration` | Registrate builders and entry wrappers | `all`, API, concrete content |
| `blocks`, `items` | World/item definitions and local behavior | API, block entities, multiblock contracts |
| `blocks.entity` | Durable local state, ticking, capabilities, UI hosts | API, `impl` services, multiblock clusters |
| `grid` | AE grid-owned services, especially `PatternCatalog` | API and AE2 grid APIs |
| `multiblock.calculator` | Detect and validate physical structures | block entities and cluster types |
| `multiblock.cluster` | Runtime aggregate of a formed structure | block entities, network pooling, crafting CPU |
| `multiblock.network` | Logical pooling/linking between formed structures | cluster abstractions |
| `impl.crafting` | Planner, execution plans, fast path, dispatch, recovery implementation | public crafting contracts and AE2 internals |
| `impl.storage` | Finite cells, infinite engine, transfer/migration | public storage contracts |
| `recipe` | Recipe data, codecs, serializers, data builders | `NERecipeTypes` |
| `network` | Private C2S/S2C payload protocol | menus/client caches; not public API |
| `menu`, `gui`, `client` | Menu sync, screens, rendering and model mapping | read-only server snapshots/API bridges |
| `integration` | Optional content and lifecycle integration | target mod API, guarded by discovery/plugin lifecycle |
| `compat` | Runtime adapters around optional provider/crafting APIs | reflection or guarded optional APIs |
| `mixins` | AE2 and optional-mod injection points | smallest possible bridge into `api`/`impl` |
| `data`, `src/generated/resources` | Datagen definitions and generated output | registry handles and recipe builders |
| `guidebook` | English and Chinese GuideME source | user-facing gameplay docs |

Dependency intent is `entry/registries -> content -> service implementation`, while contracts point inward through `api`. New third-party hooks belong in `api`; algorithmic code belongs in `impl`; optional target-mod references belong in `integration` or `compat`.

## 4. Bootstrap sequence

Common construction in `NeoECOAE` performs, in order:

1. Force menu type initialization needed by the copied AE2/ExtendedAE-style menu.
2. Queue/register creative tabs, menus, items, blocks, fluids, block entities, datagen, grid services, tiers, cell types, recipes, and data components.
3. Scan `@Integration` classes and invoke common integrations whose target mod is loaded.
4. Register the synchronized server config.
5. Attach capability, upgrade, storage-cell, custom-registry, resource-pack, and network lifecycle listeners to the mod bus.
6. Attach tooltip, command, tag reload, storage unload/stop/tick listeners to the NeoForge bus.

`NewRegistryEvent` creates the synchronized `CELL_TYPE` and `ECO_TIER` registries. Common setup installs upgrades and the built-in cell handler. `NeoECOAEClient` separately loads client integrations, resolves deferred model registrations, installs fixed renderers/screens, and registers item colors.

Do not move client work into common construction. Do not resolve registry objects earlier merely to simplify code; use holders/deferred registration.

## 5. Major runtime data flows

### 5.1 Craft planning and execution

```text
Crafting UI / AE2 request
  -> Mixin-exposed ECO planning settings
  -> ECOPlanningService / ECOCraftingPlannerService (async calculation)
  -> immutable plan + ECOPlanningResultRegistry handoff
  -> CraftingService submission on server thread
  -> NEComputationCluster selects/creates ECOCraftingCPU
  -> ECOCraftingCPULogic owns the job and inventories
  -> dispatch policy + ECOCraftingDispatchStrategy
  -> ordinary provider OR verified FastPath provider
  -> ECO crafting worker / external provider
  -> job-scoped output routing and claim
  -> requester, network storage, or durable CPU remainder
  -> lifecycle completion + attachment cleanup
```

Planning produces descriptions; it does not transfer resources. `ECOPlanningResultRegistry` bridges asynchronous results to submission and contains recovery/matching logic. Treat it as internal handoff state, not a general cache API.

The computation cluster owns active CPUs and capacity. In a logical computation network, threads, byte capacity, and parallelism are pooled; only the representative advertises placeholder free capacity to avoid duplicate terminal entries.

### 5.2 Dispatch paths

Ordinary dispatch probes AE2 providers and can use `ECOParallelCraftingProvider` for atomic multi-craft acceptance. Verified FastPath classifies/materializes a recipe, computes a safe arithmetic/stateful batch, extracts inputs, reserves energy, and commits through `ECOFastPathDispatchProvider`.

Never silently route a processing pattern through the FastPath-only contract. Never retry an indeterminate commit through another provider. Exactly-once accounting must happen after acceptance and before observable completion.

### 5.3 Worker custody and recovery

`ECOCraftingThread` is durable work custody. It serializes active work, inputs, outputs, remainders, progress, job id, and recovery state. On cancellation or missing owner it returns eligible contents to network storage; if insertion is partial, it retains the remainder for retry.

Breaking a permanent structure must cancel/recover before cluster destruction. Ordinary topology rebuilds and chunk unloads preserve deferred CPU/thread state. Do not replace these paths with world drops: non-item keys and amounts over `Integer.MAX_VALUE` cannot be represented safely that way.

### 5.4 Pattern catalog

```text
Grid node joins/leaves or pattern slots change
  -> PatternCatalog updates indexed locations/counts/capacity generation
  -> combined IECOPatternStorage selects a writable ECO pattern bus
  -> duplicate proof permits KnownUnique insertion
  -> external inventories are scanned incrementally under a time budget
  -> migration claims slots by owner UUID
  -> success removes candidate; every terminal path releases claims
```

Do not add a second network-wide scan/cache in a menu or block entity. Extend the catalog and update it from slot/topology deltas.

### 5.5 Storage domains

Finite cells are discovered through `ECOStorageCells` handlers and exposed as `IECOStorageCell`. The storage multiblock aggregates cells and statistics. Infinite mode uses a SavedData-backed engine and resumable transfer objects. Only `IECOStorageMigrationCell` participates in migration.

Migration is a custody transfer, not a copy. Simulation must precede mutation; persisted progress must survive restart; the source may only be cleared after the destination has accepted the corresponding contents. Level unload/server stop clears transient handler/runtime caches without deleting durable storage.

### 5.6 Multiblock lifecycle

Calculators validate blocks and construct `NECluster` subclasses. Block entities attach to the cluster, the controller exposes aggregate state, and network switches optionally connect clusters through `NELogicalNetworkManager` into network-cluster pools.

Formation and destruction are server-side topology operations. Code that adds a component must update all of: physical validation, cluster membership lists, capacity/stat aggregation, serialization if local state exists, UI snapshot, renderer/model if visible, and permanent-removal recovery if it can hold resources.

## 6. State ownership and persistence

| State | Owner | Persistence/transport |
| --- | --- | --- |
| Registry definitions | NeoForge registries | synchronized registry data |
| Per-block inventory/config | Block entity | block entity NBT/data components |
| Pattern network index | `PatternCatalog` grid service | rebuilt/maintained from grid nodes; claims are runtime coordination |
| Active crafting job | `ECOCraftingCPULogic`/CPU | computation controller/core NBT and execution codecs |
| Worker in-flight custody | `ECOCraftingThread` | full NBT, including recovery state |
| Per-job extension state | attachment instance | namespaced compound inside job data |
| Infinite storage contents | infinite storage engine | level `SavedData` plus resumable transfer state |
| Planner result handoff | planning result registry | bounded runtime metadata plus explicit persisted recovery metadata |
| Client exact amounts/UI | client cache/menu payload | private network packets; never authoritative |
| Config | `NEConfig` server spec | NeoForge server config synchronized to clients where needed |

When adding data, first choose the owner. Persist it with that owner. Do not duplicate authoritative values in a UI, cluster and block entity without a defined invalidation/reconciliation protocol.

## 7. Server/client and threading boundaries

- Common/API classes must be loadable on a dedicated server. Client imports belong under `client` or guarded client entry points.
- Block entities, grid services, clusters, provider commits, output claims, and migrations run on the server thread.
- ECO planning uses an executor and interruption checkpoints. Give the planner immutable snapshots or planner-owned structures only.
- Network handlers validate the current menu/container and enqueue work through NeoForge context before mutation.
- Renderers and screens consume snapshots. They must tolerate stale or absent server data.
- Global registries use synchronized or copy-on-write structures where callbacks can coexist, but this does not grant permission to perform gameplay mutation off-thread.

## 8. Optional integration architecture

There are four different mechanisms; choose intentionally:

1. `integration/<mod>`: optional content registration or official target-mod API use. `@Integration` gates common/client `apply` methods by loaded mod.
2. `compat/<mod>`: behavioral adapter, often for provider fast paths and version-tolerant calls.
3. `mixins/compat/<mod>` plus a dedicated mixin JSON: injection into optional-mod internals. Use `requiredMods` where eager class resolution would otherwise occur.
4. Plugin entry points such as JEI/EMI/Jade/KubeJS/LDLib: target framework discovers the plugin.

Keep common signatures free of absent target classes. Prefer a small local bridge interface in `api`, implemented/adapted in the optional layer. Reflection is acceptable for genuine cross-version support, but centralize it and fail with an explicit diagnostic.

## 9. Networking and menus

`ECONetwork` owns a private protocol version `"1"`:

- C2S force-craft-start flag.
- C2S ECO plan request for a container.
- S2C exact-amount data.

Packets are UI commands/snapshots, not a public RPC API. Server handlers must derive authority from the sender's open menu/grid, not packet-provided world references. Adding a payload requires registration, codec bounds, side-correct handling, menu/session validation, and compatibility consideration for the protocol version.

## 10. Configuration, data, and resources

- `NEConfig` is a server config and is authoritative for gameplay values.
- `src/main/resources` contains handwritten assets/data/mixin configs.
- `src/generated/resources` is datagen output and is included in main resources.
- `guidebook` is copied to `assets/neoecoae/ae2guide` by `processResources`.
- Recipe and model changes should originate in data providers/builders where an established generator exists.
- Mixin JSON files are split into core, terminal display, and optional compatibility sets. A new mixin must be added to the correct configuration and side.

## 11. Change routing for AI agents

| Requested change | Start reading here | Also verify |
| --- | --- | --- |
| Add/modify block or item | `all/NEBlocks`, `all/NEItems`, concrete class | block entity, model/datagen, loot, recipe, language, creative tab |
| Change a multiblock component | matching calculator + cluster + block entity | destroy/recovery, network pooling, UI and renderer |
| Change crafting planning | `impl/crafting/planner` | planning result handoff, cycle/substitution settings, async safety |
| Change dispatch/performance | `ECOCraftingCPULogic`, dispatch and fastpath packages | atomic rollback, energy, task/output accounting, optional bridges |
| Add external provider support | public provider contract, then `compat` adapter | rejection/exception/indeterminate tests and absent-mod loading |
| Change job outputs | output API + CPU logic + worker ejection | job id ownership, partial insertion, durable remainder, completion |
| Change storage cells | `api/storage`, handler, `impl/storage` | migration eligibility, cache release, AE2 cell semantics, UI colors |
| Change infinite storage | `impl/storage/infinite` and transfer package | SavedData, restart, simulated insertion, rollback/no duplication |
| Change pattern migration | `PatternCatalog`, pattern bus, machine interface | claims, generation invalidation, scan budget, duplicate proof |
| Add optional mod support | `integration`, `compat`, or compat mixin as appropriate | classloading without target, version bounds, client/server split |
| Add UI field | server owner -> menu sync/payload -> client snapshot/render | authorization, codec bounds, stale-data behavior |
| Expose new public API | smallest interface/record in `api` | ownership docs, nullability, thread rules, failure semantics, binary evolution |

## 12. Testing and verification

The Gradle project uses Java 21 and JUnit 5. Normal checks:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
```

There is also a `testThunderboltLegacy` task that runs compatibility tests against older Thunderbolt/AE2 Lightning Tech artifacts. Datagen uses the `runData` configuration/task generated by NeoForge ModDev.

Test effort should match custody risk:

- Pure codec/math/view change: focused unit tests.
- Registry or classloading change: client and dedicated-server startup.
- Planner/dispatch change: success, missing input, cancellation, provider reject, provider throw, indeterminate acceptance, overflow, and restart.
- Storage/migration change: simulation vs action, partial capacity, unload/reload, restart, duplicate prevention, and quantities over `Integer.MAX_VALUE`.
- Optional integration: target absent plus every supported target version family.

Before finishing, inspect the diff for generated-resource churn and unrelated IDE/run files. Do not regenerate all data for a narrow Java change unless the data contract changed.

## 13. Known architectural risks

- The `api` package currently contains both stable contracts and large implementation classes; visibility does not equal support status.
- No separate API artifact or public Maven publication is configured.
- The code depends on AE2 implementation classes and mixins in addition to AE2's public API, so AE2 upgrades require broad compatibility testing.
- Several optional integrations are version-sensitive and use compile-only artifacts, runtime adapters, or mixins.
- Process-global callback registries require disciplined single registration and test cleanup.
- The private network protocol uses a coarse literal version and has no documented backward compatibility negotiation.

When uncertain, preserve custody and fail closed. A stalled job with a diagnostic is recoverable; duplicated or deleted resources are not.

