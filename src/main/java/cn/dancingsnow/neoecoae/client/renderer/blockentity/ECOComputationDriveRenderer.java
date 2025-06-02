package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ECOComputationDriveRenderer implements IFixedBlockEntityRenderer<ECOComputationDriveBlockEntity> {
    private final Map<Thread, RandomSource> randomSourceMap = new IdentityHashMap<>();

    @Override
    public void render(
        ECOComputationDriveBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        ItemStack itemStack = blockEntity.getCellStack();
        Direction facing = blockEntity.getBlockState().getValue(ECOComputationDrive.FACING);
        int rotateDegrees = ((int) facing.toYRot() + 180) % 360;
        Quaternionf facingRot = Axis.YN.rotationDegrees(rotateDegrees);
        poseStack.pushPose();

        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.translate(0.25 * facing.getStepX(), 0, 0.25 * facing.getStepZ());
        poseStack.mulPose(facingRot);
        boolean formed = blockEntity.isFormed();
        if (itemStack != null && !itemStack.isEmpty()) {
            ResourceLocation cellModel = formed
                ? ECOComputationModels.getNormalModel(itemStack.getItem())
                : ECOComputationModels.getFormedModel(itemStack.getItem());
            tesselateModel(
                poseStack,
                bufferSource,
                cellModel,
                packedLight,
                packedOverlay
            );
        }
        ResourceLocation cableModel = null;
        if (formed) {
            if (itemStack != null) {
                poseStack.translate(0, 0, -0.35);
                ECOComputationCellItem item = (ECOComputationCellItem) itemStack.getItem();
                cableModel = ECOComputationModels.getCableConnectedModel(item.getTier());
            } else {
                poseStack.translate(0, -0.655, -0.3);
                //TODO 用控制器tier
                cableModel = ECOComputationModels.getCableDisconnectedModel(ECOTier.L4);
            }
        }
        if (cableModel == null) {
            poseStack.popPose();
            return;
        }
        tesselateModel(
            poseStack,
            bufferSource,
            cableModel,
            packedLight,
            packedOverlay
        );
        poseStack.popPose();
    }

    private RandomSource getRandom() {
        synchronized (randomSourceMap) {
            Thread thread = Thread.currentThread();
            if (randomSourceMap.containsKey(thread)) {
                return randomSourceMap.get(thread);
            }
            RandomSource randomSource = RandomSource.create();
            randomSourceMap.put(thread, randomSource);
            return randomSource;
        }
    }

    private void tesselateModel(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ResourceLocation model,
        int packedLight,
        int packedOverlay
    ) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel bakedModel = mc.getModelManager()
            .getModel(ModelResourceLocation.standalone(model));
        for (Direction value : Direction.values()) {
            List<BakedQuad> quads = bakedModel.getQuads(
                null,
                value,
                getRandom()
            );
            renderQuadsWithoutAO(
                poseStack,
                bufferSource.getBuffer(RenderType.cutout()),
                quads,
                packedLight,
                packedOverlay
            );
        }
        List<BakedQuad> quads = bakedModel.getQuads(
            null,
            null,
            getRandom()
        );
        renderQuadsWithoutAO(
            poseStack,
            bufferSource.getBuffer(RenderType.cutout()),
            quads,
            packedLight,
            packedOverlay
        );
    }

    private void renderQuadsWithoutAO(
        PoseStack poseStack,
        VertexConsumer buffer,
        List<BakedQuad> quads,
        int packedLight,
        int packedOverlay
    ) {
        for (BakedQuad quad : quads) {
            buffer.putBulkData(
                poseStack.last(),
                quad,
                1, 1, 1, 1,
                packedLight,
                packedOverlay
            );
        }
    }
}
