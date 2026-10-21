package cn.dancingsnow.neoecoae.gui.ldlib.support;

import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public final class NELDLibMultiblockSceneAdapter {
    public static final float DEFAULT_YAW = -72.0F;
    public static final float DEFAULT_PITCH = 24.0F;
    public static final float DEFAULT_ZOOM = 0.78F;
    private static final float ORTHO_PADDING = 1.18F;
    private static final float MIN_ORTHO_RANGE = 3.0F;

    private NELDLibMultiblockSceneAdapter() {}

    public static boolean apply(SceneWidget widget, MultiblockPreviewScene scene) {
        return apply(widget, scene, true, DEFAULT_YAW, DEFAULT_PITCH, DEFAULT_ZOOM);
    }

    public static boolean apply(
            SceneWidget widget, MultiblockPreviewScene scene, boolean resetCamera, float yaw, float pitch, float zoom) {
        TrackedDummyWorld world = widget.getDummyWorld();
        if (world == null) {
            return false;
        }

        world.clear();
        if (widget.getRenderer() != null) {
            widget.getRenderer().renderedBlocksMap.clear();
        }
        widget.getCore().clear();

        if (scene == null || scene.isEmpty()) {
            widget.setCenter(new Vector3f());
            if (resetCamera) {
                widget.setZoom(zoom);
            }
            widget.setOrthoRange(MIN_ORTHO_RANGE);
            if (resetCamera) {
                widget.setCameraYawAndPitch(yaw, pitch);
            }
            return false;
        }

        Map<BlockPos, BlockInfo> blocks = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : scene.blocks().entrySet()) {
            BlockState state = entry.getValue();
            if (state == null || state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            blocks.put(entry.getKey(), BlockInfo.fromBlockState(state));
        }

        if (blocks.isEmpty()) {
            widget.setCenter(new Vector3f(scene.centerX(), scene.centerY(), scene.centerZ()));
            widget.setOrthoRange(MIN_ORTHO_RANGE);
            if (resetCamera) {
                widget.setZoom(zoom);
                widget.setCameraYawAndPitch(yaw, pitch);
            }
            return false;
        }

        world.addBlocks(blocks);
        widget.setRenderedCore(blocks.keySet());
        widget.setCenter(new Vector3f(scene.centerX(), scene.centerY(), scene.centerZ()));
        if (resetCamera) {
            widget.setZoom(zoom);
        }
        widget.setOrthoRange(calculateOrthoRange(widget, scene, yaw, pitch, zoom));
        if (resetCamera) {
            widget.setCameraYawAndPitch(yaw, pitch);
        }
        widget.needCompileCache();
        return true;
    }

    private static float calculateOrthoRange(
            SceneWidget widget, MultiblockPreviewScene scene, float yaw, float pitch, float zoom) {
        float aspect = Math.max(0.1F, widget.getSize().width / (float) Math.max(1, widget.getSize().height));
        float horizontalRadius = 0.0F;
        float verticalRadius = 0.0F;
        float centerX = scene.centerX();
        float centerY = scene.centerY();
        float centerZ = scene.centerZ();

        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vector3f eyeDirection = new Vector3f(
                        (float) Math.cos(yawRadians), (float) Math.tan(pitchRadians), (float) Math.sin(yawRadians))
                .normalize();
        Vector3f forward = new Vector3f(eyeDirection).negate();
        Vector3f right = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F).normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();

        for (int x = 0; x <= 1; x++) {
            float px = (x == 0 ? scene.minX() : scene.maxX() + 1) - centerX;
            for (int y = 0; y <= 1; y++) {
                float py = (y == 0 ? scene.minY() : scene.maxY() + 1) - centerY;
                for (int z = 0; z <= 1; z++) {
                    float pz = (z == 0 ? scene.minZ() : scene.maxZ() + 1) - centerZ;
                    horizontalRadius = Math.max(horizontalRadius, Math.abs(px * right.x + py * right.y + pz * right.z));
                    verticalRadius = Math.max(verticalRadius, Math.abs(px * up.x + py * up.y + pz * up.z));
                }
            }
        }

        float visibleZoom = Math.max(0.1F, zoom);
        float range = Math.max(horizontalRadius, verticalRadius * aspect) / visibleZoom;
        return Math.max(MIN_ORTHO_RANGE, range * ORTHO_PADDING);
    }
}
