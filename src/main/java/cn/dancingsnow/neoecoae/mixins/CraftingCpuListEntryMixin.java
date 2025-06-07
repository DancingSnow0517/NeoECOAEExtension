package cn.dancingsnow.neoecoae.mixins;

import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.api.IOverlayTextureHolder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingStatusMenu.CraftingCpuListEntry.class)
public class CraftingCpuListEntryMixin implements IOverlayTextureHolder {

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
    private static void onReadFromPacket(RegistryFriendlyByteBuf buf, CallbackInfoReturnable<CraftingStatusMenu.CraftingCpuListEntry> cir) {
        CraftingStatusMenu.CraftingCpuListEntry entry = cir.getReturnValue();
        String texture = buf.readUtf();
        if (!texture.isEmpty()) {
            IOverlayTextureHolder.of(entry).neoecoae$setOverlay(ResourceLocation.parse(texture));
        } else {
            IOverlayTextureHolder.of(entry).neoecoae$setOverlay(null);
        }
    }

    @Inject(
        method = "writeToPacket",
        at = @At("RETURN")
    )
    private void onWriteToPacket(RegistryFriendlyByteBuf buf, CallbackInfo ci) {
        if (neoecoae$overlayTexture != null) {
            buf.writeUtf(neoecoae$overlayTexture.toString());
        } else {
            buf.writeUtf("");
        }
    }
}
