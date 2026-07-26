package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Thread-safe view of the baked model registry, for use by {@link cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer}.
 *
 * <p>Those renderers run inside {@code AddSectionGeometryEvent} callbacks, which NeoForge executes on the chunk
 * meshing worker threads. {@code ModelManager#getModel} reads two plain, non-volatile fields
 * ({@code bakedRegistry} / {@code missingModel}) that the render thread swaps wholesale in {@code ModelManager#apply},
 * so reading it from a worker thread has no happens-before edge and may observe a stale or half-published registry.
 * Whatever it returns is then baked into the section mesh and stays there until that section is rebuilt again.
 *
 * <p>Capturing the finished registry here instead hands the workers a properly published snapshot: the event fires
 * on the render thread once baking is complete, and the volatile write publishes the whole map.
 */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class NEBakedModelCache {
    private static volatile Map<ModelResourceLocation, BakedModel> models = Map.of();
    @Nullable
    private static volatile BakedModel missingModel = null;

    private NEBakedModelCache() {
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        missingModel = event.getModelManager().getMissingModel();
        // Volatile write, ordered after the one above: safely publishes both to the chunk meshing threads.
        models = event.getModels();
    }

    /**
     * @return the baked model for {@code location}, or {@code null} if it was never registered, never baked, or
     *         resolved to the magenta missing model. Callers are expected to render nothing in that case rather
     *         than baking a missing-model cube into the chunk mesh.
     */
    @Nullable
    public static BakedModel get(@Nullable ResourceLocation location) {
        if (location == null) {
            return null;
        }
        BakedModel model = models.get(ModelResourceLocation.standalone(location));
        return model == null || model == missingModel ? null : model;
    }
}
