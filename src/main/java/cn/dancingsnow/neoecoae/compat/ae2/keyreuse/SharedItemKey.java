package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

/** Internal bridge for fully initializing AE2 15's lazy fields before publication. */
public interface SharedItemKey {
    boolean neoecoae$isShared();
    void neoecoae$initializeShared();
}
