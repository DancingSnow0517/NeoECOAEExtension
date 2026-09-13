# ECO Cycle Planner to CPU Handoff Investigation

Date: 2026-09-07. Inspected checkout: `v21.1.2`, HEAD `bb5d8b6b`, including the existing uncommitted long-batch changes.

## Conclusion

The current planner-to-CPU handoff can turn a valid, materially balanced cycle plan into a permanently stalled job. Correct per-pattern counts alone do not guarantee executable order. The production CPU discards the cycle's order and seed metadata, dispatches enabled patterns from a HashMap, and allows final-output delivery to consume multi-step feedback.

Five runtime characterizations reproduce stalls using the production planning service, submission path, CPU dispatch, and output insertion. They cover four scenarios, with the split/join scenario repeated through the atomic batch path. Every declared provider output is returned exactly once. Each scenario also completes using the same plan and counts when the fixture follows the solver's compact execution trace before delivering the final output.

This establishes concrete causes of crafting stalls in this checkout. The user's particular large job has not been captured, so it does not establish which cause affected that job. The local development `run/logs/latest.log` contained no matching blocked-output or cycle execution diagnostics.

## Handoff Trace

| Stage | Current behavior | Relevant source |
| --- | --- | --- |
| Cycle solve | Produces exact firing counts, seed requirements, and a replayable compact trace | `impl/crafting/planner/cycle/CycleSolveResult.java:14` |
| Numeric merge | Commits cycle counts and seed/external stock reservations into a copied SolveState | `impl/crafting/planner/solve/SolveState.java:130` |
| Material validation | Checks total supply against total demand; does not validate the runtime firing order | `impl/crafting/planner/solve/ECOPlanMaterialValidator.java:18` |
| Public AE2 plan | Projects final output, used/emitted/missing items, and aggregate pattern counts | `impl/crafting/planner/bridge/AE2CraftingPlanBridge.java:11` |
| Execution metadata | Builds phases, dependencies, task ownership, compact steps or dynamic firing counts, and initialSeed | `impl/crafting/planner/result/ECOExecutionPlanBuilder.java:38` |
| Submission alias | Makes matching execution metadata available during submission | `api/me/ECOPlanningResultRegistry.java:124` |
| CPU submission | Extracts initial items and constructs ExecutingCraftingJob without resolving execution metadata | `api/me/ECOCraftingCPULogic.java:92` |
| Runtime job | Copies only aggregate patternTimes into a HashMap of remaining task counts | `api/me/ExecutingCraftingJob.java:57`, `:82` |
| Dispatch | Chooses any task with a ready provider and available inputs; there is no phase or seed admission check | `api/me/ECOCraftingCPULogic.java:254` |
| Accounting | On acceptance, adds expected outputs/remainders and subtracts the accepted craft count; rejection restores inputs | `api/me/ECOCraftingCPULogic.java:333` |
| Return | Moves accepted outstanding outputs into shared physical CPU inventory | `api/me/ECOCraftingCPULogic.java:377` |
| Delivery | Runs before dispatch; reserves feedback only for patterns that individually produce more of the final key than they consume | `api/me/ECOCraftingCPULogic.java:173`, `impl/crafting/planner/result/ECOPhaseScheduler.java:76` |

Paths in the table are relative to `src/main/java/cn/dancingsnow/neoecoae/`.

The metadata is present during the reproduced submissions: the fixture asserts `activeSubmissionMetadata(plan) != null` inside the submission alias. Thus these failures do not require registry expiry, a copied plan losing its attachment, or a changed recipe count.

## Confirmed Runtime Defects

### P1: Multi-step feedback can be delivered as finished product

`deliverStoredFinalOutput()` calls `growingPatternFeedbackReserveExact()` for each remaining pattern independently. That helper returns zero unless the same pattern consumes and produces the final key with positive net growth. It cannot represent a feedback path spanning several patterns.

Two reproductions:

