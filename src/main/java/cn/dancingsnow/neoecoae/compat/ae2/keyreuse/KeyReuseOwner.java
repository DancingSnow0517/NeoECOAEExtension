package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

/** Internal Mixin bridge; never use identity equality as a storage contract. */
public interface KeyReuseOwner {
    KeyReuseSlot neoecoae$keyReuseSlot();
}
