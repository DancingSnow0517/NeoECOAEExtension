package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_BORDER;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_FIELD_H;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_FIELD_W;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_FIELD_X;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_FIELD_Y;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_PANEL_H;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_PANEL_W;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_PANEL_X;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_PANEL_Y;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_STEP;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TAB_SIZE;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TITLE_X;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TITLE_Y;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TOGGLE_H;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TOGGLE_W;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TOGGLE_X;
import static cn.dancingsnow.neoecoae.gui.ldlib.computation.NEComputationLayout.PARALLEL_TOGGLE_Y;

import cn.dancingsnow.neoecoae.blocks.entity.computation.NEComputationUpgradeRules;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** A small LDLib1 replica of GTCEu's ConfiguratorPanel tab. */
public final class NEGtceuConfiguratorTabWidget extends WidgetGroup {
    private static final IGuiTexture BACKGROUND =
            new ResourceBorderTexture("gtceu:textures/gui/base/background.png", 16, 16, 4, 4);

    private final Supplier<Integer> valueSupplier;
    private final Consumer<Integer> valueConsumer;
    private final IntSupplier maxSupplier;
    private final TextTexture titleTexture;
    private final Widget nativeInput;
    private final TextFieldWidget fallbackInput;
    /** Everything that belongs to the expanded panel and must disappear when it collapses. */
    private final List<Widget> collapsibleInputs = new ArrayList<>();

    private Method nativeSetMax;
    private int lastAppliedMax = Integer.MIN_VALUE;
    private boolean expanded;
    /**
     * GTCEu's {@code NumberInputWidget.updateTextFieldRange()} ends with
     * {@code setValue(clamp(valueSupplier.get(), min, max))}, and {@code setValue} forwards to
     * {@code onChanged} unconditionally. It runs from the constructor and from every
     * {@code setMin}/{@code setMax} call, all of which happen before the synced state arrives -- so
     * the supplier still reports the empty state's 0. On the server that push is indistinguishable
     * from a real edit and would overwrite the saved configuration every time the screen opens.
     * Stay closed until initial data has been exchanged, and close again around our own range
     * updates, so only input the player actually produced reaches the consumer.
     */
    private boolean acceptsValueWrites;

