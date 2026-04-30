package org.example.plugin;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Server-side configuration for Axo Tales.
 *
 * <p>Loaded from {@code server-config.json} under the plugin data directory.</p>
 */
public final class AxoTalesServerConfig {

    public Spellbooks spellbooks = new Spellbooks();
    public Worldgen worldgen = new Worldgen();
    public Workarounds workarounds = new Workarounds();
    public CloudBlock cloudBlock = new CloudBlock();
    public BounceBlock bounceBlock = new BounceBlock();
    public RuneKnight runeKnight = new RuneKnight();
    public KuduAdept kuduAdept = new KuduAdept();
    public HordeBook hordeBook = new HordeBook();
    public DoomBook doomBook = new DoomBook();
    public MorphBook morphBook = new MorphBook();
    public FrostBook frostBook = new FrostBook();
    public FlameBook flameBook = new FlameBook();
    public LightBook lightBook = new LightBook();
    public TeleportBook teleportBook = new TeleportBook();
    public MiningBook miningBook = new MiningBook();
    public HealingBook healingBook = new HealingBook();
    public ImmunityBook immunityBook = new ImmunityBook();
    public TauntBook tauntBook = new TauntBook();
    public AncientSword ancientSword = new AncientSword();

    public static final class Spellbooks {
        /**
         * Debounce window for spellbook cast animations (in seconds).
         *
         * <p>This is intended to be matched by the spellbook interaction assets (e.g., a {@code ResetCooldown} at the
         * start of the {@code Axo*Book_Secondary} interaction) so spam-clicking doesn't repeatedly restart the
         * animation.</p>
         */
        public double inputDebounceSeconds = 0.6;

        /**
         * Debounce window for spellbook cast application (in seconds). Prevents rapid re-casting if input spam slips
         * through due to packet/item desync.
         */
        public double castDebounceSeconds = 0.6;

        /**
         * Delay (in seconds) before applying Secondary/Use spell effects so the gameplay action happens mid-animation.
         *
         * <p>We don't reliably detect "animation finished" from the server, so this is a fixed timing knob.</p>
         */
        public double secondaryUseDelaySeconds = 0.3;
    }

    /**
     * World generation tuning for this mod.
     */
    public static final class Worldgen {
        /**
         * Chance (0..1) to attempt Arcane Crystal placement in each eligible chunk.
         */
        public double arcaneCrystalChancePerNewChunk = 0.33;

        /**
         * Target placements to try per eligible chunk before the radius density cap is applied.
         */
        public int arcaneCrystalPlacementsPerChunk = 1;

        /**
         * Horizontal radius, in blocks, used by the Arcane Crystal density cap.
         */
        public int arcaneCrystalDensityRadiusBlocks = 64;

        /**
         * Maximum Arcane Crystal columns allowed within {@link #arcaneCrystalDensityRadiusBlocks} of a candidate.
         */
        public int arcaneCrystalMaxPlacementsPerRadius = 1;

        /**
         * Internal one-time migration marker for Arcane Crystal default-density changes.
         */
        public int arcaneCrystalDefaultsVersion = 3;

        /**
         * When true, also attempts to seed Arcane Crystals into already-generated chunks as they load.
         */
        public boolean arcaneCrystalProcessExistingChunks = false;

        /**
         * When true, trims old over-dense generated crystal clusters in already-generated chunks.
         */
        public boolean arcaneCrystalPruneLegacyClusters = true;

        /**
         * Arcane Matter ore generation tuning.
         */
        public ArcaneMatterOres arcaneMatterOres = new ArcaneMatterOres();

        public static final class ArcaneMatterOres {
            /**
             * Master toggle for Arcane Matter ore placement.
             *
             * <p>Runs during {@code ChunkPreLoadProcessEvent}. See {@link #processExistingChunks}.</p>
             */
            public boolean enabled = true;

