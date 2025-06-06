package cn.dancingsnow.neoecoae.api.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface IFixedBlockEntityRenderer<T extends BlockEntity> {
    void renderFixed(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay);
}
