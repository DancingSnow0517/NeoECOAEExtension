package cn.dancingsnow.neoecoae.client.rendering;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.level.BlockAndTintGetter;
import java.util.Set;

/** Forge 1.20.1 chunk compiler bridge. Native chunk compilation owns and flushes these buffers. */
public final class NESectionGeometry {
    public record SectionRenderingContext(BlockAndTintGetter region,PoseStack poseStack,
            ChunkBufferBuilderPack buffers,Set<RenderType> usedLayers) {
        public BlockAndTintGetter getRegion(){return region;}
        public PoseStack getPoseStack(){return poseStack;}
        public VertexConsumer getOrCreateChunkBuffer(RenderType layer){
            var builder=buffers.builder(layer);
            if(usedLayers.add(layer))builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);
            return builder;
        }
    }
    private NESectionGeometry(){}
}
