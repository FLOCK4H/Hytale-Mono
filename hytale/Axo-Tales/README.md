# Axo Tales

A magic and progression mod for **Hytale 0.7.6**, with spellbooks, equipment,
creatures and companions. Current version: **0.1.295**. Requires Java 25.

[Official guidebook](https://flock4h.github.io/Hytale-Mono/) ·
[Getting started](docs/getting-started.md) · [Item and recipe catalog](docs/items/catalog.md)

## Features

- Spellbooks and the Arcane Wand for combat, healing, travel and gathering.
- Arcane Veil, Sa'r and Ancient equipment with set bonuses and backpack progression.
- Blood magic, the Vitae Extractor, Blood Furnace and loyal companions.
- Villa, Fossor and Cruor Souls for collection, mining and protection.
- Whisperion, a tameable flying horse with saved ownership and removable saddle.
- Cursed Knights, Elder Mages, Chiro, Dreadmaw and Tom Tom encounters.
- Agretis, Revel and Aequalis resources, Arcane Crystals and magical movement blocks.
- The Astral Satchel, skill tree, runes, bows, Scout Arrows and Spyglass.

## Installation

Install exactly one current AxoTales JAR in the server's mods directory or
`%APPDATA%/Hytale/UserData/Mods`, remove older AxoTales JARs and restart Hytale.
The mod targets the exact server version `0.7.6`.

[Configuration](docs/config.md) covers spell costs, world resources and encounters.
Whisperion and Tom Tom use native creature spawning in Zone 1 plains, forests,
autumn woods and Azure areas, during day and night. Their default relative
weights are 2 and 2.5; the native horse weight is 5. Creature sightings depend on
eligible terrain and native population space.

## Repository contents

`plugin/` contains Java source, `Common/` and `Server/` contain runtime assets,
and `docs/` with `mkdocs.yml` contains the official guidebook. Build plugin JARs
from the full standalone development checkout. This source mirror does not
include its Gradle build setup.
