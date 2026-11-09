package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot.Contents;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot.SnapshotCounter;
import cn.dancingsnow.neoecoae.storage.StoragePerformance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only ECO-published snapshots are lazy; native counters and all their mutable views retain AE2 semantics. */
@Mixin(value = KeyCounter.class, remap = false)
public abstract class TerminalSnapshotCounterMixin implements SnapshotCounter {
    @Shadow @Final @Mutable private it.unimi.dsi.fastutil.objects.Reference2ObjectMap<Object, ?> lists;
    @Unique private volatile boolean neoecoae$isSnapshot;
    @Unique private volatile Contents neoecoae$snapshot;
    @Unique private volatile KeyCounter neoecoae$materialized;

    @Override public void neoecoae$useSnapshot(Contents contents) {
        neoecoae$snapshot = contents;
        neoecoae$isSnapshot = true;
    }

    @Override public boolean neoecoae$wasMaterialized() { return neoecoae$materialized != null; }

    @Override public void neoecoae$materializeSnapshot() {
        if (!neoecoae$isSnapshot) return;
        synchronized (this) {
            if (!neoecoae$isSnapshot) return;
            Contents contents = neoecoae$snapshot;
            if (contents == null) return;
            // Readers keep using immutable contents while a complete, independent native map is built.
            KeyCounter nativeCounter = new KeyCounter();
            contents.copyInto(nativeCounter);
            lists = ((TerminalSnapshotCounterMixin) (Object) nativeCounter).lists;
            neoecoae$materialized = nativeCounter;
            // The volatile marker publishes the complete native table to readers that take AE2's path.
            neoecoae$isSnapshot = false;
            neoecoae$snapshot = null;
            StoragePerformance.count(StoragePerformance.Counter.TERMINAL_MATERIALIZED);
            if (StoragePerformance.ENABLED)
                StoragePerformance.add(StoragePerformance.Counter.TERMINAL_COPIED_KEYS, contents.size());
        }
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void neoecoae$readSnapshot(AEKey key, CallbackInfoReturnable<Long> cir) {
        if (!neoecoae$isSnapshot) return;
        Contents contents = neoecoae$snapshot;
        KeyCounter materialized = neoecoae$materialized;
        cir.setReturnValue(contents != null ? contents.get(key) : materialized == null ? 0 : materialized.get(key));
    }

    @Inject(method = "size", at = @At("HEAD"), cancellable = true)
    private void neoecoae$snapshotSize(CallbackInfoReturnable<Integer> cir) {
        if (!neoecoae$isSnapshot) return;
        Contents contents = neoecoae$snapshot;
        KeyCounter materialized = neoecoae$materialized;
        cir.setReturnValue(contents != null ? contents.size() : materialized == null ? 0 : materialized.size());
    }

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void neoecoae$snapshotEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (!neoecoae$isSnapshot) return;
        Contents contents = neoecoae$snapshot;
        KeyCounter materialized = neoecoae$materialized;
        cir.setReturnValue(contents != null ? contents.size() == 0 : materialized == null || materialized.isEmpty());
    }

    @ModifyVariable(method = {"add", "set", "remove(Lappeng/api/stacks/AEKey;)J"},
        at = @At("HEAD"), argsOnly = true, index = 1)
    private AEKey neoecoae$beforeKeyMutation(AEKey key) {
        neoecoae$materializeSnapshot();
        return key;
    }

    @Inject(method = {"reset", "removeZeros", "removeEmptySubmaps"}, at = @At("HEAD"))
    private void neoecoae$beforeMutation(CallbackInfo ci) { neoecoae$materializeSnapshot(); }

    @Inject(method = {"iterator", "findFuzzy", "keySet", "getFirstEntry", "getFirstKey"}, at = @At("HEAD"))
    private void neoecoae$beforeNativeView(CallbackInfoReturnable<?> cir) { neoecoae$materializeSnapshot(); }

    @ModifyVariable(method = {"addAll", "removeAll"}, at = @At("HEAD"), argsOnly = true, index = 1)
    private KeyCounter neoecoae$beforeBulkMutation(KeyCounter source) {
        neoecoae$materializeSnapshot();
        if ((Object) source instanceof SnapshotCounter snapshot) snapshot.neoecoae$materializeSnapshot();
        return source;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void neoecoae$discardSnapshot(CallbackInfo ci) {
        if (neoecoae$isSnapshot) { neoecoae$isSnapshot = false; neoecoae$snapshot = null; }
    }
}
