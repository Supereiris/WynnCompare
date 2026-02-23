# WynnCompare

A client-side Fabric mod for Minecraft 1.21.11 that adds equipment tooltip comparison for [Wynncraft](https://wynncraft.com/). Hold a key while hovering over an item to see your equipped gear or a stat-by-stat breakdown.

## Features

- **Equipped Tooltip (C)** — View your currently equipped item's tooltip alongside the hovered item
- **Stat Comparison (X)** — See a stat-by-stat diff with color-coded improvements and downgrades
- **Smart Positioning** — Tooltips stay within screen bounds
- **Configurable Keybindings** — Rebind keys in Options > Controls under the **WynnCompare** category

Supports all Wynncraft equipment: armor, weapons, and accessories.

## Requirements

- Java 21+
- Minecraft 1.21.11
- Fabric Loader 0.16.0+
- Fabric API

## Build

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods/` folder
3. Copy the WynnCompare JAR from `build/libs/` into your `mods/` folder
4. Launch Minecraft

## Usage

While in a Wynncraft inventory screen:

| Key | Action |
|-----|--------|
| Hold **C** | Show equipped item tooltip on the left |
| Hold **X** | Show stat comparison on the left |

When both keys are held, the comparison tooltip takes priority.

## License

[MIT](LICENSE)
