package cn.dancingsnow.neoecoae.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Registry for upgrade cards that multiply Integrated Working Station processing speed. */
public final class IWSUpgradeEffects {
    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

    private IWSUpgradeEffects() {}

    /**
     * Registers an upgrade during mod construction, before the common setup event is processed.
     *
     * @param requiredModId mod that must be loaded for the card to be offered by the IWS, or {@code null}
     */
    public static synchronized void register(
            ResourceLocation itemId,
            ToIntFunction<ItemStack> speedMultiplier,
            int maxInstalled,
            @Nullable String requiredModId) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(speedMultiplier, "speedMultiplier");
        if (maxInstalled < 1) {
            throw new IllegalArgumentException("maxInstalled must be at least 1");
        }
        if (requiredModId != null && requiredModId.isBlank()) {
            throw new IllegalArgumentException("requiredModId must be null or non-blank");
        }

        Entry entry = new Entry(itemId, speedMultiplier, maxInstalled, requiredModId);
        if (ENTRIES.putIfAbsent(itemId, entry) != null) {
            throw new IllegalStateException("An IWS upgrade effect is already registered for " + itemId);
        }
    }

    public static void register(ResourceLocation itemId, ToIntFunction<ItemStack> speedMultiplier, int maxInstalled) {
        register(itemId, speedMultiplier, maxInstalled, null);
    }

    public static synchronized List<Entry> entries() {
        return List.copyOf(ENTRIES.values());
    }

    public static int getSpeedMultiplier(ItemStack stack) {
        if (stack.isEmpty()) {
            return 1;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        Entry entry;
        synchronized (IWSUpgradeEffects.class) {
            entry = ENTRIES.get(itemId);
        }
        return entry == null ? 1 : Math.max(1, entry.speedMultiplier().applyAsInt(stack));
    }

    public record Entry(
            ResourceLocation itemId,
            ToIntFunction<ItemStack> speedMultiplier,
            int maxInstalled,
            @Nullable String requiredModId) {}
}
