package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ECOExternalProviderBlockingTest {
    interface LogicHost extends ICraftingProvider {
        PatternProviderLogic getLogic();
    }

    interface ConfiguredProvider extends ICraftingProvider {
        IConfigManager getConfigManager();
    }

    @Test
    void readsLiveBlockingModeThroughProviderLogic() {
        var provider = mock(LogicHost.class);
        var logic = mock(PatternProviderLogic.class);
        when(provider.getLogic()).thenReturn(logic);
        assertFalse(ECOExternalProviderBlocking.isEnabled(provider));
        when(logic.isBlocking()).thenReturn(true);
        assertTrue(ECOExternalProviderBlocking.isEnabled(provider));
    }

    @Test
    void readsStandardBlockingSettingFromOtherProviders() {
        var provider = mock(ConfiguredProvider.class);
        var config = mock(IConfigManager.class);
        when(provider.getConfigManager()).thenReturn(config);
        when(config.getSettings()).thenReturn(Set.of(Settings.BLOCKING_MODE));
        when(config.getSetting(Settings.BLOCKING_MODE)).thenReturn(YesNo.YES);
        assertTrue(ECOExternalProviderBlocking.isEnabled(provider));
    }

    @Test
    void ecoWorkstationKeepsItsOwnBlockingAwareBatchRoute() {
        var provider = mock(LogicHost.class);
        var logic = mock(LargeWorkstationPatternProvider.class);
        when(provider.getLogic()).thenReturn(logic);
        when(logic.isBlocking()).thenReturn(true);
        assertFalse(ECOExternalProviderBlocking.isEnabled(provider));
    }
}
