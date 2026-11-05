package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KeyReuseSlotTest {
    @AfterEach void stop() { KeyReuse.configure(false); }

    @Test void reloadRejectsLatePublicationAndReleasesThePreviousKey() {
        KeyReuse.configure(true);
        KeyReuseSlot slot = new KeyReuseSlot();
        Object oldEpoch = KeyReuse.epoch();
        Object oldKey = new Object();
        synchronized (slot) { slot.set(oldEpoch, oldKey); }
        assertSame(oldKey, slot.get(oldEpoch));
        KeyReuse.invalidate();
        assertNull(slot.get(oldEpoch));
        synchronized (slot) { slot.set(oldEpoch, oldKey); }
        assertNull(slot.get(oldEpoch), "a creator crossing a reload cannot resurrect the old cache");
        Object current = KeyReuse.epoch();
        Object replacement = new Object();
        synchronized (slot) { slot.set(current, replacement); }
        assertSame(replacement, slot.get(current));
        assertNull(slot.get(oldEpoch));
    }

    @Test void disablingAndReenablingCannotReuseTheOldGeneration() {
        KeyReuse.configure(true);
        KeyReuseSlot slot = new KeyReuseSlot();
        Object epoch = KeyReuse.epoch();
        synchronized (slot) { slot.set(epoch, new Object()); }
        KeyReuse.configure(false);
        assertNull(KeyReuse.epoch());
        assertNull(slot.get(epoch));
        KeyReuse.configure(true);
        assertNotSame(epoch, KeyReuse.epoch());
        assertNull(slot.get(KeyReuse.epoch()));
    }
}
