package cn.dancingsnow.neoecoae.api.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static cn.dancingsnow.neoecoae.util.ThreadLocalRandomHelper.getRandom;

public interface IFixedBlockEntityRenderer<T extends BlockEntity> {
    Logger MODEL_LOGGER = LoggerFactory.getLogger("neoecoae-renderer");
    Set<ResourceLocation> WARNED_MISSING_MODELS = ConcurrentHashMap.newKeySet();

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
        BakedModel bakedModel = getBakedModelOrNull(mc, model, null);
        if (bakedModel == null) {
            return;
        }
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
        tessellateModel(null, poseStack, bufferSource, model, packedLight, packedOverlay);
    }

    default void tessellateModel(
        BlockEntity owner,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ResourceLocation model,
        int packedLight,
        int packedOverlay
    ) {
        tessellateModel(
            owner,
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
        tessellateModel(null, poseStack, bufferSource, model, packedLight, packedOverlay, renderType);
    }

    default void tessellateModel(
        BlockEntity owner,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ResourceLocation model,
        int packedLight,
        int packedOverlay,
        RenderType renderType
    ) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel bakedModel = getBakedModelOrNull(mc, model, owner);
        if (bakedModel == null) {
            return;
        }
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

    private static BakedModel getBakedModelOrNull(Minecraft mc, ResourceLocation model, BlockEntity owner) {
        if (model == null) {
            warnMissingModel(null, owner);
            return null;
        }
        BakedModel bakedModel = mc.getModelManager().getModel(model);
        if (bakedModel == mc.getModelManager().getMissingModel()) {
            warnMissingModel(model, owner);
            return null;
        }
        return bakedModel;
    }

    private static void warnMissingModel(ResourceLocation model, BlockEntity owner) {
        if (FMLEnvironment.production) {
            return;
        }
        ResourceLocation key = model == null ? new ResourceLocation("neoecoae", "__null_model__") : model;
        if (WARNED_MISSING_MODELS.add(key)) {
            MODEL_LOGGER.warn(
                "Missing BER baked model {} for {}",
                model,
                owner == null ? "unknown block entity" : owner.getType()
            );
        }
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
                1, 1, 1,
                packedLight,
                packedOverlay
            );
        }
    }
}
