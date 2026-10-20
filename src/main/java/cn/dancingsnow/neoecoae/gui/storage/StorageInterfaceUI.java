package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

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
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** JSON owns the texture geometry; bindings and menu actions own the interface's state. */
public final class StorageInterfaceUI {
    private static final ResourceLocation JSON_LAYOUT =
        new ResourceLocation("neoecoae", "screens/storage/storage_interface");
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
        private float fontSize = 6;

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
                    if (definition.has("font_size")) fontSize = definition.get("font_size").getAsFloat();
                    mode.setValue(host.getStorageInterfaceMode().ordinal());
                    mode.bind(DataBindingBuilder.intValS2C(() -> host.getStorageInterfaceMode().ordinal())
                        .initialValue(mode.getValue()).build());
                    mode.setDisplay(false);
                    root.addChild(mode);
                    yield root;
                }
                case "toolbar" -> HostSideButtonBar.left(CreativeStorageInputButton.create(
                    host::isIgnoringCreativeStorageInput, host::toggleIgnoreCreativeStorageInput));
                case "title" -> label(() -> GuiTitleProvider.title(host.getBlockState(),
                    Component.translatable("gui.neoecoae.storage_interface.title")), fontSize);
                case "mode_caption" -> modeCaption();
                case "mode_button" -> modeButton(ECOStorageInterfaceMode.valueOf(definition.get("mode").getAsString()));
                case "structure_status" -> status(() -> Component.translatable("gui.neoecoae.storage_interface.structure")
                    .append(": ").append(Component.translatable(host.isInfiniteTransferAvailable()
                        ? "gui.neoecoae.storage_interface.infinite_ready" : "gui.neoecoae.storage_interface.infinite_unavailable")), fontSize);
                case "network_status" -> status(() -> Component.translatable("gui.neoecoae.storage_interface.network")
                    .append(": ").append(Component.translatable(host.isTargetOnline()
                        ? "gui.neoecoae.storage_interface.connected" : "gui.neoecoae.storage_interface.disconnected")
                        .withColor(host.isTargetOnline() ? 0x258345 : 0xB53643)), fontSize);
                case "transfer_status" -> status(() -> Component.translatable("gui.neoecoae.storage_interface.transfer",
                    HostText.ae2Amount(transferred.getAsLong())), () -> Component.translatable(
                        "gui.neoecoae.storage_interface.transfer", HostText.expandedNumber(transferred.getAsLong())), fontSize);
                case "filter_caption" -> label(() -> Component.translatable("gui.neoecoae.storage_interface.filter.title"), fontSize);
                case "filter_hint" -> label(() -> Component.translatable("gui.neoecoae.storage_interface.filter.hint"), fontSize);
                case "resource_filters" -> new StorageResourceFilterGrid(host, player);
                case "player_inventory" -> StorageHostUI.playerInventory();
                default -> throw new IllegalArgumentException("Unknown storage interface node: " + definition);
            };
        }

        private ECOStorageInterfaceMode currentMode() {
            return ECOStorageInterfaceMode.values()[JdkCompat.clamp(mode.getValue(), 0, 2)];
        }

        private Label modeCaption() {
            Label caption = label(Component.translatable(modeKey(currentMode())), fontSize);
            mode.registerValueListener(value -> caption.setValue(Component.translatable(modeKey(currentMode()))));
            return caption;
        }

        private Button modeButton(ECOStorageInterfaceMode selected) {
            Button button = new ModeButton().noText();
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

    private static final class ModeButton extends Button {
        @Override public void drawBackgroundAdditional(GUIContext context) {
            SpriteTexture texture = (SpriteTexture) (isActive() ? switch (getState()) {
                case DEFAULT -> getButtonStyle().baseTexture();
                case HOVERED -> getButtonStyle().hoverTexture();
                case PRESSED -> getButtonStyle().pressedTexture();
            } : getButtonStyle().baseTexture());
            // Keep the 18px hitbox fixed and draw each state at its native height against the same bottom edge.
            float height = texture.spriteSize.height;
            context.drawTexture(texture, getPositionX(), getPositionY() + getSizeHeight() - height,
                getSizeWidth(), height);
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

    private static Label label(Supplier<Component> value, float size) {
        Label text = label(value.get(), size);
        text.bind(DataBindingBuilder.componentS2C(value).initialValue(value.get()).build());
        return text;
    }

    private static Label label(Component value, float size) {
        Label text = new FittedLabel();
        text.setText(value);
        text.textStyle(style -> style.fontSize(size).textColor(TEXT).textShadow(false)
            .adaptiveWidth(false).adaptiveHeight(false).textWrap(TextWrap.NONE));
        return text;
    }

    private static UIElement status(Supplier<Component> value, float size) {
        Label text = label(value, size);
        text.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = new HoverTooltips(List.of(text.getValue()), null, null, null));
        return text;
    }

    private static UIElement status(Supplier<Component> value, Supplier<Component> tooltip, float size) {
        Label text = label(value, size);
        BindableValue<Component> full = new BindableValue<>(tooltip.get());
        full.bind(DataBindingBuilder.componentS2C(tooltip).initialValue(tooltip.get()).build());
        full.setDisplay(false);
        text.addChild(full);
        text.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = new HoverTooltips(List.of(full.getValue()), null, null, null));
        return text;
    }

    /** Fit the complete translated component without wrapping or discarding its colored spans. */
    private static final class FittedLabel extends Label {
        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            Component value = getValue();
            if (value == null || value.getString().isEmpty()) return;
            var font = getFont();
            float width = getContentWidth();
            float height = getContentHeight();
            if (width <= 0 || height <= 0) return;
            float size = Math.min(getTextStyle().fontSize(), height);
            float scale = Math.min(size / font.lineHeight, width / Math.max(1, font.width(value)));
            if (scale <= 0) return;
            var pose = context.graphics.pose();
            pose.pushPose();
            try {
                pose.translate(getContentX(), getContentY() + (height - font.lineHeight * scale) / 2, 0);
                pose.scale(scale, scale, 1);
                context.graphics.drawString(font, value, 0, 0, getTextStyle().textColor(), getTextStyle().textShadow());
            } finally {
                pose.popPose();
            }
        }
    }
}
