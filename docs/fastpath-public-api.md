# Public Fastpath integration

`ECOFastPathFacade.prepare` accepts a provider, a pattern, concrete input slots for
one copy, expected outputs/remainders, the CPU's `ListCraftingInventory`, a task
limit, per-copy energy, an energy service, a level and an optional job UUID.
It does not extract inputs or reserve energy. A non-null result reports the live
batch limit and immutable total inputs, outputs, remainders and energy.

Prepare and submit synchronously on the server thread in the same tick. Input
slots are previews; the complete batch must still be in the supplied inventory.
Call `submit` once with the CPU's energy reservation adapter. Never debit inputs
again. A false result or ordinary provider exception restores the extracted
inputs and refunds the reservation. A successful result commits energy; apply
task/output accounting exactly once using its totals. An accounting exception
after success must stop the job, never retry physical dispatch.

`ECOIndeterminateBatchException` means a provider may have accepted the batch.
Inputs and energy remain retained. Stop the job for reconciliation; never fall
back to an ordinary push. In particular, EAP's ordinary `pushPattern` does not
prove rollback after an exception, so its adapter uses this outcome.

The energy adapter owns partial-debit rollback, persistent refund credit and
NBT serialization. `commit` must not throw. NeoECO and the ordinary AE2 CPU adapter
use the existing energy ledger, including retained credit when a network is full.

`prepareAllocated` is for external SPIs that already allocate materials and own
energy/accounting. It uses an isolated descriptive ledger and never accesses the
physical CPU inventory. These SPIs only support uniform stateless recipes without
container items; reusable-state recipes fall back. The caller retains unaccepted
copies and settles its own transaction. The general `prepare` path retains the
existing stateful calculator and the caller's planner/task bound.

## Bindings

- NeoECO CPUs use the facade, retaining startup-seed checks and dynamic-output accounting.
- AE2LT/Thunderbolt 1.0.0 uses `IBatchCraftingProvider`, including the time-wheel CPU.
  Returned values are unaccepted copies, not accepted copies.
- OmniSequence API v1 uses `OmniBatchAdmission` and explicit delivery receipts.
  A delivery must match the exact admitted count and output totals. Old/missing
  APIs are disabled before loading optional provider interfaces.
- Omni Cells CPUs use AE2's ordinary CPU engine, with a separate facade adapter
  when Thunderbolt and OmniSequence are absent. When those engines are present,
  their provider SPIs own batching instead. CPU operation budgets are preserved.
- Job-directed output routing recognizes AE2 CPUs and Thunderbolt time-wheel CPUs.
- EAP super and ultimate matrices share `SuperAssemblerMatrixBlockEntity` in the
  inspected local EAP source. The ultimate structure is a flag on the same cluster,
  not a separate provider class. The adapter verifies real assembly, input totals,
  output totals and absence of remainders before constructing the scaled pattern.

## Optional API declarations

`src/omniApi` contains compile-only ABI declarations matching the local
OmniSequence 1.3.9 API v1 sources. They are not added to main resources, the runtime
classpath or the published mod jar. The installed OmniSequence mod supplies the
actual implementation. Updating its ABI requires rechecking declarations and the
runtime version gate together.

## Validation scope

Unit tests cover facade ownership, resource refunds, single-use submissions,
allocated-input handoff, EAP provider/superclass and scaled-dispatch contracts,
overflow fallback and Omni receipt handling. EAP test classes are ABI fixtures;
the matrix queue and actual Minecraft recipe execution require in-game testing.
Test ordinary, substituted and container recipes on real super/ultimate matrices,
and verify task completion/output return for each external CPU before release.
