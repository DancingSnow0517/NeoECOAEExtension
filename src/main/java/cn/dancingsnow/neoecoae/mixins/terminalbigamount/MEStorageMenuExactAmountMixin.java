package cn.dancingsnow.neoecoae.mixins.terminalbigamount;

import appeng.menu.me.common.MEStorageMenu;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalExactAmountSync;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MEStorageMenu.class)
public abstract class MEStorageMenuExactAmountMixin {
    @Unique private final TerminalExactAmountSync neoecoae$exactAmountSync = new TerminalExactAmountSync();

    @WrapOperation(method = "broadcastChanges", at = @At(value = "INVOKE", target =
        "Lappeng/api/storage/MEStorage;getAvailableStacks()Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter neoecoae$collectExactAmounts(MEStorage storage, Operation<KeyCounter> original) {
        return neoecoae$exactAmountSync.collect((MEStorageMenu) (Object) this, () -> original.call(storage));
    }
}
