package cn.dancingsnow.neoecoae.platform.ui.runtime.integration.xei.jei;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import java.util.function.Function;
/** Delegates recipe rendering, ingredients and native event listeners to LDLib1's JEI integration. */
public abstract class ModularUIRecipeCategory<T> extends com.lowdragmc.lowdraglib.jei.ModularUIRecipeCategory<T> {
    protected ModularUIRecipeCategory(Function<T,ModularUI> factory){super(recipe->new com.lowdragmc.lowdraglib.jei.ModularWrapper<>(factory.apply(recipe).prepareWidget()));}
}
