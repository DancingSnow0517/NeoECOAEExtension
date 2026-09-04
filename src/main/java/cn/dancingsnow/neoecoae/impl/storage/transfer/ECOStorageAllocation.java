package cn.dancingsnow.neoecoae.impl.storage.transfer;

public record ECOStorageAllocation(int shardIndex, long amount) {
    public ECOStorageAllocation {
        if (shardIndex < 0 || amount <= 0L) {
            throw new IllegalArgumentException("Invalid storage allocation");
        }
    }
}
