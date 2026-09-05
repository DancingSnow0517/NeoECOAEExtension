package cn.dancingsnow.neoecoae.api.me;

import java.util.List;
import java.util.Objects;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

final class ECOCraftingTestKey extends AEKey {
    private static final AEKeyType TYPE = new Type();
    private final String variant;

    ECOCraftingTestKey(String variant) { this.variant = variant; }
    @Override public AEKeyType getType() { return TYPE; }
    @Override public AEKey dropSecondary() { return new ECOCraftingTestKey(""); }
    @Override public Object getPrimaryKey() { return "material"; }
    @Override public ResourceLocation getId() { return ResourceLocation.fromNamespaceAndPath("neoecoae_test", "material"); }
    @Override public CompoundTag toTag(HolderLookup.Provider registries) { throw new UnsupportedOperationException(); }
    @Override public void writeToPacket(RegistryFriendlyByteBuf data) { throw new UnsupportedOperationException(); }
    @Override protected Component computeDisplayName() { return Component.literal(variant); }
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    @Override public boolean hasComponents() { return !variant.isEmpty(); }
    @Override public boolean equals(Object other) { return other instanceof ECOCraftingTestKey key && variant.equals(key.variant); }
    @Override public int hashCode() { return Objects.hash(variant); }
    @Override public String toString() { return variant; }

    private static final class Type extends AEKeyType {
        private Type() {
            super(ResourceLocation.fromNamespaceAndPath("neoecoae_test", "crafting_keys"),
                ECOCraftingTestKey.class, Component.literal("Crafting Test Keys"));
        }
        @Override public MapCodec<? extends AEKey> codec() { throw new UnsupportedOperationException(); }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { throw new UnsupportedOperationException(); }
    }
}
