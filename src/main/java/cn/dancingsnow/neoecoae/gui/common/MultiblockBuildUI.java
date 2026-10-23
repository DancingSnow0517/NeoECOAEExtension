package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.SpriteTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.data.TextWrap;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextField;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.util.WindowDragHelper;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Four-row builder settings shared by the crafting, storage and computation hosts. */
public final class MultiblockBuildUI {
    private static final ResourceLocation LAYOUT = new ResourceLocation("neoecoae", "screens/common/multiblock_build");

    private MultiblockBuildUI() {}

    public record Config(IntSupplier length, int minLength, int maxLength, IntConsumer setLength,
                         IntConsumer changeLength, BooleanSupplier mirrored, Runnable toggleMirrored,
                         BooleanSupplier editable, Runnable build) {}

    public record Elements(UIElement window, Button openButton) {}

    public static Elements create(MultiBlockBuildController.Host host, Player player) {
        MultiBlockBuildController controller = host.getBuildController();
        return create(new Config(host::getSelectedBuildLength, host.getMinBuildLength(), host.getMaxBuildLength(),
            length -> controller.setBuildLength(player, length), delta -> controller.changeBuildLength(player, delta),
            host::isMirrorBuild, () -> controller.setMirrorBuild(player, !host.isMirrorBuild()),
            () -> !host.isFormed() && !host.isBuildInProgress() && host.getBuildDefinition() != null,
            () -> controller.autoBuild(player)));
    }

    public static Elements create(Config config) {
        Context context = new Context(config);
        UIElement window = NeoUiJsonLoader.load(LAYOUT, context);
        context.attach(window);
        Button open = HostSideButtonBar.createButton().noText().addPostIcon(AETextures.icon(ECOIcon.CRAFT_HAMMER))
            .setOnClick(event -> window.setDisplay(true));
        open.setId("multiblock-build-open");
        HostElements.tooltips(open, Component.translatable("gui.neoecoae.multiblock.builder"));
        return new Elements(window, open);
    }

    private static final class Context implements NeoUiJsonContext {
        private final Config config;
        private final List<UIElement> controls = new ArrayList<>();
        private UIElement window;
        private UIElement dragBar;
        private TextField lengthInput;

        private Context(Config config) {
            this.config = config;
        }

        @Override
        public UIElement createCustom(JsonObject node) {
            return switch (node.get("name").getAsString()) {
                case "drag_bar" -> dragBar = new UIElement();
                case "back" -> HostElements.tooltips(HostSubWindowControls.backButton(() -> window.setDisplay(false)),
                    Component.translatable("gui.neoecoae.multiblock.close_builder"));
                case "length_field" -> editable(lengthField());
                case "length_step" -> editable(lengthStep(node.get("step").getAsInt()));
                case "mirror" -> editable(mirrorSwitch());
                case "build" -> editable(HostSubWindowControls.textButton(() ->
                    Component.translatable("gui.neoecoae.multiblock.build"))
                    .setOnClick(event -> lengthInput.updateScreen())
                    .setOnServerClick(event -> config.build().run()));
                default -> throw new IllegalArgumentException("Unknown multiblock builder element: " + node);
            };
        }

        @Override
        public void configure(JsonObject node, UIElement element) {
            if (element instanceof TextElement text) {
                text.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true)
                    .textWrap(TextWrap.NONE).textColor(0x413f54).textShadow(false));
            }
            if ("multiblock-build-root".equals(element.getId())) {
                ((SpriteTexture) element.getStyle().backgroundTexture()).setBorder(1, 1, 1, 3);
                element.setDisplay(false);
            }
        }

        private <T extends UIElement> T editable(T control) {
            controls.add(control);
            return control;
        }

        private TextField lengthField() {
            TextField field = HostSubWindowControls.integerField(config.minLength(), config.maxLength());
            lengthInput = field;
            field.bind(DataBindingBuilder.string(() -> String.valueOf(config.length().getAsInt()), text -> {
                try {
                    config.setLength().accept(Integer.parseInt(text));
                } catch (NumberFormatException ignored) {
                    // Clearing or partially editing the field must not change the selected length.
                }
            }).initialValue(String.valueOf(config.length().getAsInt())).build());
            return HostElements.tooltips(field, Component.translatable("gui.neoecoae.structure_terminal.length_range",
                config.minLength(), config.maxLength()));
        }

        private Button lengthStep(int step) {
            return HostSubWindowControls.textButton(() -> Component.literal(step > 0 ? "+" + step : String.valueOf(step)))
                // Flush a just-typed value before the button's server action is sent.
                .setOnClick(event -> lengthInput.updateScreen())
                .setOnServerClick(event -> config.changeLength().accept(step));
        }

        private Button mirrorSwitch() {
            Button button = new Button().noText().setOnServerClick(event -> config.toggleMirrored().run());
            BindableValue<Boolean> mirrored = new BindableValue<>(config.mirrored().getAsBoolean());
            mirrored.bind(DataBindingBuilder.boolS2C(config.mirrored()::getAsBoolean).build());
            mirrored.registerValueListener(value -> updateMirror(button, Boolean.TRUE.equals(value)));
            mirrored.setDisplay(false);
            button.addChild(mirrored);
            updateMirror(button, Boolean.TRUE.equals(mirrored.getValue()));
            button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty().append(
                Component.translatable("gui.neoecoae.multiblock.mirror."
                    + (Boolean.TRUE.equals(mirrored.getValue()) ? "on" : "off") + ".tooltip")));
            return button;
        }

        private static void updateMirror(Button button, boolean mirrored) {
            button.buttonStyle(style -> style.baseTexture(mirrored ? NETextures.SWITCH_ON : NETextures.SWITCH_OFF)
                .hoverTexture(mirrored ? NETextures.SWITCH_ON_HOVER : NETextures.SWITCH_OFF_HOVER)
                .pressedTexture(mirrored ? NETextures.SWITCH_ON_HOVER : NETextures.SWITCH_OFF_HOVER));
        }

        private void attach(UIElement window) {
            this.window = window;
            WindowDragHelper.setDragMove(dragBar, window, null, null);
            BindableValue<Boolean> editable = new BindableValue<>(config.editable().getAsBoolean()) {
                @Override
                public void detectAndSendChanges() {
                    setValue(config.editable().getAsBoolean());
                    super.detectAndSendChanges();
                }
            };
            editable.bind(DataBindingBuilder.boolS2C(editable::getValue).build());
            editable.registerValueListener(value -> controls.forEach(control -> control.setActive(Boolean.TRUE.equals(value))));
            controls.forEach(control -> control.setActive(Boolean.TRUE.equals(editable.getValue())));
            editable.setDisplay(false);
            window.addChild(editable);
        }
    }
}
