package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.GuiTitleProvider;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.style.animation.Transition;
import com.lowdragmc.lowdraglib2.math.interpolate.Eases;
import com.lowdragmc.lowdraglib2.utils.animation.Animation;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class StorageInterfaceUI {
    private static final ResourceLocation JSON_LAYOUT =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "screens/storage/storage_interface");
    private static final int STATUS_CONNECTED = 0x55CC77;
    private static final int STATUS_DISCONNECTED = 0xDD5555;
    private static final int INFINITE_VALUE = 0xCA6CFF;

    private StorageInterfaceUI() {}

    public static ModularUI create(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface, Player player) {
        var transferred = new cn.dancingsnow.neoecoae.gui.common.SampledValue<>(
            () -> player.level().getGameTime(), storageInterface::getTransferredLastTick, 5);
        UIElement root = NeoUiJsonLoader.load(JSON_LAYOUT, new JsonContext(storageInterface, transferred));
        return ECOModularUI.create(root, player);
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface;
        private final cn.dancingsnow.neoecoae.gui.common.SampledValue<Long> transferred;

        private JsonContext(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface,
                            cn.dancingsnow.neoecoae.gui.common.SampledValue<Long> transferred) {
            this.storageInterface = storageInterface;
            this.transferred = transferred;
        }

        @Override
        public UIElement createCustom(JsonObject definition) {
            return switch (definition.get("custom").getAsString()) {
                case "toolbar" -> cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar.left(
                    cn.dancingsnow.neoecoae.gui.common.CreativeStorageInputButton.create(
                        storageInterface::isIgnoringCreativeStorageInput,
                        storageInterface::toggleIgnoreCreativeStorageInput));
                case "title" -> boundLabel(() -> GuiTitleProvider.title(storageInterface.getBlockState(),
                    Component.translatable("gui.neoecoae.storage_interface.title")));
                case "content" -> contentFrame(storageInterface, transferred);
                default -> throw new IllegalArgumentException("Unknown storage interface UI custom element: "
                    + definition.get("custom").getAsString());
            };
        }
    }

    private static UIElement contentFrame(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface,
                                          cn.dancingsnow.neoecoae.gui.common.SampledValue<Long> transferred) {
        UIElement contentFrame = new UIElement().style(style -> style.backgroundTexture(NETextures.HOST_PANEL_BORDER));
        contentFrame.addChild(modeSelector(storageInterface));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.structure")
            .append(": ").append(Component.translatable(storageInterface.isInfiniteTransferAvailable()
                ? "gui.neoecoae.storage_interface.infinite_ready" : "gui.neoecoae.storage_interface.infinite_unavailable"))));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.network")
            .append(": ").append(Component.translatable(storageInterface.isTargetOnline()
                ? "gui.neoecoae.storage_interface.connected" : "gui.neoecoae.storage_interface.disconnected")
                .withColor(storageInterface.isTargetOnline() ? STATUS_CONNECTED : STATUS_DISCONNECTED))));
        contentFrame.addChild(statusLabel(() -> Component.translatable("gui.neoecoae.storage_interface.transfer_prefix")
            .append(Component.literal(NumberFormat.getIntegerInstance(Locale.ROOT)
                .format(transferred.get())).withColor(INFINITE_VALUE))
            .append(Component.translatable("gui.neoecoae.storage_interface.transfer_suffix"))));
        return contentFrame;
    }

    private static UIElement modeSelector(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        final int segmentWidth = 44;
        final int selectorHeight = 16;
        ToggleGroupElement group = new ToggleGroupElement();
        group.layout(layout -> {
            layout.width(segmentWidth * 3).height(selectorHeight).gapAll(0).flexDirection(FlexDirection.ROW);
            layout.marginLeft(30);
        }).style(style -> style.backgroundTexture(NETextures.RECT_RD_DARK));

        UIElement slider = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(storageInterface.getStorageInterfaceMode().ordinal() * segmentWidth);
            layout.top(0).width(segmentWidth).height(selectorHeight);
        }).style(style -> {
            style.backgroundTexture(NETextures.RECT_RD);
            style.transition(new Transition(Map.of(
                LayoutProperties.LEFT, new Animation(0.12F, 0, Eases.QUAD_OUT)
            )));
        });
        group.addChild(slider);

        Toggle storage = modeToggle(storageInterface, ECOStorageInterfaceMode.STORAGE,
            "gui.neoecoae.storage_interface.mode.storage", segmentWidth, selectorHeight);
        Toggle input = modeToggle(storageInterface, ECOStorageInterfaceMode.INPUT,
            "gui.neoecoae.storage_interface.mode.input", segmentWidth, selectorHeight);
        Toggle output = modeToggle(storageInterface, ECOStorageInterfaceMode.OUTPUT,
            "gui.neoecoae.storage_interface.mode.output", segmentWidth, selectorHeight);
        group.addChildren(storage, input, output);
        group.addEventListener(UIEvents.TICK, event -> {
            ECOStorageInterfaceMode mode = storageInterface.getStorageInterfaceMode();
            storage.setOn(mode == ECOStorageInterfaceMode.STORAGE, false);
            input.setOn(mode == ECOStorageInterfaceMode.INPUT, false);
            output.setOn(mode == ECOStorageInterfaceMode.OUTPUT, false);
            slider.layout(layout -> layout.left(mode.ordinal() * segmentWidth));
        });
        return group;
    }

    private static Toggle modeToggle(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface,
                                     ECOStorageInterfaceMode mode, String key, int width, int height) {
        Toggle toggle = new Toggle().noText();
        toggle.toggleStyle(style -> style
            .baseTexture(IGuiTexture.EMPTY)
            .hoverTexture(NETextures.RECT_RD_T)
            .markTexture(IGuiTexture.EMPTY)
            .unmarkTexture(IGuiTexture.EMPTY));
        toggle.toggleButton(button -> {
            button.noText();
            button.setOnServerClick(event -> storageInterface.setStorageInterfaceMode(mode));
            button.layout(layout -> layout.width(width).height(height));
            Label label = new Label();
            label.setText(Component.translatable(key));
            label.textStyle(style -> style
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
            label.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0).top(0).width(width).height(height);
            });
            button.addChild(label);
        });
        toggle.layout(layout -> layout.width(width).height(height).paddingAll(0));
        return toggle;
    }

    private static Label boundLabel(Supplier<Component> text) {
        Label label = new Label();
        label.setText(text.get());
        label.bind(DataBindingBuilder.componentS2C(text).build());
        label.layout(layout -> layout.height(12));
        return label;
    }

    private static Label statusLabel(Supplier<Component> text) {
        Label label = boundLabel(text);
        label.layout(layout -> layout.height(12).marginLeft(2));
        return label;
    }
}
