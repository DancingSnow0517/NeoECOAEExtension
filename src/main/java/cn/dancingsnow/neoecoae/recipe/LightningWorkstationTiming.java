package cn.dancingsnow.neoecoae.recipe;

import net.neoforged.fml.ModList;

/** Source machine with no speed cards, including its minimum process time. */
final class LightningWorkstationTiming {
    private LightningWorkstationTiming() {}

    static int ticks(String type, long energy) {
        long rate = type.equals("ae2lt:overload_processing") ? overloadRate() : 200;
        if (rate <= 0) throw new IllegalArgumentException("Source machine's reference FE rate is disabled");
        return (int) Math.max(4, Math.min(LargeWorkstationRecipe.MAX_PROCESSING_TICKS,
            energy / rate + (energy % rate == 0 ? 0 : 1)));
    }

    private static long overloadRate() {
        var mods = ModList.get();
        if (mods != null && mods.isLoaded("ae2lt")) {
            try { return Access.rate(); }
            catch (LinkageError unavailable) { /* Older API: documented default reference rate. */ }
        }
        return 400_000;
    }

    private static final class Access {
        static long rate() {
            return com.moakiee.ae2lt.config.AE2LTCommonConfig.SPEC.isLoaded()
                ? com.moakiee.ae2lt.config.AE2LTCommonConfig.overloadFactoryFePerTickNoSpeedCard() : 400_000;
        }
    }
}
