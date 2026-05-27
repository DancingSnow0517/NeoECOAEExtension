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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ECOComputationDriveRenderer
    implements
    IFixedBlockEntityRenderer<ECOComputationDriveBlockEntity>,
    BlockEntityRenderer<ECOComputationDriveBlockEntity> {

    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae-renderer");
    private static final Set<String> LOGGED_RENDER_ENTRIES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_MODELS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_MISSING_CELL_MAPPINGS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_MISSING_CABLE_MAPPINGS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_RENDERED_CELL_MODELS = ConcurrentHashMap.newKeySet();


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
        BlockState blockState = blockEntity.getBlockState();
        Direction facing = blockState.getValue(ECOComputationDrive.FACING);
        boolean formed = blockEntity.isFormed();
        IECOTier driveTier = blockEntity.getTier();
        ComputationRenderModels models = selectModels(blockEntity, itemStack, formed, driveTier);

        logRenderEntry(blockEntity, itemStack, facing, formed);
        logComputationModels(blockEntity, itemStack, models, facing, formed, driveTier);

        poseStack.pushPose();
        applyOriginalComputationTransform(poseStack, facing);

        renderComputationCell(blockEntity, poseStack, bufferSource, models, facing, formed, packedLight, packedOverlay);
        if (formed) {
            renderComputationCable(blockEntity, poseStack, bufferSource, models, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private static ComputationRenderModels selectModels(
        ECOComputationDriveBlockEntity blockEntity,
        ItemStack itemStack,
        boolean formed,
        IECOTier driveTier
    ) {
        ResourceLocation normalCellModel = null;
        ResourceLocation formedCellModel = null;
        ResourceLocation selectedCellModel = null;
        ResourceLocation cableModel = null;
        IECOTier itemTier = null;
        IECOTier cableTier = driveTier;
        boolean shouldCellWork = false;
        boolean hasComputationCell = itemStack != null
            && !itemStack.isEmpty()
            && itemStack.getItem() instanceof ECOComputationCellItem;

        if (hasComputationCell) {
            ECOComputationCellItem item = (ECOComputationCellItem) itemStack.getItem();
            itemTier = item.getTier();
            shouldCellWork = formed && driveTier != null && itemTier.compareTo(driveTier) <= 0;
            normalCellModel = ECOComputationModels.getNormalModel(itemStack.getItem());
            formedCellModel = ECOComputationModels.getFormedModel(itemStack.getItem());
            selectedCellModel = shouldCellWork ? formedCellModel : normalCellModel;
            if (selectedCellModel == null && shouldCellWork) {
                selectedCellModel = normalCellModel;
            }
            if (shouldCellWork) {
                cableTier = itemTier;
            }
        }

        if (formed) {
            if (cableTier == null && itemTier != null) {
                cableTier = itemTier;
            }
            cableModel = shouldCellWork
                ? ECOComputationModels.getCableConnectedModel(cableTier)
                : ECOComputationModels.getCableDisconnectedModel(cableTier);
        }

        return new ComputationRenderModels(
            normalCellModel,
            formedCellModel,
            selectedCellModel,
            cableModel,
            itemTier,
            driveTier,
            shouldCellWork,
            blockEntity.isLowerDrive()
        );
    }

    private void renderComputationCell(
        ECOComputationDriveBlockEntity blockEntity,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ComputationRenderModels models,
        Direction facing,
        boolean formed,
        int packedLight,
        int packedOverlay
    ) {
        ResourceLocation cellModel = models.selectedCellModel();
        if (cellModel == null) {
            return;
        }
        logRenderingCellModel(blockEntity, cellModel, models, facing, formed);
        poseStack.pushPose();
        tessellateModel(blockEntity, poseStack, bufferSource, cellModel, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private void renderComputationCable(
        ECOComputationDriveBlockEntity blockEntity,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ComputationRenderModels models,
        int packedLight,
        int packedOverlay
    ) {
        if (models.cableModel() == null) {
            logMissingCableModel(blockEntity, models);
            return;
        }
        poseStack.pushPose();
        if (models.shouldCellWork()) {
            poseStack.translate(0, 0, -0.35);
        } else if (models.lowerDrive()) {
            poseStack.translate(0, 0.688, -0.3);
        } else {
            poseStack.translate(0, -0.688, -0.3);
        }

        if (models.lowerDrive()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            poseStack.scale(-1, -1, 1);
            if (models.shouldCellWork()) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            }
        }

        tessellateModel(blockEntity, poseStack, bufferSource, models.cableModel(), packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void logRenderEntry(
        ECOComputationDriveBlockEntity blockEntity,
        ItemStack itemStack,
        Direction facing,
        boolean formed
    ) {
        // No-op: verbose debug logging removed.
    }

    private static void logMissingCableModel(
        ECOComputationDriveBlockEntity blockEntity,
        ComputationRenderModels models
    ) {
        if (FMLEnvironment.production || !blockEntity.isFormed()) {
            return;
        }
        String key = blockEntity.getBlockPos()
            + "|" + models.itemTier()
            + "|" + models.driveTier()
            + "|" + models.shouldCellWork()
            + "|" + models.lowerDrive();
        if (LOGGED_MISSING_CABLE_MAPPINGS.add(key)) {
            LOGGER.warn(
                "Missing computation cable model mapping: pos={}, itemTier={}, driveTier={}, shouldCellWork={}, lowerDrive={}, clientSide={}",
                blockEntity.getBlockPos(),
                models.itemTier(),
                models.driveTier(),
                models.shouldCellWork(),
                models.lowerDrive(),
                blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide()
            );
        }
    }

    private static void logComputationModels(
        ECOComputationDriveBlockEntity blockEntity,
        ItemStack itemStack,
        ComputationRenderModels models,
        Direction facing,
        boolean formed,
        IECOTier driveTier
    ) {
        // Check for missing model mappings (real issue, keep as warn)
        ResourceLocation itemId = itemStack == null || itemStack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        if (itemId != null && models.selectedCellModel() == null) {
            String missingKey = itemId + "|" + formed + "|" + driveTier;
            if (LOGGED_MISSING_CELL_MAPPINGS.add(missingKey)) {
                LOGGER.warn(
                    "Missing computation cell model mapping: item={}, formed={}, tier={}",
                    itemId,
                    formed,
                    driveTier
                );
            }
        }
    }

    private static void logRenderingCellModel(
        ECOComputationDriveBlockEntity blockEntity,
        ResourceLocation selectedCellModel,
        ComputationRenderModels models,
        Direction facing,
        boolean formed
    ) {
        // No-op: verbose debug logging removed.
    }

    @Override
    public void render(ECOComputationDriveBlockEntity driveBlockEntity, float v, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int i1) {
        renderFixed(driveBlockEntity, v, poseStack, multiBufferSource, i, i1);
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

    private static void applyOriginalComputationTransform(PoseStack poseStack, Direction facing) {
        int rotateDegrees = ((int) facing.toYRot() + 180) % 360;
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.translate(0.25 * facing.getStepX(), 0, 0.25 * facing.getStepZ());
        poseStack.mulPose(Axis.YN.rotationDegrees(rotateDegrees));
    }

    private record ComputationRenderModels(
        ResourceLocation normalCellModel,
        ResourceLocation formedCellModel,
        ResourceLocation selectedCellModel,
        ResourceLocation cableModel,
        IECOTier itemTier,
        IECOTier driveTier,
        boolean shouldCellWork,
        boolean lowerDrive
    ) {
    }
}
