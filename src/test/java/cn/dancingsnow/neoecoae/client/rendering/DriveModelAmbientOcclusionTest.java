package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DriveModelAmbientOcclusionTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void outerFacesKeepCanonicalAmbientOcclusionVertexOrderInEveryFacing() throws Exception {
        FaceBakery bakery = new FaceBakery();
        for (String name : List.of("eco_drive_empty", "eco_drive_full", "computation_drive_empty", "computation_drive_full")) {
            try (var input = getClass().getResourceAsStream("/assets/neoecoae/models/block/" + name + ".json")) {
                assertNotNull(input, name);
                BlockModel model = BlockModel.fromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                for (var rotation : List.of(BlockModelRotation.X0_Y0, BlockModelRotation.X0_Y90,
                    BlockModelRotation.X0_Y180, BlockModelRotation.X0_Y270)) {
                    for (var element : model.getElements()) {
                        if (element.rotation != null && element.rotation.angle() != 0) continue;
                        for (var entry : element.faces.entrySet()) {
                            Direction face = entry.getKey();
                            var actual = bakery.bakeQuad(element.from, element.to, entry.getValue(),
                                UnitTextureAtlasSprite.INSTANCE, face, rotation, element.rotation, element.shade);
                            var canonical = bakery.bakeQuad(element.from, element.to, entry.getValue(),
                                UnitTextureAtlasSprite.INSTANCE, face, rotation, null, element.shade);
                            for (int vertex = 0; vertex < 4; vertex++) {
                                for (int axis = 0; axis < 3; axis++) {
                                    int offset = vertex * 8 + axis;
                                    assertEquals(Float.intBitsToFloat(canonical.getVertices()[offset]),
                                        Float.intBitsToFloat(actual.getVertices()[offset]), 0.0001F,
                                        name + " " + rotation + " " + element.from + " " + face
                                            + " vertex " + vertex + " must match the AO corner order");
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
