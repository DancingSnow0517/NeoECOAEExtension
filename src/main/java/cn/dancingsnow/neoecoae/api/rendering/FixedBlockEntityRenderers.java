package cn.dancingsnow.neoecoae.api.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class FixedBlockEntityRenderers {
    private static final Map<BlockEntityType<?>, IFixedBlockEntityRenderer<?>> renderers = new IdentityHashMap<>();

    public static void render(
        AddSectionGeometryEvent.SectionRenderingContext context,
        BlockPos sectionOrigin
    ) {
        Level level = Minecraft.getInstance().level;
        List<BlockEntity> blockEntities = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(sectionOrigin, sectionOrigin.offset(15, 15, 15))) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                if (renderers.containsKey(blockEntity.getType())) {
                    blockEntities.add(blockEntity);
                }
            }
        }
        MultiBufferSource bufferSource = new DelegatedBufferSource(context);
        for (BlockEntity blockEntity : blockEntities) {
            render(
                level,
                blockEntity,
                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(Minecraft.getInstance().isPaused()),
                context.getPoseStack(),
                bufferSource,
                LevelRenderer.getLightColor(level, blockEntity.getBlockPos()),
                OverlayTexture.NO_OVERLAY,
                sectionOrigin
            );
        }
    }

    @SuppressWarnings("rawtypes")
    public static void render(
        Level level,
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
            int i = level.getBrightness(LightLayer.SKY, pos);
            int j = level.getBrightness(LightLayer.BLOCK, pos);
            int emission = blockEntity.getBlockState().getLightEmission(level, pos);
            if (emission > j) {
                j = emission;
            }
            //noinspection unchecked
            renderer.renderFixed(
                blockEntity,
                partialTick,
                poseStack,
                bufferSource,
                i << 20 | j << 4,
                packedOverlay
            );
            poseStack.popPose();
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
