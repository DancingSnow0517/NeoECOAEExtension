package cn.dancingsnow.neoecoae.integration.megacells.backend;

import java.util.Set;
import java.util.function.Predicate;

public enum MegaCellKind {
    ITEM(Set.of(MegaCellsBackend.MOD_ID)),
    FLUID(Set.of(MegaCellsBackend.MOD_ID)),
    ENERGY(Set.of(MegaCellsBackend.MOD_ID, MegaCellsBackend.APPFLUX_MOD_ID)),
    CHEMICAL(Set.of(MegaCellsBackend.MOD_ID, MegaCellsBackend.MEKANISM_MOD_ID, MegaCellsBackend.APPMEK_MOD_ID));

    private final Set<String> requiredMods;

    MegaCellKind(Set<String> requiredMods) {
        this.requiredMods = requiredMods;
    }

    public Set<String> requiredMods() {
        return requiredMods;
    }

    public boolean isEnabled(Predicate<String> isLoaded) {
        return requiredMods.stream().allMatch(isLoaded);
    }
}
