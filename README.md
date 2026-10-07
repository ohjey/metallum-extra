# Metallum Extra

An add-on for [Metallum](https://modrinth.com/mod/metallum-mc) (the Metal backend for Minecraft on macOS).
The goal is to make uncapped FPS steady, not just high, on Apple Silicon.

**Built for:** Minecraft 26.2 · Fabric · Metallum **0.0.23** (exact version; see *Updating* below)

## What's in 0.1.0

### Frame-hitch profiler
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

## Config
`config/metallum-extra.properties` is created on first launch. Restart the game after you edit it.

With [Mod Menu](https://modrinth.com/mod/modmenu) installed, the fixes can also be switched on and off while the game
is running: **Mods → Metallum Extra → settings button**. The change applies immediately and is saved to the file.

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
- Batching uploads into one copy pass was built and tested (copy passes fell from ~65 to ~3 per frame while flying at
  97 chunks) but did not change FPS or 1% lows in an on/off/on test, so it was removed.
- Further fixes, chosen based on the profiler data.
