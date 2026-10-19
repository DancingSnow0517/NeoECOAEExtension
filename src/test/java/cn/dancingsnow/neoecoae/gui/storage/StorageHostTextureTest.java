package cn.dancingsnow.neoecoae.gui.storage;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageHostTextureTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        try (var mods = org.mockito.Mockito.mockStatic(net.neoforged.fml.ModList.class)) {
            var list = org.mockito.Mockito.mock(net.neoforged.fml.ModList.class);
            org.mockito.Mockito.when(list.getAllScanData()).thenReturn(java.util.List.of());
            mods.when(net.neoforged.fml.ModList::get).thenReturn(list);
            Class.forName(cn.dancingsnow.neoecoae.NeoECOAE.class.getName());
            // Texture initialization scans LDLib registrations and must happen while the mod list exists.
            Class.forName(cn.dancingsnow.neoecoae.gui.theme.NETextures.class.getName());
        }
    }

    @Test
    void customBadgeAndMarkersUseStandaloneImagesAndBothMarkerRows() {
        var image = ResourceLocation.parse("addon:textures/gui/host.png");
        var badge = StoragePanelTextures.tier(0, image);
        assertEquals(image, badge.getImageLocation());
        assertEquals(0, badge.spritePosition.x);
        assertEquals(0, badge.spritePosition.y);
        assertEquals(13, badge.spriteSize.width);
        assertEquals(18, badge.spriteSize.height);
        for (boolean bottom : new boolean[] {false, true}) {
            var marker = StoragePanelTextures.hostMarker(0, bottom, image);
            assertEquals(image, marker.getImageLocation());
            assertEquals(0, marker.spritePosition.x);
            assertEquals(bottom ? 8 : 0, marker.spritePosition.y);
            assertEquals(13, marker.spriteSize.width);
            assertEquals(8, marker.spriteSize.height);
        }
    }

    @Test
    void nullImagesKeepBuiltinCropsAndClampTierZeroToTheFirstColumn() {
        for (int tier = 0; tier <= 3; tier++) {
            assertEquals((Math.clamp(tier, 1, 3) - 1) * 13,
                StoragePanelTextures.tier(tier, null).spritePosition.x);
            for (boolean bottom : new boolean[] {false, true}) {
                var marker = StoragePanelTextures.hostMarker(tier, bottom, null);
                assertEquals(18 + (Math.clamp(tier, 1, 3) - 1) * 16, marker.spritePosition.x);
                assertEquals(bottom ? 43 : 19, marker.spritePosition.y);
            }
        }
    }
}
