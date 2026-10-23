package cn.dancingsnow.neoecoae.gui.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider;
import cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider.Part;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HostPanelTextureStateTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }

    @Test
    void capturesTheConfiguredHostBlockProviderWithoutSubclassingItsBlockEntity() {
        var image = new ResourceLocation("addon:textures/gui/host.png");
        var block = org.mockito.Mockito.mock(net.minecraft.world.level.block.Block.class,
            org.mockito.Mockito.withSettings().extraInterfaces(cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider.class));
        var state = org.mockito.Mockito.mock(net.minecraft.world.level.block.state.BlockState.class);
        org.mockito.Mockito.when(block.defaultBlockState()).thenReturn(state);
        org.mockito.Mockito.when(state.getBlock()).thenReturn(block);
        var provider = (cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider) block;
        org.mockito.Mockito.when(provider.getPanelTexture(state,
            cn.dancingsnow.neoecoae.api.IHostPanelTextureProvider.Part.STORAGE_TIER)).thenReturn(image);
        var item = org.mockito.Mockito.mock(net.minecraft.world.item.BlockItem.class);
        org.mockito.Mockito.when(item.getBlock()).thenReturn(block);
        var stack = org.mockito.Mockito.mock(net.minecraft.world.item.ItemStack.class);
        org.mockito.Mockito.when(stack.getItem()).thenReturn(item);
        assertEquals(new HostPanelTextureState(image, null), HostPanelTextureState.capture(stack));
        assertEquals(HostPanelTextureState.EMPTY, HostPanelTextureState.capture(net.minecraft.world.item.ItemStack.EMPTY));
    }

    @Test
    void optionalHostImagesRoundTripWithoutTierRegistration() {
        var state = new HostPanelTextureState(new ResourceLocation("addon:textures/gui/tier.png"),
            new ResourceLocation("addon:textures/gui/marker.png"));
        CompoundTag tag = new CompoundTag();
        state.write(tag);
        assertEquals(state, HostPanelTextureState.read(tag));
        assertEquals(HostPanelTextureState.EMPTY, HostPanelTextureState.read(new CompoundTag()));
        tag.putString("hostTierTexture", "invalid texture");
        assertEquals(new HostPanelTextureState(null, state.marker()), HostPanelTextureState.read(tag));
        HostPanelTextureState.EMPTY.write(tag);
        assertEquals(HostPanelTextureState.EMPTY, HostPanelTextureState.read(tag));
    }

    @Test
    void allThreePanelsReceivePlacedPropertiesAndStatefulVariantsRoundTrip() {
        var charged = BooleanProperty.create("energized_parallel_core");
        var block = mock(Block.class, withSettings().extraInterfaces(IHostPanelTextureProvider.class));
        var placed = mock(BlockState.class);
        when(placed.getBlock()).thenReturn(block);
        when(placed.getValue(charged)).thenReturn(true);
        var provider = (IHostPanelTextureProvider) block;
        when(provider.getPanelTexture(same(placed), any())).thenAnswer(call ->
            new ResourceLocation("addon:textures/" + ((Part) call.getArgument(1)).name().toLowerCase(java.util.Locale.ROOT) + ".png"));
        when(provider.getPanelTexture(same(placed), any(), anyInt(), anyBoolean(), anyBoolean())).thenAnswer(call -> {
            assertTrue(((BlockState) call.getArgument(0)).getValue(charged));
            return new ResourceLocation("addon:textures/state_" + call.getArgument(2) + "_"
                + call.getArgument(3) + "_" + call.getArgument(4) + ".png");
        });

        for (var panel : HostPanelTextureState.Panel.values()) {
            var textures = HostPanelTextureState.capture(placed, panel);
            assertEquals(new ResourceLocation("addon:textures/" + panel.name().toLowerCase(java.util.Locale.ROOT) + "_tier.png"),
                textures.tier());
            assertEquals(new ResourceLocation("addon:textures/" + panel.name().toLowerCase(java.util.Locale.ROOT) + "_marker.png"),
                textures.marker());
            assertEquals(switch (panel) { case STORAGE -> 0; case COMPUTATION -> 24; case CRAFTING -> 8; }, textures.variants().size());
            CompoundTag tag = new CompoundTag();
            textures.write(tag);
            assertEquals(textures, HostPanelTextureState.read(tag));
            assertThrows(UnsupportedOperationException.class, () -> textures.variants().clear());
            if (panel == HostPanelTextureState.Panel.COMPUTATION) {
                assertEquals(new ResourceLocation("addon:textures/state_5_false_false.png"),
                    textures.texture(Part.COMPUTATION_STATUS, 5, false, false));
                assertEquals(new ResourceLocation("addon:textures/state_2_true_true.png"),
                    textures.texture(Part.COMPUTATION_SWITCH, 2, true, true));
            }
        }
        verify(block, never()).defaultBlockState();
    }

    @Test
    void absentProvidersAndNullImagesKeepEveryFallbackAvailable() {
        var plain = mock(BlockState.class);
        when(plain.getBlock()).thenReturn(mock(Block.class));
        assertEquals(HostPanelTextureState.EMPTY, HostPanelTextureState.capture(plain));
        var block = mock(Block.class, withSettings().extraInterfaces(IHostPanelTextureProvider.class));
        var placed = mock(BlockState.class);
        when(placed.getBlock()).thenReturn(block);
        for (var panel : HostPanelTextureState.Panel.values()) {
            assertEquals(HostPanelTextureState.EMPTY, HostPanelTextureState.capture(placed, panel));
        }
    }

    @Test
    void statefulOverloadDefaultsToTheExistingProviderMethod() {
        IHostPanelTextureProvider provider = mock(IHostPanelTextureProvider.class, CALLS_REAL_METHODS);
        BlockState placed = mock(BlockState.class);
        var image = new ResourceLocation("addon:textures/status.png");
        when(provider.getPanelTexture(placed, Part.COMPUTATION_STATUS)).thenReturn(image);
        assertEquals(image, provider.getPanelTexture(placed, Part.COMPUTATION_STATUS, 0, false, false));
        assertNull(provider.getPanelTexture(placed, Part.CRAFTING_MODE, 1, true, true));
    }
}
