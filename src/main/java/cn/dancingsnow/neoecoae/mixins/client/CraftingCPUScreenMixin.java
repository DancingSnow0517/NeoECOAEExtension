package cn.dancingsnow.neoecoae.mixins.client;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps controls available for an ECO job even when its accounting table is temporarily empty. */
@Mixin(CraftingCPUScreen.class)
public abstract class CraftingCPUScreenMixin {
    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z")
    )
    private boolean neoecoae$jobCanBeControlled(List<?> entries, Operation<Boolean> original) {
        if (!original.call(entries)) return false;

        var screen = (CraftingCPUScreen<?>) (Object) this;
        if (!(screen.getMenu() instanceof CraftingStatusMenu menu)) return true;

        int selectedSerial = menu.getSelectedCpuSerial();
        return menu.cpuList.cpus().stream().noneMatch(cpu ->
            cpu.serial() == selectedSerial
                && cpu.currentJob() != null
                && IOverlayTextureHolder.of(cpu).neoecoae$getOverlay() != null);
    }
}
