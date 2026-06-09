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
    private static final float ORTHO_PADDING = 1.35F;
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
        widget.setOrthoRange(calculateOrthoRange(scene));
        if (resetCamera) {
            widget.setCameraYawAndPitch(yaw, pitch);
        }
        widget.needCompileCache();
        return true;
    }

    private static float calculateOrthoRange(MultiblockPreviewScene scene) {
        return Math.max(MIN_ORTHO_RANGE, scene.maxDimension() * ORTHO_PADDING);
    }
}
