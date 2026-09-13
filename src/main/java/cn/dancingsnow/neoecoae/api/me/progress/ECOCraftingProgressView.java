package cn.dancingsnow.neoecoae.api.me.progress;

import appeng.api.stacks.AEKeyType;

/** Read-only progress contract for displays and integrations. */
public interface ECOCraftingProgressView {

    float progress();

    long elapsedTimeNanos();

    long startedWork(AEKeyType keyType);

    long completedWork(AEKeyType keyType);
}
