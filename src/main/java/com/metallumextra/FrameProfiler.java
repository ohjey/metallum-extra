package com.metallumextra;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Per-frame timing of the things in Metallum that can block the render thread.
 * <p>
 * All hooks are called from Metallum's render-thread code. Frame boundary = end of MetalSurface.present().
 * Everything here is plain fields (no locks): only the render thread records; other threads are ignored.
 */
public final class FrameProfiler {
    private static final ExtraConfig CONFIG = ExtraConfig.get();
    private static final boolean ENABLED = CONFIG.profilerEnabled;
    private static final int WARMUP_FRAMES = 240;

    // ---- per-frame accumulators ----
    private static long compileNs;
    private static long gpuWaitNs;
    private static long drawableNs;
    private static long allocNs;
    private static int compiles;
    private static int bufferAllocs;
    private static int textureAllocs;
    private static int renderPasses;
    private static int blitPasses;
    private static int directUploads;
    private static int copyUploads;
    private static final List<String> compiledThisFrame = new ArrayList<>(4);

    // ---- in-flight timers ----
    private static long compileStart;
    private static String compilingPipeline = "?";
    private static RenderPipeline requestedPipeline;
    private static long gpuWaitStart;
    private static long drawableStart;
    private static long allocStart;

    // ---- frame state ----
    private static Thread renderThread;
    private static long lastFrameEnd;
    private static long frameIndex;
    private static double avgMs;
    private static long sessionStart;

    // ---- summary window ----
    private static float[] windowFrames = new float[16384];
    private static int windowCount;
    private static long windowStart;
    private static int windowHitches;
    private static int windowLogged;
    private static int windowCompiles;
    private static long windowCompileNs;
    private static int windowGcPauses;
    private static long windowGcMs;
    private static long windowGpuWaitNs;
    private static long windowDrawableNs;
    private static long windowRenderPasses;
    private static long windowBlitPasses;
    private static int windowPresented;
    private static boolean shownThisFrame;

    // ---- GPU timing (from Metal's command buffer timestamps) ----
    private static final double GPU_SLOW_MS = 8.0;
    private static int windowGpuFrames;
    private static double windowGpuExecMs;
    private static double windowGpuExecMaxMs;
    private static double windowGpuQueueMaxMs;
    private static double frameGpuExecMaxMs;
    private static double frameGpuQueueMaxMs;
    private static int windowSkipped;
    private static final int[] windowCauseCounts = new int[Cause.values().length];

    // ---- startup (before the first presented frame) ----
    private static int startupCompiles;
    private static long startupCompileNs;

    // ---- output ----
    private static BufferedWriter hitchCsv;
    private static BufferedWriter summaryCsv;
    private static BufferedWriter pipelinesTxt;
    private static BufferedWriter stacksTxt;
    private static BufferedWriter profileTxt;
    private static BufferedWriter gpuCsv;
    private static final Set<String> runtimePipelines = new HashSet<>();

    enum Cause { SHADER_COMPILE, GPU_WAIT, DRAWABLE_WAIT, GC_PAUSE, ALLOCATION, CPU_OTHER }

    static {
        if (ENABLED) {
            openOutputs();
            try {
                GcMonitor.install();
            } catch (Throwable t) {
                MetallumExtra.LOGGER.warn("[Metallum Extra] GC pause tracking unavailable: {}", t.toString());
            }
        }
    }

    private FrameProfiler() {
    }

    /** Forces class init (config + output files) from the mod entrypoint. */
    static void touch() {
    }

    private static boolean onRenderThread() {
        // Before the first frame we don't know the render thread yet; startup work is single-threaded enough.
        return ENABLED && (renderThread == null || Thread.currentThread() == renderThread);
    }

    // ===================================================================== hooks

    /** Called on every pipeline lookup; only stores a reference (cheap). */
    public static void pipelineRequested(final RenderPipeline pipeline) {
        if (ENABLED) requestedPipeline = pipeline;
    }

    public static void compileBegin() {
        if (!onRenderThread()) return;
        compilingPipeline = pipelineName(requestedPipeline);
        compileStart = System.nanoTime();
    }

    public static void compileEnd() {
        if (!onRenderThread() || compileStart == 0L) return;
        long dt = System.nanoTime() - compileStart;
        compileStart = 0L;
        if (renderThread == null) {
            startupCompiles++;
            startupCompileNs += dt;
            return;
        }
        compileNs += dt;
        compiles++;
        compiledThisFrame.add(compilingPipeline + String.format(Locale.ROOT, " (%.1fms)", dt / 1e6));
        if (runtimePipelines.add(compilingPipeline)) {
            writeLine(pipelinesTxt, compilingPipeline);
        }
    }

