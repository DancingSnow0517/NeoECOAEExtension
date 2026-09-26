package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import cn.dancingsnow.neoecoae.network.ECOForceCraftStartFlagC2SPacket;

/** Formats AE2's native CPU status without changing confirmation-screen planning. */
@Mixin(value = CraftConfirmScreen.class, priority = 1100)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> {
    @Shadow private Button start;
    @Shadow private Button selectCPU;

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void neoecoae$enableForceStart(CallbackInfo ci) {
        var plan = getMenu().getPlan();
        if (plan != null && plan.isSimulation() && Screen.hasShiftDown()) {
            start.active = !getMenu().hasNoCPU();
            start.setMessage(Component.translatable("gui.neoecoae.force_start"));
            selectCPU.active = true;
        } else {
            start.setMessage(GuiText.Start.text());
        }
    }

    // Carry the intent and submission together, before EAEP sends its separate flag packet.
    // The server selects EAEP's implementation when installed, including from the ECO report screen.
    @Inject(method = "start", at = @At("HEAD"), cancellable = true, order = 500)
    private void neoecoae$submitWithForceIntent(CallbackInfo ci) {
        if (start.active) {
            var plan = getMenu().getPlan();
            PacketDistributor.sendToServer(new ECOForceCraftStartFlagC2SPacket(getMenu().containerId,
                Screen.hasShiftDown() && plan != null && plan.isSimulation()));
        }
        ci.cancel();
    }

    protected CraftConfirmScreenMixin(CraftConfirmMenu menu,
                                      Inventory playerInventory,
                                      Component title,
                                      ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @WrapOperation(
        method = "updateBeforeRender",
        at = @org.spongepowered.asm.mixin.injection.At(
            value = "INVOKE",
            target = "Lappeng/core/localization/GuiText;text([Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;",
            ordinal = 1
        )
    )
    private MutableComponent neoecoae$formatCpuStatus(
            GuiText text, Object[] args, Operation<MutableComponent> original) {
        if (text != GuiText.ConfirmCraftCpuStatus || args.length < 2
                || !(args[0] instanceof Number storage)
                || !(args[1] instanceof Number coProcessors)) {
            return original.call(text, args);
        }

        return original.call(text, new Object[] {
            Component.literal(NEByteFormatter.formatCpuStorage(storage.longValue())),
            Component.literal(NEByteFormatter.formatCpuCoProcessors(coProcessors.longValue()))
        });
    }
}
