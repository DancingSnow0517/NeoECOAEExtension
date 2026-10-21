package cn.dancingsnow.neoecoae.mixins.client.minecraft;
import cn.dancingsnow.neoecoae.client.rendering.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

/** Adds fixed block entity geometry before native translucent sorting and buffer finalization. */
@Mixin(targets="net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class ChunkRebuildTaskMixin {
    @Inject(method="compile",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/RenderType;translucent()Lnet/minecraft/client/renderer/RenderType;",ordinal=0))
    private void neoecoae$fixedGeometry(float x,float y,float z,ChunkBufferBuilderPack buffers,CallbackInfoReturnable<?> callback,
            @Local(ordinal=0) BlockPos origin,@Local RenderChunkRegion region,@Local PoseStack pose,@Local Set<RenderType> layers){
        FixedBlockEntityRenderers.render(new NESectionGeometry.SectionRenderingContext(region,pose,buffers,layers),origin.immutable());
    }
}
