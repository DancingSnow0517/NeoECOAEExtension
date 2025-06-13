package cn.dancingsnow.neoecoae.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib.client.scene.ISceneEntityRenderHook;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Debug(export = true)
@Mixin(WorldSceneRenderer.class)
public abstract class WorldSceneRendererMixin {
    @Shadow
    @Final
    public Level world;

    @Shadow protected abstract void renderEntities(TrackedDummyWorld level, PoseStack poseStack, MultiBufferSource buffer, @Nullable ISceneEntityRenderHook hook, float partialTicks);

    @Shadow protected Set<BlockPos> tileEntities;

    @Shadow protected abstract void renderTESR(Collection<BlockPos> poses, PoseStack poseStack, MultiBufferSource.BufferSource buffers, @Nullable ISceneBlockRenderHook hook, float partialTicks);

    @Shadow protected boolean endBatchLast;

    @Shadow private @Nullable ISceneEntityRenderHook sceneEntityRenderHook;

    @WrapOperation(
        method = "renderBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lcom/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer;canRenderInLayer(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/RenderType;)Z"
        )
    )
    private boolean workaround(
        BlockState state,
        RenderType renderType,
        Operation<Boolean> original,
        @Local BlockEntity blockEntity
    ) {
        ModelData modelData = blockEntity == null ? ModelData.EMPTY : blockEntity.getModelData();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        ChunkRenderTypeSet set = model.getRenderTypes(state, this.world.random, modelData);
        return set.contains(renderType);
    }

    @Inject(
        method = "renderCacheBuffer",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;isInvalid()Z")
    )
    void workaroundNoTranslucentBlock(
        Minecraft mc,
        MultiBufferSource.BufferSource buffers,
        float particleTicks,
        CallbackInfo ci,
        @Local(index = 4) List<RenderType> layers,
        @Local(index = 6) int i,
        @Local(index = 7) VertexBuffer vertexBuffer,
        @Local(index = 5) PoseStack matrixstack
    ) {
        if (vertexBuffer.isInvalid() || vertexBuffer.getFormat() == null) {
            RenderType layer = layers.get(i);
            if (layer == RenderType.translucent() && tileEntities != null) { // render tesr before translucent
                if (world instanceof TrackedDummyWorld level) {
                    renderEntities(level, matrixstack, buffers, sceneEntityRenderHook, particleTicks);
                }
                renderTESR(tileEntities, matrixstack, mc.renderBuffers().bufferSource(), null, particleTicks);
                if (!endBatchLast) {
                    buffers.endBatch();
                }
            }

        }
    }
}
