package cn.dancingsnow.neoecoae.api.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

import static cn.dancingsnow.neoecoae.util.ThreadLocalRandomHelper.getRandom;

public interface IFixedBlockEntityRenderer<T extends BlockEntity> {
    void renderFixed(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay);

    default void tesselateModel(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ResourceLocation model,
        int packedLight,
        int packedOverlay
    ){
        tesselateModel(
            poseStack,
            bufferSource,
            model,
            packedLight,
            packedOverlay,
            RenderType.cutout()
        );
    }



    default void tesselateModel(
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
