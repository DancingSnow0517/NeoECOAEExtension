package cn.dancingsnow.neoecoae.gui.workstation;

import appeng.api.orientation.RelativeSide;
import appeng.api.upgrades.Upgrades;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.items.tools.NetworkToolItem;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.google.gson.JsonObject;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Standalone workstation view, including its output-side editor and network-tool slots. */
public final class IntegratedWorkstationUI {
    private static final IGuiTexture AUTO_EXPORT_OFF = AETextures.icon(ECOIcon.AUTO_EXPORT_OFF);
    private static final IGuiTexture AUTO_EXPORT_ON = AETextures.icon(ECOIcon.AUTO_EXPORT_ON);
    private static final ResourceLocation JSON_LAYOUT =
        new ResourceLocation("neoecoae", "screens/workstation/integrated");
    private final Config config;

    private IntegratedWorkstationUI(Config config) {
        this.config = config;
    }

    public record Config(IFluidHandler inputFluid, IFluidHandler outputFluid,
        IItemHandlerModifiable items, IItemHandlerModifiable upgrades,
        IntSupplier processingTime, int maxProcessingSteps, Runnable clearInputFluid, Runnable clearOutputFluid,
        BooleanSupplier autoExport, Consumer<Boolean> setAutoExport, Consumer<Boolean> syncAutoExport,
        Predicate<RelativeSide> outputAllowed, Consumer<RelativeSide> toggleOutput,
        BiConsumer<RelativeSide, Boolean> syncOutput, Function<RelativeSide, ItemStack> adjacentItem,
        Supplier<List<UIElement>> extraButtons) {}

    public static ModularUI create(Config config, Player player) {
        return new IntegratedWorkstationUI(config).create(player);
    }

    public ModularUI create(Player player) {
        UIElement root = WorkstationUiJson.load(JSON_LAYOUT, new JsonContext(config, player));
        return ECOModularUI.create(root, player);
    }

