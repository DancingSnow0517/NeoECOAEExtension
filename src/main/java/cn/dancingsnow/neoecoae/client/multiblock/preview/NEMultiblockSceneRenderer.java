package cn.dancingsnow.neoecoae.client.multiblock.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NEMultiblockSceneRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(NEMultiblockSceneRenderer.class);
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final Set<String> LOGGED_RENDER_FAILURES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private float yaw = -38.0F;
    private float pitch = 28.0F;
    private float zoom = 1.0F;

    public void render(GuiGraphics g, MultiblockPreviewScene scene, int x, int y, int width, int height, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || scene == null || scene.isEmpty()) {
            drawEmptyScene(g, x, y, width, height);
            return;
        }

        int maxDim = scene.maxDimension();
        if (maxDim <= 0 || width <= 0 || height <= 0) {
            drawEmptyScene(g, x, y, width, height);
            return;
        }

        g.flush();
        g.enableScissor(x, y, x + width, y + height);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            float scale = Math.min(width, height) * 0.68F / maxDim * zoom;
            pose.translate(x + width * 0.5F, y + height * 0.58F, 240.0F);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-scene.centerX(), -scene.centerY(), -scene.centerZ());

            BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
            MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
            Map<BlockPos, BlockState> blocks = scene.blocks();
            for (BlockPos pos : scene.orderedPositions()) {
                BlockState state = blocks.get(pos);
                if (state == null || state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
                    continue;
                }
                renderBlock(dispatcher, buffer, pose, pos, state);
            }
            buffer.endBatch();
        } finally {
            pose.popPose();
            RenderSystem.disableDepthTest();
            g.disableScissor();
        }
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
    }

    private static void renderBlock(
            BlockRenderDispatcher dispatcher,
            MultiBufferSource buffer,
            PoseStack pose,
            BlockPos pos,
            BlockState state) {
        pose.pushPose();
        try {
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            dispatcher.renderSingleBlock(state, pose, buffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException e) {
            String key = state.toString();
            if (LOGGED_RENDER_FAILURES.add(key)) {
                LOGGER.debug("Skipping block in EMI multiblock preview after render failure: {}", key, e);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawEmptyScene(GuiGraphics g, int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Component text = Component.literal("无结构数据");
        int textX = x + Math.max(0, (width - font.width(text)) / 2);
        int textY = y + Math.max(0, (height - font.lineHeight) / 2);
        g.drawString(font, text, textX, textY, 0xFF777777, false);
    }
}
