package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.display.terminal.KeyCounterSnapshotAccess;
import cn.dancingsnow.neoecoae.crafting.display.terminal.KeyCounterSnapshots;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyCounter.class)
public abstract class KeyCounterSnapshotMixin implements KeyCounterSnapshotAccess {
    @Shadow @Final @Mutable private Reference2ObjectMap<Object, ?> lists;

    @Override public Reference2ObjectMap<Object, ?> neoecoae$primaryMaps() { return lists; }
    @Override public void neoecoae$primaryMaps(Reference2ObjectMap<Object, ?> maps) { lists = maps; }

    // Read methods remain untouched. Native mutable counters pay only this reference-type check on mutation.
    @ModifyExpressionValue(method = {"getSubIndex", "remove", "addAll", "removeAll", "reset", "clear",
            "removeZeros", "removeEmptySubmaps"}, at = @At(value = "FIELD", target =
            "Lappeng/api/stacks/KeyCounter;lists:Lit/unimi/dsi/fastutil/objects/Reference2ObjectMap;"))
    private Reference2ObjectMap<Object, ?> neoecoae$detachSharedGroups(Reference2ObjectMap<Object, ?> original) {
        // addAll/removeAll also read the argument's directory; only our own directory is writable.
        if (original != lists) return original;
        if (KeyCounterSnapshots.shared(original)) lists = KeyCounterSnapshots.mutableCopy((KeyCounter) (Object) this);
        return lists;
    }
}