    public static void gpuWaitBegin() {
        if (!onRenderThread()) return;
        gpuWaitStart = System.nanoTime();
    }

    public static void gpuWaitEnd() {
        if (!onRenderThread() || gpuWaitStart == 0L) return;
        gpuWaitNs += System.nanoTime() - gpuWaitStart;
        gpuWaitStart = 0L;
    }

    public static void drawableBegin() {
        if (!onRenderThread()) return;
        drawableStart = System.nanoTime();
    }

    public static void drawableEnd() {
        if (!onRenderThread() || drawableStart == 0L) return;
        drawableNs += System.nanoTime() - drawableStart;
        drawableStart = 0L;
    }

    public static void allocBegin() {
        if (!onRenderThread()) return;
        allocStart = System.nanoTime();
    }

    public static void allocEnd(final boolean texture) {
        if (!onRenderThread() || allocStart == 0L) return;
        allocNs += System.nanoTime() - allocStart;
        allocStart = 0L;
        if (texture) textureAllocs++;
        else bufferAllocs++;
    }

    public static void renderPassStarted() {
        if (onRenderThread()) renderPasses++;
    }

    public static void blitPassStarted() {
        if (onRenderThread()) blitPasses++;
    }

    public static void bufferCreatedWithData(final boolean direct) {
        if (!onRenderThread()) return;
        if (direct) directUploads++;
        else copyUploads++;
    }

    /** Non-blocking present: whether this frame got a swapchain image (shown) or was skipped. */
    public static void framePresented(final boolean shown) {
        if (!ENABLED) return;
        if (shown) {
            windowPresented++;
            shownThisFrame = true;
        }
        else windowSkipped++;
    }

    /** Packs what this frame put into its command buffer, so a slow GPU frame can be matched to its contents. */
    public static long frameStatsForGpu() {
        if (!onRenderThread()) return -1L;
        return ((long) Math.min(renderPasses, 0xFFFF) << 48) | ((long) Math.min(blitPasses, 0xFFFF) << 32)
                | ((long) Math.min(bufferAllocs, 0x7FFF) << 16) | ((long) Math.min(textureAllocs, 0x7FFF) << 1) | (shownThisFrame ? 1L : 0L);
    }

