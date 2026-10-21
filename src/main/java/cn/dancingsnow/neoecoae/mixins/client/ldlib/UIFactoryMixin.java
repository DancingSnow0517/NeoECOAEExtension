package cn.dancingsnow.neoecoae.mixins.client.ldlib;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=UIFactory.class,remap=false)
public abstract class UIFactoryMixin {
    @Redirect(method="initClientUI",at=@At(value="NEW",
        target="com/lowdragmc/lowdraglib/gui/modular/ModularUIGuiContainer"))
    private ModularUIGuiContainer neoecoae$fitRetainedScreen(ModularUI ui,int windowId){
        return ui.mainGroup instanceof UIElement?new RetainedUIGuiContainer(ui,windowId):new ModularUIGuiContainer(ui,windowId);
    }
}
