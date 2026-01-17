# Brightness Tweaks

Brightness Tweaks boosts the in-game torch light, without making torches obsolete.

## Features
- Torch-only light boost (utility belt)
- Adjustable strength with a command
- Per-player (doesn't change world lighting for everyone)

## Commands
- `/brightness` disables the boost (reverts to normal torch behavior)
- `/brightness <value>` sets the boost from `0.05` to `1.0`
- `/brightness color` clears any custom tint override (uses the torch's normal tint)
- `/brightness color <#RRGGBB>` sets a custom tint for the boosted light (example: `/brightness color #FFAA00`)
- `/brightness warmth` clears any warmth override (uses the torch's normal tint)
- `/brightness warmth <0.0-1.0>` sets how warm the boosted light is (`0.0` = torch tint, `1.0` = warmer torch tint)

Note: the boost only applies while a torch item exists in your utility belt. If you remove all torches, the light is reverted.

## Install (Server / Local)
1. Build the jar: `.\gradlew.bat build`
2. Copy `build/libs/Brightness-Tweaks-<version>.jar` to your server's `mods/` folder (or your local `.../Hytale/UserData/Mods` folder).
3. Start/restart the server.

## Support
- Discord: `TBD`
- Telegram: `TBD`

## Notes
- This is an early mod. Hytale's API is still evolving; updates may be frequent.
