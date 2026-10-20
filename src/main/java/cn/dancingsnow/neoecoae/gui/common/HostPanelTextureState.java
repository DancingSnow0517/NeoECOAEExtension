package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider;
import cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider.Part;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Optional host artwork copied into the server's panel snapshot, without encoding a tier instance. */
public record HostPanelTextureState(@Nullable ResourceLocation tier, @Nullable ResourceLocation marker,
    Map<String, ResourceLocation> variants) {
    public static final HostPanelTextureState EMPTY = new HostPanelTextureState(null, null);

    public enum Panel { STORAGE, COMPUTATION, CRAFTING }

    public HostPanelTextureState {
        variants = Map.copyOf(variants);
    }

    public HostPanelTextureState(@Nullable ResourceLocation tier, @Nullable ResourceLocation marker) {
        this(tier, marker, Map.of());
    }

    /** Legacy item-only callers cannot provide placed properties. Host UIs use the state overload. */
    @Deprecated
    public static HostPanelTextureState capture(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item ? capture(item.getBlock().defaultBlockState()) : EMPTY;
    }

    public static HostPanelTextureState capture(@Nullable BlockState state) {
        return capture(state, Panel.STORAGE);
    }

    public static HostPanelTextureState capture(@Nullable BlockState state, Panel panel) {
        if (state == null || !(state.getBlock() instanceof IHostPanelTextureProvider)) return EMPTY;
        Part tier = switch (panel) {
            case STORAGE -> Part.STORAGE_TIER;
            case COMPUTATION -> Part.COMPUTATION_TIER;
            case CRAFTING -> Part.CRAFTING_TIER;
        };
        Part marker = switch (panel) {
            case STORAGE -> Part.STORAGE_MARKER;
            case COMPUTATION -> Part.COMPUTATION_MARKER;
            case CRAFTING -> Part.CRAFTING_MARKER;
        };
        Map<String, ResourceLocation> variants = new HashMap<>();
        if (panel == Panel.COMPUTATION) {
            captureVariants(variants, state, Part.COMPUTATION_STATUS, 6, false);
            captureVariants(variants, state, Part.COMPUTATION_SWITCH, 3, true);
        } else if (panel == Panel.CRAFTING) {
            captureVariants(variants, state, Part.CRAFTING_MODE, 2, true);
        }
        return new HostPanelTextureState(IHostPanelTextureProvider.texture(state, tier),
            IHostPanelTextureProvider.texture(state, marker), variants);
    }

    private static void captureVariants(Map<String, ResourceLocation> images, BlockState state, Part part,
        int count, boolean withHover) {
        for (int index = 0; index < count; index++) {
            for (int active = 0; active < 2; active++) {
                for (int hover = 0; hover < (withHover ? 2 : 1); hover++) {
                    var image = IHostPanelTextureProvider.texture(state, part, index, active != 0, hover != 0);
                    if (image != null) images.put(key(part, index, active != 0, hover != 0), image);
                }
            }
        }
    }

    @Nullable
    public ResourceLocation texture(Part part, int index, boolean active, boolean hovered) {
        return variants.get(key(part, index, active, hovered));
    }

    private static String key(Part part, int index, boolean active, boolean hovered) {
        return part.name() + ":" + index + ":" + active + ":" + hovered;
    }

    public void write(CompoundTag tag) {
        if (tier != null) tag.putString("hostTierTexture", tier.toString());
        else tag.remove("hostTierTexture");
        if (marker != null) tag.putString("hostMarkerTexture", marker.toString());
        else tag.remove("hostMarkerTexture");
        CompoundTag images = new CompoundTag();
        variants.forEach((key, image) -> images.putString(key, image.toString()));
        if (!images.isEmpty()) tag.put("hostPanelVariants", images);
        else tag.remove("hostPanelVariants");
    }

    public static HostPanelTextureState read(CompoundTag tag) {
        Map<String, ResourceLocation> images = new HashMap<>();
        CompoundTag variants = tag.getCompound("hostPanelVariants");
        for (String key : variants.getAllKeys()) {
            var image = readTexture(variants, key);
            if (image != null) images.put(key, image);
        }
        return new HostPanelTextureState(readTexture(tag, "hostTierTexture"), readTexture(tag, "hostMarkerTexture"), images);
    }

    @Nullable
    private static ResourceLocation readTexture(CompoundTag tag, String key) {
        String value = tag.getString(key);
        return value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }
}
