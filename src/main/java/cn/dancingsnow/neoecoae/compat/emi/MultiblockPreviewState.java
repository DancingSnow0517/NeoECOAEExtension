package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewContext;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

final class MultiblockPreviewState {
    static final int MATERIAL_PAGE_SIZE = 9;

    private final MultiBlockDefinition definition;
    private final List<EmiStack> materialStacks = new ArrayList<>();
    private final List<EmiIngredient> inputs = new ArrayList<>();

    private MultiblockPreviewScene scene;
    private int expand;
    private int layer = -1;
    private int materialPage = 0;
    private boolean formed = false;

    MultiblockPreviewState(MultiBlockDefinition definition) {
        this.definition = definition;
        this.expand = definition.getExpandMin();
        rebuildScene();
    }

    List<EmiIngredient> inputs() {
        return inputs;
    }

    MultiblockPreviewScene scene() {
        return scene;
    }

    int expand() {
        return expand;
    }

    int layer() {
        return layer;
    }

    boolean formed() {
        return formed;
    }

    int materialPage() {
        return materialPage;
    }

    List<EmiStack> materialStacks() {
        return materialStacks;
    }

    void nextExpand() {
        if (expand >= definition.getExpandMax()) {
            expand = definition.getExpandMin();
        } else {
            expand++;
        }
        if (formed) {
            formed = false;
        }
        rebuildScene();
    }

    void nextLayer() {
        int maxLayer = scene == null ? 0 : scene.yMax();
        if (layer + 1 > maxLayer) {
            layer = -1;
        } else {
            layer++;
        }
        if (formed) {
            formed = false;
        }
        rebuildScene();
    }

    void toggleFormed() {
        formed = !formed;
        rebuildScene();
    }

    void previousMaterialsPage() {
        int pages = materialPages();
        if (pages > 1) {
            materialPage = materialPage <= 0 ? pages - 1 : materialPage - 1;
        }
    }

    void nextMaterialsPage() {
        int pages = materialPages();
        if (pages > 1) {
            materialPage = materialPage + 1 >= pages ? 0 : materialPage + 1;
        }
    }

    int materialPages() {
        return Math.max(1, (materialStacks.size() + MATERIAL_PAGE_SIZE - 1) / MATERIAL_PAGE_SIZE);
    }

    int materialIndexForSlot(int slot) {
        int index = materialPage * MATERIAL_PAGE_SIZE + slot;
        return index < materialStacks.size() ? index : -1;
    }

    private void rebuildScene() {
        scene = MultiblockPreviewContext.createScene(definition, expand, formed);
        if (scene != null && layer > scene.yMax()) {
            layer = -1;
        }
        refreshMaterials(scene == null ? List.of() : scene.requiredItems());
    }

    private void refreshMaterials(List<ItemStack> stacks) {
        materialStacks.clear();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                materialStacks.add(EmiStack.of(stack.copy()));
            }
        }
        inputs.clear();
        inputs.addAll(materialStacks);
        if (materialPage >= materialPages()) {
            materialPage = Math.max(0, materialPages() - 1);
        }
    }
}
