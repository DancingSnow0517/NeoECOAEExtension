package cn.dancingsnow.neoecoae.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectionGeometryOriginTest {
    @Test
    void queuedRendererKeepsItsOriginalSectionAfterRenderSectionMoves() {
        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos(-32, 64, 48);
        AddSectionGeometryEvent event = new AddSectionGeometryEvent(origin, null);
        NeoECOAEClient.onAddChunkGeometry(event);

        origin.set(256, -64, -256);
        BlockAndTintGetter region = mock(BlockAndTintGetter.class);
        int[] visited = {0};
        when(region.getBlockEntity(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            assertTrue(pos.getX() >= -32 && pos.getX() <= -17);
            assertTrue(pos.getY() >= 64 && pos.getY() <= 79);
            assertTrue(pos.getZ() >= 48 && pos.getZ() <= 63);
            visited[0]++;
            return null;
        });
        var context = new AddSectionGeometryEvent.SectionRenderingContext(
            layer -> { throw new AssertionError("Empty section must not request a buffer"); },
            region,
            new PoseStack()
        );

        assertEquals(1, event.getAdditionalRenderers().size());
        event.getAdditionalRenderers().getFirst().render(context);
        assertEquals(16 * 16 * 16, visited[0]);
    }
}
