package cn.dancingsnow.neoecoae.multiblock.definition;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public abstract class MultiBlockContext {
    @Getter
    protected int repeats;

    public abstract void setBlock(BlockPos pos, BlockState blockState);

    public abstract void setBlockEntity(BlockPos pos, BiFunction<BlockPos, BlockState, BlockEntity> sup);

    public abstract Level getLevel();

    public abstract List<BlockPos> allBlocks();

    public abstract boolean isFormed();

    public static MultiBlockContext.DummyDelegated dummyDelegated(int repeats, Level world) {
        return new DummyDelegated(repeats, world);
    }

    public static class DummyDelegated extends MultiBlockContext {
        private final Level dummyWorld;
        private final List<ItemStack> itemStacks = new ArrayList<>(16);
        private final List<BlockPos> posList = new ArrayList<>();

        @Getter
        private int yMax = 0;

        @Setter
        @Getter
        private boolean formed = false;

        public DummyDelegated(int repeats, Level dummyWorld) {
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
            dummyWorld.setBlock(pos, blockState, Block.UPDATE_CLIENTS);
        }

        @Override
        public void setBlockEntity(BlockPos pos, BiFunction<BlockPos, BlockState, BlockEntity> sup) {
            BlockEntity be = sup.apply(pos, getLevel().getBlockState(pos));
            if (be instanceof NEBlockEntity<?, ?>) {
                ((NEBlockEntity<?, ?>) be).setFormed(formed);
            }
            be.setLevel(dummyWorld);
            dummyWorld.setBlockEntity(be);
        }

        public void addRequiredItem(ItemStack itemStack) {
            if (itemStack.isEmpty()) return;
            for (ItemStack stack : itemStacks) {
                if (ItemStack.isSameItemSameTags(itemStack, stack)) {
                    stack.grow(itemStack.getCount());
                    return;
                }
            }
            itemStacks.add(itemStack.copy());
        }

        @Override
        public List<BlockPos> allBlocks() {
            return posList;
        }

        @Override
        public Level getLevel() {
            return dummyWorld;
        }

        public List<ItemStack> getRequiredItems() {
            return itemStacks;
        }
    }
}
