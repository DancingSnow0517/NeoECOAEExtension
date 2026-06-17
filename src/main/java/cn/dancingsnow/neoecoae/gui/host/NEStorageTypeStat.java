package cn.dancingsnow.neoecoae.gui.host;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record NEStorageTypeStat(
    ResourceLocation typeId,
    Component displayName,
    long usedTypes,
    long totalTypes,
    long usedBytes,
    long totalBytes
) {
}
