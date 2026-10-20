package fr.hardek.shadowlod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(ShadowLOD.MODID)
public final class ShadowLOD {
    public static final String MODID = "shadowlod";
    public static final Logger LOG = LogUtils.getLogger();

    public ShadowLOD() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LodConfig.SPEC);
        ShadowLODClient.init(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
