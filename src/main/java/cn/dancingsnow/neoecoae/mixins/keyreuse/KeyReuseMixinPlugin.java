package cn.dancingsnow.neoecoae.mixins.keyreuse;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Protects ECO-owned mutable views and reuses the immutable, enum-only Applied Flux factory. */
public final class KeyReuseMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            if (mixinClassName.contains("FluxKey")) return supportsFluxFactory(node);
            String result = mixinClassName.contains("Matter")
                ? "Lcom/buuz135/replication/api/matter_fluid/MatterStack;"
                : "Lmekanism/api/chemical/ChemicalStack;";
            boolean field = node.fields.stream().anyMatch(value -> value.name.equals("stack")
                && value.desc.equals(result) && (value.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0);
            boolean getter = node.methods.stream().anyMatch(method -> method.name.equals("getStack")
                && method.desc.equals("()" + result) && (method.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0);
            boolean hash = mixinClassName.contains("Matter") || node.methods.stream().anyMatch(method ->
                method.name.equals("hashCode") && method.desc.equals("()I"));
            return field && getter && hash;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Exception | LinkageError failure) {
            org.slf4j.LoggerFactory.getLogger("neoecoae/keyreuse").warn("Cannot protect ECO key view for {}; retaining uncached addon keys", targetClassName, failure);
            return false;
        }
    }
    static boolean supportsFluxFactory(ClassNode node) {
        String type = "Lcom/glodblock/github/appflux/common/me/key/type/EnergyType;";
        String key = "com/glodblock/github/appflux/common/me/key/FluxKey";
        if (!node.name.equals(key)) return false;
        long fields = node.fields.stream().filter(field -> (field.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0).count();
        boolean immutable = fields == 1 && node.fields.stream().anyMatch(field -> field.name.equals("type")
                && field.desc.equals(type) && (field.access & org.objectweb.asm.Opcodes.ACC_FINAL) != 0);
        boolean constructor = node.methods.stream().anyMatch(method -> method.name.equals("<init>")
                && method.desc.equals("(" + type + ")V"));
        boolean factory = node.methods.stream().anyMatch(method -> method.name.equals("of")
                && method.desc.equals("(" + type + ")L" + key + ";")
                && (method.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0
                && java.util.Arrays.stream(method.instructions.toArray()).anyMatch(instruction ->
                    instruction instanceof org.objectweb.asm.tree.TypeInsnNode allocation
                        && allocation.getOpcode() == org.objectweb.asm.Opcodes.NEW && allocation.desc.equals(key)));
        return immutable && constructor && factory;
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
