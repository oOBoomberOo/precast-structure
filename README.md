# Pre-cast Structure

Build something once, bag it, and drop it somewhere else.

Pre-cast Structure is a Minecraft mod for **1.21.1** (Fabric and NeoForge). You frame a build, scan it into a blueprint, print that into a portable structure item, then place it with a hologram preview — useful for camps, shops, or anything you keep rebuilding by hand.

## How it works

Frame the volume first:

1. Lay a solid rectangle of **Platform Floor** (3×3 or larger).
2. Run **Perimeter Fence** (or gates) one block above the platform edge.
3. Put a **Metal Scaffold** pillar on exactly one corner — that sets the height.
4. Park a **Structure Scanner** against one side of the platform.

Build inside the frame, then open the scanner with an **Empty Blueprint** in your inventory. It digitizes the interior and hands you a filled **Blueprint**. Scanning empties chests, lecterns, jukeboxes, and other containers in the frame first (contents drop in place), so replicas never copy stored items. Beds, doors, and similar double blocks count as one material each.

Feed that blueprint into a **Structure Printer** along with the materials the build needs. When it finishes, you get a **Pre-cast Structure** item. Hold it to see a ghost preview; right-click to place (or watch it deploy with the rising hologram if animated deploy is on).

Filled blueprints can be cloned (blueprint + empty blueprint) or cleared back to empty by crafting the filled one alone. Recipes are in JEI/REI.

## Compatibility

Optional soft dependencies — the mod runs fine without them:

- **[Create](https://modrinth.com/mod/create)** — kinetic block NBT, hologram meshes, and material costs (including brackets / encased shafts) when Create is installed.
- **Sable** (Create Aeronautics) — scan and placement holograms follow Simulated ship poses so previews stay visible on moving plots.

## Requirements

- Minecraft **1.21.1**
- Java **21**
- [Architectury API](https://modrinth.com/mod/architectury-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)
- Fabric build also needs [Fabric API](https://modrinth.com/mod/fabric-api)

There's a Cloth Config screen for scan/deploy timing, frame size limits, printer delay, and hologram collision. A matching **1.20.1** line lives on the `version/1.20.x` branch (Fabric + Forge).

## Building

```bash
./gradlew :fabric:remapJar :forge:remapJar
```

Jars land under `fabric/build/libs` and `forge/build/libs`. GitHub Releases for Fabric and NeoForge jars are produced from semver tags (or manual workflow dispatch) via `.github/workflows/release.yml`.

## License

MIT — see [LICENSE](LICENSE).