| Recipes and initial stock | Valid plan | Current runtime result |
| --- | --- | --- |
| `A -> B`, `B -> 2A`; stock `A=1`; request `A=1` | Each pattern once, seed `A=1` | First tick delivers the seed. No pattern fires. Remaining final amount becomes zero while two firings remain; CPU stays busy. |
| `A -> B`, `B -> 2A`; stock `A=1`; request `B=2` | `A -> B` three times, `B -> 2A` once | First returned B is delivered before it can feed the loop. One B remains requested, three firings remain, and both stock and in-flight outputs are empty. |

The second case shows that protecting only the initial seed key is insufficient: a different key becomes feedback halfway through the loop.

### P1: Downstream work can consume a cycle's startup stock

Recipes: `A -> 2A`, `A -> G`. Stock: `A=1`. Request: `G=2`.

The real planner schedules two growth firings and two consumer firings, reserves one A, and emits a dependency from the growth phase to the consumer phase. Making the growth provider busy for the first dispatch pass allows the consumer to take the one A. After its G returns and the growth provider becomes available, the CPU has no A, no in-flight outputs, and three remaining firings. It cannot recover.

The existing final-output reservation helper protects only the final output key. In this job that key is G, so it does not protect A from other input extractions.

### P1: An enabled firing can destroy the feasibility of a dynamic cycle

Recipes:

```text
L: A -> B
R: A -> C
J: B + C -> 3A
F: 3A -> G
```

Stock: `A=2`. Request: `G=2`.

The production planner returns SUCCESS with `L=4, R=4, J=4, F=2`, cycle seed `A=2`, and a valid compact trace. In one legal HashMap iteration order, the CPU runs L twice, turning both seeds into B. It then has `A=0, B=2, C=0`: R cannot run and J cannot join. All providers are available and all outputs have arrived, but 12 firings and both G remain.

The test fixes the patterns' hash codes to make that legal order deterministic across JVMs; it does not replace the production task map. The same stall occurs with one accepted batch of size two, or two ordinary pushes of size one. Disabling batching cannot correct this scheduling defect.

`ComponentPlanner.cycleExecutionDisposition()` uses dynamic execution for SCCs with more than two patterns. `ECOExecutionPlanBuilder` retains only dynamic firing counts and initialSeed for that mode, while the solver's compact trace stays in the component result. Reconnecting a phase gate alone would therefore not solve intra-component ordering. Counts plus current input availability are insufficient for safe dynamic admission.

## Additional Confirmed Gaps

- **P2, metadata consistency:** `ECOPlanningResultRegistry.inspectRegistration()` recognizes only phase type CYCLE when cycle execution is expected. A valid DYNAMIC_CYCLE plan is registered with `MISSING_OR_INVALID_SCHEDULE` and `NO_CYCLE_PHASE`, even though its execution plan exists and has no build error. A sixth characterization reproduces this with a three-pattern ring. This is currently a latent handoff defect because the CPU does not consume the recovery result.
- **Diagnosis:** `ECOCraftingCPULogic.getPermanentExecutionError()` always returns null. Repeatedly attempting an impossible remaining task vector produces no specific execution failure state. The reproduction checks that no remaining recipe has its inputs and that no output is in flight; it does not infer deadlock from elapsed time alone.
- **Persistence:** ExecutingCraftingJob saves remaining pattern counts, waiting outputs, and final amount, but no cycle steps, phase cursor, or resource ownership. Reloading cannot recover the original proof of order from those counters alone. New scheduling state would need an explicit persistence and migration design.
- **Unused protection:** CycleResourceLedger has no production caller. The ownership-related helper classes and most ECOPhaseScheduler policies do not currently control CPU dispatch. The CPU's only ECOPhaseScheduler call is the per-pattern final-feedback calculation.

## Relation to the Reported Waiting State

