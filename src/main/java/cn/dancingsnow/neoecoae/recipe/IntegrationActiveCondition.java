package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@code neoecoae:integration_active}: the mod is loaded <em>and</em> its NeoECOAE integration is ported to this
 * version ({@link NeoECOAE#isIntegrationActive}). Compat recipes use it instead of {@code neoforge:mod_loaded} so an
 * installed but unported integration never loads recipes for items it did not register.
 */
public record IntegrationActiveCondition(String modid) implements ICondition {
    public static final MapCodec<IntegrationActiveCondition> CODEC = RecordCodecBuilder.mapCodec(builder -> builder
        .group(Codec.STRING.fieldOf("modid").forGetter(IntegrationActiveCondition::modid))
        .apply(builder, IntegrationActiveCondition::new));

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
        DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, NeoECOAE.MOD_ID);

    static {
        CONDITIONS.register("integration_active", () -> CODEC);
    }

    public static void register(IEventBus modBus) {
        CONDITIONS.register(modBus);
    }

    @Override
    public boolean test(IContext context) {
        return NeoECOAE.isIntegrationActive(modid);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "integration_active(\"" + modid + "\")";
    }
}
