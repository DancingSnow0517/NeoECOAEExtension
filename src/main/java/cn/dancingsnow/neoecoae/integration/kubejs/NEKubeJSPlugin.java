package cn.dancingsnow.neoecoae.integration.kubejs;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.kubejs.recipe.CoolingRecipeSchema;
import cn.dancingsnow.neoecoae.integration.kubejs.recipe.IntegratedWorkingStationSchema;
import cn.dancingsnow.neoecoae.integration.kubejs.recipe.LargeIntegratedWorkingStationSchema;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;

public class NEKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        registry.register(NeoECOAE.id("cooling"), CoolingRecipeSchema.SCHEMA);
        registry.register(NeoECOAE.id("integrated_working_station"), IntegratedWorkingStationSchema.SCHEMA);
        registry.register(NeoECOAE.id("large_integrated_working_station"), LargeIntegratedWorkingStationSchema.SCHEMA);
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow("cn.dancingsnow.neoecoae");
    }
}
