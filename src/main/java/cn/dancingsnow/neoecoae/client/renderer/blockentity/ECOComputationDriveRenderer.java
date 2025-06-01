package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;

public class ECOComputationDriveRenderer implements IFixedBlockEntityRenderer<ECOComputationDriveBlockEntity> {
    @Override
    public void render(
        ECOComputationDriveBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack itemStack = blockEntity.getCellStack();
        if (itemStack != null) {
            poseStack.pushPose();
            poseStack.scale(0.5f, 0.5f, 0.5f);
            poseStack.translate(0.5, 0.5, 0.5);
            mc.getBlockRenderer()
                .renderSingleBlock(
                    Blocks.GLASS.defaultBlockState(),
                    poseStack,
                    bufferSource,
                    packedLight,
                    packedOverlay,
                    ModelData.EMPTY,
                    RenderType.cutout()
                );
            poseStack.popPose();
        }
    }
}
