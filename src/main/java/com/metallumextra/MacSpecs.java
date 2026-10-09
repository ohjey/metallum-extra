package com.metallumextra;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * What this Mac is: its chip, core counts and memory, read once in the background (the GPU's core count comes from
 * {@code system_profiler}, which takes a second). For the line of specs in the shader menu.
 */
public final class MacSpecs {
    public record Specs(String chip, int cpuCores, int gpuCores, int memoryGb) {
        /** For the menu: {@code Apple M1 Pro | 10-core CPU | 16-core GPU | 32 GB}. */
        public String line() {
            return chip + " | " + cpuCores + "-core CPU" + (gpuCores > 0 ? " | " + gpuCores + "-core GPU" : "") + " | " + memoryGb + " GB";
        }
    }

    private static final CompletableFuture<Specs> SPECS = CompletableFuture.supplyAsync(MacSpecs::read);

    private MacSpecs() {
    }

    /** The specs once they have been read; null until then. */
    public static @Nullable Specs get() {
        return SPECS.getNow(null);
    }

    private static Specs read() {
        String chip = run("sysctl", "-n", "machdep.cpu.brand_string");
        int cpu = Runtime.getRuntime().availableProcessors();
        try {
            cpu = Integer.parseInt(run("sysctl", "-n", "hw.physicalcpu").strip());
        } catch (NumberFormatException ignored) {
        }
        long bytes = 0;
        try {
            bytes = Long.parseLong(run("sysctl", "-n", "hw.memsize").strip());
        } catch (NumberFormatException ignored) {
        }
        int gpu = 0;
        for (String line : run("system_profiler", "SPDisplaysDataType").split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("Total Number of Cores:")) {
                try {
                    gpu = Integer.parseInt(trimmed.substring(trimmed.indexOf(':') + 1).strip());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new Specs(chip.isBlank() ? "Unknown chip" : chip.strip(), cpu, gpu, (int) Math.round(bytes / 1073741824.0));
    }

    private static String run(final String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes());
            process.waitFor(5, TimeUnit.SECONDS);
            return out;
        } catch (IOException | InterruptedException e) {
            return "";
        }
    }
}
