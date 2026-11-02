package cn.dancingsnow.neoecoae.client.gui.crafting;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.gui.widget.PatternItemSlotClient;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingInterfaceUI;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIClientAccess;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
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
                && container.getMenu() instanceof IModularUIHolder holder) {
            attachNativeSearchFields(event, holder.getModularUI());
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        prepareSearchFields(event.getScreen());
        if (event.getScreen() instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof IModularUIHolder holder
                && holder.getModularUI() != null) {
            for (var preview : holder.getModularUI().getElementsByType(CraftingInterfaceUI.PatternPreview.class)) {
                preview.advanceFrame();
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)
                || container.getMenu().getCarried().isEmpty()
                || !(container.getMenu() instanceof IModularUIHolder holder)) return;
        var modularUI = holder.getModularUI();
        if (modularUI == null || !modularUI.isDrawTooltips() || modularUI.getDragHandler().isDragging()) return;
        var hovered = modularUI.getLastHoveredElement();
        if (hovered == null) return;
        var preview = hovered.getFirstAncestorOfType(CraftingInterfaceUI.PatternPreview.class);
        if (preview == null) return;
        var tooltip = collectHoverTooltips(preview);
        if (tooltip == null || tooltip.tooltips().isEmpty()
                || !(tooltip.tooltips().getFirst() instanceof Component first)) return;
        // LDLib suppresses its tooltip pass when the cursor carries an item; draw this insertion hint afterwards.
        event.getGuiGraphics().setTooltipForNextFrame(Minecraft.getInstance().font, first,
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
        if (handleSearchCharTyped(event.getScreen(), event.getCharacterEvent())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        clearShortcutCharacter(event.getScreen());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Post event) {
        reclaimSearchFieldFocus(event.getScreen());
    }

    /** Same resolution as newer LDLib2's UIElement#collectHoverTooltips (absent from the 26.1.2.22 jar). */
    private static com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips collectHoverTooltips(
            com.lowdragmc.lowdraglib2.gui.ui.UIElement element) {
        var tooltipEvent = com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent.create(UIEvents.HOVER_TOOLTIPS);
        tooltipEvent.hasBubblePhase = false;
        tooltipEvent.hasCapturePhase = false;
        tooltipEvent.target = element;
        com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher.dispatchDirectEvent(tooltipEvent, false);
        if (tooltipEvent.hoverTooltips != null) return tooltipEvent.hoverTooltips;
        var styleTooltips = element.getStyle().tooltips();
        return styleTooltips.isEmpty() ? null
            : com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips.create((Object[]) styleTooltips.tooltips());
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

        var widget = ModularUIClientAccess.getWidget(modularUI);
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
        return field != null && field.keyPressedNative(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public static boolean handleSearchCharTyped(Screen screen, CharacterEvent characterEvent) {
        NativeSearchField field = focusedSearchField(screen);
        if (field != null && field.suppressShortcutCharacter) {
            field.suppressShortcutCharacter = false;
            return true;
        }
        return field != null && field.charTypedNative(characterEvent);
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
                && container.getMenu() instanceof IModularUIHolder holder
                && holder.getModularUI() != null) {
            return holder.getModularUI().getElementsByType(CraftingInterfaceUI.SearchField.class);
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
            editBox.setTextColor(0xFFFFFFFF);
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
                else editBox.mouseClicked(new MouseButtonEvent(
                        Math.clamp(event.x, editBox.getX(), editBox.getX() + editBox.getWidth() - 1),
                        Math.clamp(event.y, editBox.getY(), editBox.getY() + editBox.getHeight() - 1),
                        new MouseButtonInfo(0, 0)), false);
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

        private boolean keyPressedNative(KeyEvent keyEvent) {
            return editBox.keyPressed(keyEvent);
        }

        private boolean charTypedNative(CharacterEvent characterEvent) {
            return editBox.charTyped(characterEvent);
        }

        @Override
        protected void drawBackgroundTexture(IGUIContext context) {
            super.drawBackgroundTexture(context);
            if (!(context instanceof GUIContext guiContext)) return;
            syncNativeBounds();
            if (isFocused()) claimNativeFocus(Minecraft.getInstance().screen);
            editBox.renderNative(guiContext.graphics, guiContext.mouseX, guiContext.mouseY, guiContext.partialTick);
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

        private final class PositionedEditBox extends EditBox {
            private PositionedEditBox() {
                super(Minecraft.getInstance().font, 0, 0, 1, TOOL_BUTTON_SIZE,
                        Component.translatable("gui.neoecoae.crafting_interface.preview.search"));
            }

            @Override
            public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                // Screen owns this widget for native keyboard/IME focus; the field draws it in ModularUI's pass.
            }

            private void renderNative(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public boolean isMouseOver(double mouseX, double mouseY) {
                syncNativeBounds();
                return super.isMouseOver(mouseX, mouseY);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                syncNativeBounds();
                int button = event.button();
                if (!isMouseOver(event.x(), event.y())) {
                    return false;
                }
                if (button == 1) {
                    setValue("");
                    claimNativeFocus(Minecraft.getInstance().screen);
                    return true;
                }
                boolean handled = super.mouseClicked(event, doubleClick);
                if (handled) claimNativeFocus(Minecraft.getInstance().screen);
                return handled;
            }

            @Override
            public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
                syncNativeBounds();
                return super.mouseDragged(event, dragX, dragY);
            }

            @Override
            public boolean mouseReleased(MouseButtonEvent event) {
                syncNativeBounds();
                return super.mouseReleased(event);
            }

            @Override
            public boolean keyPressed(KeyEvent keyEvent) {
                int keyCode = keyEvent.key();
                if (super.keyPressed(keyEvent)) {
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
