package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import appeng.client.render.BlockEntityRenderHelper;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class ECOAdvancedCraftingWorkerRenderer implements BlockEntityRenderer<ECOCraftingWorkerBlockEntity> {
    public ECOAdvancedCraftingWorkerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        ECOCraftingWorkerBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        var displayedJob = blockEntity.getDisplayedJob();
        if (!blockEntity.isMonitor()
            || !blockEntity.getBlockState().getValue(NEBlock.FORMED)
            || displayedJob == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        BlockEntityRenderHelper.rotateToFace(poseStack, blockEntity.getOrientation());
        poseStack.translate(0, 0.02, 0.501);
        BlockEntityRenderHelper.renderItem2dWithAmount(
            poseStack,
            buffers,
            displayedJob.what(),
            displayedJob.amount(),
            false,
            0.3F,
            -0.18F,
            0xFFFFFF,
            blockEntity.getLevel()
        );
        poseStack.popPose();
    }
}
