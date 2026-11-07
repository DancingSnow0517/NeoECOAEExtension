import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.lang.management.ManagementFactory;

/** Java 17 arithmetic microbenchmark; no Minecraft client or world is loaded. */
public class AmountBenchmark {
    static final BigInteger B17 = BigInteger.valueOf(17), B5 = BigInteger.valueOf(5), B131 = BigInteger.valueOf(131);
    static final PlannerAmount P131 = PlannerAmount.of(131);
    static volatile long blackhole;
    static final com.sun.management.ThreadMXBean MEMORY = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    record Sample(long nanos, long bytes, long checksum) {}
    static Sample run(BigInteger[] big, PlannerAmount[] tiered, int count, boolean nativeAmount) {
        long thread = Thread.currentThread().getId(), bytes = MEMORY.getThreadAllocatedBytes(thread);
        long start = System.nanoTime(), checksum = 0;
        if (nativeAmount) {
            for (int i = 0; i < count; i++)
                checksum += tiered[i & 4095].add(17L).multiply(5L).subtract(17L).divide(P131).hashCode();
        } else {
            for (int i = 0; i < count; i++)
                checksum += big[i & 4095].add(B17).multiply(B5).subtract(B17).divide(B131).hashCode();
        }
        long elapsed = System.nanoTime() - start, allocated = MEMORY.getThreadAllocatedBytes(thread) - bytes;
        blackhole = checksum;
        return new Sample(elapsed, allocated, checksum);
    }
    public static void main(String[] args) {
        MEMORY.setThreadAllocatedMemoryEnabled(true);
        System.out.println("bits,big_integer_ms,tiered_ms,big_integer_bytes_per_chain,tiered_bytes_per_chain");
        for (int bits : new int[]{60, 96, 124, 192}) {
            Random random = new Random(bits);
            BigInteger[] big = new BigInteger[4096];
            PlannerAmount[] tiered = new PlannerAmount[4096];
            for (int i = 0; i < big.length; i++) {
                big[i] = new BigInteger(bits, random).setBit(bits - 1);
                tiered[i] = PlannerAmount.of(big[i]);
            }
            for (int warm = 0; warm < 5; warm++) {
                run(big, tiered, 250_000, false);
                run(big, tiered, 250_000, true);
            }
            long[] bTime = new long[5], pTime = new long[5], bBytes = new long[5], pBytes = new long[5];
            for (int round = 0; round < 5; round++) {
                Sample b, p;
                if ((round & 1) == 0) {
                    b = run(big, tiered, 500_000, false); p = run(big, tiered, 500_000, true);
                } else {
                    p = run(big, tiered, 500_000, true); b = run(big, tiered, 500_000, false);
                }
                if (b.checksum != p.checksum) throw new AssertionError("Different benchmark results");
                bTime[round] = b.nanos; pTime[round] = p.nanos;
                bBytes[round] = b.bytes; pBytes[round] = p.bytes;
            }
            Arrays.sort(bTime); Arrays.sort(pTime); Arrays.sort(bBytes); Arrays.sort(pBytes);
            System.out.printf(java.util.Locale.ROOT, "%d,%.3f,%.3f,%.1f,%.1f%n", bits,
                    bTime[2] / 1e6, pTime[2] / 1e6, bBytes[2] / 500_000.0, pBytes[2] / 500_000.0);
        }
    }
}
