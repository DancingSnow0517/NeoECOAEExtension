package cn.dancingsnow.neoecoae.gui.common;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AmountFormat;
import appeng.api.client.AEKeyRendering;
import appeng.client.gui.me.common.StackSizeRenderer;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.menu.FluidHatchStockAmount;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;

/** A real buffer, a ghost fluid selection, and the configured stock amount are separate controls. */
final class FluidInputHatchUI implements NeoUiJsonContext {
    private static final IGuiTexture BACKGROUND = SpriteTexture.of(
        NeoECOAE.id("textures/guis/fluid_input_hatch.png")).setSprite(0, 0, 176, 159);
    private final ECOFluidInputHatchBlockEntity host;
    private final Player player;

    private FluidInputHatchUI(ECOFluidInputHatchBlockEntity host, Player player) {
        this.host = host;
        this.player = player;
    }

    static ModularUI create(ECOFluidInputHatchBlockEntity host, Player player) {
        return ECOModularUI.create(createRoot(host, player), player);
    }

    static UIElement createRoot(ECOFluidInputHatchBlockEntity host, Player player) {
        var context = new FluidInputHatchUI(host, player);
        // One inventory and one upgrade slot keep menu indices stable across mode changes.
        UIElement root = FluidHatchUI.createRoot(host.getFluidTankHandler(), "block.neoecoae.input_hatch", true, true);
        root.setId("fluid-input-hatch-root");
        root.setOverflowVisible(true);
        UIElement legacy = root.getChildren().get(1);
        legacy.setId("legacy-fluid-content");
        UIElement title = root.getChildren().getFirst();
        InventorySlots inventory = (InventorySlots) root.getChildren().get(2);
        UIElement network = NeoUiJsonLoader.load(NeoECOAE.id("screens/fluid_input_hatch"), context);
        root.addChildAt(network, 2);
        root.addChild(context.upgrades());
        BindableValue<Boolean> mode = new BindableValue<>(host.hasNetworkPullCard());
        mode.setId("network-pull-mode");
        mode.setDisplay(false);
        java.util.function.Consumer<Boolean> applyMode = installed -> {
            boolean enabled = Boolean.TRUE.equals(installed);
            legacy.setDisplay(!enabled);
            network.setDisplay(enabled);
            root.style(style -> style.backgroundTexture(enabled ? BACKGROUND : NETextures.BACKGROUND));
            root.layout(layout -> {
                layout.paddingAll(enabled ? 0 : 4).gapAll(enabled ? 0 : 2);
                if (enabled) layout.width(176).height(159);
                else layout.widthAuto().heightAuto();
            });
            title.layout(layout -> {
                layout.positionType(enabled ? TaffyPosition.ABSOLUTE : TaffyPosition.RELATIVE)
                    .left(enabled ? 8 : 0).top(enabled ? 6 : 0);
                if (enabled) layout.width(160).height(10);
                else layout.widthPercent(100).heightAuto();
            });
            ((TextElement) title.getChildren().getFirst()).textStyle(style ->
                style.textColor(enabled ? 0x403F54 : 0xFFFFFFFF).textShadow(!enabled));
            inventory.layout(layout -> layout.positionType(enabled ? TaffyPosition.ABSOLUTE : TaffyPosition.RELATIVE)
                .left(enabled ? 7 : 0).top(enabled ? 74 : 0));
            inventory.hotbar.layout(layout -> layout.marginTop(enabled ? 4 : 5));
            inventory.apply(slot -> slot.style(style -> style.backgroundTexture(enabled ? IGuiTexture.EMPTY : NETextures.ITEM_SLOT)));
            inventory.getChildren().forEach(child -> child.style(style ->
                style.backgroundTexture(enabled ? IGuiTexture.EMPTY : NETextures.INVENTORY_BORDER)));
        };
        applyMode.accept(mode.getValue());
        mode.registerValueListener(installed -> {
            applyMode.accept(installed);
            var ui = root.getModularUI();
            if (player.level().isClientSide && ui != null && ui.getScreen() != null) {
                ui.init(ui.getScreen().width, ui.getScreen().height);
            }
        });
        mode.bind(DataBindingBuilder.boolS2C(host::hasNetworkPullCard).initialValue(mode.getValue()).build());
        root.addChild(mode);
        return root;
    }

    @Override
    public UIElement createCustom(JsonObject definition) {
        return switch (definition.get("custom").getAsString()) {
            case "stock" -> new BufferSlot();
            case "filter" -> new MarkedFluidSlot();
            case "edit_amount" -> amountButton();
            default -> throw new IllegalArgumentException("Unknown input hatch element: " + definition);
        };
    }

    private UIElement amountButton() {
        Button button = new StockAmountButton();
        button.style(style -> style.tooltips(appeng.core.localization.ButtonToolTips.InterfaceSetStockAmount.text()));
        var configured = new BindableValue<>(!host.getMarkedFluid().isEmpty());
        configured.setDisplay(false);
        configured.registerValueListener(button::setDisplay);
        configured.bind(DataBindingBuilder.boolS2C(() -> !host.getMarkedFluid().isEmpty()).build());
        button.setDisplay(configured.getValue());
        button.addChild(configured);
        return button;
    }

    private final class StockAmountButton extends Button {
        private final RPCEmitter openAmount;

        StockAmountButton() {
            openAmount = addRPCEvent(RPCEventBuilder.simple(() -> {
                if (player instanceof ServerPlayer serverPlayer && canEdit(this)) {
                    FluidHatchStockAmount.open(host, serverPlayer);
                }
            }));
            noText().setOnClick(event -> openAmount.send());
        }

