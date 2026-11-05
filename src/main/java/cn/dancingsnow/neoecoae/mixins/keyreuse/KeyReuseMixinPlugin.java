package cn.dancingsnow.neoecoae.mixins.keyreuse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.slf4j.LoggerFactory;

/** Inspect bytecode without initializing optional mods. Unrecognized adapters retain upstream factories. */
public final class KeyReuseMixinPlugin implements IMixinConfigPlugin {
    private final Map<String, Boolean> supported = new HashMap<>();

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("ResourceKeyOwnerMixin")) return true;
        String family = mixinClassName.contains("Matter") ? "matter" : mixinClassName.contains("Chemical") || mixinClassName.contains("Mekanism") ? "chemical"
            : mixinClassName.contains("Energy") || mixinClassName.contains("Flux") ? "energy"
            : mixinClassName.contains("AEItem") ? "item" : "fluid";
        return supported.computeIfAbsent(family, this::inspect);
    }

    private boolean inspect(String family) {
        String type = switch (family) {
            case "matter" -> "dev.lapis256.apprep.api.ae2.stack.MatterKey";
            case "item" -> "appeng.api.stacks.AEItemKey";
            case "fluid" -> "appeng.api.stacks.AEFluidKey";
            case "chemical" -> "me.ramidzkh.mekae2.ae2.MekanismKey";
            default -> "com.glodblock.github.appflux.common.me.key.FluxKey";
        };
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(type);
            if (family.equals("matter")) {
                ClassNode companion = MixinService.getService().getBytecodeProvider().getClassNode(type + "$Companion");
                boolean valid = has(node, "<init>", "(Lcom/buuz135/replication/api/IMatterType;Lkotlin/jvm/internal/DefaultConstructorMarker;)V")
                    && has(node, "getStack", "()Lcom/buuz135/replication/api/matter_fluid/MatterStack;")
                    && has(node, "MAP_CODEC$lambda$0$_init___proxy", "(Lcom/buuz135/replication/api/IMatterType;)Ldev/lapis256/apprep/api/ae2/stack/MatterKey;")
                    && has(companion, "of", "(Lcom/buuz135/replication/api/IMatterType;)Ldev/lapis256/apprep/api/ae2/stack/MatterKey;")
                    && has(companion, "of", "(Lcom/buuz135/replication/api/matter_fluid/MatterStack;)Ldev/lapis256/apprep/api/ae2/stack/MatterKey;");
                if (!valid) LoggerFactory.getLogger("neoecoae/keyreuse").warn("AE key reuse adapter disabled for unrecognized {} API; retaining upstream factories", type);
                return valid;
            }
            String stack = switch (family) {
                case "item" -> "Lnet/minecraft/world/item/ItemStack;";
                case "fluid" -> "Lnet/neoforged/neoforge/fluids/FluidStack;";
                case "chemical" -> "Lmekanism/api/chemical/ChemicalStack;";
                default -> "Lcom/glodblock/github/appflux/common/me/key/type/EnergyType;";
            };
            boolean valid = has(node, "<init>", "(" + stack + ")V")
                && has(node, "of", "(" + stack + ")L" + type.replace('.', '/') + ";");
            if (family.equals("item") || family.equals("fluid")) {
                valid &= has(node, "lambda$static$4", "(Lnet/minecraft/core/Holder;Lnet/minecraft/core/component/DataComponentPatch;)L" + type.replace('.', '/') + ";");
            } else if (family.equals("chemical")) {
                valid &= has(node, "getStack", "()" + stack) && has(node, "hashCode", "()I");
                valid &= has(node, "lambda$static$1", "(Lnet/minecraft/core/Holder;)Lme/ramidzkh/mekae2/ae2/MekanismKey;");
            } else {
                valid &= has(node, "lambda$static$1", "(Lcom/mojang/serialization/codecs/RecordCodecBuilder$Instance;)Lcom/mojang/datafixers/kinds/App;");
            }
            if (!valid) LoggerFactory.getLogger("neoecoae/keyreuse").warn("AE key reuse adapter disabled for unrecognized {} API; retaining value-based upstream factories", type);
            return valid;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Exception | LinkageError failure) {
            LoggerFactory.getLogger("neoecoae/keyreuse").warn("Cannot inspect AE key reuse adapter {}; retaining upstream factories", type, failure);
            return false;
        }
    }

    private static boolean has(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
    }

    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
