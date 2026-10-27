package cn.dancingsnow.neoecoae.compatprobe;

/** Keeps NeoECO-linked classes unloaded in the original-pack baseline. */
public interface MachineSuite {
    void tick();
    void stopping();
    boolean finished();
    int activeCount();
    void allowRestart();
}