The reproduced steady states have positive remaining tasks and an empty `waitingFor`, after every expected provider output is returned. This demonstrates a scheduling deadlock without lost machine output. The initial two-step example additionally reaches `remainingAmount=0` while retaining unfinished tasks.

In `CraftingCPUMenuMixin.java:135`, the displayed active amount comes from `getWaitingFor`, and the pending amount comes from `getPendingOutputs`. An actual snapshot is needed to distinguish an impossible unissued task from an issued task whose output never arrived. The user's description of a large job remaining in a waiting state alone cannot distinguish them.

ECO worker returns already carry job IDs through `ECOCraftingThread.java:949` and the router in `CraftingServiceMixin.java:413`. Generic provider returns use the AE2 key-only path. This investigation did not establish an output routing failure in the user's job. The runtime reproductions do not depend on routing between multiple CPUs.

## Verification

Investigation fixture: `src/storageTest/java/cn/dancingsnow/neoecoae/api/me/ECOCycleHandoffInvestigationTest.java`.

The six new tests intentionally assert the current defect states, with successful ordered controls for the five runtime tests. Passing these characterizations means the defects were reproduced; it does not mean production scheduling was fixed. When implementing a fix, convert the stalled-state expectations into progress/completion assertions.

```bash
./gradlew --offline test \
  --tests 'cn.dancingsnow.neoecoae.api.me.ECOCycleHandoffInvestigationTest' \
  --tests 'cn.dancingsnow.neoecoae.api.me.ECOCraftingDispatchRegressionTest' \
  --console=plain
```

Result: 18 tests passed: six new investigation cases and twelve existing CPU dispatch regressions. Production CPU submission, dispatch, and insert methods execute directly. The fixture substitutes the world, network storage, energy service, and deterministic providers; it does not start Minecraft or load third-party Mixins.

```bash
./gradlew --offline plannerTest \
  --tests '*ECOExternalDemandPlannerTest' \
  --tests '*ECOPhaseSchedulerTest' \
  --tests '*CycleExecutionDispositionTest' \
  --tests '*DynamicCyclePlanningTest' \
  --tests '*FeedbackSeedRetentionTest' \
  --console=plain
```

Result: 63 tests run, 60 passed, three failed in the existing CycleExecutionDispositionTest:

- `plannerRecordsStockSatisfiedInternalCycleWithoutRuntimeCyclePhase`: expected STOCK_SATISFIED, got ORDERED_EXECUTION.
- `stockAlreadyCoveringCycleDemandDoesNotScheduleCycleFirings`: expected STOCK_SATISFIED, got ORDERED_EXECUTION.
- `plannerKeepsPartialStockAndPositiveFiringsAsOrderedExecution`: expected a reservation of 30, got 1.

These disagree with the current single-pattern growth stock policy and the newer FeedbackSeedRetentionTest. No planner implementation or existing planner test was edited in this investigation. The current planner suite cannot be described as fully green.

## Repair Requirements

1. Bind the validated execution contract to the running job. Validate counts and physical pattern identity at submission before extracting inputs.
2. Preserve or reconstruct a proven admissible firing order for every cycle, including multi-pattern dynamic cycles. Restrict batch size to work that remains admissible after that batch.
3. Protect feedback across the whole component from downstream consumers and final delivery, including intermediate feedback keys that differ from initialSeed. Retention must follow the remaining work and release after its last necessary use.
4. Advance task/step counts only by the provider-accepted amount. Keep in-flight outputs distinct from available physical inventory; a slow provider is not itself a deadlock.
5. Persist the new execution state and validate it on reload. Fix dynamic-phase metadata classification before relying on the recovery path.
6. Expose the blocking pattern, missing physical inputs, pending/in-flight quantities, component/step, and last successful dispatch/return. Diagnose an impossible state without automatically cancelling legitimate long-running machines.

No production scheduling change was made as part of this investigation. Existing uncommitted changes were preserved. Commit `bb5d8b6b` changes planner seed arithmetic; its runtime counterpart is still missing in the inspected CPU path.
