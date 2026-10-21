package cn.dancingsnow.neoecoae.client.gui.crafting;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.gui.widget.PatternItemSlotClient;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Bridges the shared crafting-interface UI tree to native Minecraft widgets for text-input focus/IME support. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class CraftingInterfaceClientUI {
    private static final int TOOL_BUTTON_SIZE = 16;
    private static final int SEARCH_QUERY_MAX_LENGTH = 128;
    private static final int SEARCH_TEXT_INSET_X = 2;
    private static final int SEARCH_TEXT_INSET_Y = 2;

    private CraftingInterfaceClientUI() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof ModularUIContainer holder) {
            attachNativeSearchFields(event, ModularUI.from(holder));
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        prepareSearchFields(event.getScreen());
        if (event.getScreen() instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof ModularUIContainer holder
                && ModularUI.from(holder) != null) {
            for (var preview : ModularUI.from(holder).getElementsByType(CraftingInterfaceUI.PatternPreview.class)) {
                preview.advanceFrame();
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)
                || container.getMenu().getCarried().isEmpty()
                || !(container.getMenu() instanceof ModularUIContainer holder)) return;
        var modularUI = ModularUI.from(holder);
        if (modularUI == null || !modularUI.isDrawTooltips() || modularUI.getDragHandler().isDragging()) return;
        var hovered = modularUI.getLastHoveredElement();
        if (hovered == null) return;
        var preview = hovered.getFirstAncestorOfType(CraftingInterfaceUI.PatternPreview.class);
        if (preview == null) return;
        var tooltip = preview.collectHoverTooltips();
        if (tooltip == null || tooltip.tooltipTexts().isEmpty()) return;
        // LDLib suppresses its tooltip pass when the cursor carries an item; draw this insertion hint afterwards.
        event.getGuiGraphics().renderComponentTooltip(Minecraft.getInstance().font, cn.dancingsnow.neoecoae.platform.JdkCompat.first(tooltip.tooltipTexts()),
                event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (handleSearchKeyPressed(event.getScreen(), event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (handleSearchCharTyped(event.getScreen(), event.getCodePoint(), event.getModifiers())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        clearShortcutCharacter(event.getScreen());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Post event) {
        reclaimSearchFieldFocus(event.getScreen());
    }

    public static CraftingInterfaceUI.SearchField createSearchField(Consumer<String> responder) {
        return new NativeSearchField(responder);
    }

    public static PatternItemSlot createPatternSlot(Slot slot) {
        return new PatternItemSlotClient(slot);
    }

    public static void attachNativeSearchFields(ScreenEvent.Init.Post event, ModularUI modularUI) {
        if (modularUI == null) {
            return;
        }

        NativeSearchField first = null;
        for (CraftingInterfaceUI.SearchField field :
                modularUI.getElementsByType(CraftingInterfaceUI.SearchField.class)) {
            if (!(field instanceof NativeSearchField nativeField)) {
                continue;
            }
            // Re-register on every init/resize. Screen.rebuildWidgets() clears children.
            event.removeListener(nativeField.editBox);
            event.addListener(nativeField.editBox);
            if (first == null) {
                first = nativeField;
            }
        }
        if (first != null) {
            first.claimNativeFocus(event.getScreen());
        }
    }

    public static void prepareSearchFields(Screen screen) {
        for (CraftingInterfaceUI.SearchField field : searchFields(screen)) {
            if (field instanceof NativeSearchField nativeField) {
                nativeField.syncNativeBounds();
            }
        }
    }

    public static boolean handleSearchKeyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        List<CraftingInterfaceUI.SearchField> searchFields = searchFields(screen);
        if (searchFields.isEmpty()) return false;
        clearShortcutCharacter(screen);
        NativeSearchField field = focusedSearchField(screen);
        var quickQuery = EAEPQuickSearch.query(keyCode, scanCode, modifiers);
        if (quickQuery.isPresent()) {
            if (field == null) field = searchFields.stream().filter(NativeSearchField.class::isInstance)
                    .map(NativeSearchField.class::cast).findFirst().orElse(null);
            if (field != null) {
                field.claimNativeFocus(screen);
                field.setSearchValue(quickQuery.get());
                // GLFW sends a separate char callback even when the shortcut's key event was canceled.
                field.suppressShortcutCharacter = true;
                return true;
            }
        }
        if (field != null && keyCode == GLFW.GLFW_KEY_TAB) {
            List<NativeSearchField> fields = searchFields.stream()
                    .filter(NativeSearchField.class::isInstance).map(NativeSearchField.class::cast).toList();
            int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
            fields.get(Math.floorMod(fields.indexOf(field) + direction, fields.size())).claimNativeFocus(screen);
            return true;
        }
        return field != null && field.keyPressedNative(keyCode, scanCode, modifiers);
    }

    public static boolean handleSearchCharTyped(Screen screen, char codePoint, int modifiers) {
        NativeSearchField field = focusedSearchField(screen);
        if (field != null && field.suppressShortcutCharacter) {
            field.suppressShortcutCharacter = false;
            return true;
        }
        return field != null && field.charTypedNative(codePoint, modifiers);
    }

    private static void clearShortcutCharacter(Screen screen) {
        for (var field : searchFields(screen)) {
            if (field instanceof NativeSearchField nativeField) nativeField.suppressShortcutCharacter = false;
        }
    }

    public static void reclaimSearchFieldFocus(Screen screen) {
        NativeSearchField field = focusedSearchField(screen);
        if (field != null) {
            field.claimNativeFocus(screen);
        }
    }

    private static NativeSearchField focusedSearchField(Screen screen) {
        // Native mouse/Tab focus wins over the previous ModularUI focus until both are reconciled.
        for (CraftingInterfaceUI.SearchField field : searchFields(screen)) {
            if (field instanceof NativeSearchField nativeField && screen.getFocused() == nativeField.editBox) {
                return nativeField;
            }
        }
        for (CraftingInterfaceUI.SearchField field : searchFields(screen)) {
            if (field instanceof NativeSearchField nativeField && nativeField.ownsNativeInput(screen)) {
                return nativeField;
            }
        }
        return null;
    }

    private static List<CraftingInterfaceUI.SearchField> searchFields(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof ModularUIContainer holder
                && ModularUI.from(holder) != null) {
            return ModularUI.from(holder).getElementsByType(CraftingInterfaceUI.SearchField.class);
        }
        return List.of();
    }

    private static final class NativeSearchField extends CraftingInterfaceUI.SearchField {
        private final PositionedEditBox editBox;
        private int nativeX = Integer.MIN_VALUE;
        private int nativeY = Integer.MIN_VALUE;
        private int nativeWidth = -1;
        private int nativeHeight = -1;
        private boolean suppressShortcutCharacter;

        @Override
        public CraftingInterfaceUI.SearchField setSearchValue(String value) {
            editBox.setValue(value);
            return this;
        }

        private NativeSearchField(Consumer<String> responder) {
            super(responder);
            editBox = new PositionedEditBox();
            editBox.setBordered(false);
            editBox.setTextColor(0xFFFFFF);
            editBox.setTextShadow(false);
            editBox.setMaxLength(SEARCH_QUERY_MAX_LENGTH);
            editBox.setFilter(value -> true);
            editBox.setCanLoseFocus(true);
            editBox.setHint(Component.translatable("gui.neoecoae.crafting_interface.preview.search"));
            editBox.setResponder(this::updateSearch);
            editBox.setVisible(true);
            editBox.active = true;
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 0 && event.button != 1) return;
                claimNativeFocus(Minecraft.getInstance().screen);
                if (event.button == 1) editBox.setValue("");
                else editBox.mouseClicked(JdkCompat.clamp(event.x, editBox.getX(), editBox.getX() + editBox.getWidth() - 1),
                        JdkCompat.clamp(event.y, editBox.getY(), editBox.getY() + editBox.getHeight() - 1), 0);
                event.hasHandler = true;
                event.stopImmediatePropagation();
            });
        }

        @Override
        public CraftingInterfaceUI.SearchField setSearchHint(Component hint) {
            editBox.setHint(hint);
            editBox.setMessage(hint);
            return this;
        }

        private boolean ownsNativeInput(Screen screen) {
            return isFocused() || (screen != null && screen.getFocused() == editBox);
        }

        private void claimNativeFocus(Screen screen) {
            syncNativeBounds();
            if (!isFocused()) focus();
            if (screen != null && screen.getFocused() != editBox) {
                screen.setFocused(editBox);
            }
            if (!editBox.isFocused()) {
                editBox.setFocused(true);
            }
        }

        private boolean keyPressedNative(int keyCode, int scanCode, int modifiers) {
            return editBox.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean charTypedNative(char codePoint, int modifiers) {
            return editBox.charTyped(codePoint, modifiers);
        }

        @Override
        public void drawContents(cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext guiContext) {
            super.drawContents(guiContext);
            syncNativeBounds();
            if (isFocused()) claimNativeFocus(Minecraft.getInstance().screen);
            editBox.renderNative(guiContext.graphics, Math.round(guiContext.mouseX), Math.round(guiContext.mouseY), guiContext.partialTick);
        }

        private void syncNativeBounds() {
            // Keep native text, cursor, and selection inside the recessed search-field sprite.
            int x = Math.round(getPositionX()) + SEARCH_TEXT_INSET_X;
            int y = Math.round(getPositionY()) + SEARCH_TEXT_INSET_Y;
            int width = Math.max(1, Math.round(getSizeWidth()) - SEARCH_TEXT_INSET_X * 2);
            int height = Math.max(1, Math.round(getSizeHeight()) - SEARCH_TEXT_INSET_Y * 2);
            if (x != nativeX) editBox.setX(nativeX = x);
            if (y != nativeY) editBox.setY(nativeY = y);
            if (width != nativeWidth) editBox.setWidth(nativeWidth = width);
            if (height != nativeHeight) editBox.setHeight(nativeHeight = height);
        }

        private final class PositionedEditBox extends EditBox implements cn.dancingsnow.neoecoae.client.gui.widget.NEEditBoxStyle {
            private boolean textShadow=true;
            public void setTextShadow(boolean value){textShadow=value;}
            public boolean neoecoae$textShadow(){return textShadow;}
            private PositionedEditBox() {
                super(Minecraft.getInstance().font, 0, 0, 1, TOOL_BUTTON_SIZE,
                        Component.translatable("gui.neoecoae.crafting_interface.preview.search"));
            }

            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                // Screen owns this widget for native keyboard/IME focus; the field draws it in ModularUI's pass.
            }

            private void renderNative(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public boolean isMouseOver(double mouseX, double mouseY) {
                syncNativeBounds();
                return super.isMouseOver(mouseX, mouseY);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                syncNativeBounds();
                if (!isMouseOver(mouseX, mouseY)) {
                    return false;
                }
                if (button == 1) {
                    setValue("");
                    claimNativeFocus(Minecraft.getInstance().screen);
                    return true;
                }
                boolean handled = super.mouseClicked(mouseX, mouseY, button);
                if (handled) claimNativeFocus(Minecraft.getInstance().screen);
                return handled;
            }

            @Override
            public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
                syncNativeBounds();
                return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }

            @Override
            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                syncNativeBounds();
                return super.mouseReleased(mouseX, mouseY, button);
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (super.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
                // Keep E from closing the GUI while the native field consumes keyboard input.
                return isFocused() && canConsumeInput()
                        && keyCode != GLFW.GLFW_KEY_TAB
                        && keyCode != GLFW.GLFW_KEY_ESCAPE;
            }
        }
    }
}
