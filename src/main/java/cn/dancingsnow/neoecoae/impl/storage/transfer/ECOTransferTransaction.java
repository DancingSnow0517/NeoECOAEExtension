package cn.dancingsnow.neoecoae.impl.storage.transfer;

public final class ECOTransferTransaction {
    public enum State { RESERVED, COMMITTED, ROLLED_BACK }

    private final ECOTransferPlan plan;
    private State state = State.RESERVED;

    ECOTransferTransaction(ECOTransferPlan plan) {
        this.plan = plan;
    }

    public ECOTransferPlan plan() {
        return plan;
    }

    public State state() {
        return state;
    }

    void committed() {
        requireReserved();
        state = State.COMMITTED;
    }

    public void rollback() {
        requireReserved();
        state = State.ROLLED_BACK;
    }

    private void requireReserved() {
        if (state != State.RESERVED) {
            throw new IllegalStateException("Transaction is already " + state);
        }
    }
}
