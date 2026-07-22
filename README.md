# precast-structure

Architectury (Fabric + Forge) Minecraft 1.21.1 mod that lets players scan a framed build into a blueprint and print it into a placeable pre-cast structure item.

## Minimal gameplay loop

1. Place a **Structure Scanner** at the south-west corner of a platform.
2. Build a rectangle of **Platform Floor** blocks extending east and south from the scanner.
3. Place **Perimeter Fence** blocks one block above the platform border.
4. Place a vertical **Metal Scaffold** pillar on the opposite north-east corner of the platform border to set the height.
5. Build your structure inside the framed volume.
6. Right-click the scanner to receive a **Blueprint** item.
7. Hold the blueprint and right-click a **Structure Printer** to consume the required materials from your inventory and produce a **Pre-cast Structure** item.
8. Hold the pre-cast item to see a ghost placement preview, then right-click to place the stored structure if the target area only contains replaceable blocks.