    /**
     * Called when a finished command buffer is released. Times are Metal's own (seconds, mach time):
     * queue = commit until the driver scheduled it, exec = GPU start to GPU end.
     */
    public static void gpuFrameCompleted(final long commitNs, final long stats, final double kernelStart, final double kernelEnd,
                                         final double gpuStart, final double gpuEnd) {
        if (!onRenderThread() || renderThread == null || stats < 0L || gpuEnd <= 0.0) return;
        double execMs = (gpuEnd - gpuStart) * 1000.0;
        double kernelMs = (kernelEnd - kernelStart) * 1000.0;
        double queueMs = (kernelStart - commitNs / 1e9) * 1000.0;
        if (execMs < 0.0 || execMs > 60_000.0) return;
        // System.nanoTime() and Metal share the mach clock on macOS; if that ever stops holding, drop the value.
        if (queueMs < -5.0 || queueMs > 60_000.0) queueMs = Double.NaN;

        windowGpuFrames++;
        windowGpuExecMs += execMs;
        windowGpuExecMaxMs = Math.max(windowGpuExecMaxMs, execMs);
        frameGpuExecMaxMs = Math.max(frameGpuExecMaxMs, execMs);
        if (!Double.isNaN(queueMs)) {
            windowGpuQueueMaxMs = Math.max(windowGpuQueueMaxMs, queueMs);
            frameGpuQueueMaxMs = Math.max(frameGpuQueueMaxMs, queueMs);
        }
        if (execMs >= GPU_SLOW_MS || queueMs >= GPU_SLOW_MS) {
            writeLine(gpuCsv, String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.2f,%d,%d,%d,%d,%d",
                    (commitNs - sessionStart) / 1e9, queueMs, kernelMs, execMs,
                    (stats >>> 48) & 0xFFFF, (stats >>> 32) & 0xFFFF, (stats >>> 16) & 0x7FFF, (stats >>> 1) & 0x7FFF, stats & 1L));
        }
    }

    /** Logged on every swapchain (re)configure: FIFO means the display's refresh rate caps the frame rate. */
    public static void surfaceConfigured(final Object presentMode, final int width, final int height) {
        if (!ENABLED) return;
        MetallumExtra.LOGGER.info("[Metallum Extra] Surface configured: {}x{} presentMode={} (display sync {})",
                width, height, presentMode, "MAILBOX".equals(String.valueOf(presentMode)) ? "off" : "ON");
    }

    // ===================================================================== frame boundary

    public static void frameEnd() {
        if (!ENABLED) return;
        long now = System.nanoTime();

        if (renderThread == null) {
            renderThread = Thread.currentThread();
            lastFrameEnd = now;
            windowStart = now;
            sessionStart = now;
            MetallumExtra.LOGGER.info("[Metallum Extra] First frame. {} pipelines compiled during startup in {} ms.",
                    startupCompiles, String.format(Locale.ROOT, "%.0f", startupCompileNs / 1e6));
            GcMonitor.drainPauseMs();
            GcMonitor.drainPauseCount();
            HitchSampler.start(renderThread, now);
            resetFrame();
            return;
        }
        if (Thread.currentThread() != renderThread) return;

        long frameStart = lastFrameEnd;
        double frameMs = (now - lastFrameEnd) / 1e6;
        lastFrameEnd = now;
        HitchSampler.frameStarted(now);
        frameIndex++;

        long gcMs = GcMonitor.drainPauseMs();
        int gcCount = GcMonitor.drainPauseCount();
        windowGcMs += gcMs;
        windowGcPauses += gcCount;
        windowCompiles += compiles;
        windowCompileNs += compileNs;
        windowGpuWaitNs += gpuWaitNs;
        windowDrawableNs += drawableNs;
        windowRenderPasses += renderPasses;
        windowBlitPasses += blitPasses;

        if (windowCount == windowFrames.length) {
            windowFrames = Arrays.copyOf(windowFrames, windowFrames.length * 2);
        }
        windowFrames[windowCount++] = (float) frameMs;

        double threshold = Math.max(CONFIG.hitchMinMs, avgMs * CONFIG.hitchMultiplier);
        boolean hitch = frameIndex > WARMUP_FRAMES && frameMs > threshold;
        if (hitch) {
            recordHitch(frameMs, gcMs, gcCount, frameStart);
        } else {
            // Hitches are excluded so one stutter doesn't raise the bar for the next one.
            avgMs = avgMs == 0.0 ? frameMs : avgMs + 0.02 * (frameMs - avgMs);
        }

        if (now - windowStart >= CONFIG.summarySeconds * 1_000_000_000L) {
            summarize(now);
        }
        resetFrame();
    }

    private static void recordHitch(final double frameMs, final long gcMs, final int gcCount, final long frameStart) {
        double compileMs = compileNs / 1e6;
        double gpuMs = gpuWaitNs / 1e6;
        double drawMs = drawableNs / 1e6;
        double allocMs = allocNs / 1e6;
        double otherMs = Math.max(0.0, frameMs - compileMs - gpuMs - drawMs - allocMs - gcMs);

        double[] parts = {compileMs, gpuMs, drawMs, gcMs, allocMs, otherMs};
        int top = 0;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i] > parts[top]) top = i;
        }
        Cause cause = Cause.values()[top];
        windowHitches++;
        windowCauseCounts[top]++;

        double t = (lastFrameEnd - sessionStart) / 1e9;
        String compiled = String.join("; ", compiledThisFrame);
        writeLine(hitchCsv, String.format(Locale.ROOT,
                "%.2f,%.2f,%.2f,%s,%.2f,%d,%.2f,%.2f,%d,%.2f,%d,%d,%d,%d,%d,%d,%.2f,\"%s\",%.2f,%.2f",
                t, frameMs, avgMs, cause, compileMs, compiles, gpuMs, drawMs, gcMs, allocMs,
                bufferAllocs, textureAllocs, renderPasses, blitPasses, directUploads, copyUploads, otherMs,
                compiled.replace("\"", "'"), frameGpuExecMaxMs, frameGpuQueueMaxMs));

        String stacks = HitchSampler.report(frameStart, 3);
        if (stacks != null) {
            writeLine(stacksTxt, String.format(Locale.ROOT, "t=%.2fs frame=%.1fms cause=%s other_cpu=%.1fms", t, frameMs, cause, otherMs));
            writeLine(stacksTxt, stacks);
        }

        if (windowLogged < CONFIG.maxHitchLogsPerSummary) {
            windowLogged++;
            StringBuilder sb = new StringBuilder(160);
            sb.append(String.format(Locale.ROOT, "[Metallum Extra] HITCH %.1fms (normal %.1fms) cause=%s |", frameMs, avgMs, cause));
            if (compiles > 0) sb.append(String.format(Locale.ROOT, " shader compile %.1fms x%d [%s] |", compileMs, compiles, compiled));
            if (gpuMs >= 0.5) sb.append(String.format(Locale.ROOT, " waiting on GPU %.1fms |", gpuMs));
            if (drawMs >= 0.5) sb.append(String.format(Locale.ROOT, " waiting for drawable %.1fms |", drawMs));
            if (gcCount > 0) sb.append(String.format(Locale.ROOT, " GC %dms x%d (%s) |", gcMs, gcCount, GcMonitor.lastCollector()));
            if (bufferAllocs + textureAllocs > 0) sb.append(String.format(Locale.ROOT, " alloc %.1fms (%d buf, %d tex) |", allocMs, bufferAllocs, textureAllocs));
            sb.append(String.format(Locale.ROOT, " passes %d render / %d copy | other CPU %.1fms", renderPasses, blitPasses, otherMs));
            MetallumExtra.LOGGER.info(sb.toString());
        }
    }

    private static void summarize(final long now) {
        double seconds = (now - windowStart) / 1e9;
        int n = windowCount;
        if (n > 0) {
            float[] sorted = Arrays.copyOf(windowFrames, n);
            Arrays.sort(sorted);
            double total = 0.0;
            for (int i = 0; i < n; i++) total += sorted[i];
            double avgFps = n / seconds;
            double low1 = 1000.0 / meanOfWorst(sorted, Math.max(1, n / 100));
            double low01 = 1000.0 / meanOfWorst(sorted, Math.max(1, n / 1000));
            double worst = sorted[n - 1];
            double median = sorted[n / 2];

            StringBuilder causes = new StringBuilder();
            for (Cause c : Cause.values()) {
                int count = windowCauseCounts[c.ordinal()];
                if (count > 0) {
                    if (!causes.isEmpty()) causes.append(", ");
                    causes.append(c.name().toLowerCase(Locale.ROOT)).append(' ').append(count);
                }
            }
            MetallumExtra.LOGGER.info(String.format(Locale.ROOT,
                    "[Metallum Extra] last %.0fs: avg %.0f fps | median %.2fms | 1%% low %.0f fps | 0.1%% low %.0f fps | worst %.1fms | hitches %d%s | compiles %d (%.0fms) | GC pauses %d (%dms)"
                            + " | per frame: GPU wait %.2fms, drawable wait %.2fms, passes %.1f render / %.1f copy | shown %.0f/s, skipped %.0f/s"
                            + " | GPU exec avg %.2fms max %.1fms, queue max %.1fms",
                    seconds, avgFps, median, low1, low01, worst, windowHitches,
                    causes.isEmpty() ? "" : " (" + causes + ")",
                    windowCompiles, windowCompileNs / 1e6, windowGcPauses, windowGcMs,
                    windowGpuWaitNs / 1e6 / n, windowDrawableNs / 1e6 / n,
                    (double) windowRenderPasses / n, (double) windowBlitPasses / n,
                    windowPresented / seconds, windowSkipped / seconds,
                    windowGpuFrames == 0 ? 0.0 : windowGpuExecMs / windowGpuFrames, windowGpuExecMaxMs, windowGpuQueueMaxMs));
            writeLine(summaryCsv, String.format(Locale.ROOT,
                    "%.1f,%d,%.1f,%.3f,%.1f,%.1f,%.2f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.1f,%.1f,%d,%d,%.3f,%.2f,%.2f",
                    (now - sessionStart) / 1e9, n, avgFps, median, low1, low01, worst, windowHitches,
                    windowCauseCounts[0], windowCauseCounts[1], windowCauseCounts[2], windowCauseCounts[3],
                    windowCauseCounts[4], windowCauseCounts[5], windowCompiles, windowGcMs,
                    windowGpuWaitNs / 1e6 / n, windowDrawableNs / 1e6 / n,
                    (double) windowRenderPasses / n, (double) windowBlitPasses / n, windowPresented, windowSkipped,
                    windowGpuFrames == 0 ? 0.0 : windowGpuExecMs / windowGpuFrames, windowGpuExecMaxMs, windowGpuQueueMaxMs));
            String[] where = HitchSampler.drainBackground(10);
            writeLine(profileTxt, String.format(Locale.ROOT, "t=%.0fs avg %.0f fps median %.2fms", (now - sessionStart) / 1e9, avgFps, median));
            writeLine(profileTxt, "  phases: " + where[0]);
            writeLine(profileTxt, "  leaves: " + where[1]);
        }
        flush();

        windowStart = now;
        windowCount = 0;
        windowHitches = 0;
        windowLogged = 0;
        windowCompiles = 0;
        windowCompileNs = 0L;
        windowGcPauses = 0;
        windowGcMs = 0L;
        windowGpuWaitNs = 0L;
        windowDrawableNs = 0L;
        windowRenderPasses = 0L;
        windowBlitPasses = 0L;
        windowPresented = 0;
        windowGpuFrames = 0;
        windowGpuExecMs = 0.0;
        windowGpuExecMaxMs = 0.0;
        windowGpuQueueMaxMs = 0.0;
        windowSkipped = 0;
        Arrays.fill(windowCauseCounts, 0);
    }

    private static double meanOfWorst(final float[] sortedAsc, final int count) {
        double sum = 0.0;
        for (int i = sortedAsc.length - count; i < sortedAsc.length; i++) sum += sortedAsc[i];
        return sum / count;
    }

    private static void resetFrame() {
        compileNs = 0L;
        gpuWaitNs = 0L;
        drawableNs = 0L;
        allocNs = 0L;
        compiles = 0;
        bufferAllocs = 0;
        textureAllocs = 0;
        renderPasses = 0;
        blitPasses = 0;
        directUploads = 0;
        copyUploads = 0;
        compiledThisFrame.clear();
        shownThisFrame = false;
        frameGpuExecMaxMs = 0.0;
        frameGpuQueueMaxMs = 0.0;
    }

    // ===================================================================== output

    private static String pipelineName(final RenderPipeline pipeline) {
        return pipeline == null ? "?" : String.valueOf(pipeline.getLocation());
    }

    private static void openOutputs() {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("metallum-extra");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            hitchCsv = Files.newBufferedWriter(dir.resolve("hitches-" + stamp + ".csv"));
            writeLine(hitchCsv, "time_s,frame_ms,normal_ms,cause,compile_ms,compiles,gpu_wait_ms,drawable_wait_ms,gc_ms,alloc_ms,"
                    + "buffers_allocated,textures_allocated,render_passes,copy_passes,direct_uploads,copy_uploads,other_cpu_ms,compiled_pipelines,gpu_exec_ms_of_awaited,gpu_queue_ms_of_awaited");
            summaryCsv = Files.newBufferedWriter(dir.resolve("summary-" + stamp + ".csv"));
            writeLine(summaryCsv, "time_s,frames,avg_fps,median_ms,low1_fps,low01_fps,worst_ms,hitches,"
                    + "hitch_compile,hitch_gpu_wait,hitch_drawable,hitch_gc,hitch_alloc,hitch_cpu_other,compiles,gc_ms,"
                    + "avg_gpu_wait_ms,avg_drawable_wait_ms,avg_render_passes,avg_copy_passes,frames_shown,frames_skipped,avg_gpu_exec_ms,max_gpu_exec_ms,max_gpu_queue_ms");
            pipelinesTxt = Files.newBufferedWriter(dir.resolve("runtime-pipelines-" + stamp + ".txt"));
            stacksTxt = Files.newBufferedWriter(dir.resolve("hitch-stacks-" + stamp + ".txt"));
            profileTxt = Files.newBufferedWriter(dir.resolve("profile-" + stamp + ".txt"));
            gpuCsv = Files.newBufferedWriter(dir.resolve("gpu-slow-" + stamp + ".csv"));
            writeLine(gpuCsv, "commit_time_s,queue_ms,kernel_ms,gpu_exec_ms,render_passes,copy_passes,buffers_allocated,textures_allocated,presented");
            Runtime.getRuntime().addShutdownHook(new Thread(FrameProfiler::flush, "metallum-extra-flush"));
            MetallumExtra.LOGGER.info("[Metallum Extra] Profiler output: {}", dir);
        } catch (IOException e) {
            MetallumExtra.LOGGER.warn("[Metallum Extra] Could not create profiler output files", e);
        }
    }

    private static void writeLine(final BufferedWriter w, final String line) {
        if (w == null) return;
        try {
            w.write(line);
            w.newLine();
        } catch (IOException ignored) {
        }
    }

    private static synchronized void flush() {
        for (BufferedWriter w : new BufferedWriter[]{hitchCsv, summaryCsv, pipelinesTxt, stacksTxt, profileTxt, gpuCsv}) {
            if (w == null) continue;
            try {
                w.flush();
            } catch (IOException ignored) {
            }
        }
    }
}
