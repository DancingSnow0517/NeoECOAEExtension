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
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
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
        render(g, scene, scene == null ? List.of() : scene.orderedPositions(), x, y, width, height, partialTick);
    }

    public void render(GuiGraphics g, MultiblockPreviewScene scene, List<BlockPos> positions, int x, int y, int width, int height, float partialTick) {
        render(g, scene, positions, x, y, width, height, partialTick, true);
    }

    public void render(GuiGraphics g, MultiblockPreviewScene scene, List<BlockPos> positions, int x, int y, int width, int height, float partialTick, boolean clip) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || scene == null || scene.isEmpty()) {
            drawEmptyScene(g, x, y, width, height);
            return;
        }

        SceneBounds bounds = calculateBounds(scene, positions);
        if (bounds.maxDimension() <= 0 || width <= 0 || height <= 0) {
            drawEmptyScene(g, x, y, width, height);
            return;
        }

        g.flush();
        if (clip) {
            Matrix4f matrix = g.pose().last().pose();
            int scissorX = Math.round(x + matrix.m30());
            int scissorY = Math.round(y + matrix.m31());
            g.enableScissor(scissorX, scissorY, scissorX + width, scissorY + height);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            float scale = calculateScale(bounds, width, height) * zoom;
            pose.translate(x + width * 0.5F, y + height * 0.54F, 240.0F);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());

            BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
            MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
            Map<BlockPos, BlockState> blocks = scene.blocks();
            for (BlockPos pos : positions) {
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
            if (clip) {
                g.disableScissor();
            }
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

    public void rotate(float yawDelta, float pitchDelta) {
        this.yaw += yawDelta;
        this.pitch = Math.max(-75.0F, Math.min(75.0F, this.pitch + pitchDelta));
    }

    private float calculateScale(SceneBounds bounds, int width, int height) {
        float sizeX = Math.max(1.0F, bounds.sizeX());
        float sizeY = Math.max(1.0F, bounds.sizeY());
        float sizeZ = Math.max(1.0F, bounds.sizeZ());

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        float projectedW = (float) (Math.abs(sizeX * Math.cos(yawRad)) + Math.abs(sizeZ * Math.sin(yawRad)));
        float projectedD = (float) (Math.abs(sizeX * Math.sin(yawRad)) + Math.abs(sizeZ * Math.cos(yawRad)));
        float horizontalExtent = Math.max(1.0F, projectedW + projectedD * 0.35F);
        float verticalExtent = Math.max(1.0F, (float) (sizeY * Math.cos(pitchRad) + projectedD * Math.sin(Math.abs(pitchRad))));

        float scaleX = width * 0.78F / horizontalExtent;
        float scaleY = height * 0.78F / verticalExtent;
        return Math.min(scaleX, scaleY);
    }

    private static SceneBounds calculateBounds(MultiblockPreviewScene scene, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return new SceneBounds(scene.minX(), scene.minY(), scene.minZ(), scene.maxX(), scene.maxY(), scene.maxZ());
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        Map<BlockPos, BlockState> blocks = scene.blocks();
        for (BlockPos pos : positions) {
            BlockState state = blocks.get(pos);
            if (state == null || state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX == Integer.MAX_VALUE) {
            return new SceneBounds(0, 0, 0, -1, -1, -1);
        }
        return new SceneBounds(minX, minY, minZ, maxX, maxY, maxZ);
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

    private record SceneBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int sizeX() {
            return maxX - minX + 1;
        }

        int sizeY() {
            return maxY - minY + 1;
        }

        int sizeZ() {
            return maxZ - minZ + 1;
        }

        int maxDimension() {
            return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
        }

        float centerX() {
            return (minX + maxX + 1.0F) * 0.5F;
        }

        float centerY() {
            return (minY + maxY + 1.0F) * 0.5F;
        }

        float centerZ() {
            return (minZ + maxZ + 1.0F) * 0.5F;
        }
    }
}
