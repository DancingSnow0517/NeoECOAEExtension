package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationNetworkSwitch;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockLightEmissionTest {
    private static List<Block> blocks;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            blocks = List.of(
                new ECODriveBlock(BlockBehaviour.Properties.of().noOcclusion()),
                new ECOComputationDrive(BlockBehaviour.Properties.of().noOcclusion()),
                new ECOComputationNetworkSwitch(BlockBehaviour.Properties.of()),
                new ECOComputationDrive(BlockBehaviour.Properties.of().lightLevel(state -> 7)));
            for (int index = 0; index < blocks.size(); index++) {
                Block block = blocks.get(index);
                Registry.register(registry, "neoecoae_light_emission_test:block_" + index, block);
                block.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
            }
        } finally {
            registry.freeze();
        }
    }

    @Test
    void formationDoesNotTurnMachineBodiesIntoLightSources() {
        for (int index = 0; index < 3; index++) {
            Block block = blocks.get(index);
            BooleanProperty formed = block instanceof NENetworkSwitchBlock<?> ? NENetworkSwitchBlock.FORMED : NEBlock.FORMED;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                assertEquals(0, state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), state.toString());
                assertEquals(0, state.setValue(formed, true).getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            }
        }
    }

    @Test
    void explicitlyConfiguredLightEmissionIsPreserved() {
        for (BlockState state : blocks.getLast().getStateDefinition().getPossibleStates()) {
            assertEquals(7, state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        }
    }
}
