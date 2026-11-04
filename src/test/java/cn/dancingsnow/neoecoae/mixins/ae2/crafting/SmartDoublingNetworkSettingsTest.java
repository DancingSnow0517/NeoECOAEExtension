package cn.dancingsnow.neoecoae.mixins.ae2.crafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmartDoublingNetworkSettingsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void defaultsOnRestoresFirstHostsSavedSettingAndSynchronizesLaterHosts() throws Exception {
        var settings = mock(CraftingServiceMixin.class, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
        assertTrue(settings.neoecoae$isSmartDoublingEnabled());
        var first = mock(ECOComputationSystemBlockEntity.class);
        var second = mock(ECOComputationSystemBlockEntity.class);
        when(first.isLocallySmartDoublingEnabled()).thenReturn(false);
        when(second.isLocallySmartDoublingEnabled()).thenReturn(true);
        join(settings, first);
        assertFalse(settings.neoecoae$isSmartDoublingEnabled());
        join(settings, second);
        verify(second).applyNetworkSmartDoublingEnabled(false);

        var grid = mock(IGrid.class);
        when(grid.getMachineClasses()).thenReturn(List.of(ECOComputationSystemBlockEntity.class));
        when(grid.getMachines(ECOComputationSystemBlockEntity.class)).thenReturn(java.util.Set.of(first, second));
        var field = CraftingServiceMixin.class.getDeclaredField("grid");
        field.setAccessible(true);
        field.set(settings, grid);
        settings.neoecoae$setSmartDoublingEnabled(true);
        verify(first).applyNetworkSmartDoublingEnabled(true);
        verify(second).applyNetworkSmartDoublingEnabled(true);
        assertTrue(settings.neoecoae$isSmartDoublingEnabled());
    }

    private static void join(CraftingServiceMixin settings, ECOComputationSystemBlockEntity host) throws Exception {
        var node = mock(IGridNode.class);
        when(node.getOwner()).thenReturn(host);
        var method = CraftingServiceMixin.class.getDeclaredMethod("onAddNode", IGridNode.class,
            CompoundTag.class, CallbackInfo.class);
        method.setAccessible(true);
        method.invoke(settings, node, new CompoundTag(), new CallbackInfo("addNode", false));
    }
}
