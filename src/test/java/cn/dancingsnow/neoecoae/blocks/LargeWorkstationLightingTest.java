package cn.dancingsnow.neoecoae.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockModel;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LargeWorkstationLightingTest {
    private static List<NEBlock<?>> members;
    private static ECOMachineCasing<NEIntegratedWorkingStationCluster> casing;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        initializeBlocks();
    }

    private static void initializeBlocks() {
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            members = List.of(
                new ECOLargeIntegratedWorkingStationInputHatch(BlockBehaviour.Properties.of()),
                new ECOLargeIntegratedWorkingStationOutputHatch(BlockBehaviour.Properties.of()),
                new ECOMachineInterface<NEIntegratedWorkingStationCluster>(BlockBehaviour.Properties.of()));
            casing = new ECOMachineCasing<>(BlockBehaviour.Properties.of().noOcclusion());
            for (int index = 0; index < members.size(); index++) {
                Registry.register(registry, "neoecoae_lighting_test:member_" + index, members.get(index));
                members.get(index).getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
            }
            Registry.register(registry, "neoecoae_lighting_test:casing", casing);
            casing.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
        } finally {
            registry.freeze();
        }
    }

    @Test
    void hiddenMembersDoNotOccludeLightAndKeepTheirCollision() {
        for (NEBlock<?> member : members) {
            BlockState unformed = member.defaultBlockState();
            assertEquals(RenderShape.MODEL, unformed.getRenderShape());
            assertEquals(15, unformed.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            assertEquals(0.2F, unformed.getShadeBrightness(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            assertFalse(unformed.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            assertHidden(unformed.setValue(NEBlock.FORMED, true));
            assertEquals(15, unformed.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        }
    }

    @Test
    void casingOcclusionFollowsItsVisibilityRatherThanFormationAlone() {
        BlockState visible = casing.defaultBlockState().setValue(NEBlock.FORMED, true);
        assertEquals(RenderShape.MODEL, visible.getRenderShape());
        assertEquals(1, visible.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        assertFalse(visible.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
        assertHidden(visible.setValue(ECOMachineCasing.INVISIBLE, true));
        assertEquals(1, visible.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    @Test
    void formedBodyKeepsNaturalAmbientOcclusion() throws Exception {
        for (String variant : List.of("off", "on")) {
            var model = BlockModel.fromString(readModel(variant).toString());
            assertTrue(model.hasAmbientOcclusion(), variant + " model must retain natural body AO");
        }
    }

    @Test
    void onlyEmissiveFacesBypassAmbientOcclusion() throws Exception {
        for (String variant : List.of("off", "on")) {
            var model = BlockModel.fromString(readModel(variant).toString());
            int emissiveFaces = 0;
            for (var element : model.getElements()) {
                for (var face : element.faces.values()) {
                    String texture = face.texture();
                    var lighting = face.faceData();
                    if (texture.equals("#light") || variant.equals("on") && texture.equals("#screen")) {
                        assertEquals(15, lighting.blockLight());
                        assertEquals(15, lighting.skyLight());
                        assertFalse(lighting.ambientOcclusion());
                        assertFalse(element.shade);
                        emissiveFaces++;
                    } else {
                        assertEquals(0, lighting.blockLight());
                        assertEquals(0, lighting.skyLight());
                        assertTrue(lighting.ambientOcclusion());
                        assertTrue(element.shade);
                    }
                }
            }
            assertEquals(variant.equals("on") ? 7 : 5, emissiveFaces);
        }
    }

    private void assertHidden(BlockState state) {
        var level = EmptyBlockGetter.INSTANCE;
        assertEquals(RenderShape.INVISIBLE, state.getRenderShape());
        assertEquals(0, state.getLightBlock(level, BlockPos.ZERO));
        assertFalse(state.isSolidRender(level, BlockPos.ZERO));
        assertTrue(state.getOcclusionShape(level, BlockPos.ZERO).isEmpty());
        assertEquals(1.0F, state.getShadeBrightness(level, BlockPos.ZERO));
        assertTrue(state.propagatesSkylightDown(level, BlockPos.ZERO));
        assertTrue(state.isCollisionShapeFullBlock(level, BlockPos.ZERO));
    }

    private JsonObject readModel(String variant) throws Exception {
        String resource = "/assets/neoecoae/models/block/large_integrated_working_station_" + variant + ".json";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
