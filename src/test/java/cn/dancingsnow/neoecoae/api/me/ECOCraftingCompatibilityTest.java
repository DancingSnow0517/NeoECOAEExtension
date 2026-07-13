package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingCompatibilityTest {
    @Test
    void finishJobKeepsAddonMixinDescriptor() throws NoSuchMethodException {
        var method = ECOCraftingCPULogic.class.getDeclaredMethod("finishJob", boolean.class);

        assertEquals(void.class, method.getReturnType());
    }
}
