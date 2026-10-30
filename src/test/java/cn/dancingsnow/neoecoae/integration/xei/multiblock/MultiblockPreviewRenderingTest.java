package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class MultiblockPreviewRenderingTest {
    private static PreviewController controller;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            controller = new PreviewController();
            Registry.register(registry, "neoecoae_preview_test:controller", controller);
        } finally {
            registry.freeze();
        }
    }

    @Test void formationUsesTheControllersOwnPropertyAndPreservesOtherState() {
        assertNotSame(PreviewController.FORMED, BooleanProperty.create("formed"));
        var state = controller.defaultBlockState().setValue(PreviewController.WORKING, true);
        var formed = MultiBlockContext.formedPreviewState(state);
        assertTrue(formed.getValue(PreviewController.FORMED));
        assertTrue(formed.getValue(PreviewController.WORKING));
        assertSame(formed, MultiBlockContext.formedPreviewState(formed));
        assertSame(Blocks.GLASS.defaultBlockState(), MultiBlockContext.formedPreviewState(Blocks.GLASS.defaultBlockState()));
    }

    @Test void materialCountsUseVanillaDecorationsRatherThanScaledCustomText() throws Exception {
        var type = read("cn/dancingsnow/neoecoae/integration/xei/multiblock/MultiBlockInfoWrapper$RequiredItemSlot");
        var draw = type.methods.stream().filter(method -> method.name.equals("drawItemStack")).findFirst().orElseThrow();
        boolean helper = false;
        for (var instruction : draw.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                assertNotEquals("drawStringFixedCorner", call.name);
                helper |= call.owner.equals("com/lowdragmc/lowdraglib2/gui/util/DrawerHelper")
                    && call.name.equals("drawItemStack");
            }
        }
        assertTrue(helper, "LDLib's depth setup must keep the item visible above the recipe background");
        var itemHelper = read("com/lowdragmc/lowdraglib2/gui/util/DrawerHelper");
        var helperDraw = itemHelper.methods.stream().filter(method -> method.name.equals("drawItemStack")).findFirst().orElseThrow();
        boolean vanilla = false;
        for (var instruction : helperDraw.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                vanilla |= call.owner.equals("net/minecraft/client/gui/GuiGraphics")
                    && call.desc.equals("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V");
            }
        }
        assertTrue(vanilla, "Counts must use the same font, shadow and corner coordinates as vanilla");
    }

    @Test void theActualJeiJarHasBothCategoryAndSizingInjectionTargets() throws Exception {
        var gui = read("mezz/jei/gui/recipes/RecipesGui");
        boolean sizing = false, category = false;
        for (var method : gui.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    sizing |= call.owner.equals("mezz/jei/gui/recipes/RecipeGuiSizing")
                        && call.name.equals("calculateInitialSize")
                        && call.desc.equals("(IZI)Lmezz/jei/gui/recipes/RecipeGuiSizing$Size;");
                    category |= method.name.equals("updateLayout")
                        && call.owner.equals("mezz/jei/gui/recipes/IRecipeGuiLogic")
                        && call.name.equals("getSelectedRecipeCategory");
                }
            }
        }
        assertTrue(sizing, "JEI size hook must match the installed dependency");
        assertTrue(category, "Category changes must restore the user's ordinary JEI size");
    }

    private static ClassNode read(String name) throws Exception {
        try (var input = MultiblockPreviewRenderingTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static final class PreviewController extends Block {
        static final BooleanProperty FORMED = BooleanProperty.create("formed");
        static final BooleanProperty WORKING = BooleanProperty.create("working");
        PreviewController() { super(BlockBehaviour.Properties.of()); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FORMED, WORKING);
        }
    }
}
