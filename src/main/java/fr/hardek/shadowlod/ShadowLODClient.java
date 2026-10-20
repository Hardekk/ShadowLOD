package fr.hardek.shadowlod;

import com.mojang.blaze3d.platform.InputConstants;
import fr.hardek.shadowlod.compat.EmbeddiumCompat;
import fr.hardek.shadowlod.compat.ShadowCulling;
import fr.hardek.shadowlod.compat.IrisCompat;
import fr.hardek.shadowlod.core.DepthData;
import fr.hardek.shadowlod.core.RenderManager;
import fr.hardek.shadowlod.core.Settings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

public final class ShadowLODClient {
    private static final KeyMapping TOGGLE = new KeyMapping("key.shadowlod.toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_DIVIDE, "key.categories.shadowlod");
    private static final KeyMapping TOGGLE_SHADOW = new KeyMapping("key.shadowlod.toggle_shadow",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_MULTIPLY, "key.categories.shadowlod");

    static void init(IEventBus modBus) {
        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, e -> { e.register(TOGGLE); e.register(TOGGLE_SHADOW); });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, ShadowLODClient::onTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, CustomizeGuiOverlayEvent.DebugText.class, ShadowLODClient::onDebugText);
    }

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        while (TOGGLE_SHADOW.consumeClick()) {
            Settings.shadowRuntimeToggle = !Settings.shadowRuntimeToggle;
            fr.hardek.shadowlod.compat.ShadowCache.invalidate();
            RenderManager.onToggled();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("ShadowLOD shadows: " + (Settings.shadowRuntimeToggle ? "ON" : "OFF")), true);
            }
        }
        while (TOGGLE.consumeClick()) {
            Settings.runtimeToggle = !Settings.runtimeToggle;
            RenderManager.onToggled();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("ShadowLOD: " + (Settings.runtimeToggle ? "ON" : "OFF")), true);
            }
        }
    }

    private static void onDebugText(CustomizeGuiOverlayEvent.DebugText e) {
        if (!Settings.debugInfo || !Minecraft.getInstance().options.renderDebug) return;
        var left = e.getLeft();
        left.add("");
        if (RenderManager.PYRAMID.isFailed()) {
            left.add("[ShadowLOD] GL error, disabled (see latest.log)");
            return;
        }
        String state = !Settings.enabled || !Settings.runtimeToggle ? "OFF"
                : (Settings.disableWithShaders && IrisCompat.isShaderPackInUse()) ? "OFF (shaders)" : "ON";
        left.add("[ShadowLOD] " + state + "  latency: " + (RenderManager.lastDataAge < 0 ? "-" : RenderManager.lastDataAge + " f")
                + (EmbeddiumCompat.isLoaded() ? "" : "  (no Embeddium: terrain not culled)"));
        DepthData hd = RenderManager.PYRAMID.latest();
        if (!Settings.occlusion) {
            left.add("[ShadowLOD] occlusion: off");
        } else if (hd != null) {
            float cd = hd.centerDistance();
            left.add(String.format("[ShadowLOD] depth: center %s, sky %.0f%%  (main %d, oculus %d, used %d)",
                    Float.isInfinite(cd) ? "far" : String.format("%.1f blocks", cd), hd.skyFraction() * 100f,
                    RenderManager.lastMainDepth, IrisCompat.lastPipelineDepth, RenderManager.lastUsedDepth));
        }
        left.add("[ShadowLOD] terrain " + RenderManager.lastTerrainCulled + "/" + RenderManager.lastTerrainTested
                + "  entities " + RenderManager.lastEntitiesCulled + "/" + RenderManager.lastEntitiesTested
                + "  BE " + RenderManager.lastBeCulled + "/" + RenderManager.lastBeTested
                + "  part " + RenderManager.lastPartCulled + "/" + RenderManager.lastPartTested);
        left.add(String.format("[ShadowLOD] frame %.1f ms (avg %.1f)  spikes %d%s  shadow updates %d/%d per s",
                fr.hardek.shadowlod.core.FrameGovernor.lastFrameMs, fr.hardek.shadowlod.core.FrameGovernor.averageMs(),
                fr.hardek.shadowlod.core.FrameGovernor.spikes, fr.hardek.shadowlod.core.FrameGovernor.inSpike() ? " [slowed down]" : "",
                fr.hardek.shadowlod.core.ShadowThrottle.lastRendered,
                fr.hardek.shadowlod.core.ShadowThrottle.lastRendered + fr.hardek.shadowlod.core.ShadowThrottle.lastSkipped));
        left.add("[ShadowLOD] distant mobs: " + fr.hardek.shadowlod.core.Impostors.lastDrawn + " pictures, "
                + fr.hardek.shadowlod.core.Impostors.lastCaptured + " updated");
        left.add(String.format("[ShadowLOD] auto tune: %s  scale %.2f", Settings.autoTune ? Settings.targetFps + " fps" : "off",
                fr.hardek.shadowlod.core.AutoTune.scale));
        if (fr.hardek.shadowlod.compat.ShadowCulling.isShadowPassAvailable()) {
            left.add("[ShadowLOD] shadow optimizations: " + (Settings.shadowRuntimeToggle ? "ON" : "OFF"));
            double eff = fr.hardek.shadowlod.compat.ShadowCulling.effectiveTerrainDistance();
            left.add("[ShadowLOD] shadow terrain distance: " + (eff <= 0 ? "unlimited" : (int) eff + " blocks")
                    + (Settings.shadowTerrainDistance <= 0 ? " (auto, pack " + (int) fr.hardek.shadowlod.compat.ShadowCulling.packShadowDistance() + ")" : " (manual)"));
            left.add("[ShadowLOD] shader: " + fr.hardek.shadowlod.compat.ShaderProfile.describe());
            left.add("[ShadowLOD] shadow cache " + fr.hardek.shadowlod.compat.ShadowCache.status + " : "
                    + fr.hardek.shadowlod.compat.ShadowCache.lastHits + " reused / " + fr.hardek.shadowlod.compat.ShadowCache.lastMisses
                    + " redrawn / " + fr.hardek.shadowlod.compat.ShadowCache.lastBuilds + " split rebuild per s");
        }
        if (Settings.shadowOptimizations && fr.hardek.shadowlod.compat.ShadowCulling.isShadowPassAvailable()) {
            left.add("[ShadowLOD] shadow: terrain " + ShadowCulling.lastTerrainCulled + "/" + ShadowCulling.lastTerrainTested
                    + "  entities " + ShadowCulling.lastEntitiesCulled + "/" + ShadowCulling.lastEntitiesTested
                    + "  BE " + ShadowCulling.lastBeCulled + "/" + ShadowCulling.lastBeTested);
        }
    }

    private ShadowLODClient() {}
}
