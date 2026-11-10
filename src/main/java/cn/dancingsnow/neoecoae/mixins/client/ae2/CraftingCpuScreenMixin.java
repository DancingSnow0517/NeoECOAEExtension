package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.menu.me.crafting.CraftingStatusMenu;
import appeng.menu.me.crafting.CraftingStatusEntry;
import org.spongepowered.asm.mixin.Unique;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCpuListPageHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps controls available for an ECO job even when its accounting table is temporarily empty. */
@Mixin(CraftingCPUScreen.class)
public abstract class CraftingCpuScreenMixin {
    @Unique private boolean neoecoae$warnedAboutMissingKey;

    @WrapOperation(method = "postUpdate", at = @At(value = "INVOKE",
        target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object neoecoae$putResolvedStatusEntry(Map<?, ?> entries, Object serial, Object value,
            Operation<Object> original) {
        // A normal delta omits its key and resolves it from the existing row before this call.
        if (value instanceof CraftingStatusEntry entry && entry.getWhat() == null) {
            if (!neoecoae$warnedAboutMissingKey) {
                neoecoae$warnedAboutMissingKey = true;
                org.slf4j.LoggerFactory.getLogger("neoecoae").warn(
                    "Skipped unresolved crafting status entry: serial={}, stored={}, active={}, pending={}",
                    entry.getSerial(), entry.getStoredAmount(), entry.getActiveAmount(), entry.getPendingAmount());
            }
            return entries.get(serial);
        }
        return original.call(entries, serial, value);
    }
    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z")
    )
    private boolean neoecoae$jobCanBeControlled(List<?> entries, Operation<Boolean> original) {
        if (!original.call(entries)) return false;

        var screen = (CraftingCPUScreen<?>) (Object) this;
        if (!(screen.getMenu() instanceof CraftingStatusMenu menu)) return true;

        int selectedSerial = menu.getSelectedCpuSerial();
        if (menu instanceof ECOCpuListPageHost host) {
            var selected = host.neoecoae$getCpuPage().selected();
            return selected == null || selected.serial() != selectedSerial || selected.currentJob() == null
                || IOverlayTextureHolder.of(selected).neoecoae$getOverlay() == null;
        }
        return menu.cpuList.cpus().stream().noneMatch(cpu ->
            cpu.serial() == selectedSerial
                && cpu.currentJob() != null
                && IOverlayTextureHolder.of(cpu).neoecoae$getOverlay() != null);
    }
}
