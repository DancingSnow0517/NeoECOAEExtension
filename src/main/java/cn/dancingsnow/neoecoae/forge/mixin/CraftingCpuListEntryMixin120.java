package cn.dancingsnow.neoecoae.forge.mixin;

import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingStatusMenu.CraftingCpuListEntry.class, remap = false)
public abstract class CraftingCpuListEntryMixin120 implements IOverlayTextureHolder {
    @Unique
    private ResourceLocation neoecoae$overlayTexture;

    @Override
    public @Nullable ResourceLocation neoecoae$getOverlay() {
        return neoecoae$overlayTexture;
    }

    @Override
    public void neoecoae$setOverlay(@Nullable ResourceLocation overlay) {
        this.neoecoae$overlayTexture = overlay;
    }

    @Inject(method = "readFromPacket", at = @At("RETURN"))
    private static void neoecoae$readOverlay(FriendlyByteBuf data, CallbackInfoReturnable<CraftingStatusMenu.CraftingCpuListEntry> cir) {
        String texture = data.readUtf();
        IOverlayTextureHolder.of(cir.getReturnValue()).neoecoae$setOverlay(texture.isEmpty() ? null : ResourceLocation.parse(texture));
    }

    @Inject(method = "writeToPacket", at = @At("RETURN"))
    private void neoecoae$writeOverlay(FriendlyByteBuf data, CallbackInfo ci) {
        data.writeUtf(neoecoae$overlayTexture == null ? "" : neoecoae$overlayTexture.toString());
    }
}
