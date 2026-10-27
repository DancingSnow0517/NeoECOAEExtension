package cn.dancingsnow.neoecoae.integration.kubejs;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.kubejs.schema.NecoCoolingSchema;
import cn.dancingsnow.neoecoae.integration.kubejs.schema.NecoIntegratedWorkingStationSchema;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public class NeoECOAEKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
        event.namespace(NeoECOAE.MOD_ID)
                .register("cooling", NecoCoolingSchema.SCHEMA)
                .register("integrated_working_station", NecoIntegratedWorkingStationSchema.SCHEMA)
                .register("large_integrated_working_station", NecoIntegratedWorkingStationSchema.SCHEMA)
                .register("extended_crystal_assembler", NecoIntegratedWorkingStationSchema.SCHEMA);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("NeoECOAE", NeoECOAE.class);
    }

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        filter.allow("cn.dancingsnow.neoecoae");
    }
}