# Metallum Extra: details

What each part does, the config keys, the profiler's output, and notes for working on the mod.
For the short version see the [README](../README.md).

## Features

### Shaders (`shaders.enabled`, off by default)
A shader pipeline of Metallum Extra's own, written for Metal. It is one fixed look, built in; it does **not** load
OptiFine or Iris shader packs. Needs Sodium. Switch it on under **Video Settings → Metallum Extra → Shaders**.

What it draws:
- **Sun and moon light with shadows.** Terrain, mobs, block entities, dropped items and the player (in first
  person too) all cast and receive shadows. Light is warm and low at sunrise and sunset, white at noon, dim and
  blue under the moon. The sun, moon and shadows follow the game's own sun, which passes straight overhead at
  noon, so at midday shadows sit under their objects. `shaders.sunPathRotation` tilts the sun's path towards the
  south (shader packs commonly use 35–40°) if you prefer long shadows all day; the drawn sun and moon follow it.
- **Sky and fog.** The sky is one smooth gradient with a glow around the sun and a band of color at dusk and dawn.
  Distant terrain fades into that same sky, so there is no visible fog wall. The game's sun, moon and stars are
  kept, and so are its clouds, lit by the sun, moon and sky: white tops and grey undersides by day, orange where
  the low sun catches them, dim under the moon.
- **Water.** Clear at the shore and deep-colored further out, with ripples, a sun glint, and reflections of the
  sky, the land and anything standing at the water's edge, from any angle. Under water, sunlight breaks into
  moving caustics on the bottom, and the surface overhead shows the sky through a window of light that the
  ripples bend; past its edge the surface mirrors the bottom, as real water does.
- **Light from blocks.** Torches, lava and glowstone give warm light, and the blocks themselves glow.
- **Glow:** brightness spills from the sun, lava, fire and sunlit snow onto what is next to them.
- **Leaves and plants let light through:** seen against the sun they glow with it.
- **Smooth edges:** the stair steps along the edges of blocks, leaves and far terrain are blended away; the
  texels of a block's own texture are left crisp.
- **Moving plants.** Leaves, grass, flowers and crops sway, more in the rain.

- **Sun rays** through trees, cave openings and morning haze (High and above).
- **Ambient occlusion:** corners, crevices and the ground under things are a little darker.
- **Colored light:** torches glow orange, soul fire blue, sea lanterns teal, amethyst purple, redstone red, end
  rods white, and each colors what it lights.
- **Shine on smooth things:** polished stone, glass, ice and metal catch the sun; everything is wet and shiny in rain.
- **Night sky:** stars that twinkle, the band of the Milky Way, and a halo around the moon.

A **Shader Quality** preset (Low, Medium, High, Ultra; Medium by default) sets the expensive parts: shadow
sharpness and distance, reflections, ambient occlusion and sun rays. Each part also has its own switch (Shadows,
Glow, Water Reflections, Moving Water and Plants, Sun Rays, Ambient Occlusion, Colored Light, Smooth Edges), and all of them
change while the game is running; changing one makes the preset read Custom. On an M4 Max in a forest scene:
about 255 FPS with shaders off, 160 on Medium, 125 on High. Three more settings are in the config file:
`shaders.shadowResolution` (1024, 2048 or 4096), `shaders.shadowDistance` (in chunks) and
`shaders.sunPathRotation` (degrees).

