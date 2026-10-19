package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Resource-defined geometry with a bounded synchronized task viewport. */
public final class LargeWorkstationUI {
    public static final int PAGE_SIZE = 3;
    private LargeWorkstationUI() {}

    public record Config(IFluidHandler fluids, IntFunction<CompoundTag> snapshot,
        Supplier<HolderLookup.Provider> registries, Consumer<UUID> returnTask,
        Runnable toggleOverclocked, Runnable toggleActiveCooling,
        Runnable cycleOverclockTier, Runnable togglePreheating) {}

    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/workstation/large");

    public static ModularUI create(Config config, Player player) {
        UIElement root = WorkstationUiJson.load(JSON_LAYOUT, new JsonContext(config, player));
        root.addChild(HostSideButtonBar.left(GuideButton.create(player,
            "neoecoae:neoecoae_intro/large_integrated_working_station.md")));
        return ECOModularUI.create(root, player);
    }

    private record JsonContext(Config config, Player player) implements NeoUiJsonContext {
        @Override
        public UIElement createCustom(JsonObject node) {
            return switch (node.get("custom").getAsString()) {
                case "content" -> new LargeWorkstationPanel(config, player);
                case "player_inventory" -> playerInventory(node, player);
                case "input_fluid" -> new WorkstationFluidGauge(config.fluids(), 0, false, false);
                case "output_fluid" -> new WorkstationFluidGauge(config.fluids(), 1, false, false);
                default -> throw new IllegalArgumentException("Unknown large workstation element: " + node);
            };
        }
    }

    private static UIElement playerInventory(JsonObject node, Player player) {
        UIElement inventory = new UIElement();
        for (var value : node.getAsJsonArray("slots")) {
            JsonObject slot = value.getAsJsonObject();
            ItemSlot element = new ItemSlot(new net.minecraft.world.inventory.Slot(
                player.getInventory(), slot.get("index").getAsInt(), 0, 0));
            element.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
            LargeWorkstationPanel.absolute(element, slot.get("left").getAsInt(), slot.get("top").getAsInt(), 18, 18);
            inventory.addChild(element);
        }
        return inventory;
    }

    public static Button settingButton(ECOIcon icon, Runnable action, Supplier<Component> tooltip) {
        return settingButton(AETextures.icon(icon), action, tooltip);
    }

    public static Button settingButton(IGuiTexture icon, Runnable action, Supplier<Component> tooltip) {
        Button button = HostSideButtonBar.createButton().noText().addPostIcon(icon)
            .setOnServerClick(event -> { if (event.button == 0) action.run(); });
        BindableValue<Component> text = new BindableValue<>(tooltip.get());
        text.bind(DataBindingBuilder.componentS2C(tooltip).build());
        text.setDisplay(false);
        button.addChild(text);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = HoverTooltips.empty().append(text.getValue()));
        return button;
    }
}
