package cn.dancingsnow.neoecoae.api.me.fastpath;

public final class ECOFastPathLimits {
    private ECOFastPathLimits() {}

    public static int limitBatchSize(int requested, int workerRemaining, int controllerRemaining) {
        if (requested <= 0 || workerRemaining <= 0 || controllerRemaining <= 0) {
            return 0;
        }
        return Math.min(requested, Math.min(workerRemaining, controllerRemaining));
    }

    public static boolean canAcceptBatch(int batchSize, int workerRemaining, int controllerRemaining) {
        return batchSize > 0 && limitBatchSize(batchSize, workerRemaining, controllerRemaining) == batchSize;
    }
}
