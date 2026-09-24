package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.math.BigInteger;
import java.util.Objects;

/** Optional exact-amount insertion for storage that can represent quantities beyond AE2's long API. */
public interface ECOBigIntegerStorage {
    BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source);

    static BigInteger insert(MEStorage storage, AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
        if (amount.signum() < 0) throw new IllegalArgumentException("Amount must not be negative");
        if (amount.signum() == 0) return BigInteger.ZERO;

        BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
        if (amount.compareTo(max) <= 0) {
            return checkedResult(
                    amount, BigInteger.valueOf(storage.insert(what, amount.longValueExact(), mode, source)));
        }
        if (storage instanceof ECOBigIntegerStorage exact) {
            return checkedResult(amount, exact.insertBigInteger(what, amount, mode, source));
        }
        return checkedResult(amount, BigInteger.valueOf(storage.insert(what, Long.MAX_VALUE, mode, source)));
    }

    private static BigInteger checkedResult(BigInteger offered, BigInteger inserted) {
        if (inserted == null || inserted.signum() < 0 || inserted.compareTo(offered) > 0) {
            throw new IllegalStateException(
                    "Invalid BigInteger storage insertion result: " + inserted + " for " + offered);
        }
        return inserted;
    }
}
