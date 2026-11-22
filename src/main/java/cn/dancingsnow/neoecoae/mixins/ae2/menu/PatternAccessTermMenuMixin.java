package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.core.sync.BasePacket;
import cn.dancingsnow.neoecoae.network.PatternAccessTerminalBatches;
import appeng.menu.implementations.PatternAccessTermMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PatternAccessTermMenu.class, remap = false)
public abstract class PatternAccessTermMenuMixin {
    @WrapOperation(method = {"sendFullUpdate", "sendIncrementalUpdate"}, at = @At(value = "INVOKE",
        target = "Lappeng/menu/implementations/PatternAccessTermMenu;sendPacketToClient(Lappeng/core/sync/BasePacket;)V"))
    private void neoecoae$sendPatternBatches(PatternAccessTermMenu menu, BasePacket packet, Operation<Void> original) {
        original.call(menu, packet);
        if (packet instanceof PatternAccessTerminalBatches update) {
            for (var continuation : update.neoecoae$continuation()) original.call(menu, continuation);
        }
    }
}
