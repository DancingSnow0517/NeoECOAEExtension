package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ECOExtendedAEPlusVirtualCraftingTest {
    interface Provider extends IUpgradeableObject, PatternProviderLogicVirtualCompatBridge {}

    final Item card = mock(Item.class);
    final IUpgradeInventory upgrades = mock(IUpgradeInventory.class);
    final Provider provider = mock(Provider.class);

    ECOExtendedAEPlusVirtualCraftingTest() {
        ECOExtendedAEPlusVirtualCrafting.setCardLookupForTesting(() -> card);
        when(provider.getUpgrades()).thenReturn(upgrades);
    }

    @AfterEach void restoreLookup() {
        ECOExtendedAEPlusVirtualCrafting.setCardLookupForTesting(() -> null);
    }

    @Test void installedCardWinsOverStaleLegacyBridgeFlag() {
        // EAEP <= 1.6.1 caches the flag and can report false after a world reload.
        when(upgrades.isInstalled(card)).thenReturn(true);
        when(provider.eap$compatIsVirtualCraftingEnabled()).thenReturn(false);
        assertTrue(ECOExtendedAEPlusVirtualCrafting.isEnabled(provider));
    }

    @Test void bridgeRemainsFallbackWhenUpgradeInventoryHasNoCard() {
        when(provider.eap$compatIsVirtualCraftingEnabled()).thenReturn(true);
        assertTrue(ECOExtendedAEPlusVirtualCrafting.isEnabled(provider));
        when(provider.eap$compatIsVirtualCraftingEnabled()).thenReturn(false);
        assertFalse(ECOExtendedAEPlusVirtualCrafting.isEnabled(provider));
    }

    @Test void unresolvedCardFallsBackToBridge() {
        ECOExtendedAEPlusVirtualCrafting.setCardLookupForTesting(() -> null);
        when(provider.eap$compatIsVirtualCraftingEnabled()).thenReturn(false);
        assertFalse(ECOExtendedAEPlusVirtualCrafting.isEnabled(provider));
    }

    @Test void upgradeableProviderWithoutEaepBridgeIsIgnored() {
        var plain = mock(IUpgradeableObject.class);
        when(plain.getUpgrades()).thenReturn(upgrades);
        when(upgrades.isInstalled(card)).thenReturn(true);
        assertFalse(ECOExtendedAEPlusVirtualCrafting.isEnabled(plain));
    }
}