    /** Interactive contents share the background's native 176 by 190 pixel coordinate system. */
    private UIElement createRoot(Player player) {
        UIElement root = new UIElement();
        root.setOverflowVisible(true);
        UIElement allowOutputWindow = WorkstationOutputPanel.create(config);

        root.addChild(place(new TextElement()
            .setText("block.neoecoae.integrated_working_station", true)
            .textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).textShadow(false).textColor(0x403e53)),
            8, 6, 160, 10));
        root.addChild(place(new WorkstationFluidGauge(config.inputFluid(), 0, true, true, 5), 8, 23, 16, 54));
        root.addChild(clearButton(10, config.clearInputFluid()));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                ItemSlot slot = new ItemSlot(new ItemHandlerSlot(config.items(), row + column * 3));
                slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
                root.addChild(place(slot, 36 + column * 18, 24 + row * 18, 16, 16));
            }
        }
        ItemSlot output = new ItemSlot(new ItemHandlerSlot(config.items(), 9));
        output.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        root.addChild(place(output, 123, 43, 16, 16));

        WorkstationProgressBar progress = new WorkstationProgressBar(config.maxProcessingSteps());
        progress.bind(DataBindingBuilder.floatValS2C(() -> (float) config.processingTime().getAsInt()).build());
        progress.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty()
            .append(Component.literal("%.0f%%".formatted(progress.getValue() / Math.max(1, config.maxProcessingSteps()) * 100))));
        root.addChild(place(progress, 123, 68, 16, 3));
        root.addChild(place(new WorkstationFluidGauge(config.outputFluid(), 0, true, false, 5), 152, 23, 16, 54));
        root.addChild(clearButton(154, config.clearOutputFluid()));

        var networkTool = NetworkToolItem.findNetworkToolInv(player);
        Integer networkToolSlot = null;
        if (networkTool != null) {
            networkToolSlot = networkTool.getPlayerInventorySlot();
        }

        List<UIElement> upgradeSlots = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            upgradeSlots.add(new ItemSlot(new ItemHandlerSlot(config.upgrades(), i))
                .slotStyle(style -> style
                    .slotOverlay(AETextures.icon(ECOIcon.BACKGROUND_UPGRADE))
                    .showSlotOverlayOnlyEmpty(true))
                .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                    List<Component> tooltips = new ArrayList<>();
                    tooltips.add(GuiText.CompatibleUpgrades.text());
                    tooltips.addAll(Upgrades.getTooltipLinesForMachine(NEBlocks.INTEGRATED_WORKING_STATION));
                    event.hoverTooltips = new HoverTooltips(tooltips, null, null, null);
                }));
        }
        root.addChild(HostSideButtonBar.rightSlots(upgradeSlots));

        List<UIElement> sideButtons = new ArrayList<>(List.of(
            GuideButton.create(player, "neoecoae:neoecoae_intro/integrated_working_station.md"),
            createAutoExportButton(),
            WorkstationOutputPanel.createOpenButton(allowOutputWindow)
        ));
        sideButtons.addAll(config.extraButtons().get());
        root.addChild(HostSideButtonBar.left(sideButtons));

        root.addChild(place(new TextElement()
            .setText("container.inventory", true)
            .textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).textShadow(false).textColor(0x403e53)),
            8, 94, 160, 10));

        var inventory = WorkstationInventoryUI.createPlayerInventory(player.getInventory(), networkToolSlot);
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        inventory.apply(slot -> slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        inventory.getChildren().forEach(child -> child.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        root.addChild(place(inventory, 7, 105, 162, 76));
        if (networkTool != null) {
            root.addChild(WorkstationInventoryUI.createNetworkToolbox(networkTool)
                .layout(layout -> layout.left(174).top(101)));
        }

        // Add absolute-positioned floating window last so it renders on top
        root.addChild(allowOutputWindow);
        return root;
    }

    private static <T extends UIElement> T place(T element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
            .left(x).top(y).width(width).height(height).paddingAll(0));
        return element;
    }

    private static Button clearButton(int x, Runnable clear) {
        Button button = new Button().noText().setOnServerClick(event -> clear.run());
        button.buttonStyle(style -> style.baseTexture(WorkstationTextures.widget(0, 6, 12, 11))
            .hoverTexture(WorkstationTextures.widget(12, 6, 12, 11))
            .pressedTexture(WorkstationTextures.widget(24, 6, 12, 11)));
        return place(button, x, 81, 12, 11);
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final IntegratedWorkstationUI owner;
        private final Player player;

        private JsonContext(Config config, Player player) {
            this.owner = new IntegratedWorkstationUI(config);
            this.player = player;
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            if ("content".equals(string(node, "custom", ""))) {
                return owner.createRoot(player);
            }
            throw new IllegalArgumentException("Unknown integrated workstation custom element: "
                + string(node, "custom", ""));
        }

        private static String string(JsonObject node, String key, String fallback) {
            return node.has(key) && node.get(key).isJsonPrimitive() ? node.get(key).getAsString() : fallback;
        }
    }

    private Button createAutoExportButton() {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPostIcon(config.autoExport().getAsBoolean() ? AUTO_EXPORT_ON : AUTO_EXPORT_OFF)
            .setOnServerClick(e -> config.setAutoExport().accept(!config.autoExport().getAsBoolean()));
        UIElement icon = button.getChildren().getLast();
        BindableValue<Boolean> syncedState = new BindableValue<>(config.autoExport().getAsBoolean());
        syncedState.bind(DataBindingBuilder.boolS2C(() -> config.autoExport().getAsBoolean()).build());
        syncedState.registerValueListener(on -> {
            config.syncAutoExport().accept(Boolean.TRUE.equals(on));
            icon.style(style -> style.backgroundTexture(config.autoExport().getAsBoolean() ? AUTO_EXPORT_ON : AUTO_EXPORT_OFF));
        });
        syncedState.setDisplay(false);
        button.addChild(syncedState);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            List.of(
                ButtonToolTips.AutoExport.text().withColor(-1),
                (config.autoExport().getAsBoolean() ? ButtonToolTips.AutoExportOn : ButtonToolTips.AutoExportOff).text().withStyle(ChatFormatting.GRAY)
            ),
            null,
            null,
            null
        ));
        return button;
    }

}
