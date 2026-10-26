package cn.dancingsnow.neoecoae.impl.storage.bulk;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Stable component-aware keys, also used to read historical ECO bulk data. */
public final class BulkItemKeys {
    private BulkItemKeys() {}
    public static CompoundTag write(AEItemKey key) {
        var tag = new CompoundTag();
        tag.putString("item", key.getId().toString());
        tag.put("components", DataComponentPatch.CODEC.encodeStart(NbtOps.INSTANCE,
            key.getReadOnlyStack().getComponentsPatch()).getOrThrow());
        return tag;
    }
    public static @Nullable AEItemKey read(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("item"));
        var item = id == null ? null : BuiltInRegistries.ITEM.getHolder(id).orElse(null);
        if (item == null) return null;
        var patch = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("components")).result().orElse(null);
        return patch == null ? null : AEItemKey.of(new ItemStack(item, 1, patch));
    }
}
