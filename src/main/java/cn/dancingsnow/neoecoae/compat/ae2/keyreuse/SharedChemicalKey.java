package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

/** Marks a completely initialized chemical key before publication to other threads. */
public interface SharedChemicalKey {
    void neoecoae$markShared();
}
