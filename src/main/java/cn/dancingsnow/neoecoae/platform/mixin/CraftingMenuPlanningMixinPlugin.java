package cn.dancingsnow.neoecoae.platform.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Routes calculation calls after all addons have merged their menu entry methods. */
public final class CraftingMenuPlanningMixinPlugin implements IMixinConfigPlugin {
    private static final String MENU = "appeng.menu.me.crafting.CraftConfirmMenu";
    private static final String MENU_MIXIN = "cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin";
    private static final String BRIDGE = "cn/dancingsnow/neoecoae/platform/mixin/CraftConfirmPlanningBridge";
    private static final String CALCULATION = "(Lnet/minecraft/world/level/Level;"
        + "Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;"
        + "JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;";
    private static final String ROUTE = "(Lappeng/api/networking/crafting/ICraftingService;"
        + CALCULATION.substring(1, CALCULATION.indexOf(')')) + "L" + BRIDGE + ";)Ljava/util/concurrent/Future;";

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> ownTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!MENU.equals(target) || !MENU_MIXIN.equals(mixin)) return;
        // Mixin 0.8.5's wildcard selectors skip methods merged by another mixin. Inspect the final
        // standard API calls instead, without depending on addon class names or entry method names.
        for (var method : node.methods) {
            if ((method.access & Opcodes.ACC_STATIC) != 0
                    || method.name.equals("neoecoae$routeEcoPlanningRequest")) continue;
            boolean changed = false;
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !call.name.equals("beginCraftingCalculation") || !call.desc.equals(CALCULATION)
                        || !(call.owner.equals("appeng/api/networking/crafting/ICraftingService")
                            || call.owner.equals("appeng/me/service/CraftingService"))) continue;
                // Keep all original arguments on the stack and append the owning menu. The bridge
                // consumes that extra argument, so existing locals and stack-map frames stay valid.
                method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    BRIDGE, "routePlanningRequest", ROUTE, true));
                changed = true;
            }
            if (changed) method.maxStack++;
        }
    }
}
