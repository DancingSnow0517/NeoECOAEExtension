package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewContext;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternPreviewService;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

public final class NEMultiblockPatternViewerWidget extends NELDLibMultiblockSceneWidget {
    private final PatternState patternState;

    public NEMultiblockPatternViewerWidget(
            int x,
            int y,
            int width,
            int height,
            Supplier<MultiBlockDefinition> definitionSupplier,
            IntSupplier repeatsSupplier,
            BooleanSupplier mirroredSupplier) {
        this(x, y, width, height, definitionSupplier, repeatsSupplier, mirroredSupplier, () -> false);
    }

    public NEMultiblockPatternViewerWidget(
            int x,
            int y,
            int width,
            int height,
            Supplier<MultiBlockDefinition> definitionSupplier,
            IntSupplier repeatsSupplier,
            BooleanSupplier mirroredSupplier,
            BooleanSupplier formedSupplier) {
        this(
                x,
                y,
                width,
                height,
                definitionSupplier,
                repeatsSupplier,
                mirroredSupplier,
                formedSupplier,
                () -> -1);
    }

    public NEMultiblockPatternViewerWidget(
            int x,
            int y,
            int width,
            int height,
            Supplier<MultiBlockDefinition> definitionSupplier,
            IntSupplier repeatsSupplier,
            BooleanSupplier mirroredSupplier,
            BooleanSupplier formedSupplier,
            IntSupplier selectedLayerSupplier) {
        this(
                x,
                y,
                width,
                height,
                new PatternState(
                        definitionSupplier, repeatsSupplier, mirroredSupplier, formedSupplier, selectedLayerSupplier));
    }

    private NEMultiblockPatternViewerWidget(int x, int y, int width, int height, PatternState patternState) {
        super(x, y, width, height, patternState::scene);
        this.patternState = patternState;
    }

    public @Nullable MultiblockPatternSnapshot snapshot() {
        return patternState.snapshot();
    }

    public int selectedLayer() {
        return patternState.selectedLayer();
    }

    private static final class PatternState {
        private final Supplier<MultiBlockDefinition> definitionSupplier;
        private final IntSupplier repeatsSupplier;
        private final BooleanSupplier mirroredSupplier;
        private final BooleanSupplier formedSupplier;
        private final IntSupplier selectedLayerSupplier;

        private MultiBlockDefinition cachedDefinition;
        private int cachedRepeats = Integer.MIN_VALUE;
        private boolean cachedMirrored;
        private MultiblockPatternSnapshot cachedSnapshot;

        private PatternState(
                Supplier<MultiBlockDefinition> definitionSupplier,
                IntSupplier repeatsSupplier,
                BooleanSupplier mirroredSupplier,
                BooleanSupplier formedSupplier,
                IntSupplier selectedLayerSupplier) {
            this.definitionSupplier = definitionSupplier;
            this.repeatsSupplier = repeatsSupplier;
            this.mirroredSupplier = mirroredSupplier;
            this.formedSupplier = formedSupplier;
            this.selectedLayerSupplier = selectedLayerSupplier;
        }

        private @Nullable MultiblockPatternSnapshot snapshot() {
            MultiBlockDefinition definition = definitionSupplier.get();
            if (definition == null) {
                cachedDefinition = null;
                cachedSnapshot = null;
                return null;
            }
            int repeats = repeatsSupplier.getAsInt();
            boolean mirrored = mirroredSupplier.getAsBoolean();
            if (cachedSnapshot == null
                    || cachedDefinition != definition
                    || cachedRepeats != repeats
                    || cachedMirrored != mirrored) {
                cachedDefinition = definition;
                cachedRepeats = repeats;
                cachedMirrored = mirrored;
                cachedSnapshot = MultiblockPatternPreviewService.create(definition, repeats, mirrored);
            }
            return cachedSnapshot;
        }

        private @Nullable MultiblockPreviewScene scene() {
            MultiblockPatternSnapshot snapshot = snapshot();
            return snapshot == null
                    ? null
                    : MultiblockPreviewContext.createScene(snapshot, formedSupplier.getAsBoolean(), selectedLayer());
        }

        private int selectedLayer() {
            MultiblockPatternSnapshot snapshot = snapshot();
            int selectedLayer = selectedLayerSupplier.getAsInt();
            if (snapshot == null || selectedLayer < 0 || snapshot.blocksForLayer(selectedLayer).isEmpty()) {
                return -1;
            }
            return selectedLayer;
        }
    }
}
