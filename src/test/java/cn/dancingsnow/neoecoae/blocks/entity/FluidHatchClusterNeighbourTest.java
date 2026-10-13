package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FluidHatchClusterNeighbourTest {
    private static final BlockPos HATCH_POS = BlockPos.ZERO;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void tickSkipsMemberOfItsOwnClusterAndProbesEveryOtherSide() {
        var cluster = mock(NECraftingCluster.class);
        var hatch = newHatch(cluster);
        var level = mock(Level.class);
        var member = mock(BlockEntity.class);
        Direction memberSide = Direction.NORTH;
        BlockPos memberPos = HATCH_POS.relative(memberSide);
        when(level.getBlockEntity(memberPos)).thenReturn(member);
        when(cluster.containsBlockEntity(member)).thenReturn(true);

        hatch.tick(level, HATCH_POS, Blocks.AIR.defaultBlockState());

        verify(level, never()).getCapability(
            eq(Capabilities.FluidHandler.BLOCK), eq(memberPos), any(Direction.class));
        for (Direction face : Direction.values()) {
            if (face == memberSide) {
                continue;
            }
            verify(level).getCapability(
                eq(Capabilities.FluidHandler.BLOCK), eq(HATCH_POS.relative(face)), eq(face.getOpposite()));
        }
    }

    // The parameter must not be called "cluster": inside the anonymous subclass that simple name resolves to
    // the inherited protected field NEBlockEntity.cluster (null), not to the captured parameter.
    private static ECOFluidHatchBlockEntity<NECraftingCluster> newHatch(NECraftingCluster stubbedCluster) {
        var type = mock(BlockEntityType.class);
        when(type.isValid(any())).thenReturn(true);
        return new ECOFluidHatchBlockEntity<>(
            type,
            HATCH_POS,
            Blocks.AIR.defaultBlockState(),
            NECraftingClusterCalculator::new,
            ECOFluidHatchBlockEntity.CRAFTING_INPUT) {
            @Override
            protected IManagedGridNode createMainNode() {
                return mock(IManagedGridNode.class, RETURNS_SELF);
            }

            @Override
            public void setChanged() {}

            @Override
            public void markForUpdate() {}

            @Override
            public NECraftingCluster getCluster() {
                return stubbedCluster;
            }
        };
    }
}
