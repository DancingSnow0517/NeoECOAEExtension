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
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.google.gson.JsonObject;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Standalone workstation view, including its output-side editor and network-tool slots. */
public final class IntegratedWorkstationUI {
    private static final IGuiTexture AUTO_EXPORT_OFF = AETextures.icon(ECOIcon.AUTO_EXPORT_OFF);
    private static final IGuiTexture AUTO_EXPORT_ON = AETextures.icon(ECOIcon.AUTO_EXPORT_ON);
    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "ui/workstation/integrated");
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

    /** Legacy dynamic subtree retained behind the resource-defined root. */
    private UIElement createLegacyRoot(Player player) {
        UIElement root = new UIElement();

        // Floating output side config window (hidden by default)
        UIElement allowOutputWindow = WorkstationOutputPanel.create(config);

        root.addChild(new TextElement()
            .setText("block.neoecoae.integrated_working_station", true)
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true).textShadow(false).textColor(0x403e53)));

        UIElement inputArea = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW).marginBottom(5));
        // Input Fluid
        UIElement inputFluid = new UIElement().addClass("panel_border");
        inputFluid.addChild(new FluidSlot()
            .bind(config.inputFluid(), 0)
            .slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP))
            .setAllowClickDrained(true)
            .layout(style -> style.heightPercent(100)));
        inputArea.addChild(inputFluid);
        // Clear button to the right of fluid slot, bottom-aligned with the fluid slot
        inputArea.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(ECOIcon.CLEAR))
            .setOnServerClick(e -> config.clearInputFluid().run())
            .layout(layout -> layout.width(8).height(8).alignSelf(AlignItems.FLEX_END).paddingAll(1)));

        // Input slots
        UIElement inputSlots = new UIElement().addClass("panel_border").layout(layout -> layout.marginLeft(10).marginRight(10));
        for (int x = 0; x < 3; x++) {
            UIElement row = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW));
            for (int y = 0; y < 3; y++) {
                int i = x + y * 3;
                row.addChild(new ItemSlot(new ItemHandlerSlot(config.items(), i)));
            }
            inputSlots.addChild(row);
        }
        inputArea.addChild(inputSlots);

        // output slot
        UIElement outputSlots = new UIElement().layout(layout -> {
            layout.marginLeft(5);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.justifyContent(AlignContent.CENTER);
        });
        UIElement outputSlot = new UIElement().addClass("panel_border").layout(layout -> layout.justifyContent(AlignContent.CENTER));
        outputSlots.addChild(outputSlot);
        outputSlot.addChild(new ItemSlot(new ItemHandlerSlot(config.items(), 9)));
        inputArea.addChild(outputSlots);

        // progress bar
        UIElement progressBar = new UIElement().layout(layout -> {
            layout.marginLeft(2);
            layout.marginRight(5);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.justifyContent(AlignContent.CENTER);
        }).addChildren(
            new WorkstationProgressBar(config.maxProcessingSteps())
                .bind(DataBindingBuilder.floatValS2C(() -> (float) config.processingTime().getAsInt()).build())
                .layout(layout -> layout.height(18).width(6))
                .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                    Float value = ((WorkstationProgressBar) event.currentElement).getValue();
                    event.hoverTooltips = new HoverTooltips(
                        List.of(Component.literal("%.0f%%".formatted(value / config.maxProcessingSteps() * 100))),
                        null,
                        null,
                        null
                    );
                })
        );

        inputArea.addChild(progressBar);

        // clear button on the left of output slot, bottom-aligned
        inputArea.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(ECOIcon.CLEAR))
            .setOnServerClick(e -> config.clearOutputFluid().run())
            .layout(layout -> layout.width(8).height(8).alignSelf(AlignItems.FLEX_END).paddingAll(1)));
        // output fluid
        UIElement outputFluid = new UIElement().addClass("panel_border");
        outputFluid.addChild(new FluidSlot()
            .bind(config.outputFluid(), 0)
            .slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP))
            .setAllowClickFilled(true)
            .setAllowClickDrained(false)
            .layout(style -> style.heightPercent(100)));
        inputArea.addChild(outputFluid);

        var networkTool = NetworkToolItem.findNetworkToolInv(player);
        Integer networkToolSlot = null;
        if (networkTool != null) {
            networkToolSlot = networkTool.getPlayerInventorySlot();
        }

        // add main input area and upgrades panel side-by-side
        root.addChild(inputArea);

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

        root.addChild(new TextElement()
            .setText("container.inventory", true)
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true).textShadow(false).textColor(0x403e53)));

        UIElement playerArea = new UIElement().layout(layout -> layout
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.FLEX_START)
            .marginTop(2));
        playerArea.addChild(WorkstationInventoryUI.createPlayerInventory(player.getInventory(), networkToolSlot));
        if (networkTool != null) {
            playerArea.addChild(WorkstationInventoryUI.createNetworkToolbox(networkTool));
        }
        root.addChild(playerArea);

        // Add absolute-positioned floating window last so it renders on top
        root.addChild(allowOutputWindow);
        return root;
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
                return owner.createLegacyRoot(player);
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
