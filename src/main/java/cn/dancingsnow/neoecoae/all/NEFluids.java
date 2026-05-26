package cn.dancingsnow.neoecoae.all;

import com.tterrag.registrate.util.entry.FluidEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder;
import net.minecraftforge.fluids.ForgeFlowingFluid;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

public class NEFluids {
    public static final FluidEntry<ForgeFlowingFluid.Flowing> CRYOTHEUM_SOLUTION = REGISTRATE
        .object("cryotheum_solution")
        .fluid("cryotheum_solution")
        .fluidProperties(p -> p.tickRate(2).slopeFindDistance(0))
        .source(ForgeFlowingFluid.Source::new)
        .bucket()
        .model((ctx, prov) -> {
            prov.withExistingParent(
                ctx.getName(),
                ResourceLocation.parse("forge:item/bucket_drip")
            ).customLoader((builder, helper) -> DynamicFluidContainerModelBuilder.begin(builder, helper).fluid(Fluids.WATER));
        })
        .build()
        .register();

    public static void register() {

    }
}
