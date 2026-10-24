package cn.dancingsnow.neoecoae.verification.bootstrap;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** The original unsafe rewrite, retained only to reproduce the released startup crash. */
public class UnsafePlanningEntryPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> ownTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!target.equals("appeng.menu.me.crafting.CraftConfirmMenu")
                || !mixin.equals("cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin")) return;
        String bridge = "cn/dancingsnow/neoecoae/platform/mixin/CraftConfirmPlanningBridge";
        String calculation = "(Lnet/minecraft/world/level/Level;"
            + "Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;"
            + "JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;";
        String route = "(Lappeng/api/networking/crafting/ICraftingService;"
            + calculation.substring(1, calculation.indexOf(')')) + "L" + bridge + ";)Ljava/util/concurrent/Future;";
        for (var method : node.methods) {
            if ((method.access & Opcodes.ACC_STATIC) != 0
                    || method.name.equals("neoecoae$routeEcoPlanningRequest")) continue;
            boolean changed = false;
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || !call.name.equals("beginCraftingCalculation")
                        || !call.desc.equals(calculation)) continue;
                method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    bridge, "routePlanningRequest", route, true));
                changed = true;
            }
            if (changed) method.maxStack++;
        }
    }
}
