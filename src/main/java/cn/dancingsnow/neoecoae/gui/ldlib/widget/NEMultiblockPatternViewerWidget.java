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
        this(x, y, width, height, new PatternState(definitionSupplier, repeatsSupplier, mirroredSupplier));
    }

    private NEMultiblockPatternViewerWidget(int x, int y, int width, int height, PatternState patternState) {
        super(x, y, width, height, patternState::scene);
        this.patternState = patternState;
    }

    public @Nullable MultiblockPatternSnapshot snapshot() {
        return patternState.snapshot();
    }

    public int selectedLayer() {
        return patternState.selectedLayer;
    }

    public void previousLayer() {
        patternState.previousLayer();
    }

    public void nextLayer() {
        patternState.nextLayer();
    }

    private static final class PatternState {
        private final Supplier<MultiBlockDefinition> definitionSupplier;
        private final IntSupplier repeatsSupplier;
        private final BooleanSupplier mirroredSupplier;

        private MultiBlockDefinition cachedDefinition;
        private int cachedRepeats = Integer.MIN_VALUE;
        private boolean cachedMirrored;
        private MultiblockPatternSnapshot cachedSnapshot;
        private int selectedLayer = -1;

        private PatternState(
                Supplier<MultiBlockDefinition> definitionSupplier,
                IntSupplier repeatsSupplier,
                BooleanSupplier mirroredSupplier) {
            this.definitionSupplier = definitionSupplier;
            this.repeatsSupplier = repeatsSupplier;
            this.mirroredSupplier = mirroredSupplier;
        }

        private @Nullable MultiblockPatternSnapshot snapshot() {
            MultiBlockDefinition definition = definitionSupplier.get();
            if (definition == null) {
                cachedDefinition = null;
                cachedSnapshot = null;
                selectedLayer = -1;
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
                normalizeSelectedLayer();
            }
            return cachedSnapshot;
        }

        private @Nullable MultiblockPreviewScene scene() {
            MultiblockPatternSnapshot snapshot = snapshot();
            return snapshot == null ? null : MultiblockPreviewContext.createScene(snapshot, false, selectedLayer);
        }

        private void previousLayer() {
            MultiblockPatternSnapshot snapshot = snapshot();
            if (snapshot == null || snapshot.layers().isEmpty()) {
                selectedLayer = -1;
                return;
            }
            if (selectedLayer < 0) {
                selectedLayer = snapshot.maxLayerY();
                return;
            }
            int previous = -1;
            for (var layer : snapshot.layers()) {
                if (layer.y() >= selectedLayer) {
                    break;
                }
                previous = layer.y();
            }
            selectedLayer = previous;
        }

        private void nextLayer() {
            MultiblockPatternSnapshot snapshot = snapshot();
            if (snapshot == null || snapshot.layers().isEmpty()) {
                selectedLayer = -1;
                return;
            }
            if (selectedLayer < 0) {
                selectedLayer = snapshot.minLayerY();
                return;
            }
            for (var layer : snapshot.layers()) {
                if (layer.y() > selectedLayer) {
                    selectedLayer = layer.y();
                    return;
                }
            }
            selectedLayer = -1;
        }

        private void normalizeSelectedLayer() {
            if (cachedSnapshot == null || selectedLayer < 0) {
                return;
            }
            if (cachedSnapshot.blocksForLayer(selectedLayer).isEmpty()) {
                selectedLayer = -1;
            }
        }
    }
}