            /**
             * When true, also processes already-generated chunks as they are pre-loaded (best effort).
             *
             * <p>This is useful for development because a world may have many chunks generated before the plugin
             * finishes loading.</p>
             */
            public boolean processExistingChunks = true;

            /**
             * Block item id to place for the stone-hosted Arcane Matter ore.
             */
            public String stoneOreBlockId = "Ore_Stone_Parent";

            /**
             * Block item id to place for the volcanic-hosted Arcane Matter ore.
             */
            public String volcanicOreBlockId = "Arcane_Matter_Volcanic";

            public Stone stone = new Stone();
            public Volcanic volcanic = new Volcanic();

            public static final class Stone {
                public boolean enabled = true;

                /**
                 * When true, treats any non-volcanic rock (block id starts with {@code Rock_}) as a valid host.
                 *
                 * <p>This is recommended if you want the ore to spawn across every zone's cave/dungeon rock palette.</p>
                 */
                public boolean matchAnyRock = true;

                /**
                 * Chance (0..1) to attempt stone-ore placement in each newly-generated chunk.
                 */
                public double chancePerNewChunk = 0.5;

                /**
                 * Target number of ore blocks to place per eligible chunk (best effort).
                 */
                public int targetPlacementsPerChunk = 12;

                /**
                 * Maximum random samples per chunk while trying to reach the placement target.
                 */
                public int maxAttemptsPerChunk = 256;

                /**
                 * When true, requires the host block to have at least one adjacent air block.
                 *
                 * <p>This biases placement toward cave/dungeon surfaces (easier to find while exploring).</p>
                 */
                public boolean requireAdjacentAir = true;

                /**
                 * Inclusive world Y range to consider for placement.
                 */
                public int minY = 1;

                /**
                 * Inclusive world Y range to consider for placement.
                 */
                public int maxY = 9999;

                /**
                 * Block ids that the stone ore is allowed to replace.
                 */
                public String[] hostBlockIds = new String[] { "Rock_Stone", "Rock_Shale", "Rock_Marble", "Rock_Quartzite" };
            }

            public static final class Volcanic {
                public boolean enabled = true;

                /**
                 * When true, treats any volcanic rock block (id starts with {@code Rock_Volcanic}) as a valid host.
                 */
                public boolean matchAnyVolcanicRock = true;

                /**
                 * Chance (0..1) to attempt volcanic-ore placement in each newly-generated chunk.
                 */
                public double chancePerNewChunk = 0.5;

                /**
                 * Target number of ore blocks to place per eligible chunk (best effort).
                 */
                public int targetPlacementsPerChunk = 12;

                /**
                 * Maximum random samples per chunk while trying to reach the placement target.
                 */
                public int maxAttemptsPerChunk = 256;

                /**
                 * When true, requires the host block to have at least one adjacent air block.
                 *
                 * <p>When false, placement can occur fully inside volcanic rock layers.</p>
                 */
                public boolean requireAdjacentAir = false;

                /**
                 * Inclusive world Y range to consider for placement.
                 */
                public int minY = 1;

                /**
                 * Inclusive world Y range to consider for placement.
                 */
                public int maxY = 9999;

                /**
                 * Block ids that the volcanic ore is allowed to replace.
                 */
                public String[] hostBlockIds = new String[] { "Rock_Volcanic" };
            }
        }
    }

    /**
     * Compatibility and debugging toggles.
     */
    public static final class Workarounds {
        /**
         * When true, disables compass updating and world map ticking to work around client crashes in some builds.
         *
         * <p>Warning: this may cause the in-game map to show "Unknown location".</p>
         */
        public boolean disableWorldMap = false;
    }

    /**
     * Cloud Block movement tuning.
     */
    public static final class CloudBlock {
        public boolean enabled = true;

        /**
         * Approximate upward/downward launch distance when entering the cloud.
         */
        public double targetHeightBlocks = 6.0;

        /**
         * Absolute cap for the resulting vertical speed.
         */
        public double maxVerticalSpeed = 32.0;

