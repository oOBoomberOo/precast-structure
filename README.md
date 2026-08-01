# Pre-cast Structure

Build something once, bag it, and drop it somewhere else.

Pre-cast Structure is a Minecraft mod for **1.21.1** (Fabric and NeoForge). You frame a build, scan it into a blueprint, print that into a portable structure item, then place it with a hologram preview — useful for camps, shops, or anything you keep rebuilding by hand.

## How it works

Frame the volume first:

1. Lay a solid rectangle of **Platform Floor** (3×3 or larger).
2. Run **Perimeter Fence** (or gates) one block above the platform edge.
3. Put a **Metal Scaffold** pillar on exactly one corner — that sets the height.
4. Park a **Structure Scanner** against one side of the platform.

Build inside the frame, then open the scanner with an **Empty Blueprint** in your inventory. It digitizes the interior and hands you a filled **Blueprint**.

Feed that blueprint into a **Structure Printer** along with the materials the build needs. When it finishes, you get a **Pre-cast Structure** item. Hold it to see a ghost preview; right-click to place (or watch it deploy with the rising hologram if animated deploy is on).

Filled blueprints can be cloned (blueprint + empty blueprint) or cleared back to empty by crafting the filled one alone. Recipes are in JEI/REI.

## Requirements

- Minecraft **1.21.1**
- [Architectury API](https://modrinth.com/mod/architectury-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)
- Fabric build also needs [Fabric API](https://modrinth.com/mod/fabric-api)

There's a Cloth Config screen for scan/deploy timing, frame size limits, and hologram collision. A matching **1.20.1** line lives on the `version/1.20.x` branch (Fabric + Forge).

## License

MIT — see the license file in the repo.
