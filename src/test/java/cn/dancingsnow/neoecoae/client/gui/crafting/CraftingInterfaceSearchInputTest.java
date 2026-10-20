package cn.dancingsnow.neoecoae.client.gui.crafting;

import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingInterfaceSearchInputTest {
    @BeforeAll static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var modList = mockStatic(ModList.class)) {
            var mods = mock(ModList.class);
            when(mods.getAllScanData()).thenReturn(List.of());
            modList.when(ModList::get).thenReturn(mods);
            Class.forName("com.lowdragmc.lowdraglib2.gui.ui.UIElement");
        }
    }

    @Test void shortcutFillsTheSelectedFieldBeforeTypingAndSuppressesItsCharacterCallback() throws Exception {
        try (var minecraft = mockStatic(Minecraft.class);
             var quickSearch = mockStatic(EAEPQuickSearch.class);
             var screenKeys = mockStatic(Screen.class, CALLS_REAL_METHODS)) {
            screenKeys.when(Screen::hasControlDown).thenReturn(false);
            screenKeys.when(Screen::hasShiftDown).thenReturn(false);
            var client = mock(Minecraft.class);
            var font = mock(Font.class);
            when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
            when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
            var fontField = Minecraft.class.getDeclaredField("font");
            fontField.setAccessible(true);
            fontField.set(client, font);
            minecraft.when(Minecraft::getInstance).thenReturn(client);
            var inputs = new ArrayList<String>();
            var outputs = new ArrayList<String>();
            var input = CraftingInterfaceClientUI.createSearchField(inputs::add);
            var output = CraftingInterfaceClientUI.createSearchField(outputs::add);
            var screen = mock(AbstractContainerScreen.class);
            var menu = mock(AbstractContainerMenu.class, withSettings().extraInterfaces(IModularUIHolder.class));
            var ui = mock(ModularUI.class);
            when(screen.getMenu()).thenReturn(menu);
            when(((IModularUIHolder) menu).getModularUI()).thenReturn(ui);
            when(ui.getElementsByType(CraftingInterfaceUI.SearchField.class)).thenReturn(List.of(input, output));
            var focus = new AtomicReference<GuiEventListener>();
            when(screen.getFocused()).thenAnswer(call -> focus.get());
            doAnswer(call -> {
                if (focus.get() != null) focus.get().setFocused(false);
                focus.set(call.getArgument(0));
                focus.get().setFocused(true);
                return null;
            }).when(screen).setFocused(any());

            quickSearch.when(() -> EAEPQuickSearch.query(GLFW.GLFW_KEY_F, 0, 0)).thenReturn(Optional.of("\u94c1\u952d"));
            assertTrue(CraftingInterfaceClientUI.handleSearchKeyPressed(screen, GLFW.GLFW_KEY_F, 0, 0));
            assertEquals("\u94c1\u952d", inputs.getLast());
            assertTrue(outputs.isEmpty());
            assertTrue(CraftingInterfaceClientUI.handleSearchCharTyped(screen, 'f', 0));
            assertEquals("\u94c1\u952d", inputs.getLast(), "the shortcut character must not be appended to the filled query");

            quickSearch.when(() -> EAEPQuickSearch.query(GLFW.GLFW_KEY_TAB, 0, 0)).thenReturn(Optional.empty());
            assertTrue(CraftingInterfaceClientUI.handleSearchKeyPressed(screen, GLFW.GLFW_KEY_TAB, 0, 0));
            quickSearch.when(() -> EAEPQuickSearch.query(GLFW.GLFW_KEY_F, 0, 0)).thenReturn(Optional.of("\u94bb\u77f3"));
            assertTrue(CraftingInterfaceClientUI.handleSearchKeyPressed(screen, GLFW.GLFW_KEY_F, 0, 0));
            assertEquals("\u94bb\u77f3", outputs.getLast());
            assertEquals("\u94c1\u952d", inputs.getLast());

            quickSearch.when(() -> EAEPQuickSearch.query(GLFW.GLFW_KEY_A, 0, 0)).thenReturn(Optional.empty());
            assertTrue(CraftingInterfaceClientUI.handleSearchKeyPressed(screen, GLFW.GLFW_KEY_A, 0, 0));
            assertTrue(CraftingInterfaceClientUI.handleSearchCharTyped(screen, 'a', 0));
            assertEquals("\u94bb\u77f3a", outputs.getLast(), "a later typed character must survive a shortcut without a char callback");
        }
    }
}
