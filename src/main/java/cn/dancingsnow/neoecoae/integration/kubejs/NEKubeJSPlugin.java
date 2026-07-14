package cn.dancingsnow.neoecoae.integration.kubejs;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.kubejs.recipe.CoolingRecipeSchema;
import cn.dancingsnow.neoecoae.integration.kubejs.recipe.IntegratedWorkingStationRecipeSchema;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public class NEKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
        event.register(NeoECOAE.id("cooling"), CoolingRecipeSchema.SCHEMA);
        event.register(NeoECOAE.id("integrated_working_station"), IntegratedWorkingStationRecipeSchema.SCHEMA);
    }

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        filter.allow("cn.dancingsnow.neoecoae");
    }
}
