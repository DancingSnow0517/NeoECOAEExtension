package cn.dancingsnow.neoecoae.platform.ui.runtime.integration.xei.jei;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Keeps ECO recipe models separate from the native wrapper contract used by older LDLib. */
public abstract class ModularUIRecipeCategory<T> implements IRecipeCategory<T> {
    private final Function<T, ModularUI> factory;
    private final LoadingCache<T, ModularWrapper<?>> wrappers;
    // Invoke through JEI's interface: LDLib changed its generic bound and method descriptors.
    private final IRecipeCategory<ModularWrapper<?>> nativeCategory = createNativeCategory();

    protected ModularUIRecipeCategory(Function<T, ModularUI> factory) {
        this.factory = factory;
        wrappers = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.SECONDS).maximumSize(10)
            .build(new CacheLoader<>() {
                @Override
                public ModularWrapper<?> load(T recipe) {
                    return createWrapper(recipe);
                }
            });
    }

    protected ModularWrapper<?> createWrapper(T recipe) {
        return new ModularWrapper<>(factory.apply(recipe).prepareWidget());
    }

    @SuppressWarnings("deprecation")
    private IRecipeCategory<ModularWrapper<?>> createNativeCategory() {
        return new com.lowdragmc.lowdraglib.jei.ModularUIRecipeCategory<ModularWrapper<?>>() {
            @Override
            @SuppressWarnings("unchecked")
            public RecipeType<ModularWrapper<?>> getRecipeType() {
                return (RecipeType<ModularWrapper<?>>) (RecipeType<?>) ModularUIRecipeCategory.this.getRecipeType();
            }

            @Override
            public Component getTitle() { return ModularUIRecipeCategory.this.getTitle(); }

            @Override
            public IDrawable getIcon() { return ModularUIRecipeCategory.this.getIcon(); }

            @Override
            public int getWidth() { return ModularUIRecipeCategory.this.getWidth(); }

            @Override
            public int getHeight() { return ModularUIRecipeCategory.this.getHeight(); }
        };
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        nativeCategory.setRecipe(builder, wrappers.getUnchecked(recipe), focuses);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, T recipe, IFocusGroup focuses) {
        nativeCategory.createRecipeExtras(builder, wrappers.getUnchecked(recipe), focuses);
    }

    @Override
    public void draw(T recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        nativeCategory.draw(wrappers.getUnchecked(recipe), slots, graphics, mouseX, mouseY);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, T recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        nativeCategory.getTooltip(tooltip, wrappers.getUnchecked(recipe), slots, mouseX, mouseY);
    }
}
