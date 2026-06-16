package cn.dancingsnow.neoecoae.gui.host;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.LongSupplier;

public record NEStorageTypeStat(
    ResourceLocation typeId,
    Component displayName,
    LongSupplier usedTypes,
    LongSupplier totalTypes,
    LongSupplier usedBytes,
    LongSupplier totalBytes
) {
}
