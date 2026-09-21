package cn.dancingsnow.neoecoae.mixins.client.ae2;

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
public abstract class CraftingCpuScreenMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "render", at = @At("TAIL"))
    private void neoecoae$renderParent(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var screen = (CraftingCPUScreen<?>) (Object) this;
        if (!(screen.getMenu() instanceof cn.dancingsnow.neoecoae.api.me.menu.ECOBigOrderStatusHost host)) return;
        var progress = host.neoecoae$getBigOrderProgress();
        if (progress == null) return;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int width = Math.min(270, screen.width - 12);
        int x = screen.width - width - 6;
        int y = 6;
        var lines = java.util.List.of(
            "BigInt · " + net.minecraft.network.chat.Component.translatable(
                "gui.neoecoae.big_order." + progress.state().name().toLowerCase(java.util.Locale.ROOT)).getString(),
            net.minecraft.network.chat.Component.translatable("gui.neoecoae.big_order.total",
                neoecoae$amount(progress.requested())).getString(),
            net.minecraft.network.chat.Component.translatable("gui.neoecoae.big_order.done",
                neoecoae$amount(progress.completed())).getString(),
            net.minecraft.network.chat.Component.translatable("gui.neoecoae.big_order.remaining",
                neoecoae$amount(progress.remaining())).getString(),
            net.minecraft.network.chat.Component.translatable("gui.neoecoae.big_order.child",
                neoecoae$amount(java.math.BigInteger.valueOf(progress.childTarget())),
                neoecoae$amount(java.math.BigInteger.valueOf(progress.childRemaining()))).getString(),
            progress.waitingReason().isEmpty() ? "" : net.minecraft.network.chat.Component.translatable(
                "gui.neoecoae.big_order.reason." + progress.waitingReason().toLowerCase(java.util.Locale.ROOT)).getString());
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x, y, x + width, y + lines.size() * 11 + 8, 0xE020252B);
        for (int i = 0; i < lines.size(); i++)
            graphics.drawString(font, font.plainSubstrByWidth(lines.get(i), width - 8), x + 4, y + 4 + i * 11,
                0xFFFFFF, false);
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + lines.size() * 11 + 8)
            graphics.renderComponentTooltip(font, lines.stream().filter(line -> !line.isEmpty())
                .map(net.minecraft.network.chat.Component::literal)
                .map(line -> (net.minecraft.network.chat.Component) line).toList(), mouseX, mouseY);
        graphics.pose().popPose();
    }

    @org.spongepowered.asm.mixin.Unique
    private static String neoecoae$amount(java.math.BigInteger amount) {
        return cn.dancingsnow.neoecoae.crafting.display.format.ExactAmountFormatter.full(
            cn.dancingsnow.neoecoae.crafting.amount.ExactAmount.finite(amount));
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
        return menu.cpuList.cpus().stream().noneMatch(cpu ->
            cpu.serial() == selectedSerial
                && cpu.currentJob() != null
                && IOverlayTextureHolder.of(cpu).neoecoae$getOverlay() != null);
    }
}