        /**
         * Minimum absolute Y velocity required before the cloud decides up/down.
         */
        public double minContactVelocity = 0.12;

        /**
         * Seconds the player must be clear of the cloud before it can launch again.
         */
        public double cooldownSeconds = 1.0;

        /**
         * Multiplier applied for each next cloud block touched in the same vertical direction.
         */
        public double chainVelocityMultiplier = 1.5;

        /**
         * Seconds before the cloud chain resets after the last successful cloud launch.
         */
        public double chainResetSeconds = 4.0;
    }

    /**
     * Orange Bounce Block upward-only launch tuning.
     */
    public static final class BounceBlock {
        public boolean enabled = true;

        /**
         * Starting approximate upward launch distance.
         */
        public double baseTargetHeightBlocks = 4.0;

        /**
         * Extra target height added for each chained bounce.
         */
        public double heightGainPerBounceBlocks = 2.0;

        /**
         * Maximum chained upward launch distance.
         */
        public double maxTargetHeightBlocks = 18.0;

        /**
         * Absolute cap for the resulting upward speed.
         */
        public double maxVerticalSpeed = 48.0;

        /**
         * Seconds the player must be clear of the block before it can bounce again.
         */
        public double cooldownSeconds = 0.2;

        /**
         * Seconds before the bounce chain resets after the last successful bounce.
         */
        public double streakResetSeconds = 8.0;
    }

    /**
     * Night-time spawning and tuning for Kudu Rune Knights.
     */
    public static final class RuneKnight {
        /**
         * Master toggle for Rune Knight systems (spawning, aggro, ranged, drops).
         */
        public boolean enabled = true;

        /**
         * NPC role asset id to spawn (JSON filename without extension).
         */
        public String roleName = "Kudu_Rune_Knight";

        /**
         * Spawn tuning for Rune Knights.
         */
        public Spawn spawn = new Spawn();

        /**
         * Despawn tuning for Rune Knights.
         */
        public Despawn despawn = new Despawn();

        /**
         * Aggro/targeting tuning for Rune Knights.
         */
        public Aggro aggro = new Aggro();

        /**
         * Projectile/ranged combat tuning for Rune Knights.
         */
        public Projectiles projectiles = new Projectiles();

        /**
         * Loot tuning for Rune Knights.
         */
        public Loot loot = new Loot();

        public static final class Spawn {
            /**
             * The maximum number of active Rune Knights per world.
             */
            public int maxActivePerWorld = 4;

            /**
             * How many Rune Knights to attempt to spawn each interval (clamped by {@link #maxActivePerWorld}).
             */
            public int spawnsPerInterval = 1;

            /**
             * Interval (in seconds) between spawn attempts while it's night.
             */
            public double intervalSeconds = 300.0;

            /**
             * Maximum total spawn attempts per interval (sampling random locations around players).
             */
            public int maxAttemptsPerInterval = 12;

            /**
             * Minimum horizontal distance (in blocks) from any player for a spawn location.
             */
            public double minDistanceFromPlayersBlocks = 16.0;

            /**
             * Minimum spawn radius (in blocks) around the chosen player anchor.
             */
            public double radiusMinBlocks = 24.0;

            /**
             * Maximum spawn radius (in blocks) around the chosen player anchor.
             */
            public double radiusMaxBlocks = 96.0;

            /**
             * When true, allows spawning in chunks that are in memory but not currently fully loaded.
             */
            public boolean allowInMemoryChunks = true;

            /**
             * Treat the world as "night" when {@code sunlightFactor <= threshold}.
             */
            public double nightSunlightThreshold = 0.25;
        }

        public static final class Despawn {
            /**
             * When true, despawns all tracked Rune Knights at daybreak.
             */
            public boolean onDay = true;

            /**
             * Lifetime (in seconds) before a spawned Rune Knight is removed. Use 0 to disable timed despawn.
             */
            public double afterSeconds = 300.0;
        }