    public NEGtceuConfiguratorTabWidget(
            int x,
            int y,
            Component title,
            Supplier<Integer> valueSupplier,
            Consumer<Integer> valueConsumer,
            IntSupplier maxSupplier) {
        super(x, y, PARALLEL_TAB_SIZE, PARALLEL_TAB_SIZE);
        this.valueSupplier = valueSupplier;
        this.valueConsumer = valueConsumer;
        this.maxSupplier = maxSupplier;
        this.titleTexture = new TextTexture(title.getString())
                .setType(TextTexture.TextType.LEFT_HIDE)
                .setWidth(PARALLEL_PANEL_W - PARALLEL_TAB_SIZE - 5);

        this.nativeInput = createNativeInput();
        if (nativeInput != null) {
            nativeInput.setSelfPosition(fieldX(), fieldY());
            addWidget(nativeInput);
            collapsibleInputs.add(nativeInput);
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
        int toggleX = toggleButtonX();
        return Widget.isMouseOver(
                getPositionX() + toggleX,
                getPositionY() + toggleButtonY(),
                PARALLEL_TOGGLE_W,
                PARALLEL_TOGGLE_H,
                mouseX,
                mouseY);
    }

    public boolean isInputHovered(int mouseX, int mouseY) {
        return expanded
                && Widget.isMouseOver(
                        getPositionX() + fieldX(),
                        getPositionY() + fieldY(),
                        PARALLEL_FIELD_W,
                        PARALLEL_FIELD_H,
                        mouseX,
                        mouseY);
    }

    @Override
    public void updateScreen() {
        updateInputRange();
        super.updateScreen();
    }

    /**
     * Server-side counterpart of {@link #updateScreen()}. {@code ModularUIContainer} only ticks
     * {@code detectAndSendChanges()}, so without this the server's copy of the input would keep
     * whatever range the constructor installed and validate every incoming client action against it.
     */
    @Override
    public void detectAndSendChanges() {
        updateInputRange();
        super.detectAndSendChanges();
    }

    /**
     * The parent {@code NELDLibSyncedStateWidget} refreshes its state before dispatching initial
     * data to children, so the authoritative limit is already available here. Applying it before
     * {@code super} matters: the child text field's initial value goes through its validator, and a
     * stale range would clamp the real value and echo the clamped one straight back to the server.
     */
    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        updateInputRange();
        super.writeInitialData(buffer);
        acceptsValueWrites = true;
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        updateInputRange();
        super.readInitialData(buffer);
        acceptsValueWrites = true;
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
                    getPositionX() + titleX(),
                    getPositionY() + titleY(),
                    PARALLEL_PANEL_W - PARALLEL_TAB_SIZE - 5,
                    PARALLEL_TAB_SIZE - PARALLEL_BORDER);
        }
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);

        int iconX = getPositionX() + toggleButtonX() + 4;
        int iconY = getPositionY() + toggleButtonY() + 4;
        drawParallelChannelsIcon(graphics, iconX, iconY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isToggleHovered((int) mouseX, (int) mouseY)) {
            expanded = !expanded;
            applyExpandedState();
            Widget.playButtonClickSound();
            return true;
        }
        return expanded && super.mouseClicked(mouseX, mouseY, button);
    }

    private void applyExpandedState() {
        setSize(expanded ? PARALLEL_PANEL_W : PARALLEL_TAB_SIZE, expanded ? PARALLEL_PANEL_H : PARALLEL_TAB_SIZE);
        setSelfPosition(
                expanded ? PARALLEL_PANEL_X : PARALLEL_TOGGLE_X, expanded ? PARALLEL_PANEL_Y : PARALLEL_TOGGLE_Y);
        updateInputState();
    }

    private void updateInputState() {
        // Only visibility follows the toggle. isActive() is what gates
        // WidgetGroup.detectAndSendChanges(), and the server never learns the panel was expanded --
        // the toggle lives in mouseClicked(), which runs client-side only. Deactivating here would
        // make the server skip the input forever, so its value would freeze at whatever
        // readInitialData delivered and never reflect the -/+ buttons. Mouse and keyboard routing
        // both require isVisible() as well, so a collapsed panel still captures nothing.
        for (Widget widget : collapsibleInputs) {
            widget.setVisible(expanded);
            widget.setActive(true);
        }
    }

    private Widget createNativeInput() {
        try {
            Class<?> inputClass = Class.forName("com.gregtechceu.gtceu.api.gui.widget.IntInputWidget");
            Constructor<?> constructor = inputClass.getConstructor(Supplier.class, Consumer.class);
            Consumer<Integer> guarded = this::onInputValueChanged;
            Widget input = (Widget) constructor.newInstance(valueSupplier, guarded);
            Method setMin = inputClass.getMethod("setMin", Number.class);
            Method setMax = inputClass.getMethod("setMax", Number.class);
            setMin.invoke(input, 0);
            // Deliberately permissive: the widget tree is built before the synced state
            // arrives, so maxValue() still reads the empty state's limit of 0. Installing
            // that as the clamp would make the input rewrite the real value to zero the
            // moment it receives it. updateInputRange() applies the authoritative limit
            // once a real state exists; leaving lastAppliedMax unset guarantees it runs.
            setMax.invoke(input, NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS);
            nativeSetMax = setMax;
            return input;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private TextFieldWidget createFallbackInput() {
        NEAe2TextButtonWidget decrease = new NEAe2TextButtonWidget(
                        fieldX(),
                        fieldY(),
                        20,
                        PARALLEL_FIELD_H,
                        () -> Component.literal("-"),
                        click -> {
                            if (!click.isRemote) {
                                onInputValueChanged(clamp(valueSupplier.get() - PARALLEL_STEP));
                            }
                        },
                        () -> false,
                        NEAe2TextButtonWidget.BackgroundStyle.TOOLBAR)
                .setTextColors(0xFFFFFFFF, 0xFFFFFFFF, 0xFF777777);
        addWidget(decrease);
        collapsibleInputs.add(decrease);

        TextFieldWidget input = new TextFieldWidget(
                        fieldX() + 22,
                        fieldY(),
                        PARALLEL_FIELD_W - 44,
                        PARALLEL_FIELD_H,
                        () -> Integer.toString(valueSupplier.get()),
                        raw -> {
                            try {
                                onInputValueChanged(clamp(Integer.parseInt(raw.trim())));
                            } catch (NumberFormatException ignored) {
                                // The field restores its supplier value on the next update.
                            }
                        })
                .setBackground(IGuiTexture.EMPTY)
                .setBordered(false)
                .setTextColor(0xFFFFFFFF)
                .setMaxStringLength(10)
                // Permissive for the same reason as the native input above.
                .setNumbersOnly(0, NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS);
        addWidget(input);
        collapsibleInputs.add(input);

        NEAe2TextButtonWidget increase = new NEAe2TextButtonWidget(
                        fieldX() + PARALLEL_FIELD_W - 20,
                        fieldY(),
                        20,
                        PARALLEL_FIELD_H,
                        () -> Component.literal("+"),
                        click -> {
                            if (!click.isRemote) {
                                onInputValueChanged(clamp(valueSupplier.get() + PARALLEL_STEP));
                            }
                        },
                        () -> false,
                        NEAe2TextButtonWidget.BackgroundStyle.TOOLBAR)
                .setTextColors(0xFFFFFFFF, 0xFFFFFFFF, 0xFF777777);
        addWidget(increase);
        collapsibleInputs.add(increase);
        return input;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(maxValue(), value));
    }

    private int maxValue() {
        return Math.max(0, Math.min(NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS, maxSupplier.getAsInt()));
    }

    /** Forwards a value only once it can represent a real edit. See {@link #acceptsValueWrites}. */
    private void onInputValueChanged(int value) {
        if (!acceptsValueWrites) {
            return;
        }
        valueConsumer.accept(value);
    }

    private void updateInputRange() {
        int max = maxValue();
        if (max == lastAppliedMax) {
            return;
        }
        // setMax re-clamps the current value through onChanged. That echo is ours, not the player's,
        // and the cluster already clamps its own configuration whenever the limit moves.
        boolean armed = acceptsValueWrites;
        acceptsValueWrites = false;
        try {
            if (nativeInput != null && nativeSetMax != null) {
                nativeSetMax.invoke(nativeInput, max);
            } else if (fallbackInput != null) {
                fallbackInput.setNumbersOnly(0, max);
            }
            lastAppliedMax = max;
        } catch (ReflectiveOperationException ignored) {
            // The native GTCEu widget is optional and may vary between versions.
        } finally {
            acceptsValueWrites = armed;
        }
    }

    private int toggleButtonX() {
        return expanded ? PARALLEL_TOGGLE_X - PARALLEL_PANEL_X : 0;
    }

    private int toggleButtonY() {
        return 0;
    }

    /**
     * This tab configures the parallel accelerator count, so it deliberately uses a local glyph
     * rather than an AE2 atlas icon. AE2's icon texture layout is not a stable GUI contract across
     * versions and direct UV sampling can render the missing-texture checkerboard in LDLib screens.
     */
    private static void drawParallelChannelsIcon(GuiGraphics graphics, int x, int y) {
        final int shadow = 0xB3000000;
        final int channel = 0xFFE5B6FF;
        final int accent = 0xFF9D5CD0;

        // Three independent lanes communicate that this control increases concurrent work.
        for (int row = 0; row < 3; row++) {
            int laneY = y + 2 + row * 4;
            graphics.fill(x + 1, laneY + 1, x + 13, laneY + 3, shadow);
            graphics.fill(x + 1, laneY, x + 12, laneY + 1, channel);
            graphics.fill(x + 1, laneY + 1, x + 10, laneY + 2, accent);
            graphics.fill(x + 11, laneY - 1, x + 14, laneY + 3, channel);
            graphics.fill(x + 12, laneY, x + 15, laneY + 2, channel);
        }
    }

    private int fieldX() {
        return PARALLEL_FIELD_X - PARALLEL_PANEL_X;
    }

    private int fieldY() {
        return PARALLEL_FIELD_Y - PARALLEL_PANEL_Y;
    }

    private int titleX() {
        return PARALLEL_TITLE_X - PARALLEL_PANEL_X;
    }

    private int titleY() {
        return PARALLEL_TITLE_Y - PARALLEL_PANEL_Y;
    }
}
