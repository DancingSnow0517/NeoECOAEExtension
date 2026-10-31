package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Reads EAEP's live upgrade state without linking an optional mod into the CPU.
 *
 * <p>EAEP 1.6.1 and older answer {@code eap$compatIsVirtualCraftingEnabled()} from a cached flag that can stay
 * {@code false} after a world reload (EAEP #147). The installed card is therefore read from the provider's
 * upgrade inventory first, and the bridge method is only a fallback.
 */
public final class ECOExtendedAEPlusVirtualCrafting {
    static final ResourceLocation CARD_ID = new ResourceLocation("extendedae_plus", "virtual_crafting_card");
    private static final Method ENABLED = findBridge();
    private static Supplier<Item> cardLookup = ECOExtendedAEPlusVirtualCrafting::lookupCard;
    private static volatile Item card;

    private ECOExtendedAEPlusVirtualCrafting() {}

    public static boolean isEnabled(Object provider) {
        if (ENABLED == null || !ENABLED.getDeclaringClass().isInstance(provider)) return false;
        if (provider instanceof IUpgradeableObject upgradeable && hasInstalledCard(upgradeable)) return true;
        try {
            return Boolean.TRUE.equals(ENABLED.invoke(provider));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static boolean hasInstalledCard(IUpgradeableObject upgradeable) {
        try {
            var item = cardItem();
            IUpgradeInventory upgrades = upgradeable.getUpgrades();
            return item != null && upgrades != null && upgrades.isInstalled(item);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static Item cardItem() {
        var current = card;
        if (current == null) {
            current = cardLookup.get();
            if (current != null) card = current;
        }
        return current;
    }

    private static Item lookupCard() {
        var item = ForgeRegistries.ITEMS.getValue(CARD_ID);
        return item == null || item == Items.AIR ? null : item;
    }

    /** Test seam: replaces the registry lookup and clears the cached card. */
    static void setCardLookupForTesting(Supplier<Item> lookup) {
        cardLookup = lookup;
        card = null;
    }

    private static Method findBridge() {
        try {
            return Class.forName("com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge",
                    false, ECOExtendedAEPlusVirtualCrafting.class.getClassLoader())
                    .getMethod("eap$compatIsVirtualCraftingEnabled");
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }
}
