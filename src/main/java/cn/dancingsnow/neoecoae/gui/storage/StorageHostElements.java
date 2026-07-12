package cn.dancingsnow.neoecoae.gui.storage;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class StorageHostElements {
    private StorageHostElements() {
    }

    static UIElement syncedDisplay(BooleanSupplier display) {
        SyncedDisplayElement element = new SyncedDisplayElement(display.getAsBoolean());
        element.bind(DataBindingBuilder.boolS2C(display::getAsBoolean).build());
        return element;
    }

    public static Label sectionLabel(Supplier<Component> text, IntSupplier color) {
        Label label = textSegment(text, color);
        label.textStyle(StorageHostElements::sectionTextStyle);
        return label;
    }

    static Label panelTitle(Supplier<Component> text) {
        Label label = new Label();
        Supplier<Component> styledText = () -> text.get().copy().withColor(StorageHostText.PRIMARY);
        label.setText(styledText.get());
        label.bind(DataBindingBuilder.componentS2C(styledText).build());
        label.textStyle(StorageHostElements::panelTitleTextStyle);
        return label;
    }

    public static Label textSegment(Supplier<Component> text, IntSupplier color) {
        Label label = new Label();
        Supplier<Component> styledText = () -> text.get().copy().withColor(color.getAsInt());
        label.setText(styledText.get());
        label.bind(DataBindingBuilder.componentS2C(styledText).build());
        label.textStyle(StorageHostElements::lineTextStyle);
        return label;
    }

    public static UIElement horizontalRow(int height, int gap) {
        return new UIElement().layout(layout -> {
            layout.height(height);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(gap);
        });
    }

    static UIElement tinyInsetPanel(int width, int height) {
        UIElement panel = new UIElement();
        panel.addChild(insetLayer("eco-storage-load-inset-edge", 0, 0, width, height));
        panel.addChild(insetLayer("eco-storage-load-inset-border", 1, 1, width - 2, height - 2));
        panel.addChild(insetLayer("eco-storage-load-inset-fill", 2, 2, width - 4, height - 4));
        return panel;
    }

    public static <T extends UIElement> T absolute(T element, int left, int top, int width, int height) {
        element.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(left);
            layout.top(top);
            layout.width(width);
            layout.height(height);
        });
        return element;
    }

    private static UIElement insetLayer(String className, int left, int top, int width, int height) {
        return absolute(new UIElement().addClass(className), left, top, width, height);
    }

    private static void lineTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textShadow(false);
    }

    private static void sectionTextStyle(TextElement.TextStyle style) {
        lineTextStyle(style);
    }

    private static void panelTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(false)
            .textAlignHorizontal(Horizontal.CENTER)
            .textWrap(TextWrap.HOVER_ROLL)
            .textShadow(false);
    }

    private static final class SyncedDisplayElement extends UIElement implements IBindable<Boolean> {
        private boolean display;

        private SyncedDisplayElement(boolean display) {
            setValue(display);
        }

        @Override
        public Boolean getValue() {
            return display;
        }

        @Override
        public IDataSource<Boolean> setValue(@Nullable Boolean value) {
            display = Boolean.TRUE.equals(value);
            setDisplay(display);
            return this;
        }
    }
}
