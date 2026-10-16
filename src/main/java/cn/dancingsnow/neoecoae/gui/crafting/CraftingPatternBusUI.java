package cn.dancingsnow.neoecoae.gui.crafting;

import static cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity.COL_SIZE;
import static cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity.ROW_SIZE;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Pattern-bus layout and page buttons over a machine-owned inventory view. */
public final class CraftingPatternBusUI {
    private CraftingPatternBusUI() {}

    public record Config(Supplier<Component> title, IItemHandlerModifiable inventory,
                         IntSupplier pageCount, IntSupplier currentPage, IntConsumer changePage) {}

    private static final int PAGE_BUTTON_SIZE = 16;
    private static final int PAGE_CONTROL_GAP = 4;
    private static final int HEADER_HEIGHT = 36;
    private static final int SINGLE_PAGE_HEADER_HEIGHT = 16;
    private static final int HEADER_TITLE_TOP = 2;
    private static final int PAGE_TOP_MARGIN = 19;
    private static final int PAGE_RIGHT_MARGIN = 2;
    private static final int PAGE_CONTROLS_OFFSET_X = 1;
    private static final int PAGE_LABEL_WIDTH = 16;
    private static final int UI_CONTENT_WIDTH = ROW_SIZE * 18;
    private static final int PAGE_CONTROLS_WIDTH = PAGE_BUTTON_SIZE * 2 + PAGE_CONTROL_GAP * 2 + PAGE_LABEL_WIDTH;
    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/crafting/crafting_pattern_bus");

    public static ModularUI create(Config config, Player player) {
        ShiftQuickMove quickMove = new ShiftQuickMove(player);
        UIElement root = NeoUiJsonLoader.load(JSON_LAYOUT, new JsonContext(config, quickMove));
        quickMove.attachRoot(root);
        return ECOModularUI.create(root, player);
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;
        private final ShiftQuickMove quickMove;

        private JsonContext(Config config, ShiftQuickMove quickMove) {
            this.config = config;
            this.quickMove = quickMove;
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            return switch (string(node, "custom", "")) {
                case "header" -> headerRow(config);
                case "pattern_inventory" -> patternInventory(config, quickMove);
                case "player_inventory" -> playerInventory(quickMove);
                default -> throw new IllegalArgumentException("Unknown crafting pattern bus custom element: "
                    + string(node, "custom", ""));
            };
        }

        @Override
        public void configure(JsonObject node, UIElement element) {
            // The loader applies the static root class and geometry.  Page buttons and slot
            // handlers are constructed by the custom nodes because they depend on live state.
            if ("crafting_pattern_bus_root".equals(string(node, "id", ""))) {
                element.layout(layout -> layout.justifyContent(AlignContent.CENTER));
            }
        }

        private static String string(JsonObject node, String key, String fallback) {
            return node.has(key) && node.get(key).isJsonPrimitive() ? node.get(key).getAsString() : fallback;
        }
    }

