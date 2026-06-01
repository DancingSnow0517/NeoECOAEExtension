# BlockEntity Update Semantics

Date: 2026-06-01

This note records the intended meaning of the update flags used by NeoECOAE block entities. It is deliberately conservative: it documents current expectations without changing save data, packet order, or block behavior.

## Terms

`setChanged()` means the block entity has persistent data that must be saved. Use it for NBT-relevant state, inventories, fluids, energy, cached cell content, config toggles, and other server-owned data.

`markUiStateDirty()` or a `uiRevision` increment means an open menu should resend a lightweight UI state. Use it for values shown only in Native UI, such as cached stats, running thread counts, coolant amounts, available bytes, and status text.

`markStatsDirty()` means a cached derived value must be recomputed before the next server use. Examples include crafting structure stats, computation storage stats, and storage controller totals.

`markVisualDirty()` means the client-visible block state, model, light, or block entity renderer data changed. This is the narrow case where `markForUpdate()` is appropriate.

`markProviderDirty()` means the AE storage provider identity or mount state changed. It may require provider refresh/remount and should not be triggered by ordinary content count changes.

## Rules

Do not treat these events as interchangeable. A content count change usually needs persistence, controller stats, and an AE storage change notification, but it should not remount the provider unless the handler identity or availability boundary changed.

Do not call `markForUpdate()` for pure numeric UI changes. Prefer a UI revision or the existing menu state sync path.

Do not call provider refresh/remount from generic save callbacks. Use explicit provider dirty paths for cell insertion/removal, cell type changes, handler invalidation, online/mounted transitions, or state boundaries that affect provider availability.

When in doubt, name the helper after the narrowest effect it performs:

- `markPersistenceDirty`
- `markUiStateDirty`
- `markStatsDirty`
- `markVisualDirty`
- `markProviderDirty`

Keeping these names separate is part of the performance contract for the storage, crafting, and computation controllers.
