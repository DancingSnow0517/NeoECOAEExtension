package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
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

class CoolantEmissionTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void formedCoolantBakesFullBrightWithoutDirectionalShadingOrAmbientOcclusion() throws Exception {
        var root = Path.of("src/main/resources/assets/neoecoae/models/block");
        var sprite = mock(TextureAtlasSprite.class);
        var bakery = new FaceBakery();
        int coolantFaces = 0;
        for (String path : List.of("computation_controller/controller_formed_base.json",
                "computation_controller/controller_formed_base_mirrored.json",
                "computation_cooling_controller/controller_formed_base.json",
                "computation_cooling_controller/controller_formed_base_mirrored.json",
                "computation_transmitter_formed.json")) {
            var json = JsonParser.parseString(Files.readString(root.resolve(path))).getAsJsonObject();
            if (json.has("children")) json = json.getAsJsonObject("children").getAsJsonObject("coolant");
            var model = BlockModel.fromString(json.toString());
            for (var element : model.getElements()) {
                for (var entry : element.faces.entrySet()) {
                    if (!entry.getValue().texture.equals("#coolant")) continue;
                    coolantFaces++;
                    for (var rotation : List.of(BlockModelRotation.X0_Y0, BlockModelRotation.X0_Y90,
                            BlockModelRotation.X0_Y180, BlockModelRotation.X0_Y270)) {
                        var quad = bakery.bakeQuad(element.from, element.to, entry.getValue(), sprite,
                            entry.getKey(), rotation, element.rotation, element.shade,
                            new ResourceLocation("neoecoae", "block/" + path.replace(".json", "")));
                        assertFalse(quad.isShade(), path);
                        assertFalse(quad.hasAmbientOcclusion(), path);
                        for (int vertex = 0; vertex < 4; vertex++) {
                            assertEquals(LightTexture.FULL_BRIGHT,
                                quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2], path);
                        }
                    }
                }
            }
        }
        assertEquals(19, coolantFaces, "Cover both controller mirrors and the formed transmitter coolant");
    }

    @Test
    void computationLampsAndCableLightsUseTheForgeEmissionFormat() throws Exception {
        var root = Path.of("src/main/resources/assets/neoecoae/models/block");
        var sprite = mock(TextureAtlasSprite.class);
        var bakery = new FaceBakery();
        int lights = 0;
        try (var files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String path = root.relativize(file).toString().replace('\\', '/');
                if (!(path.contains("computation") || path.startsWith("compute/"))) continue;
                String source = Files.readString(file);
                assertFalse(source.contains("neoforge_data"), path);
                var json = JsonParser.parseString(source).getAsJsonObject();
                if (!json.has("elements")) continue;
                for (var element : BlockModel.fromString(source).getElements()) {
                    for (var face : element.faces.entrySet()) {
                        var data = face.getValue().getFaceData();
                        if (face.getValue().texture.equals("#coolant") || data.blockLight() == 0 && data.skyLight() == 0) continue;
                        var quad = bakery.bakeQuad(element.from, element.to, face.getValue(), sprite,
                            face.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade,
                            new ResourceLocation("neoecoae", "block/" + path.replace(".json", "")));
                        assertFalse(quad.isShade(), path);
                        assertFalse(quad.hasAmbientOcclusion(), path);
                        for (int vertex = 0; vertex < 4; vertex++) {
                            assertEquals(LightTexture.FULL_BRIGHT,
                                quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2], path);
                        }
                        lights++;
                    }
                }
            }
        }
        assertEquals(99, lights, "Cover controller lamps, interfaces, cores, cells and both cable states");
    }
}
