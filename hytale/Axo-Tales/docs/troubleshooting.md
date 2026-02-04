# Troubleshooting

## Multiple versions loading

If you see multiple AxoTales versions, remove older `AxoTales-*.jar` files from your Mods folder (the game can keep old jars locked while running).

## Where to find logs

If Axo Tales creates a `logs/` folder inside its data folder, include those logs when reporting issues.

## Config problems

`server-config.json` must be valid JSON. If values are invalid (negative, non-finite, etc.), Axo Tales sanitizes them back to safe defaults.

