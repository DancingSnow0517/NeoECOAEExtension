package cn.dancingsnow.neoecoae.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
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
        registry.unfreeze(false);
        try {
            members = List.of(
                new ECOLargeIntegratedWorkingStationInputHatch(BlockBehaviour.Properties.of().setId(blockKey("neoecoae_lighting_test:member_0"))),
                new ECOLargeIntegratedWorkingStationOutputHatch(BlockBehaviour.Properties.of().setId(blockKey("neoecoae_lighting_test:member_1"))),
                new ECOMachineInterface<NEIntegratedWorkingStationCluster>(BlockBehaviour.Properties.of().setId(blockKey("neoecoae_lighting_test:member_2"))));
            casing = new ECOMachineCasing<>(BlockBehaviour.Properties.of().setId(blockKey("neoecoae_lighting_test:casing")).noOcclusion());
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
            assertEquals(15, unformed.getLightDampening());
            assertEquals(0.2F, unformed.getShadeBrightness(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            assertFalse(unformed.propagatesSkylightDown());
            assertHidden(unformed.setValue(NEBlock.FORMED, true));
            assertEquals(15, unformed.getLightDampening());
        }
    }

    @Test
    void casingOcclusionFollowsItsVisibilityRatherThanFormationAlone() {
        BlockState visible = casing.defaultBlockState().setValue(NEBlock.FORMED, true);
        assertEquals(RenderShape.MODEL, visible.getRenderShape());
        assertEquals(1, visible.getLightDampening());
        // 26.1 port: vanilla now caches an empty occlusion shape for every noOcclusion() block
        // (BlockStateBase#initCache), so the visible casing's occlusion shape is no longer observable here.
        assertHidden(visible.setValue(ECOMachineCasing.INVISIBLE, true));
        assertEquals(1, visible.getLightDampening());
    }

    @Test
    void formedBodyKeepsNaturalAmbientOcclusion() throws Exception {
        for (String variant : List.of("off", "on", "off_smart", "on_smart")) {
            var model = CuboidModel.fromStream(new java.io.StringReader(readModel(variant).toString()));
            assertTrue(!Boolean.FALSE.equals(model.ambientOcclusion()), variant + " model must retain natural body AO");
        }
    }

    @Test
    void onlyEmissiveFacesBypassAmbientOcclusion() throws Exception {
        for (String variant : List.of("off", "on", "off_smart", "on_smart")) {
            var model = CuboidModel.fromStream(new java.io.StringReader(readModel(variant).toString()));
            int emissiveFaces = 0;
            for (var element : ((UnbakedCuboidGeometry) model.geometry()).elements()) {
                for (var face : element.faces().values()) {
                    String texture = face.texture();
                    var lighting = face.faceData();
                    if (texture.startsWith("#light") || texture.startsWith("#screen_") || texture.equals("#cooling_screen")) {
                        assertEquals(15, lighting.lightEmission());
                        assertFalse(lighting.ambientOcclusion());
                        assertFalse(element.shade());
                        emissiveFaces++;
                    } else {
                        assertEquals(0, lighting.lightEmission());
                        assertTrue(lighting.ambientOcclusion());
                        assertTrue(element.shade());
                    }
                }
            }
            assertEquals(15 + (variant.startsWith("on") ? 6 : 0) + (variant.endsWith("_smart") ? 1 : 0), emissiveFaces);
        }
    }

    private void assertHidden(BlockState state) {
        var level = EmptyBlockGetter.INSTANCE;
        assertEquals(RenderShape.INVISIBLE, state.getRenderShape());
        assertEquals(0, state.getLightDampening());
        assertFalse(state.isSolidRender());
        assertTrue(state.getOcclusionShape().isEmpty());
        assertEquals(1.0F, state.getShadeBrightness(level, BlockPos.ZERO));
        assertTrue(state.propagatesSkylightDown());
        assertTrue(state.isCollisionShapeFullBlock(level, BlockPos.ZERO));
    }

    private JsonObject readModel(String variant) throws Exception {
        String resource = "/assets/neoecoae/models/block/large_integrated_working_station_" + variant + ".json";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static net.minecraft.resources.ResourceKey<Block> blockKey(String id) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.Identifier.parse(id));
    }
}
