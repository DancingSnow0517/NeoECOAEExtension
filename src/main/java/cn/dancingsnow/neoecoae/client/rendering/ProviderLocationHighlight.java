package cn.dancingsnow.neoecoae.client.rendering;

import appeng.client.render.AERenderTypes;
import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Short-lived provider outlines, using AE2's visible and occluded overlay render types. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class ProviderLocationHighlight {
    private static final Map<Location, Long> LOCATIONS = new HashMap<>();
    record Location(ResourceKey<Level> dimension, BlockPos position, Direction side) {}

    private ProviderLocationHighlight() {}

    public static void show(CompoundTag source) {
        var dimension = Identifier.tryParse(source.getStringOr("dimension", ""));
        if (dimension == null) return;
        var position = BlockPos.of(source.getLongOr("position", 0L));
        var side = source.contains("side") ? Direction.byName(source.getStringOr("side", "")) : null;
        LOCATIONS.put(new Location(ResourceKey.create(Registries.DIMENSION, dimension), position, side),
            System.currentTimeMillis() + 8_000);
    }

    static List<Location> active(ResourceKey<Level> dimension, long now) {
        LOCATIONS.values().removeIf(expires -> expires <= now);
        return LOCATIONS.keySet().stream().filter(location -> location.dimension().equals(dimension)).toList();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent.AfterWeather event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var locations = active(minecraft.level.dimension(), System.currentTimeMillis());
        if (locations.isEmpty()) return;
        var pose = event.getPoseStack();
        if (pose == null) return;
        var buffers = minecraft.renderBuffers().bufferSource();
        var visible = AERenderTypes.AREA_OVERLAY_LINE;
        var occluded = AERenderTypes.AREA_OVERLAY_LINE_OCCLUDED;
        var position = minecraft.gameRenderer.getMainCamera().position();
        pose.pushPose();
        try {
            pose.translate(-position.x, -position.y, -position.z);
            for (var location : locations) {
                var shape = Shapes.create(new AABB(location.position()).inflate(0.002));
                ShapeRenderer.renderShape(pose, buffers.getBuffer(visible), shape, 0, 0, 0,
                    ARGB.colorFromFloat(1F, 0.78F, 0.65F, 1F), 3F);
                ShapeRenderer.renderShape(pose, buffers.getBuffer(occluded), shape, 0, 0, 0,
                    ARGB.colorFromFloat(0.6F, 0.78F, 0.65F, 1F), 3F);
            }
        } finally {
            pose.popPose();
        }
        buffers.endBatch(occluded);
        buffers.endBatch(visible);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        LOCATIONS.clear();
    }
}
