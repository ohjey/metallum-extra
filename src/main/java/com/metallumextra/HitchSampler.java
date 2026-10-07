package com.metallumextra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Explains "other CPU" hitches: once a frame has been running for {@link #START_NS}, a daemon thread samples the
 * render thread's stack every few ms. Separately, it takes a light background sample (~50/s) of every frame so
 * each summary window can say where the render thread's time went even when no single frame was a hitch.
 */
final class HitchSampler {
    private static final long START_NS = 20_000_000L;
    private static final long PERIOD_MS = 4L;
    private static final int MAX_SAMPLES = 500;
    private static final int GROUP_DEPTH = 10;
    private static final int PRINT_DEPTH = 28;
    private static final int BACKGROUND_EVERY = 5;

    private static final Map<String, Integer> phaseCounts = new java.util.HashMap<>();
    private static final Map<String, Integer> leafCounts = new java.util.HashMap<>();
    private static int backgroundSamples;

    private static final Object LOCK = new Object();
    private static final List<StackTraceElement[]> samples = new ArrayList<>();
    private static long samplesFrame;
    private static volatile long frameStart;

    private HitchSampler() {
    }

    static void start(final Thread renderThread, final long now) {
        frameStart = now;
        Thread sampler = new Thread(() -> run(renderThread), "metallum-extra-sampler");
        sampler.setDaemon(true);
        sampler.start();
    }

    static void frameStarted(final long now) {
        frameStart = now;
    }

    private static void run(final Thread renderThread) {
        int tick = 0;
        while (renderThread.isAlive()) {
            try {
                Thread.sleep(PERIOD_MS);
            } catch (InterruptedException e) {
                return;
            }
            long start = frameStart;
            boolean longFrame = System.nanoTime() - start >= START_NS;
            boolean background = ++tick % BACKGROUND_EVERY == 0;
            if (!longFrame && !background) continue;
            StackTraceElement[] trace = renderThread.getStackTrace();
            if (trace.length == 0) continue;
            synchronized (LOCK) {
                if (background) {
                    backgroundSamples++;
                    phaseCounts.merge(phase(trace), 1, Integer::sum);
                    leafCounts.merge(shortName(trace[0]), 1, Integer::sum);
                }
                if (!longFrame) continue;
                if (samplesFrame != start) {
                    samples.clear();
                    samplesFrame = start;
                }
                if (samples.size() < MAX_SAMPLES) samples.add(trace);
            }
        }
    }

    private static String shortName(final StackTraceElement e) {
        String cls = e.getClassName();
        return cls.substring(cls.lastIndexOf('.') + 1) + "." + e.getMethodName();
    }

    /** The two calls directly under Minecraft's frame loop, e.g. "GameRenderer.render > GameRenderer.renderLevel". */
    private static String phase(final StackTraceElement[] trace) {
        for (int i = 0; i < trace.length; i++) {
            if (trace[i].getClassName().equals("net.minecraft.client.Minecraft")
                    && (trace[i].getMethodName().equals("renderFrame") || trace[i].getMethodName().equals("runTick"))) {
                if (i == 0) return shortName(trace[0]);
                return i == 1 ? shortName(trace[0]) : shortName(trace[i - 1]) + " > " + shortName(trace[i - 2]);
            }
        }
        return shortName(trace[trace.length - 1]);
    }

    /** Where the render thread's time went since the last call: top phases and top leaf methods, then resets. */
    static String[] drainBackground(final int top) {
        synchronized (LOCK) {
            String[] out = {top(phaseCounts, backgroundSamples, top), top(leafCounts, backgroundSamples, top)};
            phaseCounts.clear();
            leafCounts.clear();
            backgroundSamples = 0;
            return out;
        }
    }

    private static String top(final Map<String, Integer> counts, final int total, final int top) {
        if (total == 0) return "";
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(top, entries.size()); i++) {
            if (i > 0) sb.append("; ");
            sb.append(Math.round(100.0 * entries.get(i).getValue() / total)).append("% ").append(entries.get(i).getKey());
        }
        return sb.toString();
    }

    /** The most common stacks seen during the frame that began at {@code start}, or null if it wasn't sampled. */
    static String report(final long start, final int maxGroups) {
        List<StackTraceElement[]> taken;
        synchronized (LOCK) {
            if (samplesFrame != start || samples.isEmpty()) return null;
            taken = new ArrayList<>(samples);
            samples.clear();
        }
        Map<String, List<StackTraceElement[]>> groups = new LinkedHashMap<>();
        for (StackTraceElement[] trace : taken) {
            StringBuilder key = new StringBuilder();
            for (int i = 0; i < Math.min(GROUP_DEPTH, trace.length); i++) key.append(trace[i]).append('\n');
            groups.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(trace);
        }
        List<List<StackTraceElement[]>> sorted = new ArrayList<>(groups.values());
        sorted.sort((a, b) -> b.size() - a.size());

        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < Math.min(maxGroups, sorted.size()); g++) {
            List<StackTraceElement[]> group = sorted.get(g);
            sb.append(String.format(Locale.ROOT, "  %d%% (%d of %d samples)%n",
                    Math.round(100.0 * group.size() / taken.size()), group.size(), taken.size()));
            StackTraceElement[] trace = group.get(0);
            for (int i = 0; i < Math.min(PRINT_DEPTH, trace.length); i++) {
                sb.append("      at ").append(trace[i]).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }
}
