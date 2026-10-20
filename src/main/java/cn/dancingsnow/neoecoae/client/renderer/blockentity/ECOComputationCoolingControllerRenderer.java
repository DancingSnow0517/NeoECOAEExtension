package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import cn.dancingsnow.neoecoae.client.rendering.IFixedBlockEntityRenderer;
import cn.dancingsnow.neoecoae.client.model.CoolingControllerBakedModel;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

public class ECOComputationCoolingControllerRenderer implements IFixedBlockEntityRenderer<ECOComputationCoolingControllerBlockEntity> {
    @Override
    public void renderFixed(
        ECOComputationCoolingControllerBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        BlockState blockState = blockEntity.getBlockState();
        if (!blockState.getValue(NEBlock.FORMED)) return;
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(blockState);
        if (model instanceof CoolingControllerBakedModel controller) {
            // The baked blockstate already contains both the facing and the mirrored geometry,
            // including the glass layer. Keep its resource-pack textures and render types intact.
            controller.renderFixed(blockState, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }
}
