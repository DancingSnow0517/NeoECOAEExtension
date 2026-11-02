package cn.dancingsnow.neoecoae.client.renderer.blockentity;

import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AmountFormat;
import appeng.client.api.AEKeyRenderState;
import appeng.client.render.BlockEntityRenderHelper;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ECOAdvancedCraftingWorkerRenderer
    implements BlockEntityRenderer<ECOCraftingWorkerBlockEntity, ECOAdvancedCraftingWorkerRenderer.RenderState> {
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final Font font;

    public ECOAdvancedCraftingWorkerRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(
        ECOCraftingWorkerBlockEntity blockEntity,
        RenderState state,
        float partialTicks,
        Vec3 cameraPosition,
        ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        var displayedJob = blockEntity.getDisplayedJob();
        if (!blockEntity.isMonitor()
            || !blockEntity.getBlockState().getValue(NEBlock.FORMED)
            || displayedJob == null) {
            state.visible = false;
            state.what.clear();
            state.text = null;
            state.textWidth = 0;
            return;
        }
        state.visible = true;
        state.orientation = blockEntity.getOrientation();
        int seed = (int) blockEntity.getBlockPos().asLong();
        state.what.extract(displayedJob.what(), blockEntity.getLevel(), seed);
        state.text = Component.literal(displayedJob.what().formatAmount(displayedJob.amount(), AmountFormat.SLOT))
            .getVisualOrderText();
        state.textWidth = font.width(state.text);
    }

    @Override
    public void submit(
        RenderState state,
        PoseStack poseStack,
        SubmitNodeCollector nodes,
        CameraRenderState cameraRenderState
    ) {
        if (!state.visible) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        BlockEntityRenderHelper.rotateToFace(poseStack, state.orientation);
        poseStack.translate(0, 0.02, 0.501);
        BlockEntityRenderHelper.submitItem2dWithAmount(
            poseStack,
            state.what,
            state.text,
            TEXT_COLOR,
            state.textWidth,
            nodes,
            0.3F
        );
        poseStack.popPose();
    }

    public static class RenderState extends BlockEntityRenderState {
        boolean visible;
        BlockOrientation orientation;
        final AEKeyRenderState what = new AEKeyRenderState();
        @Nullable FormattedCharSequence text;
        int textWidth;
    }
}
