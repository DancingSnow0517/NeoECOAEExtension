package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NeoEcoLegacyCpuCompatibilityTest {
    @Test
    void releasedUselessBridgeResolvesGridFromCurrentCpu() throws Exception {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        var cpu = mock(ECOCraftingCPU.class);
        var grid = mock(IGrid.class);
        when(cpu.getGrid()).thenReturn(grid);
        var context = new ECOCraftingJobContext(cpu, UUID.randomUUID(), null, 1, 1);
        var bridge = Class.forName("com.sorrowmist.useless.compat.neoecoae.NeoEcoCompat");
        var lookup = bridge.getDeclaredMethod("grid", ECOCraftingJobContext.class);
        lookup.setAccessible(true);
        assertSame(grid, lookup.invoke(null, context));
    }
}
