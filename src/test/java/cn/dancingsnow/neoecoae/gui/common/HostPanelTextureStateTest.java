package cn.dancingsnow.neoecoae.gui.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }
}
