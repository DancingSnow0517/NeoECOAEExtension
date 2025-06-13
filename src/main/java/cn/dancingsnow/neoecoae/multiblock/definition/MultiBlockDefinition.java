package cn.dancingsnow.neoecoae.multiblock.definition;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MultiBlockDefinition {
    private final List<BlockInstruction> builderActions;
    @Getter
    private final Holder<Block> owner;
    @Getter
    private final Component name;
    @Getter
    private final int expandMin;
    @Getter
    private final int expandMax;
    @Getter
    private final BiConsumer<BlockPos, Level> onFormed;

    public MultiBlockDefinition(
        List<BlockInstruction> builderActions,
        Holder<Block> owner,
        Component name,
        int expandMin,
        int expandMax,
        BiConsumer<BlockPos, Level> onFormed
    ) {
        this.builderActions = builderActions;
        this.owner = owner;
        this.name = name;
        this.expandMin = expandMin;
        this.expandMax = expandMax;
        this.onFormed = onFormed;
    }

    public static Builder builder(Holder<Block> owner) {
        return new Builder(owner);
    }

    public Level createLevel(MultiBlockContext context) {
        for (BlockInstruction builderAction : builderActions) {
            builderAction.accept(context);
        }
        if (context.isFormed()) {
            for (BlockPos pos : context.allBlocks()) {
                onFormed.accept(pos, context.getLevel());
            }
        }
        return context.getLevel();
    }

    public static class Builder {
        private final ImmutableList.Builder<BlockInstruction> builder = new ImmutableList.Builder<>();
        private final Holder<Block> owner;
        private Component name;
        private int expandMin = 1;
        private BiConsumer<BlockPos, Level> onFormed = (a, b) -> {
        };
        private int expandMax = 16;

        public Builder(Holder<Block> owner) {
            this.owner = owner;
            name = owner.value().getName();
        }

        public Builder setBlock(BlockPos pos, BlockState block) {
            builder.add(context -> context.setBlock(pos, block));
            return this;
        }

        public Builder setBlockRepeatable(BlockPos origin, Direction expandDirection, BlockState blockState) {
            builder.add(context -> {
                BlockPos.MutableBlockPos mutable = origin.mutable();
                for (int i = 0; i < context.getRepeats(); i++) {
                    mutable.set(
                        origin.getX() + expandDirection.getStepX() * i,
                        origin.getY() + expandDirection.getStepY() * i,
                        origin.getZ() + expandDirection.getStepZ() * i
                    );
                    context.setBlock(mutable.immutable(), blockState);
                }
            });
            return this;
        }

        public Builder setBlockEntityRepeatable(BlockPos origin, Direction expandDirection, BiFunction<BlockPos, BlockState, BlockEntity> sup) {
            builder.add(context -> {
                BlockPos.MutableBlockPos mutable = origin.mutable();
                for (int i = 0; i < context.getRepeats(); i++) {
                    mutable.set(
                        origin.getX() + expandDirection.getStepX() * i,
                        origin.getY() + expandDirection.getStepY() * i,
                        origin.getZ() + expandDirection.getStepZ() * i
                    );
                    context.setBlockEntity(mutable.immutable(), sup);
                }
            });
            return this;
        }

        public Builder setBlockWithRepeatShifted(BlockPos origin, Direction expandDirection, int shift, BlockState blockState) {
            builder.add(context -> {
                int i = context.getRepeats() + shift;
                BlockPos pos = new BlockPos(
                    origin.getX() + expandDirection.getStepX() * i,
                    origin.getY() + expandDirection.getStepY() * i,
                    origin.getZ() + expandDirection.getStepZ() * i
                );
                context.setBlock(pos, blockState);
            });
            return this;
        }

        public Builder setBlockEntityWithRepeatShifted(BlockPos origin, Direction expandDirection, int shift, BiFunction<BlockPos, BlockState, BlockEntity> sup) {
            builder.add(context -> {
                int i = context.getRepeats() + shift;
                BlockPos pos = new BlockPos(
                    origin.getX() + expandDirection.getStepX() * i,
                    origin.getY() + expandDirection.getStepY() * i,
                    origin.getZ() + expandDirection.getStepZ() * i
                );
                context.setBlockEntity(pos, sup);
            });
            return this;
        }

        public Builder expandMax(int expandMax) {
            this.expandMax = expandMax;
            return this;
        }

        public Builder expandMin(int expandMin) {
            this.expandMin = expandMin;
            return this;
        }

        public Builder name(Component component) {
            this.name = component;
            return this;
        }

        public Builder onFormed(BiConsumer<BlockPos, Level> levelBiConsumer) {
            this.onFormed = levelBiConsumer;
            return this;
        }

        public MultiBlockDefinition create() {
            MultiBlockDefinition def = new MultiBlockDefinition(builder.build(), owner, name, expandMin, expandMax, onFormed);
            NEMultiBlocks.DEFINITIONS.add(def);
            return def;
        }

        public MultiBlockDefinition create(Consumer<MultiBlockDefinition> onBuild) {
            MultiBlockDefinition def = new MultiBlockDefinition(builder.build(), owner, name, expandMin, expandMax, onFormed);
            onBuild.accept(def);
            return def;
        }
    }
}