        public static final class Aggro {
            /**
             * Max range (in blocks) for the aggro-steering system to acquire player targets.
             */
            public double radiusBlocks = 40.0;
        }

        public static final class Projectiles {
            /**
             * When true, Rune Knights fire projectiles at their marked target (plugin-driven ranged combat).
             */
            public boolean enabled = true;

            /**
             * Projectile asset id (JSON filename without extension) under {@code Server/Projectiles/**}.
             */
            public String projectileId = "RuneKnight_Bolt";

            /**
             * Cooldown (in seconds) between shots per Rune Knight.
             */
            public double cooldownSeconds = 1.25;

            /**
             * Max range (in blocks) for shooting.
             */
            public double rangeBlocks = 24.0;

            /**
             * Aim height offset (in blocks) applied to both the shooter origin and target aim point.
             */
            public double aimHeightBlocks = 1.25;
        }

        public static final class Loot {
            /**
             * Percent chance (0..100) for Rune Knights to drop {@code Kudu_Boots} on death.
             */
            public int kuduBootsDropChancePercent = 5;

            /**
             * Percent chance (0..100) for Rune Knights to drop {@code Book_Frost_Texture} (Axo's Frost Book) on death.
             */
            public int frostBookDropChancePercent = 5;
        }
    }

    /**
     * Day-time spawning and tuning for friendly Kudu Adepts.
     */
    public static final class KuduAdept {
        /**
         * Master toggle for Kudu Adept spawning and bonding systems.
         */
        public boolean enabled = true;

        /**
         * Internal one-time migration marker for Kudu Adept default spawning behavior.
         */
        public int defaultsVersion = 6;

        /**
         * NPC role asset id to spawn (JSON filename without extension).
         */
        public String roleName = "Kudu_Adept_Magician";

        /**
         * Spawn tuning for Kudu Adepts.
         */
        public Spawn spawn = new Spawn();

        /**
         * Despawn tuning for Kudu Adepts.
         */
        public Despawn despawn = new Despawn();

        public static final class Spawn {
            /**
             * The maximum number of active Kudu Adepts per world.
             */
            public int maxActivePerWorld = 500;

            /**
             * How many Kudu Adepts to attempt to spawn each interval (clamped by {@link #maxActivePerWorld}).
             */
            public int spawnsPerInterval = 1;

            /**
             * Interval (in seconds) between spawn attempts while it's day.
             */
            public double intervalSeconds = 120.0;

            /**
             * Maximum total spawn attempts per interval (sampling random locations around players).
             */
            public int maxAttemptsPerInterval = 3;

            /**
             * Approximate density cell size. By default, each 256x256 block cell around active players can own one wild adept spawn slot.
             */
            public double densityCellSizeBlocks = 256.0;

            /**
             * Chance for an eligible density cell to spawn an adept.
             */
            public int cellSpawnChancePercent = 33;

            /**
             * Minimum horizontal distance (in blocks) from any player for a spawn location.
             */
            public double minDistanceFromPlayersBlocks = 8.0;

            /**
             * Minimum horizontal radius (in blocks) around the anchor player for random spawn sampling.
             */
            public double radiusMinBlocks = 8.0;

            /**
             * Maximum horizontal radius (in blocks) around the anchor player for random spawn sampling.
             */
            public double radiusMaxBlocks = 280.0;

            /**
             * If true, allows using chunks that are in-memory but not fully loaded yet for spawn checks.
             */
            public boolean allowInMemoryChunks = true;

            /**
             * Sunlight factor threshold above which spawning is allowed (0..1). Use 0 to allow all times of day.
             */
            public double daySunlightThreshold = 0.0;
        }

        public static final class Despawn {
            /**
             * If true, despawn all tracked Kudu Adepts when it becomes night.
             */
            public boolean onNight = false;

            /**
             * Maximum lifetime (in seconds) before despawning a spawned Kudu Adept. Set to 0 to disable.
             */
            public double afterSeconds = 0.0;
        }
    }

