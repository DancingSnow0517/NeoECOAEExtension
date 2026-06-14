package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import appeng.client.render.AERenderTypes;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ECODriveRenderer implements BlockEntityRenderer<ECODriveBlockEntity, ECODriveRenderer.RenderState> {

    public ECODriveRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(
        ECODriveBlockEntity blockEntity,
        RenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        IECOStorageCell cellInventory = blockEntity.getCellInventory();
        if (cellInventory != null && blockEntity.isOnline()) {
            state.color = ARGB.color(255, cellInventory.getStatus().getStateColor());
            BlockState blockState = blockEntity.getBlockState();
            state.face = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        }
    }

    @Override
    public void submit(
        RenderState renderState,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState cameraRenderState
    ) {
        if (renderState.color == 0) return;
        poseStack.pushPose();

        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(renderState.face.getRotation());
        poseStack.translate(0, -0.501, 0);

        float pixel = 1f / 16;
        float sizeX = pixel * 1;
        float sizeY = pixel * 2;

        Vec2 offset = new Vec2(-5 * pixel, -5 * pixel);

        float xStart = offset.x;
        float zStart = offset.y;
        float xEnd = offset.x + sizeX;
        float zEnd = offset.y + sizeY;

        submitNodeCollector.submitCustomGeometry(
            poseStack,
            AERenderTypes.STORAGE_CELL_LEDS,
            (pose, consumer) -> {
                consumer.addVertex(pose, xStart, 0, zStart).setColor(renderState.color);
                consumer.addVertex(pose, xEnd, 0, zStart).setColor(renderState.color);
                consumer.addVertex(pose, xEnd, 0, zEnd).setColor(renderState.color);
                consumer.addVertex(pose, xStart, 0, zEnd).setColor(renderState.color);
            }
        );

        poseStack.popPose();
    }

    public static class RenderState extends BlockEntityRenderState {
        int color;
        Direction face;
    }
}
