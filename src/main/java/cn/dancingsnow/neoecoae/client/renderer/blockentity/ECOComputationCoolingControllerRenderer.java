package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
        int rotation = (int) ((blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        tesselateModel(
            poseStack,
            bufferSource,
            NEExtraModels.COMPUTATION_COOLING_CONTROLLER_GLASS,
            packedLight,
            packedOverlay
        );
        poseStack.popPose();
    }
}
