package cn.dancingsnow.neoecoae.client;

import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/** Bridges the shared crafting-interface UI tree to native Minecraft widgets for text-input focus/IME support. */
public final class CraftingInterfaceClientUI {
    private static final int TOOL_BUTTON_SIZE = 16;
    private static final int SEARCH_QUERY_MAX_LENGTH = 128;
    private static final int SEARCH_TEXT_INSET_X = 2;
    private static final int SEARCH_TEXT_INSET_Y = 3;

    private CraftingInterfaceClientUI() {
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

        var widget = modularUI.getWidget();
        NativeSearchField first = null;
        for (CraftingInterfaceUI.SearchField field :
                modularUI.getElementsByType(CraftingInterfaceUI.SearchField.class)) {
            if (!(field instanceof NativeSearchField nativeField)) {
                continue;
            }
            // Re-register on every init/resize. Screen.rebuildWidgets() clears children.
            event.removeListener(nativeField.editBox);
            event.removeListener(widget);
            event.addListener(nativeField.editBox);
            event.addListener(widget);
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
        NativeSearchField field = focusedSearchField(screen);
        return field != null && field.keyPressedNative(keyCode, scanCode, modifiers);
    }

    public static boolean handleSearchCharTyped(Screen screen, char codePoint, int modifiers) {
        NativeSearchField field = focusedSearchField(screen);
        return field != null && field.charTypedNative(codePoint, modifiers);
    }

    public static void reclaimSearchFieldFocus(Screen screen) {
        NativeSearchField field = focusedSearchField(screen);
        if (field != null) {
            field.claimNativeFocus(screen);
        }
    }

    private static NativeSearchField focusedSearchField(Screen screen) {
        for (CraftingInterfaceUI.SearchField field : searchFields(screen)) {
            if (field instanceof NativeSearchField nativeField && nativeField.ownsNativeInput(screen)) {
                return nativeField;
            }
        }
        return null;
    }

    private static List<CraftingInterfaceUI.SearchField> searchFields(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof IModularUIHolder holder
                && holder.getModularUI() != null) {
            return holder.getModularUI().getElementsByType(CraftingInterfaceUI.SearchField.class);
        }
        return List.of();
    }

    private static final class NativeSearchField extends CraftingInterfaceUI.SearchField {
        private final PositionedEditBox editBox;

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

            addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.TICK, event -> {
                syncNativeBounds();
                if (isFocused()) {
                    claimNativeFocus(Minecraft.getInstance().screen);
                }
            });
        }

        private boolean ownsNativeInput(Screen screen) {
            return isFocused() || (screen != null && screen.getFocused() == editBox);
        }

        private void claimNativeFocus(Screen screen) {
            syncNativeBounds();
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
        public void drawContents(com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext guiContext) {
            super.drawContents(guiContext);
            syncNativeBounds();
            editBox.renderNative(guiContext.graphics, guiContext.mouseX, guiContext.mouseY, guiContext.partialTick);
        }

        private void syncNativeBounds() {
            // Keep native text, cursor, and selection inside the recessed search-field sprite.
            editBox.setX(Math.round(getPositionX()) + SEARCH_TEXT_INSET_X);
            editBox.setY(Math.round(getPositionY()) + SEARCH_TEXT_INSET_Y);
            editBox.setWidth(Math.max(1, Math.round(getSizeWidth())));
            editBox.setHeight(Math.max(1, Math.round(getSizeHeight())));
        }

        private final class PositionedEditBox extends EditBox {
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
                    setFocused(true);
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
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
