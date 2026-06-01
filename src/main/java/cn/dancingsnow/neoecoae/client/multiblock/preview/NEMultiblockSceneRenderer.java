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

    private static final float DEFAULT_YAW = -38.0F;
    private static final float DEFAULT_PITCH = 28.0F;
    private static final float DEFAULT_ZOOM = 0.90F;
    private static final float FIT_PADDING = 0.68F;

    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = DEFAULT_ZOOM;

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

        SceneBounds cameraBounds = fullSceneBounds(scene);
        if (cameraBounds.maxDimension() <= 0 || width <= 0 || height <= 0) {
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
            float scale = calculateScale(cameraBounds, width, height) * zoom;
            pose.translate(x + width * 0.5F, y + height * 0.50F, 240.0F);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-cameraBounds.centerX(), -cameraBounds.centerY(), -cameraBounds.centerZ());

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

    public void resetView() {
        this.yaw = DEFAULT_YAW;
        this.pitch = DEFAULT_PITCH;
        this.zoom = DEFAULT_ZOOM;
    }

    public void rotate(float yawDelta, float pitchDelta) {
        this.yaw += yawDelta;
        this.pitch = Math.max(-75.0F, Math.min(75.0F, this.pitch + pitchDelta));
    }

    private float calculateScale(SceneBounds bounds, int width, int height) {
        float centerX = bounds.centerX();
        float centerY = bounds.centerY();
        float centerZ = bounds.centerZ();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        float minScreenX = Float.POSITIVE_INFINITY;
        float maxScreenX = Float.NEGATIVE_INFINITY;
        float minScreenY = Float.POSITIVE_INFINITY;
        float maxScreenY = Float.NEGATIVE_INFINITY;

        for (float cornerX : new float[]{bounds.minX(), bounds.maxX() + 1.0F}) {
            for (float cornerY : new float[]{bounds.minY(), bounds.maxY() + 1.0F}) {
                for (float cornerZ : new float[]{bounds.minZ(), bounds.maxZ() + 1.0F}) {
                    float localX = cornerX - centerX;
                    float localY = cornerY - centerY;
                    float localZ = cornerZ - centerZ;

                    float yawX = (float) (localX * cosYaw + localZ * sinYaw);
                    float yawZ = (float) (-localX * sinYaw + localZ * cosYaw);
                    float pitchY = (float) (localY * cosPitch - yawZ * sinPitch);

                    minScreenX = Math.min(minScreenX, yawX);
                    maxScreenX = Math.max(maxScreenX, yawX);
                    minScreenY = Math.min(minScreenY, pitchY);
                    maxScreenY = Math.max(maxScreenY, pitchY);
                }
            }
        }

        float projectedWidth = Math.max(1.0F, maxScreenX - minScreenX);
        float projectedHeight = Math.max(1.0F, maxScreenY - minScreenY);
        float scaleX = width * FIT_PADDING / projectedWidth;
        float scaleY = height * FIT_PADDING / projectedHeight;
        return Math.min(scaleX, scaleY);
    }

    private static SceneBounds fullSceneBounds(MultiblockPreviewScene scene) {
        return new SceneBounds(scene.minX(), scene.minY(), scene.minZ(), scene.maxX(), scene.maxY(), scene.maxZ());
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
