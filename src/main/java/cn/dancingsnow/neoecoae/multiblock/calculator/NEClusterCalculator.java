package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.me.cluster.MBCalculator;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Predicate;

public abstract class NEClusterCalculator<C extends NECluster<C>> extends MBCalculator<NEBlockEntity<C, ?>, C> {
    public NEClusterCalculator(NEBlockEntity<C, ?> t) {
        super(t);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateBlockEntities(C c, ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            NEBlockEntity<C, ?> blockEntity = (NEBlockEntity<C, ?>) level.getBlockEntity(blockPos);
            if (blockEntity == null) {
                throw new IllegalStateException("Expected NEBlockEntity at %s, but got null.".formatted(blockPos));
            }
            blockEntity.updateCluster(c);
            c.addBlockEntity(blockEntity);
        }

        c.updateFormed(true);
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return te instanceof NEBlockEntity<?, ?>;
    }

    @FunctionalInterface
    public interface Factory<C extends NECluster<C>> {
        NEClusterCalculator<C> create(NEBlockEntity<C, ?> blockEntity);
    }

    private <T> boolean validateBlockAt(Level level, BlockPos pos) {
        return false;
    }
}
