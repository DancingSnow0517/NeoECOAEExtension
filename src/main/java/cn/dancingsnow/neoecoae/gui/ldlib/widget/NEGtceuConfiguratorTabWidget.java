package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_BORDER;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_FIELD_H;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_FIELD_W;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_FIELD_X;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_FIELD_Y;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_PANEL_H;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_PANEL_W;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_STEP;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TAB_SIZE;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TITLE_X;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TITLE_Y;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.blocks.entity.computation.NEComputationUpgradeRules;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A small LDLib1 replica of GTCEu's ConfiguratorPanel tab. */
public final class NEGtceuConfiguratorTabWidget extends WidgetGroup {
    private static final IGuiTexture BACKGROUND =
            new ResourceBorderTexture("gtceu:textures/gui/base/background.png", 16, 16, 4, 4);
    private static final Icon ICON = Icon.WRENCH;

    private final Supplier<Integer> valueSupplier;
    private final Consumer<Integer> valueConsumer;
    private final BooleanSupplier enabledSupplier;
    private final TextTexture titleTexture;
    private final Widget nativeInput;
    private final TextFieldWidget fallbackInput;
    private boolean expanded;
    private boolean enabled;

    public NEGtceuConfiguratorTabWidget(
            int x,
            int y,
            Component title,
            Supplier<Integer> valueSupplier,
            Consumer<Integer> valueConsumer,
            BooleanSupplier enabledSupplier) {
        super(x, y, PARALLEL_TAB_SIZE, PARALLEL_TAB_SIZE);
        this.valueSupplier = valueSupplier;
        this.valueConsumer = valueConsumer;
        this.enabledSupplier = enabledSupplier;
        this.enabled = enabledSupplier.getAsBoolean();
        this.titleTexture = new TextTexture(title.getString())
                .setType(TextTexture.TextType.LEFT_HIDE)
                .setWidth(PARALLEL_PANEL_W - PARALLEL_TAB_SIZE - 5);

        this.nativeInput = createNativeInput();
        if (nativeInput != null) {
            nativeInput.setSelfPosition(PARALLEL_FIELD_X, PARALLEL_FIELD_Y);
            nativeInput.setVisible(false);
            nativeInput.setActive(false);
            addWidget(nativeInput);
            this.fallbackInput = null;
        } else {
            this.fallbackInput = createFallbackInput();
        }
        applyExpandedState();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean isToggleHovered(int mouseX, int mouseY) {
        int toggleX = expanded ? PARALLEL_PANEL_W - PARALLEL_TAB_SIZE : 0;
        return Widget.isMouseOver(
                getPositionX() + toggleX, getPositionY(), PARALLEL_TAB_SIZE, PARALLEL_TAB_SIZE, mouseX, mouseY);
    }

    public boolean isInputHovered(int mouseX, int mouseY) {
        return expanded
                && Widget.isMouseOver(
                        getPositionX() + PARALLEL_FIELD_X,
                        getPositionY() + PARALLEL_FIELD_Y,
                        PARALLEL_FIELD_W,
                        PARALLEL_FIELD_H,
                        mouseX,
                        mouseY);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && expanded) {
            expanded = false;
            applyExpandedState();
        }
        updateInputState();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        boolean currentEnabled = enabledSupplier.getAsBoolean();
        if (currentEnabled != enabled) {
            setEnabled(currentEnabled);
        }
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelWidth = expanded ? PARALLEL_PANEL_W : PARALLEL_TAB_SIZE;
        int panelHeight = expanded ? PARALLEL_PANEL_H : PARALLEL_TAB_SIZE;
        BACKGROUND.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), panelWidth, panelHeight);
        if (expanded) {
            titleTexture.draw(
                    graphics,
                    mouseX,
                    mouseY,
                    getPositionX() + PARALLEL_TITLE_X,
                    getPositionY() + PARALLEL_TITLE_Y,
                    PARALLEL_PANEL_W - PARALLEL_TAB_SIZE - 5,
                    PARALLEL_TAB_SIZE - PARALLEL_BORDER);
        }
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);

        int iconX = getPositionX() + panelWidth - 20;
        NELDLibClientStyle.drawAeIcon(graphics, ICON, iconX, getPositionY() + 4, enabled ? 1.0F : 0.45F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && enabled && isToggleHovered((int) mouseX, (int) mouseY)) {
            expanded = !expanded;
            applyExpandedState();
            Widget.playButtonClickSound();
            return true;
        }
        return enabled && expanded && super.mouseClicked(mouseX, mouseY, button);
    }

    private void applyExpandedState() {
        setSize(expanded ? PARALLEL_PANEL_W : PARALLEL_TAB_SIZE, expanded ? PARALLEL_PANEL_H : PARALLEL_TAB_SIZE);
        updateInputState();
    }

    private void updateInputState() {
        boolean visible = enabled && expanded;
        if (nativeInput != null) {
            nativeInput.setVisible(visible);
            nativeInput.setActive(visible);
        }
        if (fallbackInput != null) {
            fallbackInput.setVisible(visible);
            fallbackInput.setActive(visible);
        }
    }

    private Widget createNativeInput() {
        try {
            Class<?> inputClass = Class.forName("com.gregtechceu.gtceu.api.gui.widget.IntInputWidget");
            Constructor<?> constructor = inputClass.getConstructor(Supplier.class, Consumer.class);
            Widget input = (Widget) constructor.newInstance(valueSupplier, valueConsumer);
            Method setMin = inputClass.getMethod("setMin", Number.class);
            Method setMax = inputClass.getMethod("setMax", Number.class);
            setMin.invoke(input, 0);
            setMax.invoke(input, NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS);
            return input;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private TextFieldWidget createFallbackInput() {
        NEAe2TextButtonWidget decrease = new NEAe2TextButtonWidget(
                        PARALLEL_FIELD_X,
                        PARALLEL_FIELD_Y,
                        20,
                        PARALLEL_FIELD_H,
                        () -> Component.literal("-"),
                        click -> {
                            if (!click.isRemote) {
                                valueConsumer.accept(clamp(valueSupplier.get() - PARALLEL_STEP));
                            }
                        },
                        () -> false,
                        NEAe2TextButtonWidget.BackgroundStyle.TOOLBAR)
                .setTextColors(0xFFFFFFFF, 0xFFFFFFFF, 0xFF777777);
        addWidget(decrease);

        TextFieldWidget input = new TextFieldWidget(
                        PARALLEL_FIELD_X + 22,
                        PARALLEL_FIELD_Y,
                        PARALLEL_FIELD_W - 44,
                        PARALLEL_FIELD_H,
                        () -> Integer.toString(valueSupplier.get()),
                        raw -> {
                            try {
                                valueConsumer.accept(clamp(Integer.parseInt(raw.trim())));
                            } catch (NumberFormatException ignored) {
                                // The field restores its supplier value on the next update.
                            }
                        })
                .setBackground(IGuiTexture.EMPTY)
                .setBordered(false)
                .setTextColor(0xFFFFFFFF)
                .setMaxStringLength(10)
                .setNumbersOnly(0, NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS);
        addWidget(input);

        NEAe2TextButtonWidget increase = new NEAe2TextButtonWidget(
                        PARALLEL_FIELD_X + PARALLEL_FIELD_W - 20,
                        PARALLEL_FIELD_Y,
                        20,
                        PARALLEL_FIELD_H,
                        () -> Component.literal("+"),
                        click -> {
                            if (!click.isRemote) {
                                valueConsumer.accept(clamp(valueSupplier.get() + PARALLEL_STEP));
                            }
                        },
                        () -> false,
                        NEAe2TextButtonWidget.BackgroundStyle.TOOLBAR)
                .setTextColors(0xFFFFFFFF, 0xFFFFFFFF, 0xFF777777);
        addWidget(increase);
        return input;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS, value));
    }
}
