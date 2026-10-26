package cn.dancingsnow.neoecoae.menu;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FluidHatchStockAmountTest {
    private ECOFluidInputHatchBlockEntity host;
    private Player player;
    private AbstractContainerMenu menu;
    private AEFluidKey water;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @BeforeEach void setup() {
        host = mock(ECOFluidInputHatchBlockEntity.class, CALLS_REAL_METHODS);
        player = mock(Player.class);
        menu = mock(AbstractContainerMenu.class);
        water = AEFluidKey.of(Fluids.WATER);
        doNothing().when(host).saveChanges();
        doReturn(true).when(host).hasNetworkPullCard();
        doReturn(true).when(host).canPlayerUse(player);
        host.setMarkedFluid(water.toStack(1));
        host.setTargetAmount(16_000);
        player.containerMenu = menu;
        when(menu.stillValid(player)).thenReturn(true);
        when(menu.getCarried()).thenReturn(new ItemStack(Items.WATER_BUCKET));
    }

    @Test void configurationViewRetainsExactLongValuesAndDoesNotCreateInterfaceLogic() {
        var config = host.getConfig();
        assertSame(config, host.getConfig());
        assertSame(host, host.getBlockEntity());
        host.setTargetAmount(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, config.getAmount(0));
        assertEquals(Long.MAX_VALUE, config.getMaxAmount(water));
        config.setStack(0, new GenericStack(water, 5_000_000_001L));
        assertEquals(5_000_000_001L, host.getTargetAmount());
        config.setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 10));
        assertEquals(water, config.getKey(0));
        assertEquals(5_000_000_001L, host.getTargetAmount());
        verify(host, never()).getInterfaceLogic();
    }

    @Test void confirmsLongAmountsAndZeroClearsOnlyTheConfiguration() {
        assertTrue(FluidHatchStockAmount.apply(host, player, menu, water, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, host.getTargetAmount());
        assertTrue(menu.getCarried().is(Items.WATER_BUCKET));
        verify(menu, never()).setCarried(any());
        assertTrue(FluidHatchStockAmount.apply(host, player, menu, water, 0));
        assertTrue(host.getMarkedFluid().isEmpty());
        assertEquals(0, host.getTargetAmount());
        verify(host, never()).getInterfaceLogic();
    }

    @Test void rejectsNegativeOrStaleResourceAndInvalidSessions() {
        assertFalse(FluidHatchStockAmount.apply(host, player, menu, water, -1));
        assertFalse(FluidHatchStockAmount.apply(host, player, menu, AEFluidKey.of(Fluids.LAVA), 1000));
        assertFalse(FluidHatchStockAmount.apply(host, player, menu, AEItemKey.of(Items.STONE), 1000));
        assertFalse(FluidHatchStockAmount.apply(host, player, menu, null, 1000));
        doReturn(false).when(host).hasNetworkPullCard();
        assertFalse(FluidHatchStockAmount.apply(host, player, menu, water, 1000));
        doReturn(true).when(host).hasNetworkPullCard();
        doReturn(false).when(host).canPlayerUse(player);
        assertFalse(FluidHatchStockAmount.apply(host, player, menu, water, 1000));
        doReturn(true).when(host).canPlayerUse(player);
        when(menu.stillValid(player)).thenReturn(false);
        assertFalse(FluidHatchStockAmount.apply(host, player, menu, water, 1000));
        when(menu.stillValid(player)).thenReturn(true);
        player.containerMenu = mock(AbstractContainerMenu.class);
        assertFalse(FluidHatchStockAmount.apply(host, player, menu, water, 1000));
        assertEquals(16_000, host.getTargetAmount());
        assertEquals(water, AEFluidKey.of(host.getMarkedFluid()));
    }
}
