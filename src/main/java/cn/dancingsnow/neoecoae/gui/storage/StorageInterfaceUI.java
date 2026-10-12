package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.GuiTitleProvider;
import cn.dancingsnow.neoecoae.gui.common.CreativeStorageInputButton;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.common.SampledValue;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** JSON owns the texture geometry; bindings and menu actions own the interface's state. */
public final class StorageInterfaceUI {
    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/storage/storage_interface");
    private static final int TEXT = 0x3F3D52;

    private StorageInterfaceUI() {}

    public static ModularUI create(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface, Player player) {
        SampledValue<Long> transferred = new SampledValue<>(
            () -> player.level().getGameTime(), storageInterface::getTransferredLastTick, 5);
        return ECOModularUI.create(createRoot(storageInterface, player, transferred::get), player);
    }

    static UIElement createRoot(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface,
        Player player, LongSupplier transferred) {
        return NeoUiJsonLoader.load(JSON_LAYOUT, new JsonContext(storageInterface, player, transferred));
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final ECOMachineInterfaceBlockEntity<NEStorageCluster> host;
        private final Player player;
        private final LongSupplier transferred;
        private final BindableValue<Integer> mode = new BindableValue<>(0);

        JsonContext(ECOMachineInterfaceBlockEntity<NEStorageCluster> host, Player player, LongSupplier transferred) {
            this.host = host;
            this.player = player;
            this.transferred = transferred;
        }

        @Override
        public UIElement createCustom(JsonObject definition) {
            return switch (definition.get("custom").getAsString()) {
                case "interface_root" -> {
                    UIElement root = new UIElement();
                    mode.setValue(host.getStorageInterfaceMode().ordinal());
                    mode.bind(DataBindingBuilder.intValS2C(() -> host.getStorageInterfaceMode().ordinal())
                        .initialValue(mode.getValue()).build());
                    mode.setDisplay(false);
                    root.addChild(mode);
                    yield root;
                }
                case "toolbar" -> HostSideButtonBar.grayLeft(CreativeStorageInputButton.create(
                    host::isIgnoringCreativeStorageInput, host::toggleIgnoreCreativeStorageInput));
                case "title" -> label(() -> GuiTitleProvider.title(host.getBlockState(),
                    Component.translatable("gui.neoecoae.storage_interface.title")), 7);
                case "mode_caption" -> label(() -> Component.translatable(modeKey(currentMode())), 6);
                case "mode_button" -> modeButton(ECOStorageInterfaceMode.valueOf(definition.get("mode").getAsString()));
                case "structure_status" -> status(() -> Component.translatable("gui.neoecoae.storage_interface.structure")
                    .append(": ").append(Component.translatable(host.isInfiniteTransferAvailable()
                        ? "gui.neoecoae.storage_interface.infinite_ready" : "gui.neoecoae.storage_interface.infinite_unavailable")));
                case "network_status" -> status(() -> Component.translatable("gui.neoecoae.storage_interface.network")
                    .append(": ").append(Component.translatable(host.isTargetOnline()
                        ? "gui.neoecoae.storage_interface.connected" : "gui.neoecoae.storage_interface.disconnected")
                        .withColor(host.isTargetOnline() ? 0x258345 : 0xB53643)));
                case "transfer_status" -> status(() -> Component.translatable("gui.neoecoae.storage_interface.transfer",
                    HostText.ae2Amount(transferred.getAsLong())), () -> Component.translatable(
                        "gui.neoecoae.storage_interface.transfer", HostText.expandedNumber(transferred.getAsLong())));
                case "filter_caption" -> label(() -> Component.translatable("gui.neoecoae.storage_interface.filter.title"), 7);
                case "filter_hint" -> label(() -> Component.translatable("gui.neoecoae.storage_interface.filter.hint"), 6);
                case "resource_filters" -> new StorageResourceFilterGrid(host, player);
                case "player_inventory" -> StorageHostUI.playerInventory();
                default -> throw new IllegalArgumentException("Unknown storage interface node: " + definition);
            };
        }

        private ECOStorageInterfaceMode currentMode() {
            return ECOStorageInterfaceMode.values()[Math.clamp(mode.getValue(), 0, 2)];
        }

        private Button modeButton(ECOStorageInterfaceMode selected) {
            Button button = new Button().noText();
            button.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
            Runnable refresh = () -> button.buttonStyle(style -> style
                .baseTexture(StoragePanelTextures.interfaceMode(selected.ordinal(), currentMode() == selected, false))
                .hoverTexture(StoragePanelTextures.interfaceMode(selected.ordinal(), currentMode() == selected, true))
                .pressedTexture(StoragePanelTextures.interfaceMode(selected.ordinal(), true, true)));
            refresh.run();
            mode.registerValueListener(value -> refresh.run());
            button.setOnServerClick(event -> {
                if (event.button == 0 && canEdit(button, host, player)) host.setStorageInterfaceMode(selected);
            });
            button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.translatable(modeKey(selected))), null, null, null));
            return button;
        }
    }

    static boolean canEdit(UIElement element, ECOMachineInterfaceBlockEntity<?> host, Player player) {
        var ui = element.getModularUI();
        return host.getLevel() != null && !host.getLevel().isClientSide && host.supportsStorageInterfaceUi()
            && player.level() == host.getLevel() && player.blockPosition().distSqr(host.getBlockPos()) <= 64
            && ui != null && ui.getMenu() == player.containerMenu && player.containerMenu.stillValid(player);
    }

    private static String modeKey(ECOStorageInterfaceMode mode) {
        return "gui.neoecoae.storage_interface.mode." + mode.getSerializedName();
    }

    private static Label label(Supplier<Component> value, int size) {
        Label text = new Label();
        text.setText(value.get());
        text.bind(DataBindingBuilder.componentS2C(value).initialValue(value.get()).build());
        text.textStyle(style -> style.fontSize(size).textColor(TEXT).textShadow(false)
            .adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.NONE));
        return text;
    }

    private static UIElement status(Supplier<Component> value) {
        Label text = label(value, 6);
        text.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = new HoverTooltips(List.of(text.getValue()), null, null, null));
        return text;
    }

    private static UIElement status(Supplier<Component> value, Supplier<Component> tooltip) {
        Label text = label(value, 6);
        BindableValue<Component> full = new BindableValue<>(tooltip.get());
        full.bind(DataBindingBuilder.componentS2C(tooltip).initialValue(tooltip.get()).build());
        full.setDisplay(false);
        text.addChild(full);
        text.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = new HoverTooltips(List.of(full.getValue()), null, null, null));
        return text;
    }
}