        @Override public void drawBackgroundAdditional(GUIContext context) {}
        @Override public void drawContents(GUIContext context) {
            context.drawTexture(AETextures.icon(getState() == State.DEFAULT ? ECOIcon.COG_DISABLED : ECOIcon.COG),
                getPositionX(), getPositionY(), 16, 16);
        }
    }

    private final class BufferSlot extends FluidSlot {
        private final BindableValue<Long> exactAmount = new BindableValue<>(host.getStoredFluidAmount());

        BufferSlot() {
            bind(host.getFluidTankHandler(), 0);
            style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
            amountLabel.setDisplay(false);
            exactAmount.setDisplay(false);
            exactAmount.bind(DataBindingBuilder.longValS2C(host::getStoredFluidAmount).build());
            addChild(exactAmount);
        }

        @Override
        public Component getFluidAmountText() {
            return Component.literal(getValue().isEmpty() || exactAmount == null ? ""
                : FluidHatchInterfaceConfig.slotAmount(AEFluidKey.of(getValue()), exactAmount.getValue()));
        }

        @Override protected void drawFluid(GUIContext context, FluidStack fluid, float x, float y, float width, float height) {
            drawInterfaceFluid(context, fluid, x, y, exactAmount.getValue());
        }

        @Override
        public List<Component> getFullTooltipTexts(boolean withFluidName) {
            return interfaceTooltip(getValue(), exactAmount.getValue());
        }
    }

    private UIElement upgrades() {
        UIElement rail = HostSideButtonBar.rightSlots(List.of(
            new ItemSlot(new ItemHandlerSlot((IItemHandlerModifiable) host.getUpgrades().toItemHandler(), 0))
                .slotStyle(style -> style.slotOverlay(AETextures.icon(ECOIcon.BACKGROUND_UPGRADE))
                    .showSlotOverlayOnlyEmpty(true))
                .style(style -> style.tooltips(text("upgrade")))));
        rail.setId("upgrades");
        return rail;
    }

    private boolean canEdit(UIElement element) {
        var ui = element.getModularUI();
        return host.getLevel() != null && !host.getLevel().isClientSide && host.canPlayerUse(player)
            && ui != null && ui.getMenu() == player.containerMenu && player.containerMenu.stillValid(player);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.neoecoae.fluid_input_hatch." + key, args);
    }

    private static void drawInterfaceFluid(GUIContext context, FluidStack fluid, float x, float y, long amount) {
        var key = AEFluidKey.of(fluid);
        var mc = Minecraft.getInstance();
        AEKeyRendering.drawInGui(mc, context.graphics, Math.round(x), Math.round(y), key);
        if (amount > 0) StackSizeRenderer.renderSizeLabel(context.graphics, mc.font, x, y,
            key.formatAmount(amount, AmountFormat.SLOT), false);
    }

    private static List<Component> interfaceTooltip(FluidStack fluid, long amount) {
        List<Component> lines = new java.util.ArrayList<>();
        if (!fluid.isEmpty()) {
            var key = AEFluidKey.of(fluid);
            lines.add(key.getDisplayName());
            lines.add(Component.literal(key.formatAmount(amount, AmountFormat.FULL)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private final class MarkedFluidSlot extends FluidSlot {
        private final BindableValue<Long> exactTarget = new BindableValue<>(host.getTargetAmount());
        private final RPCEmitter click;

        MarkedFluidSlot() {
            setAllowClickFilled(false);
            setAllowClickDrained(false);
            amountLabel.setDisplay(false);
            style(style -> style.backgroundTexture(IGuiTexture.EMPTY).tooltips(text("filter_hint"), text("amount_hint")));
            bind(DataBindingBuilder.fluidStack(host::getMarkedFluid, fluid -> {
                if (canEdit(this) && host.hasNetworkPullCard()) {
                    host.setMarkedFluid(fluid);
                    host.setTargetAmount(fluid.isEmpty() ? 0 : fluid.getAmount());
                }
            }).build());
            click = addRPCEvent(RPCEventBuilder.simple(Integer.class, button -> {
                if (!canEdit(this) || !host.hasNetworkPullCard() || button < 0 || button > 1) return;
                var marked = host.getMarkedFluid();
                GenericStack current = marked.isEmpty() ? null : new GenericStack(AEFluidKey.of(marked), host.getTargetAmount());
                var updated = FluidHatchInterfaceConfig.click(current, player.containerMenu.getCarried(), button);
                host.setMarkedFluid(updated == null ? FluidStack.EMPTY : ((AEFluidKey) updated.what()).toStack(1));
                host.setTargetAmount(updated == null ? 0 : updated.amount());
            }));
            exactTarget.setDisplay(false);
            exactTarget.bind(DataBindingBuilder.longValS2C(host::getTargetAmount).build());
            addChild(exactTarget);
            if (player.level().isClientSide) xeiPhantom();
        }

        @Override
        public Component getFluidAmountText() {
            return Component.literal(getValue().isEmpty() || exactTarget == null ? ""
                : FluidHatchInterfaceConfig.slotAmount(AEFluidKey.of(getValue()), exactTarget.getValue()));
        }

        @Override protected void drawFluid(GUIContext context, FluidStack fluid, float x, float y, float width, float height) {
            drawInterfaceFluid(context, fluid, x, y, exactTarget.getValue());
        }

        @Override
        public List<Component> getFullTooltipTexts(boolean withFluidName) {
            List<Component> lines = interfaceTooltip(getValue(), exactTarget.getValue());
            lines.add(text("filter_hint"));
            lines.add(text("amount_hint"));
            return lines;
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            event.hasHandler = true;
            event.stopImmediatePropagation();
            if (event.button == 0 || event.button == 1) click.send(event.button);
        }
    }
}
