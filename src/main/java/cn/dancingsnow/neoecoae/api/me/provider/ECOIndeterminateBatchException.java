package cn.dancingsnow.neoecoae.api.me.provider;

/** Provider may have accepted the batch. Retain its inputs and energy; never retry automatically. */
public final class ECOIndeterminateBatchException extends RuntimeException {
    public ECOIndeterminateBatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
