package cn.dancingsnow.neoecoae.mixins.client.jei;

import cn.dancingsnow.neoecoae.integration.jei.categories.multiblock.MultiBlockInfoCategory;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fit the multiblock page without changing the user's preferred height for other JEI categories. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public abstract class MultiblockRecipesGuiMixin {
    @Unique private boolean neoecoae$compactMultiblock;
    @Unique private boolean neoecoae$resizePending;

    @ModifyExpressionValue(method = "updateLayout", at = @At(value = "INVOKE", target =
        "Lmezz/jei/gui/recipes/IRecipeGuiLogic;getSelectedRecipeCategory()Lmezz/jei/api/recipe/category/IRecipeCategory;"))
    private IRecipeCategory<?> neoecoae$selectedCategory(IRecipeCategory<?> category) {
        boolean compact = category instanceof MultiBlockInfoCategory;
        if (neoecoae$compactMultiblock != compact) {
            neoecoae$compactMultiblock = compact;
            neoecoae$resizePending = true;
        }
        return category;
    }

    // JEI 19.64+ sizes in updateSize; older JEI 19 and JEI 15 size in Screen.init.
    @ModifyArg(method = {"updateSize", "init", "m_7856_"}, at = @At(value = "INVOKE", target =
        "Lmezz/jei/gui/recipes/RecipeGuiSizing;calculateInitialSize(IZI)Lmezz/jei/gui/recipes/RecipeGuiSizing$Size;"),
        index = 2, require = 0)
    private int neoecoae$compactHeight(int preferredMaximum) {
        return neoecoae$compactMultiblock
            ? Math.min(preferredMaximum, MultiBlockInfoWrapper.DEFAULT_HEIGHT + 50)
            : preferredMaximum;
    }

    @Inject(method = "updateLayout", at = @At("RETURN"))
    private void neoecoae$resizeOnCategoryChange(CallbackInfo callback) {
        if (!neoecoae$resizePending) return;
        neoecoae$resizePending = false;
        var client = Minecraft.getInstance();
        if (client.screen == (Object) this) {
            Screen screen = (Screen) (Object) this;
            screen.init(client, screen.width, screen.height);
        }
    }
}
