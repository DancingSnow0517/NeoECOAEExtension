package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.menu.me.crafting.CraftingStatusMenu;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.api.ECOCraftingStatusSuspension;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftingCpuControl;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Keeps controls available for an ECO job even when its accounting table is temporarily empty. */
@Mixin(value = CraftingCPUScreen.class, remap = false)
public abstract class CraftingCpuScreenMixin extends AEBaseScreen<CraftingCPUMenu> {
    @Shadow private CraftingStatus status;
    @Unique private ToggleButton neoecoae$pause;
    @Unique private boolean neoecoae$warnedAboutMissingKey;

    @WrapOperation(method = "postUpdate", at = @At(value = "INVOKE",
        target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object neoecoae$putResolvedStatusEntry(Map<?, ?> entries, Object serial, Object value,
            Operation<Object> original) {
        // Check the merged entry: a normal delta omits its key and gets it from the existing row.
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

    protected CraftingCpuScreenMixin(CraftingCPUMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neoecoae$addPause(CraftingCPUMenu menu, Inventory inventory, Component title, ScreenStyle style,
            CallbackInfo ci) {
        neoecoae$pause = addToLeftToolbar(new ToggleButton(Icon.REDSTONE_LOW, Icon.REDSTONE_HIGH,
            Component.translatable("gui.neoecoae.computation.resume"),
            Component.translatable("gui.neoecoae.computation.pause"),
            ignored -> ((ECOCraftingCpuControl) menu).neoecoae$toggleSuspended()));
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$updatePause(CallbackInfo ci) {
        neoecoae$pause.visible = neoecoae$hasEcoJob();
        neoecoae$pause.setState(status instanceof ECOCraftingStatusSuspension state && state.neoecoae$isSuspended());
    }
    @WrapOperation(
        method = {"render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"},
        at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z")
    )
    private boolean neoecoae$jobCanBeControlled(List<?> entries, Operation<Boolean> original) {
        if (!original.call(entries)) return false;

        return !neoecoae$hasEcoJob();
    }

    @Unique
    private boolean neoecoae$hasEcoJob() {
        if (!(getMenu() instanceof CraftingStatusMenu menu)) return false;

        int selectedSerial = menu.getSelectedCpuSerial();
        return menu.cpuList.cpus().stream().anyMatch(cpu ->
            cpu.serial() == selectedSerial
                && cpu.currentJob() != null
                && IOverlayTextureHolder.of(cpu).neoecoae$getOverlay() != null);
    }
}
