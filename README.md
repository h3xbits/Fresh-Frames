# Flush Frames

A Fabric mod that makes item frames and paintings sit flush against thin and odd-shaped blocks
instead of floating in the air, and keeps them attached when those blocks change.

## Features

- **Flush placement** on fences, iron bars, glass panes, walls, fence gates, lightning rods, chains,
  lanterns, chests, decorated pots, bamboo, dried ghasts and more.
- **Item frames on top-face blocks** too: pressure plates, carpets, slabs, snow, farmland, repeaters,
  beds, daylight detectors and end portal frames.
- **Doors and trapdoors**: frames and paintings swing with the panel when it opens or closes, and stay
  on the same side of it.
- **Banners**: frames hang on the cloth, swing with it, and follow rotated banners.
- **Pressure plates**: a frame on a plate moves down with it when it's pressed.
- **Paintings** get the same treatment when they're the size of what they hang on (1x1, or 1x2 on a door).
- Frames and paintings move in the same rendered frame as the block, even at low fps.
- Clicking a frame or painting in creative no longer breaks the block behind it.
- Debug screen entry: `looking_at_item_frame` in the F3 + F6 options.

## Requirements

- Minecraft 26.2
- Fabric Loader and Fabric API

## Building

```bash
./gradlew build
```

The mod jar ends up in `build/libs/`.

## Testing

The mod has an in-game test that opens a real client, builds test setups and checks every frame and
painting position:

```bash
./gradlew runClientGameTest
```

Screenshots from the run are saved in `build/run/clientGameTest/screenshots`.

## Contributing

Contributions are welcome - see [CONTRIBUTING.md](CONTRIBUTING.md).

## Support

Flush Frames is free and always will be. If you'd like to support development, you can donate through
the links on the GitHub "Sponsor" button or the mod's Modrinth / CurseForge pages. It's never required.

## License

[MIT-based, no selling](LICENSE) - free to use, share and modify with credit, but not to sell. Modpacks are welcome.
