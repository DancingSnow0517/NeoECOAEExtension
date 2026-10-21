package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HostPanelTierTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(NeoECOAE.class.getName());
        }
    }

    @Test
    void oldTierImplementationsGetDefaultsAndBuiltinsKeepTheirNamesAndArtwork() {
        IECOTier oldAddon = mock(IECOTier.class, CALLS_REAL_METHODS);
        when(oldAddon.getTier()).thenReturn(7);
        assertEquals(Component.literal("L7"), oldAddon.getPanelTierName());
        assertNull(oldAddon.getHostMarkerTexture());
        for (ECOTier tier : ECOTier.values()) {
            assertEquals(Component.literal(tier.name()), HostPanelTier.name(tier, tier.getTier()));
            assertEquals(tier, HostPanelTier.resolve(null, tier.getTier()));
            assertFalse(HostPanelTier.drawName(null, tier, 0, 0, 13, 18, -1));
            assertFalse(HostPanelTier.drawMarker(null, tier, false, 0, 0, 13, 8));
        }
    }

    @Test
    void addonAppearanceWinsEvenWhenItsNumericTierIsZeroOrCollidesWithABuiltin() {
        IECOTier addon = mock(IECOTier.class, CALLS_REAL_METHODS);
        Component name = Component.translatable("addon.tier.l1").withStyle(style -> style.withColor(0x12AB34));
        when(addon.getPanelTierName()).thenReturn(name);
        assertSame(name, HostPanelTier.name(addon, 0));
        assertSame(name, HostPanelTier.name(addon, 1));
        assertEquals(Component.literal("L0"), HostPanelTier.name(null, 0));
    }

    @Test
    void customMarkersUseBothRowsOfTheAddonTextureAndTheRequestedPanelGeometry() {
        IECOTier addon = mock(IECOTier.class);
        ResourceLocation marker = new ResourceLocation("addon", "textures/gui/host.png");
        when(addon.getHostMarkerTexture()).thenReturn(marker);
        GUIContext context = mock(GUIContext.class);
        for (boolean bottom : new boolean[] {false, true}) {
            assertTrue(HostPanelTier.drawMarker(context, addon, bottom, 10, 40, 14, 8));
            verify(context).drawTexture(argThat(texture -> texture instanceof SpriteTexture sprite
                && sprite.getImageLocation().equals(marker) && sprite.spritePosition.x == 0
                && sprite.spritePosition.y == (bottom ? 8 : 0)
                && sprite.spriteSize.width == 13 && sprite.spriteSize.height == 8),
                eq(10F), eq(40F), eq(14F), eq(8F));
        }
    }
}
