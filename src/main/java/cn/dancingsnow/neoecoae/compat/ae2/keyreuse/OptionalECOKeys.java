package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import appeng.api.stacks.AEKey;

/** Optional implementations link only after an actual key of that exact loaded family reaches ECO. */
final class OptionalECOKeys {
    private OptionalECOKeys() {}
    static AEKey canonicalize(AEKey key) {
        return switch (key.getClass().getName()) {
            case "me.ramidzkh.mekae2.ae2.MekanismKey" -> ECOChemicalKeys.canonicalize(key);
            case "com.glodblock.github.appflux.common.me.key.FluxKey" -> ECOFluxKeys.canonicalize(key);
            default -> key;
        };
    }
}
