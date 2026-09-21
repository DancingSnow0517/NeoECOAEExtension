package cn.dancingsnow.neoecoae.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SyncWindowTest {
    @Test
    void ordinaryChurnAndHeadersShareOneFiveTickWindow() {
        var window = new SyncWindow(5);
        int allowed = 0;
        for (int tick = 0; tick < 100; tick++) {
            if (window.allow(tick, false)) allowed++;
            assertFalse(window.allow(tick, false));
        }
        assertEquals(20, allowed);
    }

    @Test
    void pauseFinishAndCpuSwitchAreImmediate() {
        var window = new SyncWindow(5);
        assertTrue(window.allow(0, false));
        assertFalse(window.allow(1, false));
        assertTrue(window.allow(1, true));
        assertFalse(window.allow(2, false));
        assertTrue(window.allow(2, true));
        window.reset();
        assertTrue(window.allow(2, false));
    }
}
