package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
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
        Direction facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        int rotation = (int) ((facing.toYRot() + 180) % 360);
        poseStack.pushPose();
        translate(poseStack, facing);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        tesselateModel(
            poseStack,
            bufferSource,
            NEExtraModels.COMPUTATION_COOLING_CONTROLLER_GLASS,
            packedLight,
            packedOverlay,
            RenderType.translucent()
        );
        poseStack.popPose();
    }

    public void translate(PoseStack poseStack, Direction facing) {
        poseStack.translate(0, 0.315, 0);
        switch (facing) {
            case SOUTH -> poseStack.translate(1, 0, 0.453125);
            case WEST -> poseStack.translate(0.453125, 0, 1);
            case EAST -> poseStack.translate(0.546875, 0, 0);
            default -> poseStack.translate(0, 0, 0.546875);
        }
    }
}
