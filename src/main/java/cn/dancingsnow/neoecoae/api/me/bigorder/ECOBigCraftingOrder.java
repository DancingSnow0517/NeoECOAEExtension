package cn.dancingsnow.neoecoae.api.me.bigorder;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** Server-owned parent ledger. A child is credited only after its final delivery has completed. */
public final class ECOBigCraftingOrder {
    public static final int MAX_AMOUNT_DIGITS = 1024;
    private final UUID id;
    private final BigInteger requested;
    private BigInteger completed;
    private final boolean forced;
    private ECOBigOrderState state;
    private long childTarget;
    private long retryTicks;
    private int retryDelay = 20;
    private String reason = "";

    public ECOBigCraftingOrder(UUID id, BigInteger requested, boolean forced) {
        this.id = Objects.requireNonNull(id);
        this.requested = checked(requested);
        if (requested.signum() == 0) throw new IllegalArgumentException("Empty order");
        this.completed = BigInteger.ZERO;
        this.forced = forced;
        this.state = ECOBigOrderState.PLANNING;
    }

    public static BigInteger checked(BigInteger amount) {
        if (amount == null || amount.signum() < 0 || amount.toString().length() > MAX_AMOUNT_DIGITS)
            throw new IllegalArgumentException("Invalid exact order amount");
        return amount;
    }

    public static BigInteger decode(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_AMOUNT_DIGITS
                || !value.chars().allMatch(c -> c >= '0' && c <= '9'))
            throw new IllegalArgumentException("Invalid exact order amount");
        return checked(new BigInteger(value));
    }

    public UUID id() { return id; }
    public BigInteger requested() { return requested; }
    public BigInteger completed() { return completed; }
    public BigInteger remaining() { return requested.subtract(completed); }
    public boolean forced() { return forced; }
    public ECOBigOrderState state() { return state; }
    public long childTarget() { return childTarget; }
    public long retryTicks() { return retryTicks; }
    public int retryDelay() { return retryDelay; }
    public String reason() { return reason; }
    public boolean terminal() {
        return state == ECOBigOrderState.COMPLETED || state == ECOBigOrderState.CANCELLED || state == ECOBigOrderState.FAILED;
    }
    public long candidate() { return remaining().min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(); }

    public void planning() {
        if (terminal() || state == ECOBigOrderState.RUNNING_CHILD) throw new IllegalStateException("Order cannot plan");
        state = ECOBigOrderState.PLANNING;
        reason = "";
    }
    public void startChild(long amount) {
        if (terminal() || state == ECOBigOrderState.RUNNING_CHILD || amount <= 0
                || BigInteger.valueOf(amount).compareTo(remaining()) > 0)
            throw new IllegalStateException("Invalid child");
        childTarget = amount;
        state = ECOBigOrderState.RUNNING_CHILD;
        resetRetry();
        reason = "";
    }
    public void resetRetry() { retryDelay = 20; retryTicks = 0; }
    public boolean completeChild() {
        if (state != ECOBigOrderState.RUNNING_CHILD) throw new IllegalStateException("No running child");
        completed = completed.add(BigInteger.valueOf(childTarget));
        childTarget = 0;
        state = remaining().signum() == 0 ? ECOBigOrderState.COMPLETED : ECOBigOrderState.PLANNING;
        return state == ECOBigOrderState.COMPLETED;
    }
    public void waitFor(ECOBigOrderState waiting, String detail) {
        if (terminal() || state == ECOBigOrderState.RUNNING_CHILD
                || waiting != ECOBigOrderState.WAITING_MATERIALS && waiting != ECOBigOrderState.WAITING_CAPACITY)
            throw new IllegalStateException("Invalid wait");
        state = waiting;
        reason = Objects.requireNonNull(detail);
        retryTicks = retryDelay;
        retryDelay = Math.min(200, retryDelay * 2);
    }
    public boolean tickRetry() {
        if (terminal() || state == ECOBigOrderState.RUNNING_CHILD) return false;
        if (retryTicks > 0) retryTicks--;
        return retryTicks == 0;
    }
    public void cancel() { if (!terminal()) { state = ECOBigOrderState.CANCELLED; reason = ""; } }
    public void fail(String detail) {
        Objects.requireNonNull(detail);
        if (!terminal()) { state = ECOBigOrderState.FAILED; reason = detail.substring(0, Math.min(256, detail.length())); }
    }
    public ECOBigOrderProgress progress(long childRemaining) {
        return new ECOBigOrderProgress(id, state, requested, completed, remaining(), childTarget,
                Math.max(0, Math.min(childTarget, childRemaining)), reason);
    }
    public static ECOBigCraftingOrder restore(UUID id, BigInteger requested, BigInteger completed,
            boolean forced, ECOBigOrderState state, long childTarget, long retryTicks, int retryDelay, String reason) {
        var order = new ECOBigCraftingOrder(id, requested, forced);
        checked(completed);
        if (completed.compareTo(requested) > 0 || childTarget < 0
                || BigInteger.valueOf(childTarget).compareTo(requested.subtract(completed)) > 0
                || state == ECOBigOrderState.RUNNING_CHILD && childTarget == 0
                || childTarget > 0 && state != ECOBigOrderState.RUNNING_CHILD && state != ECOBigOrderState.FAILED
                    && state != ECOBigOrderState.CANCELLED
                || retryTicks < 0 || retryTicks > 200 || retryDelay < 20 || retryDelay > 200
                || state == ECOBigOrderState.COMPLETED && !completed.equals(requested))
            throw new IllegalArgumentException("Invalid parent checkpoint");
        order.completed = completed;
        order.state = state;
        order.childTarget = childTarget;
        order.retryTicks = retryTicks;
        order.retryDelay = retryDelay;
        order.reason = Objects.requireNonNull(reason);
        if (reason.length() > 256) throw new IllegalArgumentException("Invalid waiting reason");
        return order;
    }
}
