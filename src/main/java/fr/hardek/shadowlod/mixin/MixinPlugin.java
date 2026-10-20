package fr.hardek.shadowlod.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class MixinPlugin implements IMixinConfigPlugin {
    private boolean embeddium;
    private boolean oculus;
    private boolean emf;

    @Override
    public void onLoad(String mixinPackage) {
        LoadingModList list = LoadingModList.get();
        embeddium = list != null && list.getModFileById("embeddium") != null;
        oculus = list != null && list.getModFileById("oculus") != null;
        emf = list != null && list.getModFileById("entity_model_features") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".embeddium.")) return embeddium;
        if (mixinClassName.contains(".oculus.")) return oculus;
        if (mixinClassName.contains(".emf.")) return emf;
        return true;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode c, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode c, String m, IMixinInfo i) {}
}
