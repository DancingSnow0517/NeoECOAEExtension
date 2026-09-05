package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class StorageTestKey extends AEKey {
    private static final AEKeyType TYPE = new AEKeyType(
        ResourceLocation.fromNamespaceAndPath("eco_storage_test", "key"), StorageTestKey.class, Component.literal("Test")) {
        @Override public MapCodec<? extends AEKey> codec() {
            return Codec.STRING.fieldOf("name").xmap(StorageTestKey::new, key -> key.name);
        }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { throw new UnsupportedOperationException(); }
    };
    public static final HolderLookup.Provider REGISTRIES;
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    }

    private final String name;
    public StorageTestKey(String name) { this.name = name; }
    @Override public AEKeyType getType() { return TYPE; }
    @Override public AEKey dropSecondary() { return this; }
    @Override public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); tag.putString("name", name); return tag;
    }
    @Override public Object getPrimaryKey() { return name; }
    @Override public ResourceLocation getId() { return ResourceLocation.fromNamespaceAndPath("eco_storage_test", name); }
    @Override public void writeToPacket(RegistryFriendlyByteBuf data) { throw new UnsupportedOperationException(); }
    @Override protected Component computeDisplayName() { return Component.literal(name); }
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    @Override public boolean hasComponents() { return false; }
    @Override public int hashCode() { return name.hashCode(); }
    @Override public boolean equals(Object other) { return other instanceof StorageTestKey key && name.equals(key.name); }
}
