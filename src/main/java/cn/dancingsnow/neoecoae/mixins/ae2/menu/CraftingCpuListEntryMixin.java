package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftingCpuListEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingStatusMenu.CraftingCpuListEntry.class)
public class CraftingCpuListEntryMixin implements IOverlayTextureHolder, ECOCraftingCpuListEntry {

    @Unique
    private boolean neoecoae$ecoCpu;

    @Override
    public boolean neoecoae$isEcoCpu() {
        return neoecoae$ecoCpu;
    }

    @Override
    public void neoecoae$setEcoCpu(boolean ecoCpu) {
        neoecoae$ecoCpu = ecoCpu;
    }

    @Unique
    private ResourceLocation neoecoae$overlayTexture = null;

    @Override
    public @Nullable ResourceLocation neoecoae$getOverlay() {
        return neoecoae$overlayTexture;
    }

    @Override
    public void neoecoae$setOverlay(@Nullable ResourceLocation overlay) {
        neoecoae$overlayTexture = overlay;
    }

    @Inject(
        method = "readFromPacket",
        at = @At("RETURN")
    )
    private static void onReadFromPacket(FriendlyByteBuf buf, CallbackInfoReturnable<CraftingStatusMenu.CraftingCpuListEntry> cir) {
        CraftingStatusMenu.CraftingCpuListEntry entry = cir.getReturnValue();
        String texture = buf.readUtf();
        if (!texture.isEmpty()) {
            IOverlayTextureHolder.of(entry).neoecoae$setOverlay(new ResourceLocation(texture));
        } else {
            IOverlayTextureHolder.of(entry).neoecoae$setOverlay(null);
        }
        ECOCraftingCpuListEntry.of(entry).neoecoae$setEcoCpu(buf.readBoolean());
    }

    @Inject(
        method = "writeToPacket",
        at = @At("RETURN")
    )
    private void onWriteToPacket(FriendlyByteBuf buf, CallbackInfo ci) {
        if (neoecoae$overlayTexture != null) {
            buf.writeUtf(neoecoae$overlayTexture.toString());
        } else {
            buf.writeUtf("");
        }
        buf.writeBoolean(neoecoae$ecoCpu);
    }
}
