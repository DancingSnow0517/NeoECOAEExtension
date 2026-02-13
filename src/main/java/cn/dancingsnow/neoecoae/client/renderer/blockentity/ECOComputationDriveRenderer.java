package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

public class ECOComputationDriveRenderer
    implements
    IFixedBlockEntityRenderer<ECOComputationDriveBlockEntity>,
    BlockEntityRenderer<ECOComputationDriveBlockEntity> {

    private static final ThreadLocal<RandomSource> RNG = ThreadLocal.withInitial(RandomSource::createNewThreadLocalInstance);


    public ECOComputationDriveRenderer() {

    }

    public ECOComputationDriveRenderer(BlockEntityRendererProvider.Context context) {

    }

    @Override
    public void renderFixed(
        ECOComputationDriveBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        ItemStack itemStack = blockEntity.getCellStack();
        Direction facing = blockEntity.getBlockState().getValue(ECOComputationDrive.FACING);
        int rotateDegrees = ((int) facing.toYRot() + 180) % 360;
        Quaternionf facingRot = Axis.YN.rotationDegrees(rotateDegrees);
        poseStack.pushPose();

        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.translate(0.25 * facing.getStepX(), 0, 0.25 * facing.getStepZ());
        poseStack.mulPose(facingRot);
        boolean formed = blockEntity.isFormed();
        boolean shouldCellWork = false;
        IECOTier cableTier = blockEntity.getTier();
        if (itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof ECOComputationCellItem item) {
            IECOTier itemTier = item.getTier();
            shouldCellWork = formed && itemTier.compareTo(blockEntity.getTier()) <= 0;
            ResourceLocation cellModel = shouldCellWork
                ? ECOComputationModels.getFormedModel(itemStack.getItem())
                : ECOComputationModels.getNormalModel(itemStack.getItem());
            if (shouldCellWork) {
                cableTier = itemTier;
            }
            tessellateModel(
                poseStack,
                bufferSource,
                cellModel,
                packedLight,
                packedOverlay
            );
//            tessellateModelWithAO(
//                blockEntity.getLevel(),
//                cellModel,
//                blockEntity.getBlockState(),
//                blockEntity.getBlockPos(),
//                poseStack,
//                bufferSource,
//                RNG.get(),
//                packedOverlay
//            );
        }
        ResourceLocation cableModel = null;
        boolean connected = false;
        if (formed) {
            if (itemStack != null) {
                if (shouldCellWork) {
                    poseStack.translate(0, 0, -0.35);
                    cableModel = ECOComputationModels.getCableConnectedModel(cableTier);
                    connected = true;
                } else {
                    if (blockEntity.isLowerDrive()) {
                        poseStack.translate(0, 0.688, -0.3);
                    } else {
                        poseStack.translate(0, -0.688, -0.3);
                    }
                    cableModel = ECOComputationModels.getCableDisconnectedModel(cableTier);
                }
            } else {
                if (blockEntity.isLowerDrive()) {
                    poseStack.translate(0, 0.688, -0.3);
                } else {
                    poseStack.translate(0, -0.688, -0.3);
                }
                cableModel = ECOComputationModels.getCableDisconnectedModel(cableTier);
            }
        }
        if (blockEntity.isLowerDrive()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            poseStack.scale(-1, -1, 1);
            if (connected) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            }
        }
        if (cableModel == null) {
            poseStack.popPose();
            return;
        }
        tessellateModel(
            poseStack,
            bufferSource,
            cableModel,
            packedLight,
            packedOverlay
        );
//        tessellateModelWithAO(
//            blockEntity.getLevel(),
//            cableModel,
//            blockEntity.getBlockState(),
//            blockEntity.getBlockPos(),
//            poseStack,
//            bufferSource,
//            RNG.get(),
//            packedOverlay
//        );
        poseStack.popPose();
    }

    @Override
    public void render(ECOComputationDriveBlockEntity driveBlockEntity, float v, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int i1) {
//        if (PonderPlatformUtils.isPonderLevel(driveBlockEntity.getLevel()) || driveBlockEntity.getLevel() instanceof DummyWorld) {
//            renderFixed(driveBlockEntity, v, poseStack, multiBufferSource, i, i1);
//        }
    }
}
