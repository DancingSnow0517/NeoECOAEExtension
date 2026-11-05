package cn.dancingsnow.neoecoae.mixins.terminalbigamount;

import appeng.menu.me.common.MEStorageMenu;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalExactAmountSync;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventoryChanges;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import java.util.Set;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MEStorageMenuExactAmountMixin {
    @Unique private final TerminalExactAmountSync neoecoae$exactAmountSync = new TerminalExactAmountSync();
    @Unique private final TerminalInventoryChanges neoecoae$inventoryChanges = new TerminalInventoryChanges();
    @Unique private KeyCounter neoecoae$comparedInventory;
    @Shadow private KeyCounter previousAvailableStacks;

    @WrapOperation(method = {"broadcastChanges()V", "m_38946_()V"}, at = @At(value = "INVOKE", target =
        "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter neoecoae$collectExactAmounts(MEStorage storage, Operation<KeyCounter> original) {
        return neoecoae$exactAmountSync.collect((MEStorageMenu) (Object) this, storage, () -> original.call(storage));
    }

    @Group(name = "neoecoae$inventoryChanges", min = 3, max = 3)
    @WrapOperation(method = "broadcastChanges", allow = 1, at = @At(value = "INVOKE", target =
        "Lappeng/api/stacks/KeyCounter;removeAll(Lappeng/api/stacks/KeyCounter;)V"))
    private void neoecoae$diffWithoutMutatingSnapshot(KeyCounter previous, KeyCounter current, Operation<Void> original) {
        neoecoae$comparedInventory = null;
        if (previous != previousAvailableStacks) {
            original.call(previous, current);
            return;
        }
        // The following two operations only need changed keys. Keep the shared snapshot read-only
        // and avoid KeyCounter mutation hooks that can alter Cell4's global big-stock bookkeeping.
        // This injection group must apply all three replacements together.
        neoecoae$inventoryChanges.between(previous, current);
        neoecoae$comparedInventory = previous;
    }

    @Group(name = "neoecoae$inventoryChanges", min = 3, max = 3)
    @WrapOperation(method = "broadcastChanges", allow = 1, at = @At(value = "INVOKE", target =
        "Lappeng/api/stacks/KeyCounter;removeZeros()V"))
    private void neoecoae$skipSnapshotZeroRemoval(KeyCounter counter, Operation<Void> original) {
        if (counter != neoecoae$comparedInventory) original.call(counter);
    }

    @Group(name = "neoecoae$inventoryChanges", min = 3, max = 3)
    @WrapOperation(method = "broadcastChanges", allow = 1, at = @At(value = "INVOKE", target =
        "Lappeng/api/stacks/KeyCounter;keySet()Ljava/util/Set;"))
    private Set<AEKey> neoecoae$reuseChangedKeys(KeyCounter counter, Operation<Set<AEKey>> original) {
        return counter == neoecoae$comparedInventory ? neoecoae$inventoryChanges.changedKeys() : original.call(counter);
    }
}
