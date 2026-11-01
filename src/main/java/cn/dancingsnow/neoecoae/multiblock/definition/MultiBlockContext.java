package cn.dancingsnow.neoecoae.multiblock.definition;

import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public abstract class MultiBlockContext {
    @Getter
    protected int repeats;

    public abstract void setBlock(BlockPos pos, BlockState blockState);

    public abstract Level getLevel();

    public abstract List<BlockPos> allBlocks();

    public abstract boolean isFormed();

    /** Use the state's own property instance; identical property names can have different identities. */
    public static BlockState formedPreviewState(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty formed
                && formed.getName().equals("formed")) {
                return state.setValue(formed, true);
            }
        }
        return state;
    }

    public static MultiBlockContext.DummyDelegated dummyDelegated(int repeats, TrackedDummyWorld world) {
        return new DummyDelegated(repeats, world);
    }

    /** Collect materials without retaining positions or allocating a preview world. */
    public static class MaterialsOnly extends MultiBlockContext {
        private final List<RequiredItem> itemStacks = new ArrayList<>(16);

        public MaterialsOnly(int repeats) {
            if (repeats < 0) throw new IllegalArgumentException("Negative repeat count");
            this.repeats = repeats;
        }

        @Override
        public void setBlock(BlockPos pos, BlockState blockState) {
            collectRequiredItem(itemStacks, blockState.getBlock().asItem().getDefaultInstance());
        }

        @Override
        public Level getLevel() {
            throw new UnsupportedOperationException("Material collection has no preview world");
        }

        @Override
        public List<BlockPos> allBlocks() { return List.of(); }

        @Override
        public boolean isFormed() { return false; }

        public List<RequiredItem> getRequiredItems() { return List.copyOf(itemStacks); }
    }

    private static void collectRequiredItem(List<RequiredItem> items, ItemStack itemStack) {
        if (itemStack.isEmpty()) return;
        for (int i = 0; i < items.size(); i++) {
            RequiredItem stack = items.get(i);
            if (ItemStack.isSameItemSameTags(itemStack, stack.stack())) {
                items.set(i, stack.grow(itemStack.getCount()));
                return;
            }
        }
        items.add(new RequiredItem(itemStack, itemStack.getCount()));
    }

    public static class DummyDelegated extends MultiBlockContext {
        private final TrackedDummyWorld dummyWorld;
        private final List<RequiredItem> itemStacks = new ArrayList<>(16);
        private final List<BlockPos> posList = new ArrayList<>();
        @Getter
        private int yMax = 0;
        @Setter
        @Getter
        private boolean formed = false;

        public DummyDelegated(int repeats, TrackedDummyWorld dummyWorld) {
            this.dummyWorld = dummyWorld;
            this.repeats = repeats;
        }

        @Override
        public void setBlock(BlockPos pos, BlockState blockState) {
            ItemStack item = blockState.getBlock().asItem().getDefaultInstance();
            addRequiredItem(item);
            if (pos.getY() < 0) return;
            posList.add(pos);
            yMax = Math.max(pos.getY(), yMax);
            dummyWorld.addBlock(pos, new BlockInfo(blockState));
        }

        public void addRequiredItem(ItemStack itemStack) {
            collectRequiredItem(itemStacks, itemStack);
        }

        @Override
        public List<BlockPos> allBlocks() {
            return posList;
        }

        @Override
        public Level getLevel() {
            return dummyWorld;
        }

        public List<RequiredItem> getRequiredItems() {
            return itemStacks;
        }
    }
}
