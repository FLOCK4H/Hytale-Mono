package org.example.plugin;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import javax.annotation.Nonnull;
import java.io.IOException;






public final class AxoTalesServerConfig {
    public int encounterDefaultsVersion = 3;
    public Encounters encounters = new Encounters();
    public static final class Encounters {
        public boolean grayCatEnabled = true;
        public double grayCatSpawnWeight = 8;
        public boolean tomTomEnabled = true;
        public double tomTomSpawnWeight = 2.5;

        public double tomTomIntervalSeconds = 100;
        public double grayCatIntervalSeconds = 600;
    }

    public Spellbooks spellbooks = new Spellbooks();
    public Worldgen worldgen = new Worldgen();
    public Workarounds workarounds = new Workarounds();
    public CloudBlock cloudBlock = new CloudBlock();
    public BounceBlock bounceBlock = new BounceBlock();
    public RuneKnight runeKnight = new RuneKnight();
    public Whisperion whisperion = new Whisperion();
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







        public double inputDebounceSeconds = 0.6;





        public double castDebounceSeconds = 0.6;






        public double secondaryUseDelaySeconds = 0.3;
    }




    public static final class Worldgen {
        public ArcaneGrass arcaneGrass=new ArcaneGrass();
        public static final class ArcaneGrass {
            public boolean enabled=true;
            public double chancePerNewChunk=.75;
            public int patchesPerChunk=2;
            public int radius=3;
        }



        public double arcaneCrystalChancePerNewChunk = 0.33;




        public int arcaneCrystalPlacementsPerChunk = 1;




        public int arcaneCrystalDensityRadiusBlocks = 64;




        public int arcaneCrystalMaxPlacementsPerRadius = 1;




        public int arcaneCrystalDefaultsVersion = 3;




        public boolean arcaneCrystalProcessExistingChunks = false;




        public boolean arcaneCrystalPruneLegacyClusters = true;




        public ArcaneMatterOres arcaneMatterOres = new ArcaneMatterOres();

        public static final class ArcaneMatterOres {





            public boolean enabled = true;







            public boolean processExistingChunks = true;




            public String stoneOreBlockId = "Axo_Arcane_Matter_Ore_Stone";




            public String volcanicOreBlockId = "Axo_Arcane_Matter_Ore_Volcanic";

            public Stone stone = new Stone();
            public Volcanic volcanic = new Volcanic();

            public static final class Stone {
                public boolean enabled = true;






                public boolean matchAnyRock = true;




                public double chancePerNewChunk = 0.5;




                public int targetPlacementsPerChunk = 12;




                public int maxAttemptsPerChunk = 256;






                public boolean requireAdjacentAir = true;




                public int minY = 1;




                public int maxY = 9999;




                public String[] hostBlockIds = new String[] { "Rock_Stone", "Rock_Shale", "Rock_Marble", "Rock_Quartzite" };
            }

            public static final class Volcanic {
                public boolean enabled = true;




                public boolean matchAnyVolcanicRock = true;




                public double chancePerNewChunk = 0.5;




                public int targetPlacementsPerChunk = 12;




                public int maxAttemptsPerChunk = 256;






                public boolean requireAdjacentAir = false;




                public int minY = 1;




                public int maxY = 9999;




                public String[] hostBlockIds = new String[] { "Rock_Volcanic" };
            }
        }
    }




    public static final class Workarounds {






        public boolean disableWorldMap = false;
    }




    public static final class CloudBlock {
        public boolean enabled = true;




        public double targetHeightBlocks = 6.0;




        public double maxVerticalSpeed = 32.0;




        public double minContactVelocity = 0.12;




        public double cooldownSeconds = 1.0;




        public double chainVelocityMultiplier = 1.5;




        public double chainResetSeconds = 4.0;
    }




    public static final class BounceBlock {
        public boolean enabled = true;




        public double baseTargetHeightBlocks = 4.0;




        public double heightGainPerBounceBlocks = 2.0;




        public double maxTargetHeightBlocks = 18.0;




        public double maxVerticalSpeed = 48.0;




        public double cooldownSeconds = 0.2;




        public double streakResetSeconds = 8.0;
    }


    public static final class Whisperion {
        public boolean enabled=true;
        public double spawnWeight=2;
        public int maxWildPerWorld=3;
        public double wildLifetimeSeconds=1200;
        public double intervalSeconds=330;
    }


    public static final class RuneKnight {



        public boolean enabled = true;




        public String roleName = "Cursed_Knight";




        public Spawn spawn = new Spawn();




        public Despawn despawn = new Despawn();




        public Aggro aggro = new Aggro();




        public Projectiles projectiles = new Projectiles();




        public Loot loot = new Loot();

        public static final class Spawn {



            public int maxActivePerWorld = 8;




            public int spawnsPerInterval = 1;




            public double intervalSeconds = 62.5;




            public int maxAttemptsPerInterval = 12;




            public double minDistanceFromPlayersBlocks = 16.0;




            public double radiusMinBlocks = 24.0;




            public double radiusMaxBlocks = 96.0;




            public boolean allowInMemoryChunks = true;




            public double nightSunlightThreshold = 0.25;
        }

        public static final class Despawn {



            public boolean onDay = true;




            public double afterSeconds = 600.0;
        }

        public static final class Aggro {



            public double radiusBlocks = 40.0;
        }

        public static final class Projectiles {



            public boolean enabled = true;




            public String projectileId = "RuneKnight_Bolt";




            public double cooldownSeconds = 1.25;




            public double rangeBlocks = 24.0;




            public double aimHeightBlocks = 1.25;
        }

        public static final class Loot {



            public int frostBookDropChancePercent = 5;
        }
    }




    public static final class KuduAdept {



        public boolean enabled = true;




        public int defaultsVersion = 6;




        public String roleName = "Kudu_Adept_Magician";




        public Spawn spawn = new Spawn();




        public Despawn despawn = new Despawn();

        public static final class Spawn {



            public int maxActivePerWorld = 500;




            public int spawnsPerInterval = 1;




            public double intervalSeconds = 50.0;




            public int maxAttemptsPerInterval = 12;




            public double densityCellSizeBlocks = 192.0;




            public int cellSpawnChancePercent = 50;




            public double minDistanceFromPlayersBlocks = 16.0;




            public double radiusMinBlocks = 24.0;




            public double radiusMaxBlocks = 112.0;




            public boolean allowInMemoryChunks = true;




            public double daySunlightThreshold = 0.0;
        }

        public static final class Despawn {



            public boolean onNight = false;




            public double afterSeconds = 0.0;
        }
    }

    public static final class HordeBook {



        public int manaCost = 25;




        public int minionLifetimeSeconds = 30;




        public int ownerFriendlySeconds = 60;




        public double spawnDistanceBlocks = 3.0;
    }

    public static final class DoomBook {



        public int manaCost = 25;




        public double projectileDelaySeconds = 0.24;
    }

    public static final class MorphBook {



        public int manaCost = 25;
    }

    public static final class FrostBook {



        public int manaCost = 20;
    }

    public static final class FlameBook {



        public int manaCost = 20;




        public double projectileDelaySeconds = 0.2;
    }

    public static final class LightBook {



        public int defaultsVersion = 4;




        public int manaCost = 15;




        public double projectileDelaySeconds = 0.16;




        public double maxDistanceBlocks = 100.0;




        public double initialSpeedBlocksPerSecond = 55.0;




        public double cruiseSpeedBlocksPerSecond = 0.75;




        public double slowdownSeconds = 1.2;




        public int dynamicLightRadius = 1;
        public int dynamicLightRed = 32;
        public int dynamicLightGreen = 24;
        public int dynamicLightBlue = 16;
    }

    public static final class TeleportBook {



        public int maxDistanceBlocks = 100;




        public int manaCost = 10;




        public double castDelaySeconds = 0.5;
    }

    public static final class MiningBook {



        public int maxDistanceBlocks = 12;




        public int manaCost = 5;




        public int minChargeBlocks = 1;




        public int blocksPerChargeTier = 2;




        public double chargeTierSeconds = 1.0;




        public double maxChargeSeconds = 5.0;




        public int maxTunnelBlocks = 10;
    }

    public static final class HealingBook {



        public FullOrInt healAmount = FullOrInt.full();




        public FullOrInt manaCost = FullOrInt.of(25);




        public double projectileDelaySeconds = 0.15;
    }

    public static final class ImmunityBook {



        public int manaCost = 15;




        public int immunitySeconds = 3;
    }

    public static final class TauntBook {



        public int manaCost = 25;




        public int launchHeightBlocks = 10;




        public int fallImmunitySeconds = 6;




        public int slamDamage = 40;




        public int slamRadiusBlocks = 7;




        public boolean breakBlockBelow = true;




        public int groundBreakDepthBlocks = 2;




        public int groundBreakDepthPerStack = 1;





        public double groundBreakSparingChance = 0.0;
    }




    public static final class AncientSword {



        public boolean enabled = true;




        public String projectileId = "Ancient_Slash";




        public int manaCost = 20;




        public double cooldownSeconds = 1.25;




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
