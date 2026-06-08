# Storage Priority Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an AE2-style controller-level priority panel to the storage system and use that priority when drives mount storage cells.

**Architecture:** Store one persisted, synced `storagePriority` on `ECOStorageSystemBlockEntity`. Add a focused `StoragePriorityUI` helper for the draggable panel, and route all arithmetic through a small `StoragePriority.adjust` helper covered by unit tests.

**Tech Stack:** Java 21, NeoForge, AE2 `IStorageMounts`, LDLib2 UI, JUnit 5, Gradle.

---

### Task 1: Priority Arithmetic

**Files:**
- Create: `src/main/java/cn/dancingsnow/neoecoae/gui/StoragePriority.java`
- Test: `src/test/java/cn/dancingsnow/neoecoae/gui/StoragePriorityTest.java`

- [ ] **Step 1: Write failing test**

`StoragePriorityTest` verifies normal positive/negative steps and clamping at `Integer.MAX_VALUE`/`Integer.MIN_VALUE`.

- [ ] **Step 2: Run red test**

Run: `.\gradlew test --tests cn.dancingsnow.neoecoae.gui.StoragePriorityTest`

Expected: compile failure because `StoragePriority` does not exist.

- [ ] **Step 3: Add helper**

Create `StoragePriority` with:

```java
public static int adjust(int current, int delta) {
    long adjusted = (long) current + delta;
    if (adjusted > Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
    }
    if (adjusted < Integer.MIN_VALUE) {
        return Integer.MIN_VALUE;
    }
    return (int) adjusted;
}
```

- [ ] **Step 4: Run green test**

Run: `.\gradlew test --tests cn.dancingsnow.neoecoae.gui.StoragePriorityTest`

Expected: test passes.

### Task 2: UI Helper

**Files:**
- Create: `src/main/java/cn/dancingsnow/neoecoae/gui/StoragePriorityUI.java`
- Modify: `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostWidgets.java`

- [ ] **Step 1: Create draggable priority panel**

Panel layout mirrors the reference: hidden absolute panel, title bar, close button, rows of `+1 +10 +100 +1000` and `-1 -10 -100 -1000`, centered editable priority `TextField`, and help text.

- [ ] **Step 2: Create open button**

Add `StoragePriorityUI.createOpenButton(UIElement window)` with a compact `P` button and priority tooltip.

- [ ] **Step 3: Attach panel to storage host UI**

Update `ECOHostWidgets.hostPanel` to accept an optional list of floating controls or add the priority button directly in `ECOStorageSystemBlockEntity` if the generic helper change is too invasive.

### Task 3: Controller And Mount Behavior

**Files:**
- Modify: `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECOStorageSystemBlockEntity.java`
- Modify: `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECODriveBlockEntity.java`

- [ ] **Step 1: Add controller field**

Add `@Persisted @DescSynced @Getter private int storagePriority;`.

- [ ] **Step 2: Add priority mutator**

Add `changeStoragePriority(Player player, int delta)` using `StoragePriority.adjust`, then `setChanged()`, `markForUpdate()`, and `IStorageProvider.requestUpdate(drive.getMainNode())` for each drive in the storage cluster.

- [ ] **Step 3: Wire UI**

Create the priority window in storage controller `createUI`, pass current priority supplier, direct text setter, and delta callback, and add the opener next to the builder opener.

- [ ] **Step 4: Use priority when mounting**

Change drive mount call to `storageMounts.mount(cellInventory, storageCluster.getController().getStoragePriority());`.

### Task 4: Language And Verification

**Files:**
- Modify: `src/main/java/cn/dancingsnow/neoecoae/data/lang/GuiLangs.java`
- Modify generated English and maintained Chinese language JSON files.

- [ ] **Step 1: Add language keys**

Add keys for panel title, close tooltip, open tooltip, current priority, insert/extract help, and reset if used.

- [ ] **Step 2: Regenerate or update language resources**

Run `.\gradlew runData` if practical. If data generation is too heavy, update generated English and maintained Chinese files consistently and validate JSON.

- [ ] **Step 3: Run final verification**

Run:

```powershell
.\gradlew test --tests cn.dancingsnow.neoecoae.gui.StoragePriorityTest
.\gradlew compileJava
```

Expected: both commands pass.
