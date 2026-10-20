package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState.Member;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputationMemberTextureTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        try (var mods = org.mockito.Mockito.mockStatic(net.neoforged.fml.ModList.class)) {
            var list = org.mockito.Mockito.mock(net.neoforged.fml.ModList.class);
            org.mockito.Mockito.when(list.getAllScanData()).thenReturn(java.util.List.of());
            mods.when(net.neoforged.fml.ModList::get).thenReturn(list);
            Class.forName(cn.dancingsnow.neoecoae.NeoECOAE.class.getName());
        }
    }

    @Test
    void customMemberImagesKeepTheThreeOriginalCropSizes() {
        var image = ResourceLocation.parse("addon:textures/gui/member.png");
        for (int row = 0; row < 3; row++) {
            var sprite = ComputationControllerTextures.member(new Member(true, 0, image), row);
            assertEquals(image, sprite.getImageLocation());
            assertEquals(0, sprite.spritePosition.x);
            assertEquals(0, sprite.spritePosition.y);
            assertEquals(10, sprite.spriteSize.width);
            assertEquals(row == 0 ? 5 : 6, sprite.spriteSize.height);
        }
    }

    @Test
    void tierZeroWithoutAnImageUsesTheFirstBuiltinColumn() {
        for (int tier = 0; tier <= 3; tier++) {
            for (int row = 0; row < 3; row++) {
                var sprite = ComputationControllerTextures.member(new Member(true, tier, null), row);
                assertEquals(17 + (Math.clamp(tier, 1, 3) - 1) * 12, sprite.spritePosition.x);
                assertEquals(new int[] {67, 53, 61}[row], sprite.spritePosition.y);
            }
        }
    }
}
