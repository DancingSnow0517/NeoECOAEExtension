package cn.dancingsnow.neoecoae.gui.workstation;

import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingListCard;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Resource-defined geometry with a bounded synchronized task viewport. */
public final class LargeWorkstationUI {
    public static final int PAGE_SIZE = 3;
    public static final int SNAPSHOT_PADDING = 2;
    public static final int SNAPSHOT_SIZE = PAGE_SIZE + 2 * SNAPSHOT_PADDING;
    private LargeWorkstationUI() {}

    public record Config(IFluidHandler fluids, IntFunction<CompoundTag> snapshot,
        Supplier<HolderLookup.Provider> registries, Consumer<UUID> returnTask,
        Runnable toggleOverclocked, Runnable toggleActiveCooling,
        Runnable togglePreheating, IItemHandlerModifiable moduleInventory) {}

    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/workstation/large");

    public static ModularUI create(Config config, Player player) {
        return create(config, player, () -> false, null);
    }

    public static ModularUI create(Config config, Player player, BooleanSupplier fePreferred, Runnable togglePriority) {
        UIElement root = WorkstationUiJson.load(JSON_LAYOUT, new JsonContext(config, player));
        root.setOverflowVisible(true);
        var guide = GuideButton.create(player, "neoecoae:neoecoae_intro/large_integrated_working_station.md");
        var panel = (LargeWorkstationPanel) root.selectId("large_workstation_content").findFirst().orElseThrow();
        root.addChild(togglePriority == null ? HostSideButtonBar.left(guide)
            : HostSideButtonBar.left(guide, powerPriorityButton(fePreferred, togglePriority,
                () -> panel.getValue() instanceof CompoundTag tag ? tag : new CompoundTag())));
        return ECOModularUI.create(root, player);
    }

    static Button powerPriorityButton(BooleanSupplier fePreferred, Runnable toggle, Supplier<CompoundTag> snapshot) {
        Button button = HostSideButtonBar.createButton().noText();
        button.setId("large-workstation-power-priority");
        var card = new LargeWorkstationPowerPriorityCard(fePreferred.getAsBoolean());
        var icon = new UIElement().style(style -> style.backgroundTexture(WorkstationTextures.powerPriority(fePreferred.getAsBoolean())));
        icon.setId("power-priority-icon");
        button.addChild(icon);
        long[] acknowledgedClicks = {0};
        button.setOnServerClick(event -> {
            if (event.button == 0) {
                toggle.run();
                acknowledgedClicks[0]++;
            }
        });
        button.setOnClick(event -> {
            card.click();
            icon.style(style -> style.backgroundTexture(WorkstationTextures.powerPriority(card.fePreferred())));
        });
        Supplier<Tag> priorityState = () -> {
            var state = new CompoundTag();
            state.putBoolean("fePreferred", fePreferred.getAsBoolean());
            state.putLong("acknowledgedClicks", acknowledgedClicks[0]);
            return state;
        };
        var synced = new BindableValue<>(priorityState.get());
        synced.bind(DataBindingBuilder.tagS2C(priorityState).build());
        synced.registerValueListener(value -> {
            if (value instanceof CompoundTag state) card.receive(state);
            icon.style(style -> style.backgroundTexture(WorkstationTextures.powerPriority(card.fePreferred())));
        });
        synced.setDisplay(false);
        button.addChild(synced);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS,
            event -> event.hoverTooltips = CraftingListCard.hover(card.builder(snapshot.get()).build()));
        return button;
    }

    /** Advance before LDLib calculates transforms and resolves this frame's hovered element. */
    public static void advanceFrame(ModularUI ui) {
        for (var panel : ui.getElementsByType(LargeWorkstationPanel.class)) panel.advanceFrame();
    }

    private record JsonContext(Config config, Player player) implements NeoUiJsonContext {
        @Override
        public UIElement createCustom(JsonObject node) {
            return switch (node.get("custom").getAsString()) {
                case "content" -> new LargeWorkstationPanel(config, player);
                case "player_inventory" -> playerInventory(node, player);
                case "input_fluid" -> fluidGauge(config.fluids(), 0, "input");
                case "output_fluid" -> fluidGauge(config.fluids(), 1, "output");
                case "module_card" -> moduleSlot(config.moduleInventory());
                default -> throw new IllegalArgumentException("Unknown large workstation element: " + node);
            };
        }
    }

    private static UIElement playerInventory(JsonObject node, Player player) {
        UIElement inventory = new UIElement();
        // Only actual slots should intercept the controls behind this full-panel container.
        inventory.setAllowHitTest(false);
        for (var value : node.getAsJsonArray("slots")) {
            JsonObject slot = value.getAsJsonObject();
            ItemSlot element = new ItemSlot(new net.minecraft.world.inventory.Slot(
                player.getInventory(), slot.get("index").getAsInt(), 0, 0));
            element.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
            // JSON coordinates name the 16-pixel contents inside the baked slot frame.
            element.slotStyle(style -> style.isPlayerSlot(true));
            LargeWorkstationPanel.absolute(element, slot.get("left").getAsInt(), slot.get("top").getAsInt(), 16, 16);
            inventory.addChild(element);
        }
        return inventory;
    }

    private static WorkstationFluidGauge fluidGauge(IFluidHandler fluids, int tank, String name) {
        return new WorkstationFluidGauge(fluids, tank, false, false).withTooltipRole(name);
    }

    private static ItemSlot moduleSlot(IItemHandlerModifiable inventory) {
        ItemSlot slot = new ItemSlot(new ItemHandlerSlot(inventory, 0) {
            @Override
            public int getMaxStackSize(net.minecraft.world.item.ItemStack stack) {
                // A capacity query must not briefly remove the card and republish the pattern catalog.
                return Math.min(getMaxStackSize(), stack.getMaxStackSize());
            }
        }) {
            @Override
            protected void onHoverTooltips(UIEvent event) {
                var lines = new ArrayList<Component>();
                lines.add(Component.translatable("gui.neoecoae.workstation.module_slot").withStyle(ChatFormatting.YELLOW));
                if (!getValue().isEmpty()) lines.addAll(getFullTooltipTexts());
                else lines.add(Component.translatable("gui.neoecoae.workstation.module_slot_empty"));
                lines.add(Component.translatable("gui.neoecoae.workstation.module_slot_hint").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.neoecoae.workstation.module_swap_hint").withStyle(ChatFormatting.GRAY));
                event.hoverTooltips = new HoverTooltips(lines, null, null, getValue());
            }
        };
        slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        return slot;
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
