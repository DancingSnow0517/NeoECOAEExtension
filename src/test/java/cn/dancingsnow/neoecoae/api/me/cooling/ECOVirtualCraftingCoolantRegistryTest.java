package cn.dancingsnow.neoecoae.api.me.cooling;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ECOVirtualCraftingCoolantRegistryTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void defaultCoolantCannotBeRemovedAndEmptyStacksAreNeverAllowed() {
        var id = ECOVirtualCraftingCoolantRegistry.CRYOTHEUM_SOLUTION;
        ECOVirtualCraftingCoolantRegistry.register(id);
        ECOVirtualCraftingCoolantRegistry.unregister(id);
        assertEquals(id, cn.dancingsnow.neoecoae.platform.JdkCompat.first(ECOVirtualCraftingCoolantRegistry.registeredFluidIds()));
        assertFalse(ECOVirtualCraftingCoolantRegistry.isAllowed(FluidStack.EMPTY));
        assertFalse(ECOVirtualCraftingCoolantRegistry.isAllowed(null));
    }

    @Test
    void customRegistrationIsIdempotentAndCanBeRevoked() {
        var id = new ResourceLocation("minecraft:water");
        var water = new FluidStack(Fluids.WATER, 1);
        assertFalse(ECOVirtualCraftingCoolantRegistry.isAllowed(water));
        ECOVirtualCraftingCoolantRegistry.register(id);
        try {
            ECOVirtualCraftingCoolantRegistry.register(id);
            assertTrue(ECOVirtualCraftingCoolantRegistry.isAllowed(water));
            assertFalse(ECOVirtualCraftingCoolantRegistry.isAllowed(new FluidStack(Fluids.LAVA, 1)));
            assertEquals(1L, ECOVirtualCraftingCoolantRegistry.registeredFluidIds().stream().filter(id::equals).count());
            assertThrows(UnsupportedOperationException.class,
                () -> ECOVirtualCraftingCoolantRegistry.registeredFluidIds().clear());
        } finally {
            ECOVirtualCraftingCoolantRegistry.unregister(id);
        }
        assertFalse(ECOVirtualCraftingCoolantRegistry.isAllowed(water));
    }
}
