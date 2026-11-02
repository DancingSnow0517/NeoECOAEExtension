package cn.dancingsnow.neoecoae.mixins.compat.useless;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Retire the legacy submission hook when Useless already preserves ECO plans through its public API. */
public final class ECOUselessMixinPlugin implements IMixinConfigPlugin {
    private static final String PLAN = "Lappeng/api/networking/crafting/ICraftingPlan;";
    private static final String REWRITE = "(" + PLAN + "Ljava/util/function/Function;)" + PLAN;
    private static final String REWRITE_WITH_LEVEL =
        "(" + PLAN + "Ljava/util/function/Function;Lnet/minecraft/world/level/Level;)" + PLAN;

    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.endsWith(".UselessSmartDoublingPlansMixin")) return true;
        // Read bytecode only: loading a target here would run before Mixin transforms it.
        try (var input = getClass().getClassLoader()
                .getResourceAsStream(targetClassName.replace('.', '/') + ".class")) {
            if (input == null) return false;
            var target = new ClassNode();
            new ClassReader(input).accept(target, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (var method : target.methods) {
                if (!method.name.equals("rewriteForSubmission")
                        || !(method.desc.equals(REWRITE) || method.desc.equals(REWRITE_WITH_LEVEL))) continue;
                boolean nativeProtection = false;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.name.equals("shouldPreserveSubmissionPlan")
                            && call.desc.equals("(" + PLAN + ")Z")
                            && (call.owner.equals("com/sorrowmist/useless/compat/neoecoae/NeoEcoPlanningCompat")
                                || call.owner.equals("cn/dancingsnow/neoecoae/api/me/planning/ECOPlanningResultRegistry"))) {
                        nativeProtection = true;
                        break;
                    }
                }
                // Keep both compatibility hooks if even one released entry still bypasses the API.
                if (!nativeProtection) return true;
            }
            // Shipped in 2.4.5.11, and also works for forks that backport the fix.
            return false;
        } catch (IOException | IllegalArgumentException unreadable) {
            // Keep the existing legacy protection if a present target cannot be inspected.
            return true;
        }
    }

    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
