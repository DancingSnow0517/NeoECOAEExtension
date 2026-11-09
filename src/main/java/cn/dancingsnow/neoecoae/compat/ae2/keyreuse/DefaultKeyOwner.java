package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

/** A lazy, generation-cleared slot on an Item or Fluid; never a separate quantity/identity domain. */
public interface DefaultKeyOwner {
    KeyReuseSlot neoecoae$defaultKeySlot();
}