    private static UIElement patternInventory(Config config, ShiftQuickMove quickMove) {
        UIElement patternInv = new UIElement().addClass("panel_border");
        for (int row = 0; row < COL_SIZE; row++) {
            UIElement rowInv = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW));
            for (int col = 0; col < ROW_SIZE; col++) {
                int slotIndex = row * ROW_SIZE + col;
                UIElement slot = new PatternItemSlot(new ItemHandlerSlot(config.inventory(), slotIndex))
                    .slotStyle(style -> style.slotOverlay(AETextures.icon(ECOIcon.BACKGROUND_BLANK_PATTERN)))
                    .addClass("eco-pattern-slot");
                quickMove.attach(slot);
                rowInv.addChild(slot);
            }
            patternInv.addChild(rowInv);
        }
        return patternInv;
    }

    private static UIElement playerInventory(ShiftQuickMove quickMove) {
        InventorySlots inventory = new InventorySlots();
        inventory.layout(layout -> layout.marginTop(5));
        inventory.apply(slot -> quickMove.attach(slot));
        return inventory;
    }

    /**
     * LDLib2 renders these slots outside vanilla's screen drag path. Keep the same
     * Shift-drag gesture as Mouse Tweaks by issuing one server-validated quick move
     * for every distinct slot entered while the button is held.
     */
    private static final class ShiftQuickMove {
        private final Player player;
        private final java.util.Set<Integer> visited = new java.util.HashSet<>();
        private boolean dragging;

        private ShiftQuickMove(Player player) {
            this.player = player;
        }

        private void attachRoot(UIElement root) {
            root.addEventListener(UIEvents.MOUSE_UP, event -> {
                if (event.button == 0) {
                    dragging = false;
                    visited.clear();
                }
            }, true);
            root.addServerEventListener(UIEvents.MOUSE_UP, event -> {
                if (event.button == 0) reset();
            }, true);
            root.addEventListener(UIEvents.TICK, event -> {
                if (dragging && !root.isMouseDown(0)) {
                    dragging = false;
                    visited.clear();
                }
            });
        }

        private void attach(UIElement element) {
            if (!(element instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot itemSlot)) return;
            UIEventListener listener = event -> {
                if (event.button != 0 || !event.isShiftDown()) return;
                reset();
                dragging = true;
                quickMove(itemSlot.getSlot());
                var payload = new net.minecraft.nbt.CompoundTag();
                payload.putBoolean("start", true);
                itemSlot.sendMessage("neoecoae:shift_quick_move", payload);
                event.hasHandler = true;
                event.stopImmediatePropagation();
            };
            UIEventListener enter = event -> {
                if (dragging && event.isShiftDown() && itemSlot.isMouseDown(0)) {
                    quickMove(itemSlot.getSlot());
                    itemSlot.sendMessage("neoecoae:shift_quick_move");
                    event.hasHandler = true;
                    event.stopImmediatePropagation();
                }
            };
            element.addEventListener(UIEvents.MOUSE_DOWN, listener);
            element.addEventListener(UIEvents.MOUSE_ENTER, enter);
            element.onMessage("neoecoae:shift_quick_move", ignored -> {
                if (ignored.getBoolean("start")) reset();
                dragging = true;
                quickMove(itemSlot.getSlot());
            });
        }

        private void reset() {
            dragging = false;
            visited.clear();
        }

        private void quickMove(Slot slot) {
            if (slot == null || !visited.add(slot.index) || player.level().isClientSide) return;
            if (player.containerMenu != null) {
                player.containerMenu.clicked(slot.index, 0, ClickType.QUICK_MOVE, player);
            }
        }
    }

    private static UIElement headerRow(Config config) {
        boolean showPageControls = config.pageCount().getAsInt() > 1;
        UIElement row = new UIElement().layout(layout -> {
            layout.width(UI_CONTENT_WIDTH);
            layout.height(showPageControls ? HEADER_HEIGHT : SINGLE_PAGE_HEADER_HEIGHT);
        });
        row.addChild(new TextElement()
            .setText(config.title().get())
            .textStyle(textStyle -> textStyle
                .textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(true))
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(HEADER_TITLE_TOP);
                layout.width(UI_CONTENT_WIDTH);
                layout.height(12);
            }));
        if (showPageControls) {
            row.addChild(pageControls(config));
        }
        return row;
    }

    private static UIElement pageControls(Config config) {
        UIElement controls = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(UI_CONTENT_WIDTH - PAGE_RIGHT_MARGIN - PAGE_CONTROLS_WIDTH + PAGE_CONTROLS_OFFSET_X);
            layout.top(PAGE_TOP_MARGIN);
            layout.width(PAGE_CONTROLS_WIDTH);
            layout.height(PAGE_BUTTON_SIZE);
        });
        controls.addChild(pageButton("<", () -> config.changePage().accept(config.currentPage().getAsInt() - 1)).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(PAGE_BUTTON_SIZE);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        TextElement pageNumber = new TextElement()
            .setText(Component.literal((config.currentPage().getAsInt() + 1) + "/" + config.pageCount().getAsInt()));
        controls.addChild(pageNumber.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP);
            layout.top(0);
            layout.width(PAGE_LABEL_WIDTH);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        controls.addChild(pageButton(">", () -> config.changePage().accept(config.currentPage().getAsInt() + 1)).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP + PAGE_LABEL_WIDTH + PAGE_CONTROL_GAP);
            layout.top(0);
            layout.width(PAGE_BUTTON_SIZE);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        return controls;
    }

    private static Button pageButton(String text, Runnable action) {
        Button button = new Button().setText(text);
        button.setOnServerClick(event -> action.run());
        return button;
    }

}
