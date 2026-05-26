package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import appeng.client.render.tesr.CellLedRenderer;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ECODriveRenderer implements BlockEntityRenderer<ECODriveBlockEntity>, IFixedBlockEntityRenderer<ECODriveBlockEntity> {
    private static final ThreadLocal<RandomSource> RNG = ThreadLocal.withInitial(RandomSource::createNewThreadLocalInstance);
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae-renderer");
    private static final Set<ResourceLocation> LOGGED_CELL_ITEMS = ConcurrentHashMap.newKeySet();

    public ECODriveRenderer() {
    }

    public ECODriveRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ECODriveBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderFixed(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        if (!blockEntity.isMounted() || !blockEntity.isOnline()) {
            return;
        }
        IECOStorageCell cellInventory = blockEntity.getCellInventory();
        if (cellInventory != null) {
            int stateColor = cellInventory.getStatus().getStateColor();
            int red = stateColor >> 16 & 255;
            int green = stateColor >> 8 & 255;
            int blue = stateColor & 255;

            BlockState blockState = blockEntity.getBlockState();
            Direction face = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();

            poseStack.pushPose();

            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.mulPose(face.getRotation());
            poseStack.translate(0, -0.501, 0);

            float pixel = 1f / 16;
            float sizeX = pixel * 1;
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
    }

    @Override
    public void renderFixed(
        ECODriveBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        ItemStack cellStack = blockEntity.getCellStack();
        if (cellStack == null || cellStack.isEmpty()) return;
        Direction facing = blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YN.rotationDegrees(yRotForFacing(facing)));
        poseStack.translate(-0.5, -0.5, -0.5);
        poseStack.translate(2 / 16f, 2 / 16f, 0 / 16f);
        ResourceLocation modelLocation = ECOCellModels.getModelLocation(cellStack.getItem());
        logCellModel(blockEntity, cellStack, modelLocation);
        tessellateModel(
            blockEntity,
            poseStack,
            bufferSource,
            modelLocation,
            packedLight,
            packedOverlay
        );
        poseStack.popPose();
    }

    private static void logCellModel(ECODriveBlockEntity blockEntity, ItemStack cellStack, ResourceLocation modelLocation) {
        if (FMLEnvironment.production) {
            return;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(cellStack.getItem());
        if (itemId != null && LOGGED_CELL_ITEMS.add(itemId)) {
            LOGGER.info(
                "ECODrive BER cell model: pos={}, item={}, model={}, clientSide={}, visualCellStackNonEmpty={}",
                blockEntity.getBlockPos(),
                itemId,
                modelLocation,
                blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide(),
                !cellStack.isEmpty()
            );
        }
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
