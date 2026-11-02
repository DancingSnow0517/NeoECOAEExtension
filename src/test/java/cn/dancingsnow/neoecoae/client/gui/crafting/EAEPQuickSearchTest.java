package cn.dancingsnow.neoecoae.client.gui.crafting;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.extendedae_plus.client.ModKeybindings;
import com.extendedae_plus.compat.JeiRuntimeCompat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import mezz.jei.api.ingredients.ITypedIngredient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EAEPQuickSearchTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void missingEaepOrViewerNeverFillsTheSearch() {
        var mods = mock(ModList.class);
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(mods);
            assertTrue(EAEPQuickSearch.query(GLFW.GLFW_KEY_F, 0, 0).isEmpty());
            when(mods.isLoaded("extendedae_plus")).thenReturn(true);
            assertTrue(EAEPQuickSearch.query(GLFW.GLFW_KEY_F, 0, 0).isEmpty());
        }
    }

    @Test void releasedEaepBindingSupportsRebindingAndSkipsTheRemovedEmiHelper() {
        var mods = mock(ModList.class);
        when(mods.isLoaded("extendedae_plus")).thenReturn(true);
        when(mods.isLoaded("emi")).thenReturn(true);
        var binding = ModKeybindings.FILL_SEARCH_KEY;
        var original = binding.getKey();
        binding.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
        try (var modList = mockStatic(ModList.class)) {
            modList.when(ModList::get).thenReturn(mods);
            // ExtendedAE Plus 26.1 dropped its EMI helper: an EMI install must neither throw nor fill the search,
            // and with no JEI runtime there is no viewer left to ask.
            assertThrows(ClassNotFoundException.class, () -> Class.forName("com.extendedae_plus.compat.EmiHelper"));
            assertTrue(EAEPQuickSearch.query(GLFW.GLFW_KEY_F, 0, 0).isEmpty());
            assertTrue(EAEPQuickSearch.query(GLFW.GLFW_KEY_G, 0, 0).isEmpty());
            assertTrue(EAEPQuickSearch.query(GLFW.GLFW_KEY_G, 0, GLFW.GLFW_MOD_ALT).isEmpty());
        } finally {
            binding.setKey(original);
        }
    }

    @Test void jeiCoordinatesAndFallbackCoverItemsAndFluidDisplayNames() throws Exception {
        ITypedIngredient<?> typed = mock(ITypedIngredient.class);
        try (var jei = mockStatic(JeiRuntimeCompat.class)) {
            jei.when(() -> JeiRuntimeCompat.getIngredientUnderMouse(20, 30)).thenReturn(Optional.of(typed));
            var item = new ItemStack(Items.DIAMOND);
            jei.when(() -> JeiRuntimeCompat.getTypedIngredientValue(typed)).thenReturn(item);
            assertEquals(item.getHoverName().getString(), EAEPQuickSearch.jeiQuery(20, 30, 0).orElseThrow());
            assertEquals("@minecraft", EAEPQuickSearch.jeiQuery(20, 30, GLFW.GLFW_MOD_ALT).orElseThrow());

            jei.when(() -> JeiRuntimeCompat.getIngredientUnderMouse(20, 30)).thenReturn(Optional.empty());
            jei.when(JeiRuntimeCompat::getIngredientUnderMouse).thenReturn(Optional.of(typed));
            jei.when(() -> JeiRuntimeCompat.getTypedIngredientValue(typed)).thenReturn(null);
            jei.when(() -> JeiRuntimeCompat.getTypedIngredientDisplayName(typed)).thenReturn("液态水");
            assertEquals("液态水", EAEPQuickSearch.jeiQuery(20, 30, 0).orElseThrow());
            assertEquals("液态水", EAEPQuickSearch.jeiQuery(20, 30, GLFW.GLFW_MOD_ALT).orElseThrow());
            jei.when(JeiRuntimeCompat::getIngredientUnderMouse).thenReturn(Optional.empty());
            assertTrue(EAEPQuickSearch.jeiQuery(20, 30, 0).isEmpty());
        }
    }
}
