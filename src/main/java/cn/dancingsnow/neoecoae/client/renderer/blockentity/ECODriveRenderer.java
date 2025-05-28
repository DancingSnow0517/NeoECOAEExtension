package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import cn.dancingsnow.neoecoae.blocks.entity.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.items.cell.ECOStorageCell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
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
        ItemStack cellStack = blockEntity.getCellStack();
        if (cellStack != null && cellStack.getItem() instanceof ECOStorageCellItem) {
            ECOStorageCell cellInventory = ECOStorageCellItem.getCellInventory(cellStack);
            int stateColor = cellInventory.getStatus().getStateColor();


            BlockState blockState = blockEntity.getBlockState();
            Direction face = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();

            poseStack.pushPose();

            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.mulPose(face.getRotation());
            poseStack.translate(0, -0.50001, 0);

            float pixel = 1f / 22;
            float sizeX = pixel * 1;
            float sizeY = pixel * 2;

            Vec2 offset = new Vec2(-0.29f, -0.267f);

            float xStart = offset.x - sizeX / 2;
            float zStart = offset.y - sizeY / 2;
            float xEnd = offset.x + sizeX;
            float zEnd = offset.y + sizeY;

            VertexConsumer consumer = bufferSource.getBuffer(RenderType.SOLID);

            Matrix4f matrix = poseStack.last().pose();
            Matrix3f normalMatrix  = poseStack.last().normal();

            Vector3f normal = new Vector3f(0, 1, 0);
            normalMatrix.transform(normal);

            consumer.addVertex(matrix, xStart, 0, zStart)
                .setColor(stateColor >> 16 & 0xFF, stateColor >> 8 & 0xff, stateColor & 0xFF, 255)
                .setUv(0, 0) // uv0
                .setLight(LightTexture.FULL_BRIGHT) // uv2
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(normal.x(), normal.y(), normal.z());
            consumer.addVertex(matrix, xEnd, 0, zStart)
                .setColor(stateColor)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
            consumer.addVertex(matrix, xEnd, 0, zEnd)
                .setColor(stateColor)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());
            consumer.addVertex(matrix, xStart, 0, zEnd)
                .setColor(stateColor)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x(), normal.y(), normal.z());

            poseStack.popPose();
        }
    }
}
