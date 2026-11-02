package cn.dancingsnow.neoecoae.gui.common;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HostTextTest {
    @Test
    void confirmationBytesContinuePastQ() {
        assertEquals("1Q", HostText.ae2Amount(BigInteger.TEN.pow(30)));
        assertEquals("999Q", HostText.ae2Amount(BigInteger.TEN.pow(33).subtract(BigInteger.ONE)));
        assertEquals("1KQ", HostText.ae2Amount(BigInteger.TEN.pow(33)));
        assertEquals("1MQ", HostText.ae2Amount(BigInteger.TEN.pow(36)));
        assertEquals("1QQ", HostText.ae2Amount(BigInteger.TEN.pow(60)));
        assertEquals("1×10^63", HostText.ae2Amount(BigInteger.TEN.pow(63)));
        assertEquals("1×10^123", HostText.ae2Amount(BigInteger.TEN.pow(123)));
        assertEquals("476PQ", HostText.ae2Amount(new BigInteger("476900530596182289").multiply(BigInteger.TEN.pow(30))));
        assertEquals("1×10^63", HostText.expandedStorageBytes(BigInteger.TEN.pow(63)));
    }

    @Test
    void tooltipBytesKeepTheirExactIntegerValue() {
        assertEquals("41,943,040", HostText.fullBytes(40L * 1024 * 1024));
        assertEquals("52,428,800", HostText.fullBytes(BigInteger.valueOf(50L * 1024 * 1024)));
        assertEquals("1,000,000,000,000,000,000,000", HostText.fullBytes(BigInteger.TEN.pow(21)));
    }
}
