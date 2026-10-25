package cn.dancingsnow.neoecoae.client.rendering;

import appeng.client.render.overlay.OverlayRenderType;
import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

/** Short-lived provider outlines, using AE2's visible and occluded overlay render types. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class ProviderLocationHighlight {
    private static final Map<Location, Long> LOCATIONS = new HashMap<>();
    record Location(ResourceKey<Level> dimension, BlockPos position, Direction side) {}

    private ProviderLocationHighlight() {}

    public static void show(CompoundTag source) {
        var dimension = ResourceLocation.tryParse(source.getString("dimension"));
        if (dimension == null) return;
        var position = BlockPos.of(source.getLong("position"));
        var side = source.contains("side") ? Direction.byName(source.getString("side")) : null;
        LOCATIONS.put(new Location(ResourceKey.create(Registries.DIMENSION, dimension), position, side),
            System.currentTimeMillis() + 8_000);
    }

    static List<Location> active(ResourceKey<Level> dimension, long now) {
        LOCATIONS.values().removeIf(expires -> expires <= now);
        return LOCATIONS.keySet().stream().filter(location -> location.dimension().equals(dimension)).toList();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var locations = active(minecraft.level.dimension(), System.currentTimeMillis());
        if (locations.isEmpty()) return;
        var buffers = minecraft.renderBuffers().bufferSource();
        var visible = OverlayRenderType.getBlockHilightLine();
        var occluded = OverlayRenderType.getBlockHilightLineOccluded();
        var pose = event.getPoseStack();
        var camera = event.getCamera();
        var position = camera.getPosition();
        pose.pushPose();
        try {
            pose.mulPose(new Quaternionf(camera.rotation()).invert());
            pose.translate(-position.x, -position.y, -position.z);
            for (var location : locations) {
                var box = new AABB(location.position()).inflate(0.002);
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(visible), box, 0.78F, 0.65F, 1F, 1F);
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(occluded), box, 0.78F, 0.65F, 1F, 0.6F);
            }
        } finally {
            pose.popPose();
        }
        buffers.endBatch(visible);
        buffers.endBatch(occluded);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        LOCATIONS.clear();
    }
}
