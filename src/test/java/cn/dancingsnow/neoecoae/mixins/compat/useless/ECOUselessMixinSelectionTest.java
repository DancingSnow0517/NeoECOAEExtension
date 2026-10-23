package cn.dancingsnow.neoecoae.mixins.compat.useless;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** This port retains only the explicitly approved optional integrations. */
class ECOUselessMixinSelectionTest {
    @Test void excludedIntegrationDoesNotShipMixinConfigurationOrSelector() {
        var loader = getClass().getClassLoader();
        assertNull(loader.getResource("neoecoae.compat.useless_mod.mixins.json"));
        assertNull(loader.getResource("cn/dancingsnow/neoecoae/mixins/compat/useless/ECOUselessMixinPlugin.class"));
    }
}
