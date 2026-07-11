package cn.dancingsnow.neoecoae.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class StorageHostTextTest {

    @Test
    void usesAe2SlotFormattingForStorageRows() {
        assertEquals("7680", StorageHostText.typeProgress(1L, 7_680L).maxText());
        assertEquals("1.8M", StorageHostText.byteProgress(1_840_640L, 6_442_450_944L).usedText());
        assertEquals("6.4G", StorageHostText.byteProgress(1_840_640L, 6_442_450_944L).maxText());
        assertEquals("1.8M", StorageHostText.ae2Amount(BigInteger.valueOf(1_840_640L)));
    }

    @Test
    void expandsInfiniteBytesWithoutAbbreviation() {
        assertEquals("832,569,344", StorageHostText.expandedStorageBytes(BigInteger.valueOf(794L).shiftLeft(20)));
    }

    @Test
    void compactsTooltipBytesToAtMostFourWholeDigits() {
        assertEquals("9999K", StorageHostText.compactStorageBytes(BigInteger.valueOf(9_999L).shiftLeft(10)));
        assertEquals("10M", StorageHostText.compactStorageBytes(BigInteger.valueOf(10_000L).shiftLeft(10)));
        assertEquals("2048E", StorageHostText.compactStorageBytes(BigInteger.valueOf(2L).shiftLeft(70)));
    }

    @Test
    void formatsHugeStacksBeyondLongRangeWithoutLosingPrecision() {
        BigInteger amount = BigInteger.valueOf(2L).multiply(BigInteger.valueOf(1024L).pow(7));
        assertEquals("2.00Z", StorageHostText.hugeStackAmount(amount));
        assertEquals("2,361,183,241,434,822,606,848", StorageHostText.expandedStorageBytes(amount));
    }

    @Test
    void fitsHugeAmountsUsingTheMostDetailedCandidateThatFits() {
        BigInteger amount = BigInteger.valueOf(2L).multiply(BigInteger.valueOf(1024L).pow(7));
        assertEquals(
            "2,361,183,241,434,822,606,848",
            StorageHostText.fitHugeAmount(amount, 31, String::length)
        );
        assertEquals("2.00Z", StorageHostText.fitHugeAmount(amount, 5, String::length));
        assertEquals("2Z", StorageHostText.fitHugeAmount(amount, 2, String::length));
    }
}
