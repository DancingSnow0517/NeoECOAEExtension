package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import appeng.api.storage.cells.CellState;
import appeng.client.render.tesr.CellLedRenderer;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix4f;

public class ECODriveRenderer
        implements BlockEntityRenderer<ECODriveBlockEntity>, IFixedBlockEntityRenderer<ECODriveBlockEntity> {
    private static final int INFINITE_MEMBER_LED_COLOR = 0xCA6CFF;

    public ECODriveRenderer() {}

    public ECODriveRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
            ECODriveBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        renderFixed(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        ItemStack cellStack = blockEntity.getCellStack();
        if (ECOInfiniteStorageMember.isMember(cellStack)) {
            renderLed(blockEntity, poseStack, bufferSource, INFINITE_MEMBER_LED_COLOR);
            return;
        }
        if (!blockEntity.isMounted() || !blockEntity.isOnline()) {
            return;
        }
        CellState cellState = blockEntity.getRenderedCellState();
        if (cellState != null) {
            renderLed(blockEntity, poseStack, bufferSource, cellState.getStateColor());
        }
    }

    private static void renderLed(
            ECODriveBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int stateColor) {
        int red = stateColor >> 16 & 255;
        int green = stateColor >> 8 & 255;
        int blue = stateColor & 255;

        BlockState blockState = blockEntity.getBlockState();
        Direction face =
                blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();

        poseStack.pushPose();

        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(face.getRotation());
        poseStack.translate(0, -0.501, 0);

        float pixel = 1f / 16;
        float sizeX = pixel;
        float sizeY = pixel * 2;

        Vec2 offset = new Vec2(-5 * pixel, -5 * pixel);

        float xStart = offset.x;
        float zStart = offset.y;
        float xEnd = offset.x + sizeX;
        float zEnd = offset.y + sizeY;

        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer consumer = bufferSource.getBuffer(CellLedRenderer.RENDER_LAYER);

        consumer.vertex(matrix, xStart, 0, zStart).color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, xEnd, 0, zStart).color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, xEnd, 0, zEnd).color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, xStart, 0, zEnd).color(red, green, blue, 255).endVertex();

        poseStack.popPose();
    }

    @Override
    public void renderFixed(
            ECODriveBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        ItemStack cellStack = blockEntity.getCellStack();
        if (cellStack == null || cellStack.isEmpty()) return;
        Direction facing = blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YN.rotationDegrees(yRotForFacing(facing)));
        poseStack.translate(-0.5, -0.5, -0.5);
        poseStack.translate(2 / 16f, 2 / 16f, 0 / 16f);
        ResourceLocation modelLocation = ECOCellModels.getModelLocation(cellStack.getItem());
        tessellateModel(blockEntity, poseStack, bufferSource, modelLocation, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static float yRotForFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> 0f;
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }
}
