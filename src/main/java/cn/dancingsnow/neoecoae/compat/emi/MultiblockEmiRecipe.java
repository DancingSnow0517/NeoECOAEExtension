package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewContext;
import cn.dancingsnow.neoecoae.client.multiblock.preview.MultiblockPreviewScene;
import cn.dancingsnow.neoecoae.client.multiblock.preview.NEMultiblockSceneRenderer;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MultiblockEmiRecipe implements EmiRecipe {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 170;
    private static final int TITLE_H = 16;
    private static final int REQUIRED_TITLE_H = 12;
    private static final int REQUIRED_SLOTS_H = 22;
    private static final int BOTTOM_PADDING = 4;
    private static final int SLOT_SIZE = 18;
    private static final int TEXT_COLOR = 0xFF404040;

    private final MultiBlockDefinition definition;
    private final ResourceLocation id;
    private final MultiblockPreviewScene scene;
    private final NEMultiblockSceneRenderer renderer = new NEMultiblockSceneRenderer();
    private final List<EmiStack> materialStacks = new ArrayList<>();
    private final List<EmiIngredient> inputs = new ArrayList<>();
    private final List<EmiStack> outputs = new ArrayList<>();

    public MultiblockEmiRecipe(MultiBlockDefinition definition) {
        this.definition = definition;
        this.id = createId(definition);
        this.scene = MultiblockPreviewContext.createScene(definition, definition.getExpandMin());

        for (ItemStack stack : scene.requiredItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            EmiStack emiStack = EmiStack.of(stack.copy());
            materialStacks.add(emiStack);
            inputs.add(emiStack);
        }

        outputs.add(EmiStack.of(definition.getOwner().value()));
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return NeoECOAEEmiPlugin.MULTIBLOCK;
    }

    @Override
    public @Nullable ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int width = Math.max(WIDTH, widgets.getWidth());
        int height = Math.max(HEIGHT, widgets.getHeight());

        int sceneX = 4;
        int sceneY = TITLE_H + 2;
        int sceneW = width - sceneX * 2;
        int slotsY = height - BOTTOM_PADDING - REQUIRED_SLOTS_H;
        int requiredTitleY = slotsY - REQUIRED_TITLE_H;
        int sceneH = Math.max(72, requiredTitleY - sceneY - 2);

        widgets.addDrawable(0, 0, width, height,
                (g, mouseX, mouseY, delta) -> renderer.render(g, scene, sceneX, sceneY, sceneW, sceneH, delta));
        widgets.addText(definition.getName(), 4, 4, TEXT_COLOR, false);
        widgets.addText(Component.literal("方块数量需求"), 4, requiredTitleY + 1, TEXT_COLOR, false);

        int visibleSlots = Math.min(materialStacks.size(), Math.max(0, (width - 8) / SLOT_SIZE));
        int totalSlotWidth = visibleSlots * SLOT_SIZE;
        int slotX = Math.max(4, (width - totalSlotWidth) / 2);
        for (int i = 0; i < visibleSlots; i++) {
            widgets.addSlot(materialStacks.get(i), slotX + i * SLOT_SIZE, slotsY)
                    .drawBack(true);
        }
    }

    private static ResourceLocation createId(MultiBlockDefinition definition) {
        Block owner = definition.getOwner().value();
        ResourceLocation ownerId = BuiltInRegistries.BLOCK.getKey(owner);
        if (ownerId == null) {
            return NeoECOAE.id("multiblock/unknown");
        }
        return NeoECOAE.id("multiblock/" + ownerId.getNamespace() + "/" + ownerId.getPath());
    }
}
