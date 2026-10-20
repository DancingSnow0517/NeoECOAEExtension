package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider;
import cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider.Part;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Optional host artwork copied into the server's panel snapshot, without encoding a tier instance. */
public record HostPanelTextureState(@Nullable ResourceLocation tier, @Nullable ResourceLocation marker) {
    public static final HostPanelTextureState EMPTY = new HostPanelTextureState(null, null);

    public static HostPanelTextureState capture(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return EMPTY;
        var state = item.getBlock().defaultBlockState();
        return new HostPanelTextureState(IHostPanelTextureProvider.texture(state, Part.STORAGE_TIER),
            IHostPanelTextureProvider.texture(state, Part.STORAGE_MARKER));
    }

    public void write(CompoundTag tag) {
        if (tier != null) tag.putString("hostTierTexture", tier.toString());
        if (marker != null) tag.putString("hostMarkerTexture", marker.toString());
    }

    public static HostPanelTextureState read(CompoundTag tag) {
        return new HostPanelTextureState(readTexture(tag, "hostTierTexture"), readTexture(tag, "hostMarkerTexture"));
    }

    @Nullable
    private static ResourceLocation readTexture(CompoundTag tag, String key) {
        String value = tag.getString(key);
        return value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }
}
