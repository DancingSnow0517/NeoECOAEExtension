package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.lang.reflect.Constructor;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraftforge.fml.loading.FMLEnvironment;

final class NEMultiblockPatternViewerFactory {
    private static final String CLIENT_VIEWER_CLASS =
            "cn.dancingsnow.neoecoae.gui.ldlib.widget.NEMultiblockPatternViewerWidget";

    private NEMultiblockPatternViewerFactory() {}

    static NEMultiblockPatternViewer create(
            int x,
            int y,
            int width,
            int height,
            Supplier<MultiBlockDefinition> definitionSupplier,
            IntSupplier repeatsSupplier,
            BooleanSupplier mirroredSupplier,
            BooleanSupplier formedSupplier,
            IntSupplier selectedLayerSupplier) {
        if (FMLEnvironment.dist.isClient()) {
            NEMultiblockPatternViewer clientViewer = createClientViewer(
                    x,
                    y,
                    width,
                    height,
                    definitionSupplier,
                    repeatsSupplier,
                    mirroredSupplier,
                    formedSupplier,
                    selectedLayerSupplier);
            if (clientViewer != null) {
                return clientViewer;
            }
        }
        return new EmptyPatternViewerWidget(x, y, width, height);
    }

    private static NEMultiblockPatternViewer createClientViewer(
            int x,
            int y,
            int width,
            int height,
            Supplier<MultiBlockDefinition> definitionSupplier,
            IntSupplier repeatsSupplier,
            BooleanSupplier mirroredSupplier,
            BooleanSupplier formedSupplier,
            IntSupplier selectedLayerSupplier) {
        try {
            Class<?> viewerClass = Class.forName(CLIENT_VIEWER_CLASS);
            Constructor<?> constructor = viewerClass.getConstructor(
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    Supplier.class,
                    IntSupplier.class,
                    BooleanSupplier.class,
                    BooleanSupplier.class,
                    IntSupplier.class);
            return (NEMultiblockPatternViewer) constructor.newInstance(
                    x,
                    y,
                    width,
                    height,
                    definitionSupplier,
                    repeatsSupplier,
                    mirroredSupplier,
                    formedSupplier,
                    selectedLayerSupplier);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static final class EmptyPatternViewerWidget extends WidgetGroup implements NEMultiblockPatternViewer {
        private EmptyPatternViewerWidget(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        public Widget asWidget() {
            return this;
        }

        @Override
        public MultiblockPatternSnapshot snapshot() {
            return null;
        }

        @Override
        public int selectedLayer() {
            return -1;
        }
    }
}
