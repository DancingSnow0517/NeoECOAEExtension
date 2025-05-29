package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import appeng.client.render.tesr.CellLedRenderer;
import cn.dancingsnow.neoecoae.blocks.entity.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.items.cell.ECOStorageCell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class ECODriveRenderer implements BlockEntityRenderer<ECODriveBlockEntity> {
    public ECODriveRenderer(BlockEntityRendererProvider.Context context) {

    }

    @Override
    public void render(ECODriveBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ECOStorageCell cellInventory = blockEntity.getCellInventory();
        if (cellInventory != null) {
            int stateColor = cellInventory.getStatus().getStateColor();

            BlockState blockState = blockEntity.getBlockState();
            Direction face = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();

            poseStack.pushPose();

            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.mulPose(face.getRotation());
            poseStack.translate(0, -0.50001, 0);

            float pixel = 1f / 16;
            float sizeX = pixel * 1;
            float sizeY = pixel * 2;

            Vec2 offset = new Vec2(-5 * pixel, -5 * pixel);

            float xStart = offset.x;
            float zStart = offset.y;
            float xEnd = offset.x + sizeX;
            float zEnd = offset.y + sizeY;

            Matrix4f matrix = poseStack.last().pose();
            Matrix3f normalMatrix = poseStack.last().normal();

            Vector3f normal = new Vector3f(0, 1, 0);
            normalMatrix.transform(normal);

            VertexConsumer consumer = bufferSource.getBuffer(CellLedRenderer.RENDER_LAYER);

            consumer.addVertex(matrix, xStart, 0, zStart)
                .setColor(stateColor >> 16 & 0xFF, stateColor >> 8 & 0xff, stateColor & 0xFF, 255)
                .setLight(LightTexture.FULL_BRIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(normal.x(), normal.y(), normal.z());
            consumer.addVertex(matrix, xEnd, 0, zStart)
                .setColor(stateColor >> 16 & 0xFF, stateColor >> 8 & 0xff, stateColor & 0xFF, 255)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
            consumer.addVertex(matrix, xEnd, 0, zEnd)
                .setColor(stateColor >> 16 & 0xFF, stateColor >> 8 & 0xff, stateColor & 0xFF, 255)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
            consumer.addVertex(matrix, xStart, 0, zEnd)
                .setColor(stateColor >> 16 & 0xFF, stateColor >> 8 & 0xff, stateColor & 0xFF, 255)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());

            poseStack.popPose();
        }
    }
}
