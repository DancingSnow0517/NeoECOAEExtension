package cn.dancingsnow.neoecoae.api.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static cn.dancingsnow.neoecoae.util.ThreadLocalRandomHelper.getRandom;

public interface IFixedBlockEntityRenderer<T extends BlockEntity> {
    void renderFixed(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay);

    default void tessellateModelWithAO(
        BlockAndTintGetter level,
        ResourceLocation model,
        BlockState state,
        BlockPos pos,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        RandomSource random,
        int packedOverlay
    ) {
        tessellateModelWithAO(
            level,
            model,
            state,
            pos,
            poseStack,
            bufferSource,
            RenderType.cutout(),
            random,
            packedOverlay
        );
    }

    default void tessellateModelWithAO(
        BlockAndTintGetter level,
        ResourceLocation model,
        BlockState state,
        BlockPos pos,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        RenderType renderType,
        RandomSource random,
        int packedOverlay
    ) {
        Minecraft mc = Minecraft.getInstance();
        ModelBlockRenderer modelRenderer = mc.getBlockRenderer().getModelRenderer();
        BakedModel bakedModel = mc.getModelManager()
            .getModel(ModelResourceLocation.standalone(model));
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
        modelRenderer.tesselateWithAO(
            level,
            bakedModel,
            state,
            pos,
            poseStack,
            vertexConsumer,
            false,
            random,
            42,
            packedOverlay
        );
    }

    default void tessellateModel(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ResourceLocation model,
        int packedLight,
        int packedOverlay
    ) {
        tessellateModel(
            poseStack,
            bufferSource,
            model,
            packedLight,
            packedOverlay,
            RenderType.cutout()
        );
    }

    default void tessellateModel(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ResourceLocation model,
        int packedLight,
        int packedOverlay,
        RenderType renderType
    ) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel bakedModel = mc.getModelManager()
            .getModel(ModelResourceLocation.standalone(model));
        for (Direction value : Direction.values()) {
            List<BakedQuad> quads = bakedModel.getQuads(
                null,
                value,
                getRandom()
            );
            renderQuadsWithoutAO(
                poseStack,
                bufferSource.getBuffer(renderType),
                quads,
                packedLight,
                packedOverlay
            );
        }
        List<BakedQuad> quads = bakedModel.getQuads(
            null,
            null,
            getRandom()
        );
        renderQuadsWithoutAO(
            poseStack,
            bufferSource.getBuffer(renderType),
            quads,
            packedLight,
            packedOverlay
        );
    }

    default void renderQuadsWithoutAO(
        PoseStack poseStack,
        VertexConsumer buffer,
        List<BakedQuad> quads,
        int packedLight,
        int packedOverlay
    ) {
        for (BakedQuad quad : quads) {
            buffer.putBulkData(
                poseStack.last(),
                quad,
                1, 1, 1, 1,
                packedLight,
                packedOverlay
            );
        }
    }
}
