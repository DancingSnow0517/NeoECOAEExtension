package cn.dancingsnow.neoecoae.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class StorageHostTextTest {

    @Test
    void expandsInfiniteBytesWithEightSignificantDigits() {
        assertEquals("794.00000M", StorageHostText.expandedStorageBytes(BigInteger.valueOf(794L).shiftLeft(20)));
    }

    @Test
    void expandsBeyondLongRangeUnits() {
        assertEquals("2.0000000Z", StorageHostText.expandedStorageBytes(BigInteger.valueOf(2L).shiftLeft(70)));
        assertEquals("3.0000000Y", StorageHostText.expandedStorageBytes(BigInteger.valueOf(3L).shiftLeft(80)));
    }
}
