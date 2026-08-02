# Pre-cast Structure

![Pre-cast Structure Logo](common/src/main/resources/assets/precast_structure/icon.png)

Build something once, bag it, and drop it somewhere else.

Pre-cast Structure is a Minecraft mod for **1.21.1** (Fabric and NeoForge). You frame a build, scan it into a blueprint, print that into a portable structure item, then place it with a hologram preview — useful for camps, shops, or anything you keep rebuilding by hand.

## How it works

Frame the volume first:

1. Lay a solid rectangle of **Platform Floor** (3×3 or larger; max size is configurable, default 64).
2. Run **Perimeter Fence** (or gates) one block above the platform edge — leave the scaffold corner open.
3. Put a **Metal Scaffold** pillar on exactly one corner — that sets the height.
4. Park a **Structure Scanner** against one side of the platform, or in the fence/scaffold line (it counts as a perimeter/scaffold block).

Build inside the frame, then open the scanner with an **Empty Blueprint** in your inventory. It digitizes the interior and hands you a filled **Blueprint**. Scanning empties chests, lecterns, jukeboxes, and other containers first (contents drop in place; ender chests are skipped), then clears the framed build into a scan hologram. The platform, fence, scaffold, and scanner stay; the build itself is consumed. Beds, doors, and similar double blocks count as one material each.

Feed that blueprint into a **Structure Printer** along with the materials the build needs. The blueprint stays in the printer; only the listed materials are consumed. When it finishes, you get a **Pre-cast Structure** item.

Hold the item for a ghost preview (it tints red when placement is blocked). Right-click to place into replaceable space only — never into a structure that is still deploying. With animated deploy on (default), it rises in with a hologram; turn that off in Cloth Config for instant place. Solid hologram collision (also on by default) keeps players from walking through scan and deploy holograms.

Filled blueprints clone map-style: filled blueprint + empty blueprint → two filled copies. Craft a filled blueprint alone to clear it back to empty. Recipes show in the vanilla recipe book, and in JEI/REI if you use them.

## Compatibility

Optional soft dependencies — the mod runs fine without them:

- **[Create](https://modrinth.com/mod/create)** (6.0+) — kinetic block NBT, hologram meshes, and material costs (including brackets / encased shafts) when Create is installed.
- **[Sable](https://modrinth.com/mod/sable)** (Create Aeronautics) — scan and placement holograms follow Simulated ship poses so previews stay visible on moving plots.

## Requirements

- Minecraft **1.21.1**
- Java **21**
- [Architectury API](https://modrinth.com/mod/architectury-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)
- Fabric build also needs [Fabric API](https://modrinth.com/mod/fabric-api)

Open the Cloth Config screen from Mod Menu (Fabric) or the mods list (NeoForge) for scan/deploy timing, frame size limits, printer delay, animated deploy, and hologram collision. Live worlds still honor the `precastStructurePrinterDelay` gamerule; the Cloth value is the fallback. A matching **1.20.1** line lives on the `version/1.20.x` branch (Fabric + Forge).

## Building

```bash
./gradlew :fabric:remapJar :forge:remapJar
```

Jars land under `fabric/build/libs` and `forge/build/libs`. GitHub Releases for Fabric and NeoForge jars are produced from semver tags (or manual workflow dispatch) via `.github/workflows/release.yml`.

## License

MIT — see [LICENSE](LICENSE).
