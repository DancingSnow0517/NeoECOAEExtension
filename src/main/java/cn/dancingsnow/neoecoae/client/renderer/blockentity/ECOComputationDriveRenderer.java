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
    private static final Set<String> LOGGED_MODELS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_MISSING_CELL_MAPPINGS = ConcurrentHashMap.newKeySet();
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

        logComputationModels(blockEntity, itemStack, models, facing, formed, driveTier);

        poseStack.pushPose();
        applyCenteredFacing(poseStack, facing);

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
            return;
        }
        poseStack.pushPose();
        if (models.shouldCellWork()) {
            poseStack.translate(0, 0, -0.6);
        } else if (models.lowerDrive()) {
            poseStack.translate(0, 0.688, -0.55);
        } else {
            poseStack.translate(0, -0.688, -0.55);
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

    private static void logComputationModels(
        ECOComputationDriveBlockEntity blockEntity,
        ItemStack itemStack,
        ComputationRenderModels models,
        Direction facing,
        boolean formed,
        IECOTier driveTier
    ) {
        if (FMLEnvironment.production) {
            return;
        }
        ResourceLocation itemId = itemStack == null || itemStack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        BlockState blockState = blockEntity.getBlockState();
        boolean blockstateFormed = blockState.hasProperty(ECOComputationDrive.FORMED) && blockState.getValue(ECOComputationDrive.FORMED);
        boolean blockstateHasCell = blockState.hasProperty(ECOComputationDrive.HAS_CELL) && blockState.getValue(ECOComputationDrive.HAS_CELL);
        String key = blockEntity.getBlockPos()
            + "|" + facing
            + "|" + blockstateFormed
            + "|" + formed
            + "|" + blockstateHasCell
            + "|" + itemId
            + "|" + models.itemTier()
            + "|" + driveTier
            + "|" + models.shouldCellWork()
            + "|" + models.normalCellModel()
            + "|" + models.formedCellModel()
            + "|" + models.selectedCellModel()
            + "|" + models.cableModel()
            + "|" + models.lowerDrive();
        if (LOGGED_MODELS.add(key)) {
            LOGGER.info(
                "ECOComputationDrive BER model: pos={}, facing={}, blockstateFormed={}, blockEntityFormed={}, blockstateHasCell={}, item={}, itemTier={}, driveTier={}, shouldCellWork={}, normalModel={}, formedModel={}, selectedCellModel={}, cableModel={}, lowerDrive={}, clientSide={}",
                blockEntity.getBlockPos(),
                facing,
                blockstateFormed,
                formed,
                blockstateHasCell,
                itemId,
                models.itemTier(),
                driveTier,
                models.shouldCellWork(),
                models.normalCellModel(),
                models.formedCellModel(),
                models.selectedCellModel(),
                models.cableModel(),
                models.lowerDrive(),
                blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide()
            );
        }
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
        if (FMLEnvironment.production) {
            return;
        }
        String key = blockEntity.getBlockPos()
            + "|" + facing
            + "|" + formed
            + "|" + selectedCellModel
            + "|" + models.itemTier()
            + "|" + models.driveTier()
            + "|" + models.shouldCellWork();
        if (LOGGED_RENDERED_CELL_MODELS.add(key)) {
            LOGGER.info(
                "Rendering computation cell model {} at centered transform: pos={}, facing={}, formed={}, itemTier={}, driveTier={}, shouldCellWork={}, clientSide={}",
                selectedCellModel,
                blockEntity.getBlockPos(),
                facing,
                formed,
                models.itemTier(),
                models.driveTier(),
                models.shouldCellWork(),
                blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide()
            );
        }
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

    private static void applyCenteredFacing(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotForFacing(facing)));
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
