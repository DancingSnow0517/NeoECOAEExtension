package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Minimal {@link AEKey} for fast-path tests that only need distinct, comparable keys. Nothing here touches a
 * registry, so these tests run without bootstrapping Minecraft.
 */
final class FastPathTestKey extends AEKey {
    private static final AEKeyType TYPE = new FastPathTestKeyType();

    private final String name;

    private FastPathTestKey(String name) {
        this.name = name;
    }

    static FastPathTestKey of(String name) {
        return new FastPathTestKey(name);
    }

    @Override
    public AEKeyType getType() {
        return TYPE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        return tag;
    }

    @Override
    public Object getPrimaryKey() {
        return name;
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath("neoecoae_test", name);
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Component computeDisplayName() {
        return Component.literal(name);
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FastPathTestKey other && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    private static final class FastPathTestKeyType extends AEKeyType {
        private FastPathTestKeyType() {
            super(
                ResourceLocation.fromNamespaceAndPath("neoecoae_test", "fastpath_keys"),
                FastPathTestKey.class,
                Component.literal("Fast Path Test Keys")
            );
        }

        @Override
        public MapCodec<? extends AEKey> codec() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
            throw new UnsupportedOperationException();
        }
    }
}
