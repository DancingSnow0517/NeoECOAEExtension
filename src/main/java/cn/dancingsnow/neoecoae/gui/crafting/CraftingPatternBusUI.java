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
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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
        UIElement root = NeoUiJsonLoader.load(JSON_LAYOUT, new JsonContext(config));
        return ECOModularUI.create(root, player);
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;

        private JsonContext(Config config) {
            this.config = config;
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            return switch (node.has("custom") ? node.get("custom").getAsString() : "") {
                case "header" -> headerRow(config);
                case "pattern_inventory" -> patternInventory(config);
                case "player_inventory" -> new InventorySlots().layout(layout -> layout.marginTop(5));
                default -> throw new IllegalArgumentException("Unknown crafting pattern bus custom element: "
                    + (node.has("custom") ? node.get("custom").getAsString() : ""));
            };
        }

        @Override
        public void configure(JsonObject node, UIElement element) {
            // The loader applies the static root class and geometry.  Page buttons and slot
            // handlers are constructed by the custom nodes because they depend on live state.
            if ("crafting_pattern_bus_root".equals(node.has("id") ? node.get("id").getAsString() : "")) {
                element.layout(layout -> layout.justifyContent(AlignContent.CENTER));
            }
        }
    }

    private static UIElement patternInventory(Config config) {
        UIElement patternInv = new UIElement().addClass("panel_border");
        for (int row = 0; row < COL_SIZE; row++) {
            UIElement rowInv = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW));
            for (int col = 0; col < ROW_SIZE; col++) {
                int slotIndex = row * ROW_SIZE + col;
                UIElement slot = new PatternItemSlot(new ItemHandlerSlot(config.inventory(), slotIndex))
                    .slotStyle(style -> style.slotOverlay(AETextures.icon(ECOIcon.BACKGROUND_BLANK_PATTERN)))
                    .addClass("eco-pattern-slot");
                rowInv.addChild(slot);
            }
            patternInv.addChild(rowInv);
        }
        return patternInv;
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
