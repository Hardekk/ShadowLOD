<p align="center">
  <img src="images/header.png" alt="ShadowLOD : Lighter shadows, smoother worlds" width="100%">
</p>

<p align="center">
  <b>More FPS with shaders, without changing how your game looks.</b><br>
  <i>Crafted by Hardek</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-5b8731?style=for-the-badge" alt="Minecraft 1.20.1">
  <img src="https://img.shields.io/badge/Forge-47+-8b5a2b?style=for-the-badge" alt="Forge 47+">
  <img src="https://img.shields.io/badge/Embeddium%20·%20Oculus-compatible-6b6b6b?style=for-the-badge" alt="Embeddium and Oculus">
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/shadowlod"><b>CurseForge</b></a> ·
  <a href="../../releases"><b>Download</b></a>
</p>

<p align="center"><img src="images/about.png" alt="About"></p>

ShadowLOD makes Minecraft lighter to render, mostly with shaders on.
Shadows are only drawn where you can see them, the terrain shadow is reused instead of redrawn every frame,
and distant mobs turn into flat pictures of their own model.
Your world looks the same, your graphics card just does less work.

<p align="center"><img src="images/features.png" alt="Features"></p>

- **Smart shadows** : objects only go into the shadow map when their shadow can land somewhere you can see.
- **Shadow cache** : the terrain shadow is kept and reused until something changes, and updates are spread over several frames to avoid stutter.
- **Distant mob LOD** : beyond 32 blocks, mobs are drawn as flat pictures of their real model, up to 128 blocks.
- **Render distances** : adjustable limits for block entities, particles and translucent sorting.
- **Smooth frames** : chunk loading is spread over frames and slowed down right after a lag spike.
- **Auto tuning** : when the frame rate drops below your target, some distances go down a little on their own.
- **Thread priorities** : the render thread comes first, world generation and Distant Horizons work in the background.
- **Any shader pack** : packs that need a safer setup are detected automatically.

<p align="center"><img src="images/results.png" alt="Results"></p>

Same spot, same settings, uncapped frame rate, mod off then on.

| Scene | Off | On |
|---|:---:|:---:|
| 🏘️ **Village house** | 44 FPS | 147 FPS |
| 🏰 **Village with a tower** | 58 FPS | 157 FPS |
| 🌙 **Village at night** | 154 FPS | 186 FPS |
| 🧱 **Wall on a flat world** | 150 FPS | 260 FPS |
| 🐄 **Pen with 130 cows, far away** | 12 FPS | 236 FPS |

*Ryzen 7 9800X3D, Radeon RX 9070 XT, 1440p, Photon shaders, Distant Horizons. Your results depend on the scene and your hardware.*

<p align="center"><img src="images/compatibility.png" alt="Compatibility"></p>

| Mod | Status |
|---|:---:|
| Embeddium | ✅ |
| Oculus (all shader packs) | ✅ |
| Distant Horizons | ✅ |
| Entity Model Features / Fresh Animations | ✅ |
| Entity Culling | ✅ ¹ |
| ImmediatelyFast, ModernFix, FerriteCore | ✅ |
| Faster Iris Shadow Mapper | ✅ ¹ |
| Vanillin / Flywheel | 🟧 ² |
| OptiFine | ❌ |

✅ Works together.

🟧 Works, with a limit.

❌ Not supported.

¹ Recommended alongside ShadowLOD, they cover different parts of rendering.

² Chests, bells and minecarts drawn by Vanillin are not handled by ShadowLOD.

**Requirements:** Minecraft 1.20.1, Forge 47 or newer. Embeddium and Oculus are optional.

**Installation:** drop the `.jar` into your `mods` folder.

<p align="center"><img src="images/settings.png" alt="Settings"></p>

- **Numpad `/`** : turn the mod on or off.
- **Numpad `*`** : turn the shadow optimizations on or off.
- **F3** : live stats for every feature.

All settings are in the mod menu, or in `config/shadowlod-client.toml`.

> **Occlusion** hides terrain behind hills and buildings. It is experimental and off by default, as it can cause slight pop-in when the camera moves fast.

<p align="center"><img src="images/terms.png" alt="Terms"></p>

- ✅ Play with it, make videos and screenshots with it.
- ✅ Use it in modpacks, simply link back to the official page please!
- ❌ Don't reupload the mod elsewhere.
- ❌ Don't modify it or publish your own version without my permission.

See [LICENSE.md](LICENSE.md) for details.

<p align="center"><sub>© 2026 Hardek. This is NOT an official Minecraft product. Not approved by or associated with Mojang or Microsoft.</sub></p>
