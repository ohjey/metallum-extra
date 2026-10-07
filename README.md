# Metallum Extra

An add-on for [Metallum](https://modrinth.com/mod/metallum-mc) (the Metal backend for Minecraft on macOS).
The goal is to make uncapped FPS steady, not just high, on Apple Silicon.

**Built for:** Minecraft 26.2 · Fabric · Metallum **0.0.23** (exact version; see *Updating* below)

## What's in 0.1.0

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
Metallum's newer (unreleased) source makes the same change.

### Fix: non-blocking present (`fix.nonBlockingPresent`)
With vsync off, Metallum 0.0.23 still stalls the render thread in `nextDrawable` whenever macOS has no swapchain
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
Metallum 0.0.23 tells the game it can draw into one color target per render pass, and it only ever attaches the
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

The smoothness settings switch on and off while the game is running and are saved to this file; see *Settings in
game* above.

## Building
You need **JDK 25** (the Java version Minecraft 26.2 uses).

```bash
# one-time, if you don't have JDK 25:
brew install --cask temurin@25

cd ~/Projects/metallum-extra
./gradlew build
```

The mod jar is written to `build/libs/metallum-extra-0.1.0+metallum.0.0.23.jar`. Ignore the `-sources` jar.
Put the mod jar in your instance's `mods` folder, next to Metallum 0.0.23.

To launch a dev client with Metallum loaded instead: `./gradlew runClient`

## Updating to a new Metallum release
Metallum Extra hooks Metallum's internal classes, so each build targets **one** Metallum version.
`fabric.mod.json` enforces this: Fabric refuses to launch with a mismatched pair and shows a clear message, so the game
won't crash. To port:
1. Set `metallum_version` in `gradle.properties` to the new Modrinth version.
2. Run `./gradlew build`, then launch. Any hook whose target changed fails at startup with a mixin error naming
   the method.
3. Update that hook, and check `com/metallum/render/MetallumExtraBridge.java` against the new source.

## Roadmap
- **0.2:** shader warm-up and a persistent pipeline cache (compile everything from `runtime-pipelines` on the
  loading screen, and cache compiled Metal binaries across launches).
- Batching uploads into one copy pass was built and tested twice (97 chunks without Bobby, then 256 with Bobby and
  GPU timings). It cut copy passes about twentyfold but did not reduce slow GPU frames or raise 1% lows in on/off/on
  tests, so it is not included. The experiment is kept on the `batch-uploads-retest` branch.
- Further fixes, chosen based on the profiler data.
