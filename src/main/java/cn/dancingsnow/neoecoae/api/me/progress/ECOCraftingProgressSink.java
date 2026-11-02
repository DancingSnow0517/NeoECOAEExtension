package cn.dancingsnow.neoecoae.api.me.progress;

import appeng.api.stacks.AEKeyType;

/**
 * 接收 ECO 合成任务进度记账的公共接口。
 * <p>
 * 外部合成设备在确认某类资源的任务产物已经完成后，可以通过此接口更新
 * ECO 合成任务的进度，而无需访问或反射内部进度追踪器。
 */
public interface ECOCraftingProgressSink {

    /**
     * 记录已经完成的合成工作量。
     *
     * @param amount 已完成的工作量，必须大于等于 {@code 0}
     * @param keyType 工作量对应的 AE Key 类型
     */
    void recordCompletedCraftingWork(long amount, AEKeyType keyType);
}
