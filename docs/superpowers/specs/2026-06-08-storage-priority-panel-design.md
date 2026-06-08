# Storage Priority Panel Design

## Goal

Add an AE2-style priority panel to the storage system controller. The panel sets one controller-level storage priority that all storage matrix drives use when mounting their storage cells into AE2.

## User Experience

The storage controller UI gains a second small floating-panel opener near the existing multiblock builder opener. Clicking it opens a draggable panel styled like the AE2 priority screen:

- Title: `Priority`
- Top row: `+1`, `+10`, `+100`, `+1000`
- Center field: an editable integer priority text field
- Bottom row: `-1`, `-10`, `-100`, `-1000`
- Footer help text: inserting prefers higher-priority devices, extracting prefers lower-priority devices

The panel is hidden by default, can be dragged by its title bar, and can be closed with a small close button. The layout should reserve enough width for localized text and large signed integer values.

## Data And Behavior

`ECOStorageSystemBlockEntity` owns the setting as a persisted, synced integer, defaulting to `0`. Button clicks update the value on the server, clamp overflow at Java `int` bounds, persist the controller, mark it for client update, and request AE2 storage provider refreshes for mounted drives through the existing storage node update path. The center value uses `TextField.setNumbersOnlyInt(Integer.MIN_VALUE, Integer.MAX_VALUE)`, `setTextResponder(...)`, and `bind(...)` so users can also type the priority directly.

`ECODriveBlockEntity.mountInventories` reads the controller priority from its `NEStorageCluster`. When the cell is valid for the controller tier, it mounts with:

```java
storageMounts.mount(cellInventory, storageCluster.getController().getStoragePriority());
```

If no valid controller/cell exists, the drive remains unmounted as before.

## Implementation Boundaries

Create a dedicated `StoragePriorityUI` helper instead of growing `MultiblockBuilderUI`. The helper should mirror the builder panel's floating-window and drag behavior, but only own priority-specific controls.

Language keys should be added at the generator source (`GuiLangs`) first, then regenerated into `en_us.json`. Existing manually maintained Chinese language files should receive matching concise translations.

## Testing

Add focused tests for priority arithmetic and clamping in a small helper if direct block-entity tests are too heavy. Verify compilation with `.\gradlew compileJava`, and run data generation or JSON validation for language resources if language files change.
