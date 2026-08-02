# Agent guide — Pre-cast Structure

Guidance for coding agents working in this repository. Player-facing gameplay is documented in `README.md`; prefer that file for product behavior.

## Project

- Minecraft mod **Pre-cast Structure** (`precast_structure`), Architectury multi-loader for **1.21.1**.
- Loaders: **Fabric** (`fabric/`) and **NeoForge** (`forge/` module; NeoForge artifacts).
- Shared logic lives in `common/`. Keep gameplay, registry, and most client code there; put only loader entrypoints and thin platform glue in `fabric/` / `forge/`.
- Java **21**, official Mojang mappings (Loom), MIT license.

## Layout

| Path | Role |
|------|------|
| `common/src/main/java/.../precaststructure/` | Shared mod code (`MOD_ID` / init in `PrecastStructureMod`) |
| `common/.../registry/` | Deferred registers (blocks, items, menus, sounds, etc.) |
| `common/.../structure/` | Frame detect, blueprint, scan, print, deploy, placement |
| `common/.../block/`, `block/entity/`, `item/`, `menu/` | Blocks, BEs, items, menus |
| `common/.../client/` | Shared client render / hologram / screens |
| `common/.../compat/` | Optional Create / Sable soft deps |
| `common/.../config/` | Cloth Config |
| `common/src/main/resources/assets/precast_structure/` | Assets (including `lang/`) |
| `common/src/main/resources/data/` | Recipes, tags, loot, etc. |
| `common/src/test/java/` | JUnit 5 unit tests |
| `fabric/`, `forge/` | Loader entrypoints + platform implementations |
| `.github/workflows/release.yml` | Tag / dispatch releases to GitHub + Modrinth |

## Commands

Build remapped jars (same as README / release workflow):

```bash
./gradlew :fabric:remapJar :forge:remapJar
```

Outputs: `fabric/build/libs/`, `forge/build/libs/`.

Unit tests (common module):

```bash
./gradlew :common:test
```

Versions and dependency pins are in `gradle.properties`.

## Conventions that matter here

- **Shared-first:** Prefer `common/` unless the change is loader-specific.
- **Locales:** `en_us.json` is the key source of truth under `assets/precast_structure/lang/`. When adding or renaming message/config/subtitle keys, update every locale file in that directory (do not leave non-English files missing keys).
- **Soft deps:** Create / Sable compatibility is optional; guard with the existing compat helpers so the mod runs without those mods.
- **Config vs gamerule:** Printer delay honors the `precastStructurePrinterDelay` gamerule in live worlds; Cloth Config is the fallback (see README).
- **Tests:** Prefer extending the existing JUnit tests under `common/src/test/java` for structure/frame/blueprint logic when the change is unit-testable.
- **Docs:** Keep `README.md` aligned with real scan / print / place behavior when those flows change.

## Scope tip

Stay scoped to the requested change. Do not invent gameplay, config knobs, or soft-dep behavior that are not already in the issue or codebase patterns.
