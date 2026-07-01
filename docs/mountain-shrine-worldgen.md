# Mountain Shrine Worldgen

Musubi now generates a small overgrown mountain shrine as a structure during world generation.

## What Generates

- A stepped stone approach climbing up the mountain
- A torii gate at the entrance
- A mossy terrace with lanterns
- A small shrine pavilion with an offering box and shrine stone
- Two sacred focal pieces:
  - one `Shinboku` wrapped around a living tree
  - one `Shinboku` wrapped around a sacred stone

## Content Used

- Custom structure registration in `MusubiStructures`
- Custom structure classes in `worldgen/structure`
- Existing custom blocks:
  - `Shinboku`
  - `ShrineStone`
  - `DonationBox`
  - `Torii`

## Placement Notes

- The structure places in mountain-leaning biomes through the `has_shrine` biome tag.
- The structure piece sets explicit blockstate values for facing-sensitive blocks like the donation box and torii.
- StructurePiece rotation handles the final world orientation, so local block facings only need to match the intended in-piece layout.
- The shrine is intentionally compact so it feels tucked into the terrain rather than sitting flat on top of it.

## Extending The Shrine

- Add more custom shrine props if the scene needs more personality, such as a dedicated toro lantern block.
- Keep the path and roof geometry rotation-aware if the shrine is expanded.
- If the shrine becomes larger, consider splitting it into multiple pieces instead of one piece class.
