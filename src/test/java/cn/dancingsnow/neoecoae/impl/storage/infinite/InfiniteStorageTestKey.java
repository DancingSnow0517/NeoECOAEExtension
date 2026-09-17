package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Supplies an already validated encoding without requiring a running Forge registry. */
final class InfiniteStorageTestKey extends AEKey {
    private final int id;

    InfiniteStorageTestKey(int id) {
        this.id = id;
    }

    @SuppressWarnings("unchecked")
    void cacheEncoding(SavedDataInfiniteStorageEngine engine) throws Exception {
        var field = SavedDataInfiniteStorageEngine.class.getDeclaredField("encodedKeys");
        field.setAccessible(true);
        ((Map<AEKey, CompoundTag>) field.get(engine)).put(this, toTag());
    }

    @Override
    public AEKeyType getType() {
        return null;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "benchmark:item_" + id);
        return tag;
    }

    @Override
    public Object getPrimaryKey() {
        return this;
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath("benchmark", "item_" + id);
    }

    @Override
    public void writeToPacket(FriendlyByteBuf buffer) {}

    @Override
    protected Component computeDisplayName() {
        return Component.literal("benchmark");
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
}
