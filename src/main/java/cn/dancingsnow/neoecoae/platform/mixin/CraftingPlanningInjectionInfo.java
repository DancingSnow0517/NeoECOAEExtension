package cn.dancingsnow.neoecoae.platform.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.injection.struct.RedirectInjectionInfo;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;

/** Keeps Mixin's shared injection-node tracking when another addon wraps the same calculation call. */
@InjectionInfo.AnnotationType(RouteCraftingPlanning.class)
@InjectionInfo.HandlerPrefix("redirect")
public final class CraftingPlanningInjectionInfo extends RedirectInjectionInfo {
    private static final String CALCULATION = "(Lnet/minecraft/world/level/Level;"
        + "Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;"
        + "JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;";

    public CraftingPlanningInjectionInfo(MixinTargetContext mixin, MethodNode method, AnnotationNode annotation) {
        super(mixin, method, annotation);
    }

    @Override
    protected void parseSelectors() {
        // Mixin 0.8.5 excludes merged addon methods from wildcard selectors. Resolve explicit
        // descriptors after the merge phase, then let its regular RedirectInjector replace nodes.
        // MixinExtras can therefore follow the replacement and bind the owning menu to Operation.
        this.selectors.clear();
        for (var candidate : this.classNode.methods) {
            if ((candidate.access & Opcodes.ACC_STATIC) != 0 || candidate.name.startsWith("<")
                    || candidate.name.equals("neoecoae$routeEcoPlanningRequest")) continue;
            for (var instruction : candidate.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("beginCraftingCalculation")
                        && call.desc.equals(CALCULATION)
                        && (call.owner.equals("appeng/api/networking/crafting/ICraftingService")
                            || call.owner.equals("appeng/me/service/CraftingService"))) {
                    this.selectors.add(new MemberInfo(candidate.name, null, candidate.desc));
                    break;
                }
            }
        }
    }
}
