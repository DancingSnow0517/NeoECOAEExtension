package cn.dancingsnow.neoecoae.multiblock.definition;

import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

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
        private final List<ItemStack> itemStacks = new ArrayList<>(16);
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
            boolean added = false;
            for (ItemStack stack : itemStacks) {
                if (ItemStack.isSameItemSameComponents(itemStack, stack)) {
                    if (stack.getCount() + itemStack.getCount() > stack.getMaxStackSize()) {
                        itemStack.setCount(stack.getCount() + itemStack.getCount() - stack.getMaxStackSize());
                        stack.setCount(stack.getMaxStackSize());
                    } else {
                        stack.setCount(stack.getCount() + itemStack.getCount());
                    }
                    added = true;
                    break;
                }
            }
            if (!added) {
                itemStacks.add(itemStack);
            }
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
