package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.Palette;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Formed large-workstation layout. Batch ownership and operations remain in the controller. */
public final class LargeWorkstationUI {
    private LargeWorkstationUI() {}

    public record Config(IFluidHandler fluids, Supplier<Component> energyText,
        Supplier<Component> taskText, Supplier<Component> coolingTierText,
        Supplier<Component> batchSettingsText, Supplier<Component> pauseReasonText,
        BooleanSupplier overclocked, BooleanSupplier activeCooling,
        Runnable returnStoredInputs, Runnable toggleOverclocked, Runnable toggleActiveCooling) {}

    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/workstation/large");

    public static ModularUI create(Config config, Player player) {
        UIElement root = WorkstationUiJson.load(JSON_LAYOUT, new JsonContext(config, player));
        return ECOModularUI.create(root, player);
    }

    private static UIElement createLegacyRoot(Config config, Player player) {
        UIElement root = new UIElement();

        TextElement title = staticLabel("block.neoecoae.large_integrated_working_station", 8, 4, 106, 10);
        title.textStyle(style -> style.fontSize(8));
        root.addChild(title);
        root.addChild(staticLabel("container.inventory", 8, 85, 160, 9));

        // Keep all status text inside the 96x54 inset in the background texture.
        root.addChild(syncedLabel(config.energyText(), 42, 25, 92, 8));
        root.addChild(syncedLabel(config.taskText(), 42, 35, 92, 8));
        root.addChild(syncedLabel(config.coolingTierText(), 42, 45, 92, 8));
        root.addChild(syncedLabel(config.batchSettingsText(), 42, 55, 92, 8));
        root.addChild(syncedLabel(config.pauseReasonText(), 42, 65, 92, 8));

        // Resolve through the controller on every sync, so a rebuilt cluster cannot leave
        // an open screen displaying a detached hatch's tank.
        root.addChild(HostSideButtonBar.left(
            GuideButton.create(player, "neoecoae:neoecoae_intro/large_integrated_working_station.md"),
            settingButton(ECOIcon.ARROW_LEFT, config.returnStoredInputs(), () -> Component.translatable(
                "gui.neoecoae.large_integrated_working_station.return_inputs")),
            settingButton(ECOIcon.POWER_UNIT_AE, config.toggleOverclocked(), () -> Component.translatable(
                config.overclocked().getAsBoolean() ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off")),
            settingButton(ECOIcon.TYPE_FILTER_ALL, config.toggleActiveCooling(), () -> Component.translatable(
                config.activeCooling().getAsBoolean() ? "gui.neoecoae.crafting.active_cooling.on" : "gui.neoecoae.crafting.active_cooling.off"))
        ));
        return root;
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final Config config;
        private final Player player;

        private JsonContext(Config config, Player player) {
            this.config = config;
            this.player = player;
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            return switch (node.has("custom") ? node.get("custom").getAsString() : "") {
                case "content" -> createLegacyRoot(config, player);
                case "player_inventory" -> playerInventory(node, player);
                case "input_fluid" -> fluidGauge(config.fluids(), 0, 8, false);
                case "output_fluid" -> fluidGauge(config.fluids(), 1, 150, false);
                default -> throw new IllegalArgumentException("Unknown large workstation custom element: "
                    + (node.has("custom") ? node.get("custom").getAsString() : ""));
            };
        }

        private static UIElement playerInventory(JsonObject node, Player player) {
            UIElement inventory = new UIElement();
            JsonArray slots = node.getAsJsonArray("slots");
            for (JsonElement value : slots) {
                JsonObject slot = value.getAsJsonObject();
                inventory.addChild(new ItemSlot(new net.minecraft.world.inventory.Slot(
                    player.getInventory(), slot.get("index").getAsInt(), 0, 0))
                    .style(style -> style.backgroundTexture(IGuiTexture.EMPTY))
                    .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                        .left(slot.get("left").getAsInt()).top(slot.get("top").getAsInt())
                        .width(18).height(18)));
            }
            return inventory;
        }
    }

    private static UIElement fluidGauge(IFluidHandler fluids, int tank, int left, boolean withLayout) {
        FluidSlot slot = new FluidSlot().bind(fluids, tank)
            .setAllowClickFilled(false)
            .setAllowClickDrained(false)
            .slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP));
        slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        if (withLayout) {
            slot.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                .left(left).top(20).width(18).height(60));
        }
        return slot;
    }

    public static Button settingButton(ECOIcon icon, Runnable action, java.util.function.Supplier<Component> tooltip) {
        return settingButton(AETextures.icon(icon), action, tooltip);
    }

    public static Button settingButton(IGuiTexture icon, Runnable action,
                                        java.util.function.Supplier<Component> tooltip) {
        Button button = HostSideButtonBar.createButton().noText()
            .addPostIcon(icon).setOnServerClick(event -> action.run());
        BindableValue<Component> text = HostElements.hiddenValue(button, tooltip.get(),
            DataBindingBuilder.componentS2C(tooltip).build());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = HoverTooltips.empty().append(text.getValue()));
        return button;
    }

    private static TextElement staticLabel(String translationKey, int left, int top, int width, int height) {
        TextElement label = new TextElement()
            .setText(translationKey, true)
            .textStyle(style -> style
                .fontSize(6)
                .adaptiveWidth(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(false)
                .textShadow(false)
                .textColor(Palette.INK_403E53));
        label.layout(layout -> layout
            .positionType(TaffyPosition.ABSOLUTE)
            .left(left)
            .top(top)
            .width(width)
            .height(height));
        return label;
    }

    private static Label syncedLabel(java.util.function.Supplier<Component> supplier, int left, int top, int width, int height) {
        return syncedLabel(supplier, left, top, width, height, TextWrap.HOVER_ROLL);
    }

    private static Label syncedLabel(
        java.util.function.Supplier<Component> supplier,
        int left,
        int top,
        int width,
        int height,
        TextWrap textWrap
    ) {
        Label label = new Label();
        label.setText(supplier.get());
        label.bind(DataBindingBuilder.componentS2C(supplier).build());
        label.textStyle(style -> style.fontSize(6).adaptiveWidth(false).textWrap(textWrap).adaptiveHeight(false).textShadow(false).textColor(Palette.INK_403E53));
        label.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(left).top(top).width(width).height(height));
        return label;
    }

}
