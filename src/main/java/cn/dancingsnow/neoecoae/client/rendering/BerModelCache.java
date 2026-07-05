package cn.dancingsnow.neoecoae.client.rendering;

import cn.dancingsnow.neoecoae.api.rendering.IFixedBlockEntityRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class BerModelCache {
    private static final Map<ResourceLocation, List<BakedQuad>> QUADS = new ConcurrentHashMap<>();

    private BerModelCache() {}

    public static List<BakedQuad> getQuads(ResourceLocation model, BlockEntity owner) {
        if (model == null) {
            warnNullModel(owner);
            return List.of();
        }
        return QUADS.computeIfAbsent(model, key -> bakeQuads(key, owner));
    }

    public static void clear() {
        QUADS.clear();
    }

    private static List<BakedQuad> bakeQuads(ResourceLocation model, BlockEntity owner) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel bakedModel = mc.getModelManager().getModel(model);
        if (bakedModel == mc.getModelManager().getMissingModel()) {
            warnMissingModel(model, owner);
            return List.of();
        }

        List<BakedQuad> quads = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        for (Direction direction : Direction.values()) {
            quads.addAll(bakedModel.getQuads(null, direction, random));
        }
        quads.addAll(bakedModel.getQuads(null, null, random));
        return List.copyOf(quads);
    }

    private static void warnNullModel(BlockEntity owner) {
        if (FMLEnvironment.production) {
            return;
        }
        String ownerName =
                owner == null ? "unknown block entity" : owner.getType().toString();
        String key = "null:" + ownerName;
        if (IFixedBlockEntityRenderer.WARNED_MISSING_MODELS.add(key)) {
            IFixedBlockEntityRenderer.MODEL_LOGGER.warn("Missing BER model location for {}", ownerName);
        }
    }

    private static void warnMissingModel(ResourceLocation model, BlockEntity owner) {
        if (FMLEnvironment.production) {
            return;
        }
        String key = "missing:" + model;
        if (IFixedBlockEntityRenderer.WARNED_MISSING_MODELS.add(key)) {
            IFixedBlockEntityRenderer.MODEL_LOGGER.warn(
                    "BER model resolved to missing model: {} for {}",
                    model,
                    owner == null ? "unknown block entity" : owner.getType());
        }
    }
}
