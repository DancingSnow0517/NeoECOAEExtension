package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

/** Read-only predicate from the checked AE2 bridge; never exposes its mutable FluidStack. */
public interface FluidKeyComponents {
    boolean neoecoae$hasComponentPatch();
}
