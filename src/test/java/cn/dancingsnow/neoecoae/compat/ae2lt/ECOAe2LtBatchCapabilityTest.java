package cn.dancingsnow.neoecoae.compat.ae2lt;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ECOAe2LtBatchCapabilityTest {
    @Test
    void absentLoaderStateDisablesTheOptionalAdapter() {
        assertNull(ECOAe2LtBatchCapability.open(new Object()));
    }
}