    public static final class HordeBook {
        /**
         * Mana points consumed per successful summon cast.
         */
        public int manaCost = 25;

        /**
         * Lifetime (in seconds) of the summoned minion before it is removed.
         */
        public int minionLifetimeSeconds = 30;

        /**
         * Duration (in seconds) to override the summoned minion's attitude towards the caster as {@code FRIENDLY}.
         */
        public int ownerFriendlySeconds = 60;

        /**
         * Spawn distance (in blocks) in front of the player.
         */
        public double spawnDistanceBlocks = 3.0;
    }

    public static final class DoomBook {
        /**
         * Mana points consumed per cast.
         */
        public int manaCost = 25;

        /**
         * Delay (in seconds) before the Doom projectile is spawned so the explosion leaves slightly earlier in the cast.
         */
        public double projectileDelaySeconds = 0.24;
    }

    public static final class MorphBook {
        /**
         * Mana points consumed per cast.
         */
        public int manaCost = 25;
    }

    public static final class FrostBook {
        /**
         * Mana points consumed per cast.
         */
        public int manaCost = 20;
    }

    public static final class FlameBook {
        /**
         * Mana points consumed per cast.
         */
        public int manaCost = 20;

        /**
         * Delay (in seconds) before the flame projectile is spawned so the launch lines up with the visible cast.
         */
        public double projectileDelaySeconds = 0.2;
    }

    public static final class LightBook {
        /**
         * Internal one-time migration marker for Light Book visual defaults.
         */
        public int defaultsVersion = 4;

        /**
         * Mana points consumed per cast.
         */
        public int manaCost = 15;

        /**
         * Delay (in seconds) before the light ball is spawned so it leaves on the forward push.
         */
        public double projectileDelaySeconds = 0.16;

        /**
         * Maximum travel distance before the light ball is removed.
         */
        public double maxDistanceBlocks = 100.0;

        /**
         * Starting speed before the projectile strongly slows down.
         */
        public double initialSpeedBlocksPerSecond = 55.0;

        /**
         * Constant cruise speed after the initial slowdown.
         */
        public double cruiseSpeedBlocksPerSecond = 0.75;

        /**
         * Seconds used to ease from the initial speed into the cruise speed.
         */
        public double slowdownSeconds = 1.2;

        /**
         * Dynamic light radius/intensity attached to the in-flight projectile.
         */
        public int dynamicLightRadius = 1;
        public int dynamicLightRed = 32;
        public int dynamicLightGreen = 24;
        public int dynamicLightBlue = 16;
    }

    public static final class TeleportBook {
        /**
         * Maximum allowed teleport distance (in blocks) from the player look origin.
         */
        public int maxDistanceBlocks = 100;

        /**
         * Mana points consumed per successful teleport cast.
         */
        public int manaCost = 10;

        /**
         * Delay (in seconds) before applying the teleport so the blink lands well after the Taunt-mirrored charge cast starts settling.
         */
        public double castDelaySeconds = 0.5;
    }

    public static final class MiningBook {
        /**
         * Maximum allowed mining cast distance (in blocks) from the player look origin.
         */
        public int maxDistanceBlocks = 12;

        /**
         * Mana points consumed per successful cast (at least one block broken).
         */
        public int manaCost = 5;

        /**
         * Minimum tunnel depth (in blocks) for a released charge below the first full charge threshold.
         */
        public int minChargeBlocks = 1;

        /**
         * Additional tunnel blocks awarded for each fully charged tier.
         */
        public int blocksPerChargeTier = 2;

        /**
         * Seconds required for each full charge tier.
         */
        public double chargeTierSeconds = 1.0;

        /**
         * Maximum time (in seconds) that contributes to tunnel depth.
         */
        public double maxChargeSeconds = 5.0;

        /**
         * Maximum tunnel depth (in blocks) at full charge.
         */
        public int maxTunnelBlocks = 10;
    }

