package cn.dancingsnow.neoecoae.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;


import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class FixedBlockEntityRenderers {
    private static final Map<BlockEntityType<?>, IFixedBlockEntityRenderer<?>> renderers = new IdentityHashMap<>();

    public static void render(
        NESectionGeometry.SectionRenderingContext context,
        BlockPos sectionOrigin
    ) {
        BlockAndTintGetter region = context.getRegion();
        List<BlockEntity> blockEntities = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(sectionOrigin, sectionOrigin.offset(15, 15, 15))) {
            BlockEntity blockEntity = region.getBlockEntity(pos);
            if (hasRenderer(blockEntity)) blockEntities.add(blockEntity);
        }
        renderSection(region, sectionOrigin, context.getPoseStack(), new DelegatedBufferSource(context), blockEntities);
    }

    public static boolean hasRenderer(BlockEntity blockEntity) {
        return blockEntity != null && renderers.containsKey(blockEntity.getType());
    }

    /** Shares transforms and world lighting between the native and replacement chunk compilers. */
    public static void renderSection(BlockAndTintGetter region, BlockPos sectionOrigin, PoseStack poseStack,
                                     MultiBufferSource bufferSource, Iterable<BlockEntity> blockEntities) {
        for (BlockEntity blockEntity : blockEntities) {
            if (!hasRenderer(blockEntity)) continue;
            render(
                region,
                blockEntity,
                0,
                poseStack,
                bufferSource,
                LevelRenderer.getLightColor(region, blockEntity.getBlockPos()),
                OverlayTexture.NO_OVERLAY,
                sectionOrigin
            );
        }
    }

    @SuppressWarnings("rawtypes")
    public static void render(
        BlockAndTintGetter level,
        BlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        BlockPos origin
    ) {
        BlockEntityType<?> type = blockEntity.getType();
        IFixedBlockEntityRenderer renderer = renderers.get(type);
        if (renderer != null) {
            poseStack.pushPose();
            BlockPos pos = blockEntity.getBlockPos();
            poseStack.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
            try (var lighting = new FixedModelLighting(level, pos, blockEntity.getBlockState(), poseStack.last(), bufferSource)) {
                //noinspection unchecked
                renderer.renderFixed(
                    blockEntity,
                    partialTick,
                    poseStack,
                    lighting,
                    packedLight,
                    packedOverlay
                );
            } finally {
                poseStack.popPose();
            }
        }
    }

    public static <T extends BlockEntity> void register(
        BlockEntityType<T> type,
        IFixedBlockEntityRenderer<T> renderer
    ) {
        renderers.put(
            type,
            renderer
        );
    }
}
