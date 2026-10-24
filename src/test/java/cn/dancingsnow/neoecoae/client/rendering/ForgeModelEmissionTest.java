package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.IQuadTransformer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ForgeModelEmissionTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void allAuthoredEmissiveModelFacesBakeFullBrightOnForge() throws Exception {
        var sprite = mock(TextureAtlasSprite.class);
        int faces = 0;
        int models = 0;
        for (var root : List.of(Path.of("src/main/resources/assets"), Path.of("src/generated/resources/assets"))) {
            try (var files = Files.walk(root)) {
                for (var file : files.filter(path -> path.toString().endsWith(".json") && path.toString().contains("models")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("neoforge_data"), file.toString());
                    if (!source.contains("forge_data")) continue;
                    int baked = verify(JsonParser.parseString(source).getAsJsonObject(), sprite, file.toString());
                    assertTrue(baked > 0, file.toString());
                    faces += baked;
                    models++;
                }
            }
        }
        assertEquals(167, models);
        assertEquals(831, faces, "Cover the 819 migrated faces and 12 existing Forge emissive faces");
    }

    private static int verify(JsonObject json, TextureAtlasSprite sprite, String path) {
        int faces = 0;
        if (json.has("elements")) {
            var bakery = new FaceBakery();
            for (var element : BlockModel.fromString(json.toString()).getElements()) {
                for (var face : element.faces.entrySet()) {
                    var data = face.getValue().getFaceData();
                    if (data.blockLight() == 0 && data.skyLight() == 0) continue;
                    var quad = bakery.bakeQuad(element.from, element.to, face.getValue(), sprite,
                        face.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade,
                        new ResourceLocation("neoecoae", "emission_validation"));
                    assertFalse(quad.isShade(), path);
                    assertFalse(quad.hasAmbientOcclusion(), path);
                    for (int vertex = 0; vertex < 4; vertex++) {
                        assertEquals(LightTexture.FULL_BRIGHT,
                            quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2], path);
                    }
                    faces++;
                }
            }
        }
        if (json.has("children")) {
            for (var child : json.getAsJsonObject("children").entrySet()) {
                faces += verify(child.getValue().getAsJsonObject(), sprite, path);
            }
        }
        return faces;
    }
}
