package cn.dancingsnow.neoecoae.gui.common;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.utils.FluidHelper;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** A real buffer, a ghost fluid selection, and the configured stock amount are separate controls. */
final class FluidInputHatchUI implements NeoUiJsonContext {
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
        UIElement root = FluidHatchUI.createRoot(host.tank, "block.neoecoae.input_hatch", true, true);
        root.setId("fluid-input-hatch-root");
        root.setOverflowVisible(true);
        UIElement legacy = root.getChildren().get(1);
        legacy.setId("legacy-fluid-content");
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
            case "stock_caption" -> label(() -> text("stock"));
            case "amount_caption" -> label(() -> text("target"));
            case "marked_fluid_name" -> label(() -> host.getMarkedFluid().isEmpty() ? text("select_fluid")
                : FluidHelper.getDisplayName(host.getMarkedFluid()));
            case "stock" -> {
                FluidSlot slot = new FluidSlot().bind(host.tank, 0)
                    .slotStyle(style -> style.fillDirection(FillDirection.ALWAYS_FULL));
                slot.style(style -> style.backgroundTexture(NETextures.ITEM_SLOT));
                // Exact long amounts are shown below; the standard fluid widget has an int-sized view.
                slot.amountLabel.setDisplay(false);
                yield slot;
            }
            case "filter" -> new MarkedFluidSlot();
            case "amount" -> amountField();
            case "stored_amount" -> label(() -> Component.literal(host.tank.getStoredAmount() + " mB"));
            case "network_status" -> label(() -> text(!host.hasNetworkPullCard() ? "card_required"
                : host.getMainNode().isActive() ? "online" : "offline"));
            default -> throw new IllegalArgumentException("Unknown input hatch element: " + definition);
        };
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

    private TextField amountField() {
        TextField field = new TextField();
        field.setNumbersOnlyLong(0, Long.MAX_VALUE);
        field.bind(DataBindingBuilder.string(() -> Long.toString(host.getTargetAmount()), value -> {
            if (!canEdit(field)) return;
            try { host.setTargetAmount(Long.parseLong(value)); }
            catch (NumberFormatException ignored) { /* Keep the last valid value during an incomplete edit. */ }
        }).build());
        field.textFieldStyle(style -> style.textColor(0xFFF2F2F2).textShadow(false).fontSize(7)
            .focusOverlay(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY));
        field.style(style -> style.backgroundTexture(NETextures.PRIORITY_TEXT_FIELD).tooltips(text("target_hint")));
        field.layout(layout -> layout.paddingHorizontal(3).paddingVertical(2));
        return field;
    }

    private boolean canEdit(UIElement element) {
        var ui = element.getModularUI();
        return host.getLevel() != null && !host.getLevel().isClientSide && !host.isRemoved()
            && player.level() == host.getLevel() && player.blockPosition().distSqr(host.getBlockPos()) <= 64
            && ui != null && ui.getMenu() == player.containerMenu && player.containerMenu.stillValid(player);
    }

    private static Label label(Supplier<Component> value) {
        Label label = new Label();
        label.textStyle(style -> style.fontSize(7).textColor(0x403E53).textShadow(false).textWrap(TextWrap.HOVER_ROLL));
        label.bind(DataBindingBuilder.componentS2C(value).initialValue(value.get()).build());
        return label;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.neoecoae.fluid_input_hatch." + key, args);
    }

    private final class MarkedFluidSlot extends FluidSlot {
        MarkedFluidSlot() {
            setAllowClickFilled(false);
            setAllowClickDrained(false);
            amountLabel.setDisplay(false);
            style(style -> style.backgroundTexture(NETextures.ITEM_SLOT).tooltips(text("filter_hint")));
            bind(DataBindingBuilder.fluidStack(host::getMarkedFluid, fluid -> {
                if (canEdit(this)) host.setMarkedFluid(fluid);
            }).build());
            if (player.level().isClientSide) xeiPhantom();
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            event.hasHandler = true;
            event.stopImmediatePropagation();
            if (event.button == 1) setValue(FluidStack.EMPTY);
            else if (event.button == 0) {
                var ui = getModularUI();
                if (ui == null || ui.getMenu() == null) return;
                ItemStack carried = ui.getMenu().getCarried();
                GenericStack generic = GenericStack.unwrapItemStack(carried);
                FluidStack sample = generic != null && generic.what() instanceof AEFluidKey fluid
                    ? fluid.toStack(1) : FluidUtil.getFluidContained(carried).orElse(FluidStack.EMPTY);
                if (!sample.isEmpty()) setValue(sample.copyWithAmount(1));
            }
        }
    }
}
