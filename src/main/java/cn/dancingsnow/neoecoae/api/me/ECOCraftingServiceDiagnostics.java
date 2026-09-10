package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;

/** Server-side diagnostics for the CPU candidates behind the crafting confirmation screen. */
public interface ECOCraftingServiceDiagnostics {
    String neoecoae$describeCpuSelection(ICraftingPlan plan, IActionSource source);
}
