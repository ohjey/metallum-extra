package com.metallumextra.shader;

import com.metallumextra.ExtraConfig;
import com.metallumextra.ExtraConfigScreen;
import com.metallumextra.Quality;
import com.metallumextra.MetallumExtra;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Development aid, off unless the game is started with {@code -Dmetallumextra.debugScript=<file>}: runs a list of
 * steps once a world is on screen, so the picture can be checked without anyone at the keyboard. One step per line:
 * <pre>
 * wait 500           milliseconds to let pass
 * cmd time set 6000  a command, run by the integrated server
 * shot noon          saves the world image as noon.png next to the script
 * set shadows off    a shader setting: shaders, shadows, bloom, reflections, waving (on or off)
 * pos                logs where the player is
 * view back          third person from behind (front, or first to go back)
 * hud off            hides the HUD and chat (hud on shows them again)
 * screen settings    opens this mod's settings screen (screen close closes whatever is open)
 * reload             reads the shader files again
 * quit               closes the game
 * </pre>
 */
public final class DebugScript {
    private static final @Nullable Path FILE = file();
    private static @Nullable ArrayDeque<String> steps;
    /** The next step runs once the clock passes this. */
    private static long resumeAt;

    private DebugScript() {
    }

    static void tick() {
        if (FILE == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (steps == null) {
            // Not before the world is on screen with nothing in front of it.
            if (minecraft.gui.screen() != null) return;
            try {
                steps = new ArrayDeque<>(Files.readAllLines(FILE));
                pause(1500);
            } catch (IOException e) {
                MetallumExtra.LOGGER.error("[Metallum Extra] Could not read debug script {}", FILE, e);
                steps = new ArrayDeque<>(List.of());
            }
        }
        while (System.nanoTime() >= resumeAt && !steps.isEmpty()) {
            run(minecraft, steps.poll().trim());
        }
    }

    private static void pause(final long milliseconds) {
        resumeAt = System.nanoTime() + milliseconds * 1_000_000L;
    }

    private static void run(final Minecraft minecraft, final String step) {
        if (step.isEmpty() || step.startsWith("#")) return;
        int space = step.indexOf(' ');
        String verb = space < 0 ? step : step.substring(0, space);
        String rest = space < 0 ? "" : step.substring(space + 1).trim();
        MetallumExtra.LOGGER.info("[Metallum Extra] debug script: {}", step);
        switch (verb) {
            case "wait" -> pause(Long.parseLong(rest));
            case "cmd" -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack().withSuppressedOutput(), rest));
                }
                pause(300);
            }
            case "shot" -> {
                Path target = FILE.resolveSibling(rest + ".png");
                Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> Util.ioPool().execute(() -> {
                    try (image) {
                        image.writeToFile(target);
                        MetallumExtra.LOGGER.info("[Metallum Extra] debug script: wrote {}", target);
                    } catch (IOException e) {
                        MetallumExtra.LOGGER.error("[Metallum Extra] debug script: could not write {}", target, e);
                    }
                }));
                pause(100);
            }
            case "set" -> {
                String[] parts = rest.split("\\s+");
                boolean on = parts.length > 1 && parts[1].equals("on");
                ExtraConfig config = ExtraConfig.get();
                switch (parts[0]) {
                    case "shaders" -> config.shadersEnabled = on;
                    case "shadows" -> config.shaderShadows = on;
                    case "bloom" -> config.shaderBloom = on;
                    case "reflections" -> config.shaderWaterReflections = on;
                    case "waving" -> config.shaderWaving = on;
                    case "rays" -> config.shaderSunRays = on;
                    case "ao" -> config.shaderAmbientOcclusion = on;
                    case "colored" -> config.shaderColoredLight = on;
                    case "edges" -> config.shaderSmoothEdges = on;
                    case "quality" -> config.setShaderQuality(Quality.valueOf(parts[1].toUpperCase(java.util.Locale.ROOT)));
                    default -> MetallumExtra.LOGGER.warn("[Metallum Extra] debug script: unknown setting {}", parts[0]);
                }
                pause(100);
            }
            case "screen" -> {
                minecraft.gui.setScreen(rest.equals("settings") ? new ExtraConfigScreen(null) : null);
                pause(300);
            }
            case "pos" -> MetallumExtra.LOGGER.info("[Metallum Extra] debug script: player at {} looking {} / {}", minecraft.player.position(), minecraft.player.getYRot(), minecraft.player.getXRot());
            case "view" -> minecraft.options.setCameraType(rest.equals("back") ? CameraType.THIRD_PERSON_BACK : rest.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
            case "hud" -> {
                if (minecraft.gui.hud.isHidden() != rest.equals("off")) minecraft.gui.hud.toggle();
            }
            case "reload" -> {
                Shaders.reload();
                pause(100);
            }
            case "quit" -> minecraft.stop();
            default -> MetallumExtra.LOGGER.warn("[Metallum Extra] debug script: unknown step {}", step);
        }
    }

    private static @Nullable Path file() {
        String file = System.getProperty("metallumextra.debugScript");
        return file == null || file.isBlank() ? null : Path.of(file);
    }
}
