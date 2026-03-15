package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ECOComputationModels {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long WARN_THROTTLE_MILLIS = 30_000L;
    private static final Map<Holder<Item>, Entry> deferredRegistration = new IdentityHashMap<>();
    private static final Map<Item, Entry> map = new IdentityHashMap<>();
    private static final Map<IECOTier, Entry> cableModels = new IdentityHashMap<>();
    private static final Map<String, Long> fallbackWarnTimestamps = new ConcurrentHashMap<>();

    public static final ResourceLocation DEFAULT_NORMAL_MODEL = NeoECOAE.id("block/compute/cell_l4");
    public static final ResourceLocation DEFAULT_FORMED_MODEL = NeoECOAE.id("block/compute/cell_l4_formed");
    public static final ResourceLocation DEFAULT_CABLE_DISCONNECTED_MODEL = NeoECOAE.id("block/compute/cable_l4_dis");
    public static final ResourceLocation DEFAULT_CABLE_CONNECTED_MODEL = NeoECOAE.id("block/compute/cable_l4");

    public static void registerCellModel(Holder<Item> item, ResourceLocation normalModel, ResourceLocation formedModel) {
        deferredRegistration.put(item, new Entry(normalModel, formedModel));
    }

    public static void registerCableModel(IECOTier tier, ResourceLocation normalModel, ResourceLocation formedModel) {
        cableModels.put(tier, new Entry(normalModel, formedModel));
    }

    public static ResourceLocation getNormalModel(Item item) {
        return getNormalModelOrDefault(item);
    }

    public static ResourceLocation getFormedModel(Item item) {
        return getFormedModelOrDefault(item);
    }

    public static ResourceLocation getCableDisconnectedModel(IECOTier tier) {
        return getCableDisconnectedModelOrDefault(tier);
    }

    public static ResourceLocation getCableConnectedModel(IECOTier tier) {
        return getCableConnectedModelOrDefault(tier);
    }

    public static ResourceLocation getNormalModelOrDefault(Item item) {
        Entry entry = item == null ? null : map.get(item);
        if (entry != null && entry.normalModel != null) {
            return entry.normalModel;
        }

        logFallbackThrottled("cell_normal", DEFAULT_NORMAL_MODEL, item == null ? "null" : item.getDescriptionId());
        return DEFAULT_NORMAL_MODEL;
    }

    public static ResourceLocation getFormedModelOrDefault(Item item) {
        Entry entry = item == null ? null : map.get(item);
        if (entry != null && entry.formedModel != null) {
            return entry.formedModel;
        }

        logFallbackThrottled("cell_formed", DEFAULT_FORMED_MODEL, item == null ? "null" : item.getDescriptionId());
        return DEFAULT_FORMED_MODEL;
    }

    public static ResourceLocation getCableDisconnectedModelOrDefault(IECOTier tier) {
        Entry entry = tier == null ? null : cableModels.get(tier);
        if (entry != null && entry.normalModel != null) {
            return entry.normalModel;
        }

        logFallbackThrottled("cable_disconnected", DEFAULT_CABLE_DISCONNECTED_MODEL, tier == null ? "null" : String.valueOf(tier.getTier()));
        return DEFAULT_CABLE_DISCONNECTED_MODEL;
    }

    public static ResourceLocation getCableConnectedModelOrDefault(IECOTier tier) {
        Entry entry = tier == null ? null : cableModels.get(tier);
        if (entry != null && entry.formedModel != null) {
            return entry.formedModel;
        }

        logFallbackThrottled("cable_connected", DEFAULT_CABLE_CONNECTED_MODEL, tier == null ? "null" : String.valueOf(tier.getTier()));
        return DEFAULT_CABLE_CONNECTED_MODEL;
    }

    private static void logFallbackThrottled(String queryType, ResourceLocation fallbackModel, String source) {
        String throttleKey = queryType + ":" + fallbackModel;
        long now = System.currentTimeMillis();

        fallbackWarnTimestamps.compute(throttleKey, (__, lastTimestamp) -> {
            if (lastTimestamp == null || now - lastTimestamp >= WARN_THROTTLE_MILLIS) {
                LOGGER.warn(
                    "[NeoECOAE] Missing computation model for {} (source: {}), fallback to {}.",
                    queryType,
                    source,
                    fallbackModel
                );
                return now;
            }
            return lastTimestamp;
        });
    }

    public static void runDeferredRegistration() {
        deferredRegistration.forEach((itemSupplier, entry) -> {
            map.put(itemSupplier.value(), entry);
        });
    }

    public record Entry(
        ResourceLocation normalModel,
        ResourceLocation formedModel
    ) {
    }
}
