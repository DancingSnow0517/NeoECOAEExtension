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

    private static final ThreadLocal<RandomSource> RNG = ThreadLocal.withInitial(RandomSource::createNewThreadLocalInstance);
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae-renderer");
    private static final Set<String> LOGGED_MODELS = ConcurrentHashMap.newKeySet();


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
        poseStack.pushPose();

        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotForFacing(facing)));
        boolean formed = blockEntity.isFormed();
        boolean shouldCellWork = false;
        IECOTier cableTier = blockEntity.getTier();
        ResourceLocation normalCellModel = null;
        ResourceLocation formedCellModel = null;
        if (itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof ECOComputationCellItem item) {
            IECOTier itemTier = item.getTier();
            shouldCellWork = formed && blockEntity.getTier() != null && itemTier.compareTo(blockEntity.getTier()) <= 0;
            normalCellModel = ECOComputationModels.getNormalModel(itemStack.getItem());
            formedCellModel = ECOComputationModels.getFormedModel(itemStack.getItem());
            ResourceLocation cellModel = shouldCellWork ? formedCellModel : normalCellModel;
            if (shouldCellWork) {
                cableTier = itemTier;
            }
            if (cellModel != null) {
                poseStack.pushPose();
                poseStack.translate(0, 0, -0.25);
                tessellateModel(
                    blockEntity,
                    poseStack,
                    bufferSource,
                    cellModel,
                    packedLight,
                    packedOverlay
                );
                poseStack.popPose();
            }
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
                    poseStack.translate(0, 0, -0.6);
                    cableModel = ECOComputationModels.getCableConnectedModel(cableTier);
                    connected = true;
                } else {
                    if (blockEntity.isLowerDrive()) {
                        poseStack.translate(0, 0.688, -0.55);
                    } else {
                        poseStack.translate(0, -0.688, -0.55);
                    }
                    cableModel = ECOComputationModels.getCableDisconnectedModel(cableTier);
                }
            } else {
                if (blockEntity.isLowerDrive()) {
                    poseStack.translate(0, 0.688, -0.55);
                } else {
                    poseStack.translate(0, -0.688, -0.55);
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
        logComputationModels(blockEntity, itemStack, normalCellModel, formedCellModel, cableModel, formed, blockEntity.isLowerDrive(), cableTier);
        if (cableModel == null) {
            poseStack.popPose();
            return;
        }
        tessellateModel(
            blockEntity,
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

    private static void logComputationModels(
        ECOComputationDriveBlockEntity blockEntity,
        ItemStack itemStack,
        ResourceLocation normalCellModel,
        ResourceLocation formedCellModel,
        ResourceLocation cableModel,
        boolean formed,
        boolean lowerDrive,
        IECOTier tier
    ) {
        if (FMLEnvironment.production) {
            return;
        }
        ResourceLocation itemId = itemStack == null || itemStack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        String key = itemId + "|" + normalCellModel + "|" + formedCellModel + "|" + cableModel + "|" + formed + "|" + lowerDrive + "|" + tier;
        if (LOGGED_MODELS.add(key)) {
            LOGGER.info(
                "ECOComputationDrive BER model: pos={}, item={}, normalModel={}, formedModel={}, cableModel={}, formed={}, lowerDrive={}, tier={}, clientSide={}",
                blockEntity.getBlockPos(),
                itemId,
                normalCellModel,
                formedCellModel,
                cableModel,
                formed,
                lowerDrive,
                tier,
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
}
