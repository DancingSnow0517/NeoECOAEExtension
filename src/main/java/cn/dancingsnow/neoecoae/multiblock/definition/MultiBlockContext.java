package cn.dancingsnow.neoecoae.multiblock.definition;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public abstract class MultiBlockContext {
    @Getter
    protected int repeats;

    public abstract void setBlock(BlockPos pos, BlockState blockState);

    public abstract void setBlockEntity(BlockPos pos, BiFunction<BlockPos, BlockState, BlockEntity> sup);

    public abstract Level getLevel();

    public abstract List<BlockPos> allBlocks();

    public abstract boolean isFormed();

    public static MultiBlockContext.DummyDelegated dummyDelegated(int repeats, TrackedDummyWorld world) {
        return new DummyDelegated(repeats, world);
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

        @Override
        public void setBlockEntity(BlockPos pos, BiFunction<BlockPos, BlockState, BlockEntity> sup) {
            BlockEntity be = sup.apply(pos, getLevel().getBlockState(pos));
            if (be instanceof NEBlockEntity<?,?>){
                ((NEBlockEntity<?, ?>) be).setFormed(formed);
            }
            be.setLevel(dummyWorld);
            dummyWorld.setBlockEntity(be);
        }

        public void addRequiredItem(ItemStack itemStack) {
            if (itemStack.isEmpty()) return;
            for (int i = 0; i < itemStacks.size(); i++) {
                RequiredItem stack = itemStacks.get(i);
                if (ItemStack.isSameItemSameComponents(itemStack, stack.stack())) {
                    itemStacks.set(i, stack.grow(itemStack.getCount()));
                    return;
                }
            }
            itemStacks.add(new RequiredItem(itemStack, itemStack.getCount()));
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
