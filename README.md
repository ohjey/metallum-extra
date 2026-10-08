# Metallum Extra

An add-on for [Metallum](https://modrinth.com/mod/metallum-mc), the Metal backend for Minecraft on macOS.
It makes uncapped FPS steady on Apple Silicon and adds a built-in set of shaders that runs on Metal.

**Requires:** Minecraft 26.2, Fabric, and Metallum 0.0.23 or 0.0.24. The shaders also need Sodium.

## Install
Download the jar from [Releases](https://github.com/ohjey/metallum-extra/releases) and put it in your `mods`
folder next to Metallum.

## Features
- **Shaders** (experimental, off by default): sun and moon shadows, reflective water, glow, colored light,
  sun rays, ambient occlusion and a new sky. One built-in look; it does not load OptiFine or Iris shader packs.
- **Unlocked frame rate:** with VSync off, the game no longer pauses to wait for the screen. Higher FPS and
  fewer small stutters.
- **Smooth chunk crossing:** removes the freeze when you move into a new chunk at very high render distances.
- **Smooth memory cleanup:** spreads Sodium's chunk-memory cleanup over many frames instead of one.
- **Mod compatibility:** lets Distant Horizons and Shine run on Metallum.
- **Performance logging** (off by default): records what caused each stutter.

## Settings
Everything is switched in game, under **Video Settings → Metallum Extra** (with Sodium) or from Mod Menu.
Settings are saved to `config/metallum-extra.properties`.

## Building
Needs JDK 25.

```bash
git clone https://github.com/ohjey/metallum-extra.git
cd metallum-extra
./gradlew build
```

The jar is written to `build/libs/`.

How each feature works, the config keys and how to add a new Metallum release are in
[docs/details.md](docs/details.md).

## License
MIT; see [LICENSE](LICENSE). Metallum Extra is an add-on for [Metallum](https://github.com/kokodio/metallum),
which is also MIT licensed, and parts of it mirror fixes made in Metallum's own source.
