package cn.dancingsnow.neoecoae.platform.ui.runtime.integration.xei.emi;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.*;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.*;
import java.util.function.Function;
public abstract class ModularUIEMIRecipe implements EmiRecipe {
    private final Function<ModularUIEMIRecipe,ModularUI> factory;
    protected ModularUIEMIRecipe(Function<ModularUIEMIRecipe,ModularUI> factory){this.factory=factory;}
    @Override public List<EmiStack> getOutputs(){return List.of();}
    @Override public void addWidgets(WidgetHolder holder){
        var root=factory.apply(this).prepareWidget();
        new com.lowdragmc.lowdraglib.emi.ModularEmiRecipe<UIElement>(() -> root){
            @Override public EmiRecipeCategory getCategory(){return ModularUIEMIRecipe.this.getCategory();}
            @Override public net.minecraft.resources.ResourceLocation getId(){return ModularUIEMIRecipe.this.getId();}
            @Override public int getDisplayWidth(){return ModularUIEMIRecipe.this.getDisplayWidth();}
            @Override public int getDisplayHeight(){return ModularUIEMIRecipe.this.getDisplayHeight();}
        }.addWidgets(holder);
    }
}
