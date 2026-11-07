package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Probe resources without loading optional interfaces or their game-class dependencies. */
public final class ECOThunderboltMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // NeoECO exposes its own FastPath facade. External CPUs opt into that API explicitly;
        // never make an ECO host implement a foreign provider contract at runtime.
        if (mixinClassName.endsWith(".ECOThunderboltProviderMixin")) return false;
        if (mixinClassName.endsWith(".ThunderboltPlanningEngineMixin")) {
            String request = "Lcom/moakiee/thunderbolt/api/crafting/PlanningRequest;";
            String target = targetClassName.replace('.', '/') + ".class";
            String capture = "(Lappeng/api/networking/IGrid;" + request + ")Ljava/lang/Object;";
            return hasMethod("com/moakiee/thunderbolt/api/crafting/CraftingPlanningEngine.class", "capture", capture)
                && hasMethod(target, "createSession", "(" + request
                    + "Ljava/lang/Object;Lcom/moakiee/thunderbolt/api/crafting/PlanningAttemptContext;)"
                    + "Lcom/moakiee/thunderbolt/api/crafting/PlanningEngineSession;")
                // Do not overwrite a future engine's own capture state.
                && !hasMethod(target, "capture", capture);
        }
        if (mixinClassName.endsWith(".ECOProcessingPatternFuzzyMixin")) {
            return hasMethod("com/moakiee/thunderbolt/core/crafting/pattern/FuzzyPatternInputs.class",
                "acceptsSameIdVariants", "(I)Z");
        }
        if (mixinClassName.endsWith(".Ae2LtIOPortBlockEntityMixin")) {
            return present("com/moakiee/ae2lt/blockentity/OverloadedIOPortBlockEntity.class");
        }
        if (mixinClassName.endsWith(".Ae2LtIOPortMenuMixin")) {
            return present("com/moakiee/ae2lt/menu/OverloadedIOPortMenu.class");
        }
        if (mixinClassName.endsWith(".Ae2LtTimeWheelCraftingCpuLogicMixin")) {
            return present("com/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic.class");
        }
        if (mixinClassName.endsWith(".ECOThunderboltWorkstationProviderMixin")) {
            return present("com/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider.class")
                && present("com/moakiee/thunderbolt/api/crafting/batch/BatchJobView.class");
        }
        return false;
    }
    private boolean present(String resource) {
        return getClass().getClassLoader().getResource(resource) != null;
    }
    private boolean hasMethod(String resource, String name, String descriptor) {
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) return false;
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
        } catch (java.io.IOException | RuntimeException unavailable) {
            return false;
        }
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
