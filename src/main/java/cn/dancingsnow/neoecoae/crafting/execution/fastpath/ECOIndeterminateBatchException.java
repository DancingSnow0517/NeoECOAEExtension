package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

/**
 * Signals that a batch provider cannot determine whether the batch was accepted.
 *
 * <p>This is deliberately distinct from an ordinary rejected batch: callers must not retry the
 * same provider in the same dispatch pass, because the provider may already own the inputs.</p>
 */
public class ECOIndeterminateBatchException extends RuntimeException {
    public ECOIndeterminateBatchException(String message, Throwable cause) {
        super(message, cause);
    }

    public ECOIndeterminateBatchException(Throwable cause) {
        this("Batch dispatch result is indeterminate", cause);
    }
}
