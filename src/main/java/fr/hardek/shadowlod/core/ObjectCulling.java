package fr.hardek.shadowlod.core;

import fr.hardek.shadowlod.compat.ShadowCulling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.extensions.IForgeBlockEntity;

public final class ObjectCulling {
    public static boolean shouldCullEntity(Entity e, double camX, double camY, double camZ) {
        if (!RenderManager.isRunning()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (e.level() != mc.level) return false;
        if (e == mc.getCameraEntity() || e == mc.player) return false;
        if (mc.player != null && (e.hasPassenger(mc.player) || mc.player.isPassengerOfSameVehicle(e))) return false;

        AABB b = e.getBoundingBoxForCulling();

        if (ShadowCulling.isShadowPass()) {
            if (!Settings.shadowOptimizations || !fr.hardek.shadowlod.compat.ShaderProfile.anyShadowOpts()) return false;
            ShadowCulling.entitiesTested++;
            boolean cull = ShadowCulling.classify(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, Settings.shadowEntityDistance) != ShadowCulling.KEEP;
            if (cull) ShadowCulling.entitiesCulled++;
            return cull;
        }

        boolean isPlayer = e instanceof Player;
        if (mc.shouldEntityAppearGlowing(e)) return false;
        double d2 = e.distanceToSqr(camX, camY, camZ);
        if (!isPlayer && Settings.maxEntityDistanceSq > 0 && d2 > Settings.maxEntityDistanceSq) {
            RenderManager.entitiesCulled++;
            return true;
        }
        boolean testable = Settings.cullEntities && RenderManager.isActive()
                && (!e.noCulling || Settings.ignoreNoCulling)
                && (!isPlayer || Settings.cullPlayers)
                && (Settings.safeDistanceSq <= 0 || d2 >= Settings.safeDistanceSq);
        if (testable) {
            RenderManager.entitiesTested++;
            if (RenderManager.isBoxOccluded(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)) {
                RenderManager.entitiesCulled++;
                return true;
            }
        }
        return !isPlayer && Settings.impostorDistanceSq > 0 && d2 > Settings.impostorDistanceSq
                && Impostors.tryDraw(e, camX, camY, camZ);
    }

    public static boolean shouldCullBlockEntity(BlockEntity be) {
        if (!RenderManager.isRunning()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (be.getLevel() != mc.level) return false;
        AABB b = be.getRenderBoundingBox();
        if (b == null || b == IForgeBlockEntity.INFINITE_EXTENT_AABB || b.getXsize() > 256 || b.getYsize() > 256 || b.getZsize() > 256) return false;

        if (ShadowCulling.isShadowPass()) {
            if (!Settings.shadowOptimizations || !fr.hardek.shadowlod.compat.ShaderProfile.anyShadowOpts()) return false;
            ShadowCulling.beTested++;
            boolean cull = ShadowCulling.classify(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, Settings.shadowBlockEntityDistance) != ShadowCulling.KEEP;
            if (cull) ShadowCulling.beCulled++;
            return cull;
        }

        double cx = RenderManager.camX(), cy = RenderManager.camY(), cz = RenderManager.camZ();
        double px = Math.max(b.minX, Math.min(cx, b.maxX)) - cx;
        double py = Math.max(b.minY, Math.min(cy, b.maxY)) - cy;
        double pz = Math.max(b.minZ, Math.min(cz, b.maxZ)) - cz;
        double d2 = px * px + py * py + pz * pz;
        if (Settings.maxBlockEntityDistanceSq > 0 && d2 > Settings.maxBlockEntityDistanceSq) {
            RenderManager.beCulled++;
            return true;
        }
        if (!Settings.cullBlockEntities || !RenderManager.isActive()) return false;
        if (d2 < Settings.safeDistanceSq) return false;
        RenderManager.beTested++;
        boolean occ = RenderManager.isBoxOccluded(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
        if (occ) RenderManager.beCulled++;
        return occ;
    }

    public static boolean shouldCullParticle(Particle p) {
        if (!Settings.cullParticles || !RenderManager.isRunning()) return false;
        AABB b = p.getBoundingBox();
        if (Settings.maxParticleDistanceSq > 0) {
            double dx = (b.minX + b.maxX) * 0.5 - RenderManager.camX();
            double dy = (b.minY + b.maxY) * 0.5 - RenderManager.camY();
            double dz = (b.minZ + b.maxZ) * 0.5 - RenderManager.camZ();
            if (dx * dx + dy * dy + dz * dz > Settings.maxParticleDistanceSq) {
                RenderManager.partCulled++;
                return true;
            }
        }
        if (!RenderManager.isActive() || !p.shouldCull()) return false;
        RenderManager.partTested++;
        boolean occ = RenderManager.isBoxOccluded(b.minX - 0.25, b.minY - 0.25, b.minZ - 0.25, b.maxX + 0.25, b.maxY + 0.25, b.maxZ + 0.25);
        if (occ) RenderManager.partCulled++;
        return occ;
    }

    private ObjectCulling() {}
}
