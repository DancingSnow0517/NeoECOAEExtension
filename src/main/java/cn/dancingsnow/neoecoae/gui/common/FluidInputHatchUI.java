package cn.dancingsnow.neoecoae.gui.common;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.crafting.display.format.DisplayNumbers;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.slot.ItemHandlerSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEventBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.FluidSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib.gui.widget.PhantomFluidWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import cn.dancingsnow.neoecoae.menu.FluidHatchStockAmount;
import net.minecraftforge.fluids.FluidStack;

/** The input hatch's optional ME stocking mode, hosted by the existing LDLib1 menu. */
public final class FluidInputHatchUI {
    private FluidInputHatchUI() {}

    public static ModularUI create(ECOFluidInputHatchBlockEntity host, Player player) {
        UIElement root = new UIElement();
        root.setId("fluid-input-hatch-root");
        root.layout(layout -> layout.width(176).height(159).paddingAll(0));
        root.setOverflowVisible(true);
        BindableValue<CompoundTag> state = new BindableValue<>(snapshot(host)) {
            @Override public void detectAndSendChanges() {
                setValue(snapshot(host));
                super.detectAndSendChanges();
            }
        };
        state.setDisplay(false);
        state.bind(DataBindingBuilder.tagS2C(() -> snapshot(host)).build());
        root.addChild(state);

        TextElement title = new TextElement().setText(host.getBlockState().getBlock().getName());
        title.textStyle(style -> style.textColor(0xFFFFFFFF).textShadow(true));
        root.addChild(HostElements.absolute(title, 8, 6, 160, 10));

        FluidSlot legacy = new FluidSlot() {
            @Override public Component getFluidAmountText() {
                return Component.literal(DisplayNumbers.grouped(getValue().isEmpty() ? ""
                    : ECOKeyFactory.fluid(getValue()).formatAmount(state.getValue().getLong("stored"), AmountFormat.SLOT)));
            }
            @Override public List<Component> getFullTooltipTexts() {
                return fluidTooltip(getValue(), state.getValue().getLong("stored"));
            }
        };
        legacy.bind(host.getFluidTankHandler(), 0);
        legacy.style(style -> style.backgroundTexture(NETextures.ITEM_SLOT));
        root.addChild(HostElements.absolute(legacy, 79, 31, 18, 18));

        UIElement network = new UIElement();
        HostElements.absolute(network, 0, 0, 176, 70);
        root.addChild(network);
        network.addChild(HostElements.absolute(new MarkedSlot(host, player, state), 79, 31, 18, 18));
        FluidSlot buffer = new FluidSlot() {
            @Override public void drawBackgroundAdditional(GUIContext context) {
                drawFluid(context, getValue(), state.getValue().getLong("stored"), getPositionX(), getPositionY());
            }
            @Override public List<Component> getFullTooltipTexts() {
                return fluidTooltip(getValue(), state.getValue().getLong("stored"));
            }
        };
        buffer.bind(host.getFluidTankHandler(), 0).setAllowClickFilled(true).setAllowClickDrained(true);
        buffer.amountLabel.setDisplay(false);
        network.addChild(HostElements.absolute(buffer, 79, 49, 18, 18));

        InventorySlots inventory = new InventorySlots().bind(player.getInventory());
        inventory.apply(slot -> slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        root.addChild(HostElements.absolute(inventory, 7, 74, 162, 76));
        root.addChild(HostSideButtonBar.rightSlots(List.of(HostElements.tooltips(
            new ItemSlot(new ItemHandlerSlot(host.getUpgrades().toItemHandler(), 0))
                .slotStyle(style -> style.slotOverlay(AETextures.icon(ECOIcon.BACKGROUND_UPGRADE))
                    .showSlotOverlayOnlyEmpty(true)), text("upgrade")))));

        Button gear = new StockAmountButton().noText().setOnServerClick(event -> {
            if (player instanceof ServerPlayer serverPlayer && canEdit(host, player, network)) {
                FluidHatchStockAmount.open(host, serverPlayer);
            }
        });
        HostElements.tooltips(gear, appeng.core.localization.ButtonToolTips.InterfaceSetStockAmount.text());
        network.addChild(HostElements.absolute(gear, 80, 13, 16, 16));

        java.util.function.Consumer<CompoundTag> apply = value -> {
            boolean installed = value.getBoolean("card");
            legacy.setDisplay(!installed);
            network.setDisplay(installed);
            gear.setDisplay(!FluidStack.loadFluidStackFromNBT(value.getCompound("marked")).isEmpty());
            root.style(style -> style.backgroundTexture(installed ? SpriteTexture.of(
                NeoECOAE.id("textures/guis/fluid_input_hatch.png")).setSprite(0, 0, 176, 159) : NETextures.BACKGROUND));
            inventory.apply(slot -> slot.style(style -> style.backgroundTexture(installed ? IGuiTexture.EMPTY : NETextures.ITEM_SLOT)));
        };
        state.registerValueListener(apply);
        apply.accept(state.getValue());
        return ECOModularUI.create(root, player);
    }

    /** Same borderless idle/hover artwork as the 1.21.1 stock amount control. */
    private static final class StockAmountButton extends Button {
        @Override public void drawBackgroundAdditional(GUIContext context) {}
        @Override public void drawContents(GUIContext context) {
            context.drawTexture(AETextures.icon(getState() == State.DEFAULT ? ECOIcon.COG_DISABLED : ECOIcon.COG),
                getPositionX(), getPositionY(), 16, 16);
        }
    }

    private static final class MarkedSlot extends UIElement {
        MarkedSlot(ECOFluidInputHatchBlockEntity host, Player player, BindableValue<CompoundTag> state) {
            var click = addRPCEvent(RPCEventBuilder.simple(Integer.class, button -> {
                if (button == null || button < 0 || button > 1 || !canEdit(host, player, this) || !host.hasNetworkPullCard()) return;
                FluidStack marked = host.getMarkedFluid();
                GenericStack current = marked.isEmpty() ? null : new GenericStack(ECOKeyFactory.fluid(marked), host.getTargetAmount());
                var updated = FluidHatchInterfaceConfig.click(current, player.containerMenu.getCarried(), button);
                host.setMarkedFluid(updated == null ? FluidStack.EMPTY : ((AEFluidKey) updated.what()).toStack(1));
                host.setTargetAmount(updated == null ? 0 : updated.amount());
            }));
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 0 || event.button == 1) click.send(event.button);
                event.stopImmediatePropagation();
            });
            // Reuse LDLib1's JEI/EMI fluid drag target and wire format; clicks use AE2 FakeSlot rules.
            // LDLib 1.0.33.b only provides the full constructor, without the newer setters.
            PhantomFluidWidget ghost = new PhantomFluidWidget(null, 0, 0, 0, 18, 18, () -> {
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(state.getValue().getCompound("marked"));
                return com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(fluid.getFluid(), fluid.getAmount(), fluid.getTag());
            }, fluid -> {
                if (!canEdit(host, player, this) || !host.hasNetworkPullCard()) return;
                host.setMarkedFluid(fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : new FluidStack(fluid.getFluid(), 1, fluid.getTag()));
                host.setTargetAmount(fluid == null || fluid.isEmpty() ? 0 : fluid.getAmount());
            }) {
                @Override public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float ticks) {}
                @Override public void handleClientAction(int id, FriendlyByteBuf buffer) {
                    if (id == 2 && canEdit(host, player, MarkedSlot.this) && host.hasNetworkPullCard()) super.handleClientAction(id, buffer);
                }
            };
            addWidget(ghost);
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                List<Component> lines = fluidTooltip(FluidStack.loadFluidStackFromNBT(state.getValue().getCompound("marked")), state.getValue().getLong("target"));
                lines.add(text("filter_hint"));
                lines.add(text("amount_hint"));
                event.hoverTooltips = new HoverTooltips(lines, null, null, null);
            });
            addChild(new UIElement() {
                { setAllowHitTest(false); layout(layout -> layout.width(18).height(18)); }
                @Override public void drawBackgroundAdditional(GUIContext context) {
                    drawFluid(context, FluidStack.loadFluidStackFromNBT(state.getValue().getCompound("marked")),
                        state.getValue().getLong("target"), getPositionX(), getPositionY());
                }
            });
        }
    }

    private static CompoundTag snapshot(ECOFluidInputHatchBlockEntity host) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("card", host.hasNetworkPullCard());
        tag.put("marked", host.getMarkedFluid().writeToNBT(new CompoundTag()));
        tag.putLong("target", host.getTargetAmount());
        tag.putLong("stored", host.getStoredFluidAmount());
        return tag;
    }

    private static boolean canEdit(ECOFluidInputHatchBlockEntity host, Player player, UIElement element) {
        return host.getLevel() != null && !host.getLevel().isClientSide && host.canPlayerUse(player) && element.activeMenu();
    }

    private static void drawFluid(GUIContext context, FluidStack fluid, long amount, float x, float y) {
        if (fluid.isEmpty()) return;
        var key = ECOKeyFactory.fluid(fluid);
        var mc = Minecraft.getInstance();
        AEKeyRendering.drawInGui(mc, context.graphics, Math.round(x) + 1, Math.round(y) + 1, key);
        String label = DisplayNumbers.grouped(key.formatAmount(amount, AmountFormat.SLOT));
        StackSizeRenderer.renderSizeLabel(context.graphics, mc.font, x + 1, y + 1, label, false);
    }

    private static List<Component> fluidTooltip(FluidStack fluid, long amount) {
        List<Component> lines = new ArrayList<>();
        if (!fluid.isEmpty()) {
            var key = ECOKeyFactory.fluid(fluid);
            lines.add(key.getDisplayName());
            lines.add(Component.literal(DisplayNumbers.grouped(key.formatAmount(amount, AmountFormat.FULL))));
        }
        return lines;
    }

    private static Component text(String key) { return Component.translatable("gui.neoecoae.fluid_input_hatch." + key); }

}
