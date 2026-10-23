package cn.dancingsnow.neoecoae.client.model;

import java.util.HashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkstationModuleBakedModelTest {
    @Test void wrapsTheActualControllerIdOnlyWhenFormed() {
        var event = mock(ModelEvent.ModifyBakingResult.class);
        var models = new HashMap<ModelResourceLocation, BakedModel>();
        var id = ResourceLocation.parse("neoecoae:integrated_working_station");
        var formed = new ModelResourceLocation(id, "facing=north,formed=true,module=other,working=false");
        var small = new ModelResourceLocation(id, "facing=north,formed=false,module=regular,working=false");
        var original = mock(BakedModel.class);
        models.put(formed, original); models.put(small, original);
        when(event.getModels()).thenReturn(models);
        WorkstationModuleBakedModel.wrapModels(event);
        assertInstanceOf(WorkstationModuleBakedModel.class, models.get(formed));
        assertSame(original, models.get(small));
    }

    @Test void rotatesPositionsFacesAndNormalsWithoutMutatingTheSharedQuad() {
        int[] vertices = new int[32];
        for (int v = 0; v < 4; v++) {
            vertices[v * 8] = Float.floatToRawIntBits(v % 2);
            vertices[v * 8 + 2] = Float.floatToRawIntBits(0);
            vertices[v * 8 + 7] = 0x00810000;
        }
        var quad = new BakedQuad(vertices, -1, Direction.NORTH, mock(TextureAtlasSprite.class), true, false);
        var rotated = WorkstationModuleBakedModel.rotate(quad, 1);
        assertEquals(Direction.EAST, rotated.getDirection());
        assertEquals(1.0f, Float.intBitsToFloat(rotated.getVertices()[0]));
        assertEquals(0.0f, Float.intBitsToFloat(rotated.getVertices()[2]));
        assertEquals(127, (byte) rotated.getVertices()[7]);
        assertEquals(Direction.NORTH, quad.getDirection());
        assertArrayEquals(vertices, quad.getVertices());
    }
}