How it works: the game and Sodium keep drawing with their own pipelines, but the text of their shaders is replaced
with Metallum Extra's. The extra data those shaders need (sun direction, shadow map, a copy of the scene for
reflections) is bound by Metallum's render pass itself. The world is still drawn into the game's normal image, so
anything Metallum Extra does not replace (other mods' own shaders, text, beams, the HUD) keeps working unchanged.
The shader files are plain GLSL in `assets/metallum-extra/shaders/` inside the jar.

Cost: on an M4 Max at the default 8-chunk shadow distance, about a third of the frame rate in a forest scene
(roughly 240 FPS without, 160 with). Shadows are most of that.

Limits, as tested:
- **Not together with Shine.** Shine replaces the same terrain shader with one of its own (colored light, its
  own water, caustics, foliage wind, bloom masks drawn into extra render targets), and both cannot own it at
  once; with both on the picture is broken. While Shine is installed the Shaders switch is greyed out. Shine's
  terrain features and these shaders overlap heavily, so it is one or the other.
- **No shadows from torches, fire or lava.** Shadows come from the one light the shadow map is drawn for: the sun
  or moon. Block light lights things but casts no shadow, in any dimension. In the Nether and the End there is
  no sun, so there are no shadows at all there; what those dimensions get is the lighting, the glow of lava,
  fire and glowstone, and stronger bloom.
- **Distant Horizons' far terrain is not lit by these shaders.** It keeps its own look, which shows most at night.
- **A mob off screen casts no shadow** into the picture, since the game does not draw mobs it cannot see.
- **Reflections show only what is on screen.** Water mirrors the picture itself, so something above the top
  edge of the screen is not in its reflection; there the sky is reflected instead.
- **The Improved Transparency video setting is ignored** while shaders are on (your setting is not changed).
- Tested in the Overworld (day, night, rain, underwater), the Nether and the End, and switching on and off in a
  running world, all under Apple's Metal API validation with no errors from the shaders.

### Frame-hitch profiler (off by default)
This is a troubleshooting tool, not something to leave running. Turn it on with **Performance Logging** in the
settings (or `profiler.enabled=true`) and restart; it then writes new files every session.

Each time a frame takes noticeably longer than normal (by default, longer than 15 ms *and*
4× the recent average), the profiler records where the time went:

| Bucket | Meaning |
|---|---|
| `shader compile` | Metallum compiling a pipeline the first time it's used (GLSL → SPIR-V → MSL → Metal) |
| `waiting on GPU` | CPU blocked because the GPU is ≥3 frames behind, or a fence wait |
| `waiting for drawable` | CPU blocked in `nextDrawable` (display/compositor hasn't returned a swapchain image yet) |
| `GC` | Java stop-the-world garbage collection pause |
| `alloc` | Metal buffer/texture allocations made mid-frame |
| `other CPU` | Everything else on the render thread: game logic, chunk work, Sodium, etc. |

It also counts render and copy passes per frame. On Apple GPUs, every switch between passes costs a tile store and reload.

Output:
- **Game log** (`logs/latest.log`): one `HITCH` line per stutter (rate-limited), plus a summary every 10 s
  with avg FPS, median frame time, 1% and 0.1% lows, and a count of hitches by cause.
- **`<minecraft folder>/metallum-extra/`**
  - `hitches-<time>.csv`: one row for every hitch, with the full breakdown
  - `hitch-stacks-<time>.txt`: what the render thread was doing during each long frame (sampled stacks)
  - `profile-<time>.txt`: for every summary window, where the render thread spent its time (sampled ~50×/s)
  - `gpu-slow-<time>.csv`: every frame the GPU took 8 ms or more over, with Metal's own timings (time queued, time
    executing) and what that frame contained (passes, allocations, whether it was presented)
  - `summary-<time>.csv`: one row per summary window
  - `runtime-pipelines-<time>.txt`: pipelines that had to be compiled *during gameplay*.
    This list feeds the shader warm-up cache planned for 0.2.

### Fix: direct buffer upload
In Metallum 0.0.23, creating a buffer with initial data always records a GPU copy, and that copy ends the current
render pass. For buffers in CPU-visible memory, Metallum Extra writes the data directly instead.
Metallum 0.0.24 makes the same change itself, so on 0.0.24 this fix stands aside and its setting is greyed out.

### Fix: non-blocking present (`fix.nonBlockingPresent`)
With vsync off, Metallum still stalls the render thread in `nextDrawable` whenever macOS has no swapchain
image free. Metallum Extra asks for images on a helper thread; a frame that finishes while none is free is simply
not shown (the next one is), so the render thread never waits on the display. Does nothing with vsync on.
The profiler summary reports `shown N/s, skipped N/s` when this is active.

### Fix: fast section re-centering (`fix.fastSectionRecenter`)
This one is in Minecraft itself, not Metallum. Each time the camera enters a new chunk section, the game rescans
every section slot in render distance on the render thread: about 6.3 million slots at render distance 256 (for
example with Bobby), roughly 27 ms per crossing. Metallum Extra updates only the slots that changed (about 0.2 ms).
The result is identical to the vanilla rescan; a randomized comparison of 1,600 moves found no differences.

### Fix: spread Sodium buffer cleanup (`fix.spreadSodiumCleanup`)
This one is in Sodium. Every frame Sodium empties a queue of chunk-mesh buffers the garbage collector has finished
with. The queue only fills when a collection cycle ends, so afterwards it can hold a few hundred thousand entries
and emptying it in one go stalls the render thread for 50-130 ms (seen every 25-30 s with Bobby at 256 chunks).
Metallum Extra gives that work a small time budget per frame and leaves the rest for the next frames.
The profiler summary reports `cleanup deferred N frames`.

### Compatibility: Distant Horizons (`compat.distantHorizons`)
Distant Horizons 3.3.x asks the game which graphics backend it is on and treats anything that is not "Vulkan" as
OpenGL, so on Metallum it takes its OpenGL path and crashes. Its Vulkan path is written against the game's own
rendering API, so Metallum Extra answers "Metal" the same way as "Vulkan". With that, DH starts, compiles all of
its render pipelines on Metal and runs. Experimental; needs a restart to change.

### Compatibility: multiple render targets (`compat.multipleRenderTargets`)
Metallum tells the game it can draw into one color target per render pass, and it only ever attaches the
first one. A mod that draws into several at once crashes with `Render pass created with 3 color attachments but
device only supports 1`. Metallum Extra raises the limit to 8 (Metal's own limit) and attaches the extra targets,
both in the render pass and in the pipelines that write them. A pass that leaves some of a pipeline's targets
unattached gets its own pipeline variant, since Metal wants the two to match exactly.
Passes and pipelines with a single target are untouched. Always on; can be switched off in the config file only.

### Compatibility: more textures per shader (`compat.manyTextures`)
Metallum numbers a pipeline's uniform blocks and textures in one sequence and gives each texture's sampler the same
number. Metal has 16 sampler slots per shader stage, so a shader whose textures are numbered 16 or higher fails to
compile (`'sampler' attribute parameter is out of bounds`). For such a shader Metallum Extra renumbers the samplers
0, 1, 2... Shaders that already fit are left as they are. Always on; can be switched off in the config file only.

### Compatibility: Shine (`compat.shine`)
Shine 3.1 picks its code path from the graphics backend's name and knows only OpenGL and Vulkan. On "Metal" it
settles on "unknown" and its terrain shader never gets its data. Its Vulkan path goes through the game's own
rendering API, so Metallum Extra answers "Metal" the same way as "Vulkan". Shine also relies on the two fixes
above: it draws terrain into three color targets, and its terrain shader reads nine textures.
Tested with Shine 3.1.1: it loads a world and renders without errors under Metal's API validation. How it looks has
not been compared against Shine on Vulkan. Experimental; needs a restart to change.

## Settings in game
The same settings appear in two places, with the same names:
- **Video Settings → Metallum Extra** (when Sodium is installed)
- **Mods → Metallum Extra → settings button** (when Mod Menu is installed)

| In game | Config key |
|---|---|
| Shaders | `shaders.enabled` (off by default) |
| Shadows | `shaders.shadows` |
| Glow | `shaders.bloom` |
| Water Reflections | `shaders.waterReflections` |
| Moving Water and Plants | `shaders.waving` |
| Sun Rays | `shaders.sunRays` |
| Ambient Occlusion | `shaders.ambientOcclusion` |
| Colored Light | `shaders.coloredLight` |
| Smooth Edges | `shaders.smoothEdges` |
| Shader Quality | (writes the keys above plus `shaders.shadowResolution` and `shaders.shadowDistance`) |
| Unlocked Frame Rate | `fix.nonBlockingPresent` |
| Smooth Chunk Crossing | `fix.fastSectionRecenter` |
| Smooth Memory Cleanup | `fix.spreadSodiumCleanup` |
| Faster Small Uploads | `fix.directBufferUpload` |
| Distant Horizons Support | `compat.distantHorizons` |
| Shine Support | `compat.shine` |
| Performance Logging | `profiler.enabled` (off by default) |

Two more keys are in the config file only: `compat.multipleRenderTargets` and `compat.manyTextures`. They lift
Metallum limits and do nothing unless a mod needs them, so they are always on and have no switch in game.

## Config
`config/metallum-extra.properties` is created on first launch. Restart the game after you edit it.

The shader and smoothness settings switch on and off while the game is running and are saved to this file; see
*Settings in game* above.

## Dev client
`./gradlew runClient` launches a development client with Metallum loaded
(Metallum 0.0.23; add `-Pmetallum_run_version=0.0.24` for the newer one).

## Updating to a new Metallum release
Metallum Extra hooks Metallum's internal classes, so each build only accepts the Metallum versions it has been
checked against (`metallum_version` up to `metallum_max_version` in `gradle.properties`). The shaders
also hook Sodium's chunk renderer and are written against Sodium 0.9.2.
`fabric.mod.json` enforces this: Fabric refuses to launch with any other Metallum and shows a clear message, so the
game won't crash. To add a new release:
1. Set `metallum_max_version` in `gradle.properties` to the new Modrinth version.
2. Run `./gradlew build`, then `./gradlew runClient -Pmetallum_run_version=<new version>`. Any hook whose target
   changed fails at startup with a mixin error naming the method.
3. Update that hook so it works on every accepted version, and check `com/metallum/render/MetallumExtraBridge.java`
   against the new source. The mod is compiled against the oldest accepted version, so it must only call Metallum
   methods that all of them have; where they differ, branch on `MetallumVersion`.
4. Launch once more on the oldest version (`./gradlew runClient`).

## Working on the shaders
Start the dev client with `-Dmetallumextra.shaderDir=<path to src/main/resources/assets/metallum-extra/shaders>` to
read the shader files from that folder instead of the jar, and with `-Dmetallumextra.debugScript=<file>` to run a
list of steps (set the time, move the player, save the world image as a PNG, switch settings, quit) once a world
is loaded. The steps are listed at the top of `DebugScript.java`.

## Roadmap
- **0.2:** shader warm-up and a persistent pipeline cache (compile everything from `runtime-pipelines` on the
  loading screen, and cache compiled Metal binaries across launches).
- Batching uploads into one copy pass was built and tested twice (97 chunks without Bobby, then 256 with Bobby and
  GPU timings). It cut copy passes about twentyfold but did not reduce slow GPU frames or raise 1% lows in on/off/on
  tests, so it is not included.
- Further fixes, chosen based on the profiler data.