    public static final class HealingBook {
        /**
         * Amount of health restored. Use "full" to restore to max health, or an integer to restore that many points.
         */
        public FullOrInt healAmount = FullOrInt.full();

        /**
         * Mana cost per cast. Use "full" to drain the full mana bar (requires full mana), or an integer to drain that many points.
         */
        public FullOrInt manaCost = FullOrInt.of(25);

        /**
         * Delay (in seconds) before the healing projectile is spawned so the cast lines up with the hand motion.
         */
        public double projectileDelaySeconds = 0.15;
    }

    public static final class ImmunityBook {
        /**
         * Mana points consumed per cast.
         */
        public int manaCost = 15;

        /**
         * Duration (in seconds) to negate all incoming damage after casting.
         */
        public int immunitySeconds = 3;
    }

    public static final class TauntBook {
        /**
         * Mana points consumed per successful cast.
         */
        public int manaCost = 25;

        /**
         * Vertical height (in blocks) the player is launched upwards.
         */
        public int launchHeightBlocks = 10;

        /**
         * Duration (in seconds) to negate fall damage after casting.
         */
        public int fallImmunitySeconds = 6;

        /**
         * Damage dealt to nearby entities on landing.
         */
        public int slamDamage = 40;

        /**
         * Radius (in blocks) to damage nearby entities on landing.
         */
        public int slamRadiusBlocks = 7;

        /**
         * Whether to destroy the block below the player on landing (if breakable).
         */
        public boolean breakBlockBelow = true;

        /**
         * Base crater depth (in blocks) carved downward from the impacted surface.
         */
        public int groundBreakDepthBlocks = 2;

        /**
         * Extra crater depth added per stacked taunt recast.
         */
        public int groundBreakDepthPerStack = 1;

        /**
         * Legacy crater sparing knob. Current shipped taunts clear every breakable block in the footprint, so
         * compatibility config keeps this at zero.
         */
        public double groundBreakSparingChance = 0.0;
    }

    /**
     * Special ability tuning for Axo's Ancient Sword.
     */
    public static final class AncientSword {
        /**
         * Master toggle for the Ancient Sword projectile ability.
         */
        public boolean enabled = true;

        /**
         * Projectile asset id to spawn (JSON filename without extension).
         */
        public String projectileId = "Ancient_Slash";

        /**
         * Mana points consumed per projectile cast.
         */
        public int manaCost = 20;

        /**
         * Debounce/cooldown window (in seconds) between casts.
         */
        public double cooldownSeconds = 1.25;

        /**
         * Delay (in seconds) before the projectile is spawned so the secondary cast lines up with the sword animation.
         */
        public double castDelaySeconds = 0.34;
    }

    public static final class FullOrInt {
        public boolean full;
        public int value;

        public static @Nonnull FullOrInt full() {
            FullOrInt v = new FullOrInt();
            v.full = true;
            v.value = 0;
            return v;
        }

        public static @Nonnull FullOrInt of(int value) {
            FullOrInt v = new FullOrInt();
            v.full = false;
            v.value = value;
            return v;
        }

        @Override
        public String toString() {
            return full ? "full" : Integer.toString(value);
        }
    }

    /**
     * Gson adapter for {@link FullOrInt} so config values can be written as either "full" or a number.
     */
    public static final class FullOrIntAdapter extends TypeAdapter<FullOrInt> {
        @Override
        public void write(JsonWriter out, FullOrInt value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            if (value.full) {
                out.value("full");
            } else {
                out.value(value.value);
            }
        }

        @Override
        public FullOrInt read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (token == JsonToken.STRING) {
                String s = in.nextString();
                if (s != null && s.equalsIgnoreCase("full")) {
                    return FullOrInt.full();
                }
                try {
                    return FullOrInt.of(Integer.parseInt(s));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            if (token == JsonToken.NUMBER) {
                return FullOrInt.of(in.nextInt());
            }
            in.skipValue();
            return null;
        }
    }
}
