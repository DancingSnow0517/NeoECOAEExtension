package cn.dancingsnow.neoecoae.compat.ae2;

import cn.dancingsnow.neoecoae.api.IWSUpgradeEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class IWSUpgradeCompat {
    public static final ResourceLocation AE2_SPEED_CARD = ResourceLocation.fromNamespaceAndPath("ae2", "speed_card");
    public static final ResourceLocation EAEP_ENTITY_SPEED_CARD =
            ResourceLocation.fromNamespaceAndPath("extendedae_plus", "entity_speed_card");
    private static final String EAEP_MULTIPLIER_TAG = "EAS:mult";
    private static boolean initialized;

    private IWSUpgradeCompat() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        IWSUpgradeEffects.register(AE2_SPEED_CARD, stack -> 1, 4, "ae2");
        IWSUpgradeEffects.register(EAEP_ENTITY_SPEED_CARD, IWSUpgradeCompat::readEaepMultiplier, 4, "extendedae_plus");
        initialized = true;
    }

    public static int readEaepMultiplier(ItemStack stack) {
        return readEaepMultiplierTag(stack.getTag());
    }

    public static int readEaepMultiplierTag(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(EAEP_MULTIPLIER_TAG)) {
            return 2;
        }
        int multiplier = tag.getInt(EAEP_MULTIPLIER_TAG);
        return multiplier == 2 || multiplier == 4 || multiplier == 8 || multiplier == 16 ? multiplier : 2;
    }
}
