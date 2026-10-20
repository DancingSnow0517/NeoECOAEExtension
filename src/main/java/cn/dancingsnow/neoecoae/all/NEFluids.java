package cn.dancingsnow.neoecoae.all;

import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEFluids {
    public static final FluidEntry<BaseFlowingFluid.Flowing> CRYOTHEUM_SOLUTION = REGISTRATE
        .object("cryotheum_solution")
        .fluid("cryotheum_solution")
        .fluidProperties(p -> p.tickRate(2).slopeFindDistance(0))
        .source(BaseFlowingFluid.Source::new)
        .bucket()
        .model((ctx, prov) -> {
            prov.withExistingParent(
                ctx.getName(),
                ResourceLocation.parse("neoforge:item/bucket_drip")
            ).customLoader((builder, helper) -> DynamicFluidContainerModelBuilder.begin(builder, helper).fluid(ctx.get().content));
        })
        .build()
        .register();

    public static void register() {

    }
}
