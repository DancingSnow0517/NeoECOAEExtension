package cn.dancingsnow.neoecoae.client.gui.crafting;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOCpuTierGlyph;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCraftConfirmCpuTierGlyphTest {
    private static IECOTier zeroTier;
    private static IECOTier collidingTier;
    private static IECOTier legacyTier;
    private static final ECOCpuTierGlyph ADDON_GLYPH = new ECOCpuTierGlyph(
        Identifier.parse("issue153:textures/gui/tier/l1.png"));

    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            Class.forName(NeoECOAE.class.getName());
        }
        // Register the zero-numbered addon first: registry ID zero must also be a valid glyph source.
        zeroTier = register("zero", 0, ADDON_GLYPH);
        collidingTier = register("colliding", ECOTier.L4.getTier(), new ECOCpuTierGlyph(
            Identifier.parse("issue153:textures/gui/atlas.png"), 39, 18, 128, 64));
        legacyTier = register("legacy", 0, null);
        for (ECOTier tier : ECOTier.values()) {
            if (NERegistries.ECO_TIER.getId(tier) < 0) {
                Registry.register(NERegistries.ECO_TIER, Identifier.parse("neoecoae:" + tier.name().toLowerCase(java.util.Locale.ROOT)), tier);
            }
        }
    }

    private static IECOTier register(String name, int number, ECOCpuTierGlyph glyph) {
        IECOTier tier = mock(IECOTier.class, CALLS_REAL_METHODS);
        when(tier.getTier()).thenReturn(number);
        if (glyph != null) when(tier.getCraftConfirmCpuTierGlyph()).thenReturn(glyph);
        return Registry.register(NERegistries.ECO_TIER, Identifier.parse("issue153:" + name), tier);
    }

    @Test
    void glyphHookIsDefaultAndOldAddonsDrawNothing() throws Exception {
        assertTrue(IECOTier.class.getMethod("getCraftConfirmCpuTierGlyph").isDefault());
        assertNull(legacyTier.getCraftConfirmCpuTierGlyph());
        var menu = new CraftConfirmMenuMixin();
        assertEquals(-1, menu.neoecoae$getSelectedCpuTier());
        assertNull(menu.neoecoae$getSelectedCpuTierGlyph());
    }

    @Test
    void selectedCpuUsesRegistryIdentityIncludingZeroAndNumericCollisions() throws Exception {
        for (IECOTier tier : List.of(zeroTier, collidingTier, legacyTier, ECOTier.L4, ECOTier.L6, ECOTier.L9)) {
            var cpu = mock(ECOCraftingCPU.class);
            when(cpu.getTier()).thenReturn(tier);
            var server = new CraftConfirmMenuMixin();
            var selected = CraftConfirmMenuMixin.class.getDeclaredField("selectedCpu");
            selected.setAccessible(true);
            selected.set(server, cpu);
            var report = CraftConfirmMenuMixin.class.getDeclaredMethod("neoecoae$reportCpuTier");
            report.setAccessible(true);
            int registryId = (int) report.invoke(server);
            assertEquals(NERegistries.ECO_TIER.getId(tier), registryId);
            assertTrue(registryId >= 0);

            // Apply exactly the int carried by GuiSync to a separate client menu.
            var client = new CraftConfirmMenuMixin();
            var synced = CraftConfirmMenuMixin.class.getDeclaredField("neoecoae$selectedCpuTier");
            synced.setAccessible(true);
            synced.setInt(client, registryId);
            assertEquals(tier.getCraftConfirmCpuTierGlyph(), client.neoecoae$getSelectedCpuTierGlyph());
            selected.set(server, mock(ICraftingCPU.class));
            assertEquals(-1, report.invoke(server));
            synced.setInt(client, -1);
            assertNull(client.neoecoae$getSelectedCpuTierGlyph(), "Switching to a foreign CPU clears the glyph");
        }
    }

    @Test
    void builtinsKeepExactAtlasRegionsAndCustomGlyphsUseTheirOwnTexture() throws Exception {
        int sourceX = 0;
        for (ECOTier tier : ECOTier.values()) {
            assertEquals(new ECOCpuTierGlyph(Identifier.parse("neoecoae:textures/guis/crafting_widgets.png"),
                sourceX, 0, 288, 256), tier.getCraftConfirmCpuTierGlyph());
            sourceX += 13;
        }
        for (IECOTier tier : List.of(ECOTier.L4, ECOTier.L6, ECOTier.L9, zeroTier, collidingTier, legacyTier)) {
            var menu = mock(CraftConfirmMenu.class, withSettings().extraInterfaces(ECOCraftConfirmMenuMode.class));
            var glyph = tier.getCraftConfirmCpuTierGlyph();
            when(((ECOCraftConfirmMenuMode) menu).neoecoae$getSelectedCpuTierGlyph()).thenReturn(glyph);
            var screen = mock(ECOCraftConfirmScreen.class, CALLS_REAL_METHODS);
            var menuField = AbstractContainerScreen.class.getDeclaredField("menu");
            menuField.setAccessible(true);
            menuField.set(screen, menu);
            var draw = ECOCraftConfirmScreen.class.getDeclaredMethod("drawCpuTierGlyph", GuiGraphicsExtractor.class);
            draw.setAccessible(true);
            var graphics = mock(GuiGraphicsExtractor.class);
            draw.invoke(screen, graphics);
            if (glyph == null) {
                verifyNoInteractions(graphics);
            } else {
                verify(graphics).blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, glyph.texture(), 38, 5, glyph.u(), glyph.v(),
                    13, 18, glyph.textureWidth(), glyph.textureHeight());
                verifyNoMoreInteractions(graphics);
            }
        }
    }

    @Test
    void standaloneGlyphUsesWholeImageAndRejectsInvalidAtlasRegions() {
        assertEquals(13, ADDON_GLYPH.textureWidth());
        assertEquals(18, ADDON_GLYPH.textureHeight());
        assertThrows(IllegalArgumentException.class, () -> new ECOCpuTierGlyph(ADDON_GLYPH.texture(), 1, 0, 13, 18));
        assertThrows(IllegalArgumentException.class, () -> new ECOCpuTierGlyph(ADDON_GLYPH.texture(), 0, -1, 13, 18));
        assertThrows(IllegalArgumentException.class, () -> new ECOCpuTierGlyph(ADDON_GLYPH.texture(), 0, 0, 13, 17));
    }
}
