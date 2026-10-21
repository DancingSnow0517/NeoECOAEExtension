package cn.dancingsnow.neoecoae.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import cn.dancingsnow.neoecoae.mixins.client.minecraft.ChunkRebuildTaskMixin;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectionGeometryOriginTest {
    @Test
    void chunkInjectionKeepsItsOriginalSectionWhenMutableOriginChangesDuringRendering() throws Exception {
        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos(-32, 64, 48);
        RenderChunkRegion region = mock(RenderChunkRegion.class);
        int[] visited = {0};
        when(region.getBlockEntity(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            assertTrue(pos.getX() >= -32 && pos.getX() <= -17);
            assertTrue(pos.getY() >= 64 && pos.getY() <= 79);
            assertTrue(pos.getZ() >= 48 && pos.getZ() <= 63);
            visited[0]++;
            origin.set(256, -64, -256);
            return null;
        });
        var injection=ChunkRebuildTaskMixin.class.getDeclaredMethods()[0];
        injection.setAccessible(true);
        var buffers=mock(net.minecraft.client.renderer.ChunkBufferBuilderPack.class);
        injection.invoke(new ChunkRebuildTaskMixin(){},0F,0F,0F,buffers,null,origin,region,new PoseStack(),new java.util.HashSet<net.minecraft.client.renderer.RenderType>());
        org.mockito.Mockito.verifyNoInteractions(buffers);
        assertEquals(16 * 16 * 16, visited[0]);
    }
}
