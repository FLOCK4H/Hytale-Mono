package org.example.plugin;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.DisabledWorldMapProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;

import javax.annotation.Nonnull;





public class AxoTales extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private PluginErrorReporter errors;
    private PluginDebugReporter debug;
    private AxoTalesServerConfig serverConfig;
    private TauntBookEffectState tauntBookEffectState;
    private ImmunityBookEffectState immunityBookEffectState;
    private HordeBookSummonState hordeBookSummonState;
    private MorphBookModelState morphBookModelState;
    private SarsBootsPassiveEffect sarsBootsPassiveEffect;
    private SarsBootsFallDamageImmunitySystem sarsBootsFallDamageImmunitySystem;
    private ArmorManaMaxBonusEffect armorManaMaxBonusEffect;
    private ArmorManaRegenerationSystem armorManaRegenerationSystem;
    private StarterBookGrantSystem starterBookGrantSystem;
    private HordeBookMinionCleanupSystem hordeBookMinionCleanupSystem;
    private HordeBookFriendlyFireSystem hordeBookFriendlyFireSystem;
    private HordeBookRetaliationTargetingSystem hordeBookRetaliationTargetingSystem;
    private HordeBookMinionAggroSystem hordeBookMinionAggroSystem;
    private TauntBookSlamQueue tauntBookSlamQueue;
    private TauntBookLandingSystem tauntBookLandingSystem;
    private TauntBookSlamSystem tauntBookSlamSystem;
    private TauntBookSlamAoEDamageSystem tauntBookSlamAoEDamageSystem;
    private ImmunityBookDamageImmunitySystem immunityBookDamageImmunitySystem;
    private SpellbookInputInterceptor spellbookInputInterceptor;
    private SpellbookPouchService spellbookPouches;
    private SkillTreeSystem skillTrees;
    private SuperskillPopulation superskillPopulation;
    private RuneWorldgen runeWorldgen;
    private ArcaneGrassWorldgen arcaneGrassWorldgen;
    private CustomPlaceholderBlockWorldgen customPlaceholderBlockWorldgen;
    private ArcaneMatterOreWorldgen arcaneMatterOreWorldgen;
    private PotionSplashEffectSystem potionSplashEffectSystem;
    private StrengthPotionDamageMultiplierSystem strengthPotionDamageMultiplierSystem;
    private InvisibilityPotionEffectDebugSystem invisibilityPotionEffectDebugSystem;
    private InvisibilityCloakSystem invisibilityCloakSystem;
    private InvisibilityArmorHiderSystem invisibilityArmorHiderSystem;
    private InvisibilityHiddenPlayersSystem invisibilityHiddenPlayersSystem;
    private MovementBuffSystem movementBuffSystem;
    private SarsWarfistsInputInterceptor sarsWarfistsInputInterceptor;
    private SarsWarfistsProjectileHitSystem sarsWarfistsProjectileHitSystem;
    private AncientGearSystem ancientGearSystem;
    private AncientCombatSystem ancientCombatSystem;
    private ScoutGearSystem scoutGearSystem;
    private VitaeExtractorSystem vitaeExtractorSystem;
    private BloodFurnaceSystem bloodFurnaceSystem;
    private SoulSystem soulSystem;
    private CloudBlockVelocitySystem cloudBlockVelocitySystem;
    private BounceBlockVelocitySystem bounceBlockVelocitySystem;
    private FrostBookImpactTracker frostBookImpactTracker;
    private FrostBookProjectileHitSystem frostBookProjectileHitSystem;
    private FrostBookBlockImpactSystem frostBookBlockImpactSystem;
    private FlameBookImpactTracker flameBookImpactTracker;
    private FlameBookProjectileHitSystem flameBookProjectileHitSystem;
    private FlameBookBlockImpactSystem flameBookBlockImpactSystem;
    private LightBookProjectileState lightBookProjectileState;
    private LightBookProjectileSystem lightBookProjectileSystem;
    private LightBookProjectileHitSystem lightBookProjectileHitSystem;
    private MorphBookProjectileHitSystem morphBookProjectileHitSystem;
    private HealingBookProjectileHitSystem healingBookProjectileHitSystem;
    private DoomBookProjectileHitSystem doomBookProjectileHitSystem;
    private RuneKnightSpawnState runeKnightSpawnState;
    private RuneKnightSpawnerSystem runeKnightSpawnerSystem;
    private RuneKnightAggroSystem runeKnightAggroSystem;
    private RuneKnightProjectileSystem runeKnightProjectileSystem;
    private RuneKnightLootSystem runeKnightLootSystem;
    private KuduAdeptSpawnState kuduAdeptSpawnState;
    private KuduAdeptSpawnerSystem kuduAdeptSpawnerSystem;
    private KuduAdeptBondState kuduAdeptBondState;
    private ComponentType<EntityStore, KuduAdeptBondPersistedComponent> kuduAdeptBondPersistedComponentType;
    private KuduAdeptBondSystem kuduAdeptBondSystem;
    private KuduAdeptCrystalDropOwnerSystem kuduAdeptCrystalDropOwnerSystem;
    private KuduAdeptMasterTargetingSystem kuduAdeptMasterTargetingSystem;
    private KuduAdeptNoPlayerDamageSystem kuduAdeptNoPlayerDamageSystem;
    private KuduAdeptProjectileDamageSystem kuduAdeptProjectileDamageSystem;
    private KuduAdeptNoMeleeDamageSystem kuduAdeptNoMeleeDamageSystem;

    public AxoTales(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        this.errors = new PluginErrorReporter(this);
        this.debug = new PluginDebugReporter(this);
        this.debug.trace(null, "Spellbook debug logging enabled. File: " + this.debug.getLogFile());
        @SuppressWarnings("unchecked")
        Class<LoadedAssetsEvent<String,BlockType,?>> loadedBlockTypesEventClass=
            (Class<LoadedAssetsEvent<String,BlockType,?>>)(Class<?>)LoadedAssetsEvent.class;

        try {
            AxoTalesServerConfigStore configStore = new AxoTalesServerConfigStore(this.getDataDirectory(), errors, debug);
            this.serverConfig = configStore.loadOrCreateDefault();
            this.debug.trace(
                null,
                "Loaded server config: file=" + configStore.getConfigPath()
                    + " teleportBook.maxDistanceBlocks=" + serverConfig.teleportBook.maxDistanceBlocks
                    + " teleportBook.manaCost=" + serverConfig.teleportBook.manaCost
                    + " teleportBook.castDelaySeconds=" + serverConfig.teleportBook.castDelaySeconds
                    + " miningBook.maxDistanceBlocks=" + serverConfig.miningBook.maxDistanceBlocks
                    + " miningBook.manaCost=" + serverConfig.miningBook.manaCost
                    + " miningBook.minChargeBlocks=" + serverConfig.miningBook.minChargeBlocks
                    + " miningBook.blocksPerChargeTier=" + serverConfig.miningBook.blocksPerChargeTier
                    + " miningBook.chargeTierSeconds=" + serverConfig.miningBook.chargeTierSeconds
                    + " miningBook.maxChargeSeconds=" + serverConfig.miningBook.maxChargeSeconds
                    + " miningBook.maxTunnelBlocks=" + serverConfig.miningBook.maxTunnelBlocks
                    + " worldgen.arcaneCrystalChancePerNewChunk=" + (serverConfig.worldgen != null ? serverConfig.worldgen.arcaneCrystalChancePerNewChunk : null)
                    + " worldgen.arcaneCrystalPlacementsPerChunk=" + (serverConfig.worldgen != null ? serverConfig.worldgen.arcaneCrystalPlacementsPerChunk : null)
                    + " worldgen.arcaneCrystalDensityRadiusBlocks=" + (serverConfig.worldgen != null ? serverConfig.worldgen.arcaneCrystalDensityRadiusBlocks : null)
                    + " worldgen.arcaneCrystalMaxPlacementsPerRadius=" + (serverConfig.worldgen != null ? serverConfig.worldgen.arcaneCrystalMaxPlacementsPerRadius : null)
                    + " worldgen.arcaneCrystalProcessExistingChunks=" + (serverConfig.worldgen != null ? serverConfig.worldgen.arcaneCrystalProcessExistingChunks : null)
                    + " worldgen.arcaneMatterOres.enabled=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.enabled)
                    + " worldgen.arcaneMatterOres.processExistingChunks=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null ? serverConfig.worldgen.arcaneMatterOres.processExistingChunks : null)
                    + " worldgen.arcaneMatterOres.stone.targetPlacementsPerChunk=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.stone != null ? serverConfig.worldgen.arcaneMatterOres.stone.targetPlacementsPerChunk : null)
                    + " worldgen.arcaneMatterOres.stone.maxAttemptsPerChunk=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.stone != null ? serverConfig.worldgen.arcaneMatterOres.stone.maxAttemptsPerChunk : null)
                    + " worldgen.arcaneMatterOres.stone.matchAnyRock=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.stone != null ? serverConfig.worldgen.arcaneMatterOres.stone.matchAnyRock : null)
                    + " worldgen.arcaneMatterOres.stone.requireAdjacentAir=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.stone != null ? serverConfig.worldgen.arcaneMatterOres.stone.requireAdjacentAir : null)
                    + " worldgen.arcaneMatterOres.volcanic.targetPlacementsPerChunk=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.volcanic != null ? serverConfig.worldgen.arcaneMatterOres.volcanic.targetPlacementsPerChunk : null)
                    + " worldgen.arcaneMatterOres.volcanic.maxAttemptsPerChunk=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.volcanic != null ? serverConfig.worldgen.arcaneMatterOres.volcanic.maxAttemptsPerChunk : null)
                    + " worldgen.arcaneMatterOres.volcanic.matchAnyVolcanicRock=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.volcanic != null ? serverConfig.worldgen.arcaneMatterOres.volcanic.matchAnyVolcanicRock : null)
                    + " worldgen.arcaneMatterOres.volcanic.requireAdjacentAir=" + (serverConfig.worldgen != null && serverConfig.worldgen.arcaneMatterOres != null && serverConfig.worldgen.arcaneMatterOres.volcanic != null ? serverConfig.worldgen.arcaneMatterOres.volcanic.requireAdjacentAir : null)
                    + " runeKnight.enabled=" + (serverConfig.runeKnight != null && serverConfig.runeKnight.enabled)
                    + " runeKnight.roleName=" + (serverConfig.runeKnight != null ? serverConfig.runeKnight.roleName : null)
                    + " runeKnight.spawn.intervalSeconds=" + (serverConfig.runeKnight != null && serverConfig.runeKnight.spawn != null ? serverConfig.runeKnight.spawn.intervalSeconds : null)
                    + " runeKnight.aggro.radiusBlocks=" + (serverConfig.runeKnight != null && serverConfig.runeKnight.aggro != null ? serverConfig.runeKnight.aggro.radiusBlocks : null)
                    + " runeKnight.projectiles.enabled=" + (serverConfig.runeKnight != null && serverConfig.runeKnight.projectiles != null && serverConfig.runeKnight.projectiles.enabled)
                    + " runeKnight.projectiles.projectileId=" + (serverConfig.runeKnight != null && serverConfig.runeKnight.projectiles != null ? serverConfig.runeKnight.projectiles.projectileId : null)
                    + " runeKnight.projectiles.cooldownSeconds=" + (serverConfig.runeKnight != null && serverConfig.runeKnight.projectiles != null ? serverConfig.runeKnight.projectiles.cooldownSeconds : null)
                    + " runeKnight.projectiles.rangeBlocks=" + (serverConfig.runeKnight != null && serverConfig.runeKnight.projectiles != null ? serverConfig.runeKnight.projectiles.rangeBlocks : null)
                    + " runeKnight.loot.frostBookDropChancePercent=" + (serverConfig.runeKnight != null && serverConfig.runeKnight.loot != null ? serverConfig.runeKnight.loot.frostBookDropChancePercent : null)
                    + " kuduAdept.enabled=" + (serverConfig.kuduAdept != null && serverConfig.kuduAdept.enabled)
                    + " kuduAdept.roleName=" + (serverConfig.kuduAdept != null ? serverConfig.kuduAdept.roleName : null)
                    + " kuduAdept.spawn.intervalSeconds=" + (serverConfig.kuduAdept != null && serverConfig.kuduAdept.spawn != null ? serverConfig.kuduAdept.spawn.intervalSeconds : null)
                    + " hordeBook.manaCost=" + serverConfig.hordeBook.manaCost
                    + " hordeBook.minionLifetimeSeconds=" + serverConfig.hordeBook.minionLifetimeSeconds
                    + " hordeBook.ownerFriendlySeconds=" + serverConfig.hordeBook.ownerFriendlySeconds
                    + " hordeBook.spawnDistanceBlocks=" + serverConfig.hordeBook.spawnDistanceBlocks
                    + " doomBook.manaCost=" + serverConfig.doomBook.manaCost
                    + " doomBook.projectileDelaySeconds=" + serverConfig.doomBook.projectileDelaySeconds
                    + " morphBook.manaCost=" + serverConfig.morphBook.manaCost
                    + " frostBook.manaCost=" + serverConfig.frostBook.manaCost
                    + " flameBook.manaCost=" + (serverConfig.flameBook != null ? serverConfig.flameBook.manaCost : null)
                    + " flameBook.projectileDelaySeconds=" + (serverConfig.flameBook != null ? serverConfig.flameBook.projectileDelaySeconds : null)
                    + " lightBook.manaCost=" + (serverConfig.lightBook != null ? serverConfig.lightBook.manaCost : null)
                    + " lightBook.projectileDelaySeconds=" + (serverConfig.lightBook != null ? serverConfig.lightBook.projectileDelaySeconds : null)
                    + " lightBook.maxDistanceBlocks=" + (serverConfig.lightBook != null ? serverConfig.lightBook.maxDistanceBlocks : null)
                    + " lightBook.initialSpeedBlocksPerSecond=" + (serverConfig.lightBook != null ? serverConfig.lightBook.initialSpeedBlocksPerSecond : null)
                    + " lightBook.cruiseSpeedBlocksPerSecond=" + (serverConfig.lightBook != null ? serverConfig.lightBook.cruiseSpeedBlocksPerSecond : null)
                    + " lightBook.slowdownSeconds=" + (serverConfig.lightBook != null ? serverConfig.lightBook.slowdownSeconds : null)
                    + " healingBook.healAmount=" + serverConfig.healingBook.healAmount
                    + " healingBook.manaCost=" + serverConfig.healingBook.manaCost
                    + " healingBook.projectileDelaySeconds=" + serverConfig.healingBook.projectileDelaySeconds
                    + " immunityBook.manaCost=" + serverConfig.immunityBook.manaCost
                    + " immunityBook.immunitySeconds=" + serverConfig.immunityBook.immunitySeconds
                    + " tauntBook.manaCost=" + serverConfig.tauntBook.manaCost
                    + " tauntBook.launchHeightBlocks=" + serverConfig.tauntBook.launchHeightBlocks
                    + " tauntBook.fallImmunitySeconds=" + serverConfig.tauntBook.fallImmunitySeconds
                    + " tauntBook.slamDamage=" + serverConfig.tauntBook.slamDamage
                    + " tauntBook.slamRadiusBlocks=" + serverConfig.tauntBook.slamRadiusBlocks
                    + " tauntBook.breakBlockBelow=" + serverConfig.tauntBook.breakBlockBelow
                    + " spellbooks.inputDebounceSeconds=" + (serverConfig.spellbooks != null ? serverConfig.spellbooks.inputDebounceSeconds : null)
                    + " spellbooks.castDebounceSeconds=" + (serverConfig.spellbooks != null ? serverConfig.spellbooks.castDebounceSeconds : null)
                    + " spellbooks.secondaryUseDelaySeconds=" + (serverConfig.spellbooks != null ? serverConfig.spellbooks.secondaryUseDelaySeconds : null)
            );
        } catch (Throwable t) {
            errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to load server config; using defaults.", t);
            this.serverConfig = new AxoTalesServerConfig();
        }

        this.tauntBookEffectState = new TauntBookEffectState();
        this.immunityBookEffectState = new ImmunityBookEffectState();
        this.hordeBookSummonState = new HordeBookSummonState();
        this.morphBookModelState = new MorphBookModelState();
        EntityStore.REGISTRY.registerSystem(morphBookModelState.observer());
        this.runeKnightSpawnState = new RuneKnightSpawnState();
        this.kuduAdeptSpawnState = new KuduAdeptSpawnState();
        this.kuduAdeptBondState = new KuduAdeptBondState();
        this.potionSplashEffectSystem = new PotionSplashEffectSystem(errors, debug);
        this.strengthPotionDamageMultiplierSystem = new StrengthPotionDamageMultiplierSystem(errors, debug);
        this.invisibilityPotionEffectDebugSystem = new InvisibilityPotionEffectDebugSystem(errors, debug);
        this.invisibilityCloakSystem = new InvisibilityCloakSystem(errors, debug);
        this.invisibilityArmorHiderSystem = new InvisibilityArmorHiderSystem(errors, debug);
        this.invisibilityHiddenPlayersSystem = new InvisibilityHiddenPlayersSystem(errors, debug);
        this.movementBuffSystem = new MovementBuffSystem(errors, debug);
        this.sarsWarfistsInputInterceptor = new SarsWarfistsInputInterceptor(errors, debug);
        this.sarsWarfistsProjectileHitSystem = new SarsWarfistsProjectileHitSystem(errors, debug);
        this.cloudBlockVelocitySystem = new CloudBlockVelocitySystem(errors, debug, serverConfig);
        this.bounceBlockVelocitySystem = new BounceBlockVelocitySystem(errors, debug, serverConfig);
        this.frostBookImpactTracker = new FrostBookImpactTracker();
        this.frostBookProjectileHitSystem = new FrostBookProjectileHitSystem(errors, debug, frostBookImpactTracker);
        this.frostBookBlockImpactSystem = new FrostBookBlockImpactSystem(errors, debug, frostBookImpactTracker);
        this.flameBookImpactTracker = new FlameBookImpactTracker();
        this.flameBookProjectileHitSystem = new FlameBookProjectileHitSystem(errors, debug, flameBookImpactTracker);
        this.flameBookBlockImpactSystem = new FlameBookBlockImpactSystem(errors, debug, flameBookImpactTracker);
        this.lightBookProjectileState = new LightBookProjectileState();
        this.lightBookProjectileSystem = new LightBookProjectileSystem(errors, debug, serverConfig, lightBookProjectileState);
        this.lightBookProjectileHitSystem = new LightBookProjectileHitSystem(errors, debug, lightBookProjectileState);
        this.morphBookProjectileHitSystem = new MorphBookProjectileHitSystem(errors, debug, morphBookModelState);
        this.healingBookProjectileHitSystem = new HealingBookProjectileHitSystem(errors, debug);
        this.doomBookProjectileHitSystem = new DoomBookProjectileHitSystem(errors, debug);
        this.runeKnightSpawnerSystem = new RuneKnightSpawnerSystem(errors, debug, serverConfig, runeKnightSpawnState);
        this.runeKnightAggroSystem = new RuneKnightAggroSystem(errors, debug, serverConfig, runeKnightSpawnState);
        this.runeKnightProjectileSystem = new RuneKnightProjectileSystem(errors, debug, serverConfig, runeKnightSpawnState);
        this.runeKnightLootSystem = new RuneKnightLootSystem(errors, debug, serverConfig, runeKnightSpawnState);
        var robeInventoryStateType=this.getEntityStoreRegistry().registerComponent(
            ArcaneRobeInventoryState.class,"AxoTales_ArcaneRobeInventory",ArcaneRobeInventoryState.CODEC);
        EntityStore.REGISTRY.registerSystem(new ArcaneRobeSystem(errors,debug));
        EntityStore.REGISTRY.registerSystem(new ArmorCraftingSpeed());
        EntityStore.REGISTRY.registerSystem(new SkillInvisibility(debug));
        var backpacks=new ArcaneBackpackSystem(robeInventoryStateType,debug,errors);
        EntityStore.REGISTRY.registerSystem(backpacks);
        var astralBags=new AstralBagService(this.getDataDirectory().resolve("AstralSatchels"),debug,errors);
        EntityStore.REGISTRY.registerSystem(astralBags);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(AstralBagInteraction.TYPE,AstralBagInteraction.class,AstralBagInteraction.codec(astralBags::onOpen));
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,event->astralBags.disconnect(event.getPlayerRef()));
        var backpackCodec=BackpackUpgradeInteraction.codec(backpacks::onUpgrade);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(BackpackUpgradeInteraction.TYPE,BackpackUpgradeInteraction.class,backpackCodec);
        this.getEventRegistry().registerGlobal(EventPriority.LAST,loadedBlockTypesEventClass,
            event->BackpackUpgradeInteraction.patch(event,backpackCodec,debug));
        EntityStore.REGISTRY.registerSystem(new ArcaneRobeDamageSystem(debug));
        var ancientFuel=this.getEntityStoreRegistry().registerComponent(AncientFlightState.class,"AxoTales_AncientFlight",AncientFlightState.CODEC);
        var ancientProjectile=this.getEntityStoreRegistry().registerComponent(AncientProjectileState.class,AncientProjectileState::new);
        this.ancientGearSystem=new AncientGearSystem(ancientFuel,debug,errors);
        this.ancientCombatSystem=new AncientCombatSystem(ancientProjectile,debug,errors);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(AncientGearInteraction.TYPE,AncientGearInteraction.class,AncientGearInteraction.codec(ancientCombatSystem::handle));
        EntityStore.REGISTRY.registerSystem(ancientGearSystem);
        EntityStore.REGISTRY.registerSystem(new AncientDamageSystem(ancientCombatSystem));
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,event->ancientGearSystem.disconnect(event.getPlayerRef()));
        this.kuduAdeptBondPersistedComponentType = this.getEntityStoreRegistry().registerComponent(
            KuduAdeptBondPersistedComponent.class,
            "AxoTales_KuduAdeptBondPersistedComponent",
            KuduAdeptBondPersistedComponent.CODEC
        );
        this.kuduAdeptSpawnerSystem = new KuduAdeptSpawnerSystem(errors, debug, serverConfig, kuduAdeptSpawnState, kuduAdeptBondState);
        this.kuduAdeptBondSystem = new KuduAdeptBondSystem(
            errors,
            debug,
            serverConfig,
            kuduAdeptBondState,
            kuduAdeptBondPersistedComponentType
        );
        this.kuduAdeptCrystalDropOwnerSystem = new KuduAdeptCrystalDropOwnerSystem(errors, debug, serverConfig, kuduAdeptBondState);
        this.kuduAdeptMasterTargetingSystem = new KuduAdeptMasterTargetingSystem(errors, debug, serverConfig, kuduAdeptBondState);
        this.kuduAdeptNoPlayerDamageSystem = new KuduAdeptNoPlayerDamageSystem(errors, debug, serverConfig);
        this.kuduAdeptProjectileDamageSystem = new KuduAdeptProjectileDamageSystem(errors, debug, serverConfig, kuduAdeptBondState);
        this.kuduAdeptNoMeleeDamageSystem = new KuduAdeptNoMeleeDamageSystem(errors, debug, serverConfig);
        SkillProgression.TYPE=this.getEntityStoreRegistry().registerComponent(SkillProgression.class,"AxoTales_Skills",SkillProgression.CODEC);
        var skillGrowth=this.getChunkStoreRegistry().registerComponent(SkillTreeGrowth.class,"AxoTales_SkillGrowth",SkillTreeGrowth.CODEC);
        var vineState=this.getChunkStoreRegistry().registerComponent(PoisonousVine.class,"Axo_PoisonousVineGrowth",PoisonousVine.CODEC);
        PoisonousVine.DeadRoots.TYPE=this.getChunkStoreRegistry().registerResource(PoisonousVine.DeadRoots.class,"Axo_PoisonousVineDeadRoots",PoisonousVine.DeadRoots.CODEC);
        var vines=new PoisonousVine.GrowthSystem(vineState,errors);
        this.getChunkStoreRegistry().registerSystem(vines);
        this.getChunkStoreRegistry().registerSystem(vines.lifecycle());
        var runeSeed=this.getChunkStoreRegistry().registerComponent(RuneWorldgen.Seeded.class,"AxoTales_RuneSeeded",RuneWorldgen.Seeded.CODEC);
        skillTrees=new SkillTreeSystem(debug,errors);runeWorldgen=new RuneWorldgen(runeSeed,errors);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(SkillTreeInteraction.TYPE,SkillTreeInteraction.class,SkillTreeInteraction.codec(skillTrees::handle))
            .register(SkillMeleeSelector.Interaction.TYPE,SkillMeleeSelector.Interaction.class,SkillMeleeSelector.Interaction.CODEC);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.SelectorType.CODEC)
            .register(SkillMeleeSelector.TYPE,SkillMeleeSelector.class,SkillMeleeSelector.CODEC)
            .register(SkillMeleeSelector.Stab.TYPE,SkillMeleeSelector.Stab.class,SkillMeleeSelector.Stab.CODEC);
        EntityStore.REGISTRY.registerSystem(skillTrees);
        var itemMigration=new AxoItemMigration(debug,errors);EntityStore.REGISTRY.registerSystem(itemMigration);EntityStore.REGISTRY.registerSystem(itemMigration.lifecycle());
        com.hypixel.hytale.server.core.universe.world.storage.ChunkStore.REGISTRY.registerSystem(new AxoItemMigration.Containers(errors));
        superskillPopulation=new SuperskillPopulation(debug,errors);EntityStore.REGISTRY.registerSystem(superskillPopulation);
        this.getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,event->superskillPopulation.releaseWorld(event.getWorld()));
        EntityStore.REGISTRY.registerSystem(new SkillDamageSystem());
        EntityStore.REGISTRY.registerSystem(new SkillDamageSystem.Lazarus());
        EntityStore.REGISTRY.registerSystem(new RuneWorldgen.Collect(skillTrees));
        com.hypixel.hytale.server.core.universe.world.storage.ChunkStore.REGISTRY.registerSystem(new SkillTreeGrowth.GrowthSystem(skillGrowth,errors));
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,event->skillTrees.disconnect(event.getPlayerRef()));
        this.customPlaceholderBlockWorldgen = new CustomPlaceholderBlockWorldgen(errors, debug, serverConfig);
        this.arcaneMatterOreWorldgen = new ArcaneMatterOreWorldgen(errors, debug, serverConfig);
        this.arcaneGrassWorldgen = new ArcaneGrassWorldgen(errors, debug, serverConfig);
        this.spellbookPouches = new SpellbookPouchService(debug, serverConfig);
        EntityStore.REGISTRY.registerSystem(new SpellbookPouchInventorySystem(spellbookPouches, errors));
        EntityStore.REGISTRY.registerSystem(new SpellbookPouchPickupSystem(spellbookPouches, errors));
        this.spellbookInputInterceptor = new SpellbookInputInterceptor(errors, debug, spellbookPouches, serverConfig, tauntBookEffectState, immunityBookEffectState, hordeBookSummonState, morphBookModelState, lightBookProjectileState);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(NativeSpellInputInteraction.TYPE, NativeSpellInputInteraction.class,
                NativeSpellInputInteraction.codec(spellbookInputInterceptor::onNativeInteraction));
        this.debug.trace(null, "Spellbook input enabled (native AxoTales_Input server interactions; Quiche/Netty independent).");
        var whisperionState=this.getEntityStoreRegistry().registerComponent(WhisperionState.class,"AxoTales_WhisperionState",WhisperionState.CODEC);
        var whisperionRide=this.getEntityStoreRegistry().registerComponent(WhisperionRide.class,"AxoTales_WhisperionRide",WhisperionRide.CODEC);
        var whisperionPets=new WhisperionPets(this.getDataDirectory(),errors);
        EntityStore.REGISTRY.registerSystem(whisperionPets.lifecycle(whisperionState));
        var whisperion=new WhisperionSystem(whisperionState,whisperionRide,whisperionPets,debug,errors);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(WhisperionInteraction.TYPE,WhisperionInteraction.class,WhisperionInteraction.codec(whisperion::onInteraction));
        EntityStore.REGISTRY.registerSystem(whisperion);
        var nativeEncounters = new NativeEncounterSpawns(serverConfig);
        @SuppressWarnings("unchecked")
        Class<LoadedAssetsEvent<String, com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn, ?>> loadedSpawns =
            (Class<LoadedAssetsEvent<String, com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn, ?>>) (Class<?>) LoadedAssetsEvent.class;
        this.getEventRegistry().register(EventPriority.FIRST, loadedSpawns,
            com.hypixel.hytale.server.spawning.assets.spawns.config.WorldNPCSpawn.class, event -> nativeEncounters.loaded(event));
        debug.traceFileOnly(null,"Whisperion event=registered nativeInteractions=true kelp=Plant_Crop_Mana3 tameChance=100 spawning=nativeEcology savedOwnerAndSaddle=true");
        var soulState=this.getEntityStoreRegistry().registerComponent(SoulState.class,"AxoTales_SoulState",SoulState.CODEC);
        var fossorWork=this.getEntityStoreRegistry().registerComponent(FossorWork.class,"AxoTales_FossorWork",FossorWork.CODEC);
        var souls=new SoulSystem(soulState,fossorWork,debug,errors,this.getDataDirectory());
        soulSystem=souls;
        this.getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,event->souls.releaseWorld(event.getWorld()));
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(SoulInteraction.TYPE,SoulInteraction.class,SoulInteraction.codec(souls::onInteraction));
        EntityStore.REGISTRY.registerSystem(souls);
        EntityStore.REGISTRY.registerSystem(souls.slotLifecycle());
        EntityStore.REGISTRY.registerSystem(new SoulDamageSystem(soulState,fossorWork,souls::onMeleePickup,debug));
        EntityStore.REGISTRY.registerSystem(new SoulTargetingSystem(soulState,debug));
        debug.traceFileOnly(null,"Soul event=registered kinds=Villa,Fossor,Cruor nativeInteractions=true savedOwnerCargoProgress=true immortal=true");
        var bloodTaken=this.getEntityStoreRegistry().registerComponent(BloodTaken.class,"AxoTales_BloodTaken",BloodTaken.CODEC);
        var bloodPlayer=this.getEntityStoreRegistry().registerComponent(BloodPlayerState.class,"AxoTales_BloodPlayer",BloodPlayerState.CODEC);
        var bloodbound=this.getEntityStoreRegistry().registerComponent(BloodboundState.class,"AxoTales_Bloodbound",BloodboundState.CODEC);
        var bloodMagic=new BloodMagicSystem(bloodTaken,bloodPlayer,bloodbound,spellbookPouches,debug,errors);
        var leech=new LeechSystem(spellbookPouches,debug,errors);
        spellbookInputInterceptor.setLeech(leech);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(NativeLeechHoldInteraction.TYPE,NativeLeechHoldInteraction.class,NativeLeechHoldInteraction.codec(leech::input));
        EntityStore.REGISTRY.registerSystem(leech);
        EntityStore.REGISTRY.registerSystem(leech.lifecycle());
        spellbookInputInterceptor.setBloodMagic(bloodMagic);
        EntityStore.REGISTRY.registerSystem(bloodMagic);
        var legendaryStorms = new LegendaryStormSystem(spellbookPouches, debug, errors);
        spellbookInputInterceptor.setLegendaryStorms(legendaryStorms);
        EntityStore.REGISTRY.registerSystem(legendaryStorms);
        var cloak=new InvisibilityConcealment(debug);
        EntityStore.REGISTRY.registerSystem(cloak.provider());
        EntityStore.REGISTRY.registerSystem(cloak);
        this.vitaeExtractorSystem=new VitaeExtractorSystem(debug,errors);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(VitaeExtractorInteraction.TYPE,VitaeExtractorInteraction.class,VitaeExtractorInteraction.codec(vitaeExtractorSystem::open));
        EntityStore.REGISTRY.registerSystem(vitaeExtractorSystem);
        var frozenStorage=new FrozenStorage(errors);
        this.getChunkStoreRegistry().registerSystem(frozenStorage);
        this.getChunkStoreRegistry().registerSystem(frozenStorage.lifecycle());
        this.getChunkStoreRegistry().registerSystem(frozenStorage.changes());
        var frozenWindows=new FrozenStorageWindows(errors);
        EntityStore.REGISTRY.registerSystem(frozenWindows);
        EntityStore.REGISTRY.registerSystem(frozenWindows.lifecycle());
        var furnaceState=this.getChunkStoreRegistry().registerComponent(BloodFurnaceState.class,"AxoTales_BloodFurnace",BloodFurnaceState.CODEC);
        this.bloodFurnaceSystem=new BloodFurnaceSystem(furnaceState,debug,errors);
        var arcanePickaxe=new ArcanePickaxe(debug,errors);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(BloodIndustryInteraction.TYPE,BloodIndustryInteraction.class,BloodIndustryInteraction.codec((mode,context)->mode.startsWith("pickaxe")?arcanePickaxe.use(mode,context):"furnace".equals(mode)&&bloodFurnaceSystem.open(context)));
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(NativePickaxeHoldInteraction.TYPE,NativePickaxeHoldInteraction.class,NativePickaxeHoldInteraction.codec(arcanePickaxe::hold));
        EntityStore.REGISTRY.registerSystem(arcanePickaxe);
        EntityStore.REGISTRY.registerSystem(bloodFurnaceSystem);
        this.getEventRegistry().registerGlobal(EventPriority.LAST,loadedBlockTypesEventClass,event->{if(event.getAssetClass().equals(com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe.class))bloodFurnaceSystem.refreshRecipes();});
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,event->bloodFurnaceSystem.disconnect(event.getPlayerRef()));
        EntityStore.REGISTRY.registerSystem(new BloodboundSystem(bloodbound,debug,errors));
        EntityStore.REGISTRY.registerSystem(new BloodMagicDamageSystem.Filter(bloodPlayer,bloodbound));
        EntityStore.REGISTRY.registerSystem(new BloodMagicDamageSystem.Observe(bloodPlayer,bloodbound));
        debug.traceFileOnly(null,"BloodMagic event=registered nativeInput=true channelSeconds=8 range=8 oneSamplePerEntity=true savedSpeciesOwner=true mana=nativeAllCurrent");
        var tomTomState=this.getEntityStoreRegistry().registerComponent(TomTomState.class,"AxoTales_TomTomState",TomTomState.CODEC);
        var tomTomCombat=new TomTomCombat(tomTomState,debug,errors);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(TomTomInteraction.TYPE,TomTomInteraction.class,TomTomInteraction.codec(tomTomCombat::handle));
        EntityStore.REGISTRY.registerSystem(tomTomCombat);
        var ancientArrowType=this.getEntityStoreRegistry().registerComponent(AncientArrowState.class,AncientArrowState::new);
        this.scoutGearSystem=new ScoutGearSystem(ancientArrowType,SpyglassSettings.load(this.getDataDirectory(),errors),debug,errors);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(ScoutInteraction.TYPE,ScoutInteraction.class,ScoutInteraction.codec(scoutGearSystem::handle));
        EntityStore.REGISTRY.registerSystem(scoutGearSystem);
        RelicBowShot.TYPE=this.getEntityStoreRegistry().registerComponent(RelicBowShot.class,"AxoTales_RelicBowShot",RelicBowShot.CODEC);
        var relicBows=new RelicBowSystem(scoutGearSystem,debug);
        var oraculumType=this.getChunkStoreRegistry().registerComponent(OraculumState.class,"AxoTales_OraculumState",OraculumState.CODEC);
        var oraculum=new OraculumSystem(oraculumType,debug,errors);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
            .register(RelicInteraction.TYPE,RelicInteraction.class,RelicInteraction.codec((context,mode)->"oraculum".equals(mode)?oraculum.use(context):relicBows.handle(context,mode)));
        EntityStore.REGISTRY.registerSystem(new RelicBowShot.Arrive());
        EntityStore.REGISTRY.registerSystem(new RelicBowShot.Hits());
        EntityStore.REGISTRY.registerSystem(oraculum);
        EntityStore.REGISTRY.registerSystem(new OraculumSystem.ProtectBreak(oraculum));
        EntityStore.REGISTRY.registerSystem(new OraculumSystem.ProtectDamage(oraculum));
        this.getEventRegistry().registerGlobal(EventPriority.LAST,loadedBlockTypesEventClass,event->AncientBowRoutes.patch(event,debug,errors));
        var grayCatState=this.getEntityStoreRegistry().registerComponent(GrayCatState.class,"AxoTales_GrayCatState",GrayCatState.CODEC);
        var grayCatFood=this.getEntityStoreRegistry().registerComponent(GrayCatFood.class,GrayCatFood::new);
        var grayCatOffers=new GrayCatFood.Offers();
        var grayCatPets=new GrayCatPets(this.getDataDirectory(),errors);
        EntityStore.REGISTRY.registerSystem(new GrayCatFood.Drop(grayCatOffers,debug));
        EntityStore.REGISTRY.registerSystem(new GrayCatFood.Arrive(grayCatOffers,grayCatFood));
        EntityStore.REGISTRY.registerSystem(grayCatPets.lifecycle(grayCatState));
        EntityStore.REGISTRY.registerSystem(new GrayCatSystem(grayCatState,grayCatFood,grayCatPets,debug,errors));
        var loyalty=new CompanionLoyalty(whisperionState,grayCatState,kuduAdeptBondPersistedComponentType,soulState,bloodbound,serverConfig.kuduAdept.roleName);
        EntityStore.REGISTRY.registerSystem(loyalty.provider());
        EntityStore.REGISTRY.registerSystem(loyalty);
        EntityStore.REGISTRY.registerSystem(new CompanionDamageSystem());
        MageCrystalOffer.TYPE=this.getEntityStoreRegistry().registerComponent(MageCrystalOffer.class,MageCrystalOffer::new);
        var mageOffers=new MageCrystalOffer.Offers();
        EntityStore.REGISTRY.registerSystem(new MageCrystalOffer.Drop(mageOffers,debug));
        EntityStore.REGISTRY.registerSystem(new MageCrystalOffer.Arrive(mageOffers,MageCrystalOffer.TYPE));
        EntityStore.REGISTRY.registerSystem(new MageCrystalOffer.Expire());
        EntityStore.REGISTRY.registerSystem(new KuduAdeptProjectileSystem(errors,debug,serverConfig,kuduAdeptBondState));
        var bloodCurse=this.getEntityStoreRegistry().registerComponent(BloodBookCurseState.class,"AxoTales_BloodBookCurse",BloodBookCurseState.CODEC);
        EntityStore.REGISTRY.registerSystem(new BloodBookCurseSystem(bloodCurse,spellbookPouches,debug));
        debug.traceFileOnly(null,"Companions event=registered savedOwners=horse,cat,mage,soul,bloodbound mageTargetPolicy=wildNPCs playerAndPetSafety=targetAndImpact rangedSystem=true");
        this.sarsWarfistsInputInterceptor.register();
        this.debug.trace(null, "Sa'r Warfists input interception enabled (SyncInteractionChains id=290).");
        this.sarsBootsPassiveEffect = new SarsBootsPassiveEffect(errors, this.getDataDirectory());
        this.sarsBootsFallDamageImmunitySystem = new SarsBootsFallDamageImmunitySystem();
        this.starterBookGrantSystem = new StarterBookGrantSystem(errors, debug);
        EntityStore.REGISTRY.registerSystem(starterBookGrantSystem);
        this.armorManaMaxBonusEffect = new ArmorManaMaxBonusEffect(errors, debug);
        this.armorManaRegenerationSystem = new ArmorManaRegenerationSystem(errors, debug);
        this.hordeBookMinionCleanupSystem = new HordeBookMinionCleanupSystem(errors, debug, hordeBookSummonState);
        this.hordeBookFriendlyFireSystem = new HordeBookFriendlyFireSystem(errors, debug, hordeBookSummonState);
        this.hordeBookRetaliationTargetingSystem = new HordeBookRetaliationTargetingSystem(errors, debug, hordeBookSummonState);
        this.hordeBookMinionAggroSystem = new HordeBookMinionAggroSystem(errors, debug, serverConfig, hordeBookSummonState);
        this.tauntBookSlamQueue = new TauntBookSlamQueue();
        this.tauntBookLandingSystem = new TauntBookLandingSystem(errors, debug, serverConfig, tauntBookEffectState, tauntBookSlamQueue);
        this.tauntBookSlamSystem = new TauntBookSlamSystem(errors, debug, serverConfig, tauntBookEffectState);
        this.tauntBookSlamAoEDamageSystem = new TauntBookSlamAoEDamageSystem(errors, debug, tauntBookSlamQueue);
        this.immunityBookDamageImmunitySystem = new ImmunityBookDamageImmunitySystem(errors, debug, immunityBookEffectState);
        try {
            if (!EntityStore.REGISTRY.hasSystem(sarsBootsPassiveEffect)) {
                EntityStore.REGISTRY.registerSystem(sarsBootsPassiveEffect);
            }
            if (!EntityStore.REGISTRY.hasSystem(sarsBootsFallDamageImmunitySystem)) {
                EntityStore.REGISTRY.registerSystem(sarsBootsFallDamageImmunitySystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(armorManaRegenerationSystem)) {
                EntityStore.REGISTRY.registerSystem(armorManaRegenerationSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(hordeBookMinionCleanupSystem)) {
                EntityStore.REGISTRY.registerSystem(hordeBookMinionCleanupSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(hordeBookFriendlyFireSystem)) {
                EntityStore.REGISTRY.registerSystem(hordeBookFriendlyFireSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(hordeBookRetaliationTargetingSystem)) {
                EntityStore.REGISTRY.registerSystem(hordeBookRetaliationTargetingSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(hordeBookMinionAggroSystem)) {
                EntityStore.REGISTRY.registerSystem(hordeBookMinionAggroSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(tauntBookLandingSystem)) {
                EntityStore.REGISTRY.registerSystem(tauntBookLandingSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(tauntBookSlamSystem)) {
                EntityStore.REGISTRY.registerSystem(tauntBookSlamSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(tauntBookSlamAoEDamageSystem)) {
                EntityStore.REGISTRY.registerSystem(tauntBookSlamAoEDamageSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(immunityBookDamageImmunitySystem)) {
                EntityStore.REGISTRY.registerSystem(immunityBookDamageImmunitySystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(potionSplashEffectSystem)) {
                EntityStore.REGISTRY.registerSystem(potionSplashEffectSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(strengthPotionDamageMultiplierSystem)) {
                EntityStore.REGISTRY.registerSystem(strengthPotionDamageMultiplierSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(invisibilityPotionEffectDebugSystem)) {
                EntityStore.REGISTRY.registerSystem(invisibilityPotionEffectDebugSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(invisibilityCloakSystem)) {
                EntityStore.REGISTRY.registerSystem(invisibilityCloakSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(invisibilityArmorHiderSystem)) {
                EntityStore.REGISTRY.registerSystem(invisibilityArmorHiderSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(invisibilityHiddenPlayersSystem)) {
                EntityStore.REGISTRY.registerSystem(invisibilityHiddenPlayersSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(movementBuffSystem)) {
                EntityStore.REGISTRY.registerSystem(movementBuffSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(sarsWarfistsProjectileHitSystem)) {
                EntityStore.REGISTRY.registerSystem(sarsWarfistsProjectileHitSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(cloudBlockVelocitySystem)) {
                EntityStore.REGISTRY.registerSystem(cloudBlockVelocitySystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(bounceBlockVelocitySystem)) {
                EntityStore.REGISTRY.registerSystem(bounceBlockVelocitySystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(frostBookProjectileHitSystem)) {
                EntityStore.REGISTRY.registerSystem(frostBookProjectileHitSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(frostBookBlockImpactSystem)) {
                EntityStore.REGISTRY.registerSystem(frostBookBlockImpactSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(flameBookProjectileHitSystem)) {
                EntityStore.REGISTRY.registerSystem(flameBookProjectileHitSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(flameBookBlockImpactSystem)) {
                EntityStore.REGISTRY.registerSystem(flameBookBlockImpactSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(lightBookProjectileSystem)) {
                EntityStore.REGISTRY.registerSystem(lightBookProjectileSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(lightBookProjectileHitSystem)) {
                EntityStore.REGISTRY.registerSystem(lightBookProjectileHitSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(morphBookProjectileHitSystem)) {
                EntityStore.REGISTRY.registerSystem(morphBookProjectileHitSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(healingBookProjectileHitSystem)) {
                EntityStore.REGISTRY.registerSystem(healingBookProjectileHitSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(doomBookProjectileHitSystem)) {
                EntityStore.REGISTRY.registerSystem(doomBookProjectileHitSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(runeKnightSpawnerSystem)) {
                EntityStore.REGISTRY.registerSystem(runeKnightSpawnerSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(runeKnightAggroSystem)) {
                EntityStore.REGISTRY.registerSystem(runeKnightAggroSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(runeKnightProjectileSystem)) {
                EntityStore.REGISTRY.registerSystem(runeKnightProjectileSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(runeKnightLootSystem)) {
                EntityStore.REGISTRY.registerSystem(runeKnightLootSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(kuduAdeptSpawnerSystem)) {
                EntityStore.REGISTRY.registerSystem(kuduAdeptSpawnerSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(kuduAdeptBondSystem)) {
                EntityStore.REGISTRY.registerSystem(kuduAdeptBondSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(kuduAdeptCrystalDropOwnerSystem)) {
                EntityStore.REGISTRY.registerSystem(kuduAdeptCrystalDropOwnerSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(kuduAdeptMasterTargetingSystem)) {
                EntityStore.REGISTRY.registerSystem(kuduAdeptMasterTargetingSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(kuduAdeptNoPlayerDamageSystem)) {
                EntityStore.REGISTRY.registerSystem(kuduAdeptNoPlayerDamageSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(kuduAdeptProjectileDamageSystem)) {
                EntityStore.REGISTRY.registerSystem(kuduAdeptProjectileDamageSystem);
            }
            if (!EntityStore.REGISTRY.hasSystem(kuduAdeptNoMeleeDamageSystem)) {
                EntityStore.REGISTRY.registerSystem(kuduAdeptNoMeleeDamageSystem);
            }
        } catch (Throwable t) {
            errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to register Axo Tales ECS systems.", t);
        }
        this.getCommandRegistry().registerCommand(new ExampleCommand(errors));
        this.getCommandRegistry().registerCommand(new AxoPlaceholderCommand(errors, debug, customPlaceholderBlockWorldgen));
        this.getCommandRegistry().registerCommand(new RuneKnightCommand(errors, debug, serverConfig, runeKnightSpawnState));

        try {
            var worlds = Universe.get().getWorlds();
            if (worlds != null) {
                worlds.values().forEach(this::applyWorldMapSettings);
            }
        } catch (Throwable t) {
            errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to enumerate worlds.", t);
        }

        this.getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, event -> {
            try {
                customPlaceholderBlockWorldgen.onChunkPreLoad(event);
                runeWorldgen.onChunkPreLoad(event);
            } catch (Throwable t) {
                errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "CustomPlaceholderBlockWorldgen: handler failed.", t);
            }
            try {
                arcaneMatterOreWorldgen.onChunkPreLoad(event);
                arcaneGrassWorldgen.onChunkPreLoad(event);
            } catch (Throwable t) {
                errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "ArcaneMatterOreWorldgen: handler failed.", t);
            }
        });
        this.debug.trace(null, "Arcane crystal worldgen enabled (" + CustomPlaceholderBlockWorldgen.BLOCK_ITEM_ID + ").");
        this.debug.trace(null, "Arcane Matter ore worldgen enabled (Ore_Stone_Parent + Arcane_Matter_Volcanic).");
        this.debug.trace(null, "Cloud block velocity system enabled (" + CloudBlockVelocitySystem.CLOUD_BLOCK_ITEM_ID + ").");
        this.debug.trace(null, "Bounce block velocity system enabled (" + BounceBlockVelocitySystem.BOUNCE_BLOCK_ITEM_ID + ").");

        this.getEventRegistry().registerGlobal(
            AddPlayerToWorldEvent.class,
            event -> applyWorldMapSettings(event.getWorld())
        );

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                starterBookGrantSystem.onPlayerReady(event);
                sarsBootsPassiveEffect.onPlayerReady(event);
                armorManaMaxBonusEffect.onPlayerReady(event);
                if (morphBookModelState != null) {
                    morphBookModelState.onPlayerReady(errors, debug, event.getPlayerRef());
                }
            } catch (Throwable t) {
                errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to send join message.", t);
            }
        });

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            try {
                if (spellbookInputInterceptor != null) {
                    spellbookInputInterceptor.onPlayerDisconnect(event.getPlayerRef());
                    spellbookPouches.disconnect(event.getPlayerRef());
                }
                if (invisibilityPotionEffectDebugSystem != null) {
                    invisibilityPotionEffectDebugSystem.onPlayerDisconnect(event.getPlayerRef());
                }
                if (invisibilityCloakSystem != null) {
                    invisibilityCloakSystem.onPlayerDisconnect(event.getPlayerRef());
                }
                if (invisibilityArmorHiderSystem != null) {
                    invisibilityArmorHiderSystem.onPlayerDisconnect(event.getPlayerRef());
                }
                if (invisibilityHiddenPlayersSystem != null) {
                    invisibilityHiddenPlayersSystem.onPlayerDisconnect(event.getPlayerRef());
                }
                if (movementBuffSystem != null) {
                    movementBuffSystem.onPlayerDisconnect(event.getPlayerRef());
                }
                if (sarsWarfistsInputInterceptor != null) {
                    sarsWarfistsInputInterceptor.onPlayerDisconnect(event.getPlayerRef());
                }
                if (cloudBlockVelocitySystem != null) {
                    cloudBlockVelocitySystem.onPlayerDisconnect(event.getPlayerRef());
                }
                if (bounceBlockVelocitySystem != null) {
                    bounceBlockVelocitySystem.onPlayerDisconnect(event.getPlayerRef());
                }
                sarsBootsPassiveEffect.onPlayerDisconnect(event);
                armorManaMaxBonusEffect.onPlayerDisconnect(event);
            } catch (Throwable t) {
                errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to cleanup player effects.", t);
            }
        });

        this.getEventRegistry().registerGlobal(ShutdownEvent.class, event -> {
            try {
                sarsBootsPassiveEffect.shutdown();
                armorManaMaxBonusEffect.shutdown();
                if (movementBuffSystem != null) {
                    movementBuffSystem.shutdown();
                }
                if (cloudBlockVelocitySystem != null) {
                    cloudBlockVelocitySystem.shutdown();
                }
                if (bounceBlockVelocitySystem != null) {
                    bounceBlockVelocitySystem.shutdown();
                }
                if (frostBookBlockImpactSystem != null) {
                    frostBookBlockImpactSystem.shutdown();
                }
                if (frostBookImpactTracker != null) {
                    frostBookImpactTracker.clear();
                }
                if (flameBookBlockImpactSystem != null) {
                    flameBookBlockImpactSystem.shutdown();
                }
                if (flameBookImpactTracker != null) {
                    flameBookImpactTracker.clear();
                }
                if (lightBookProjectileSystem != null) {
                    lightBookProjectileSystem.shutdown();
                }
                if (lightBookProjectileState != null) {
                    lightBookProjectileState.clearAll();
                }
            } catch (Throwable t) {
                errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to cleanup plugin state.", t);
            }
        });
    }

    @Override
    protected void shutdown() {
        if(soulSystem!=null)soulSystem.close();
        if(skillTrees!=null)skillTrees.close();
        if(superskillPopulation!=null)superskillPopulation.close();
        if(bloodFurnaceSystem!=null)bloodFurnaceSystem.close();
        if(vitaeExtractorSystem!=null)vitaeExtractorSystem.close();
        if(scoutGearSystem!=null)scoutGearSystem.close();
        if(ancientGearSystem!=null)ancientGearSystem.close();
        if(ancientCombatSystem!=null)ancientCombatSystem.close();
        try {
            if (spellbookInputInterceptor != null) {
                spellbookInputInterceptor.deregister();
            }
            if (sarsWarfistsInputInterceptor != null) {
                sarsWarfistsInputInterceptor.deregister();
            }
        } catch (Throwable t) {
            if (errors != null) {
                errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to deregister packet interceptors.", t);
            } else {
                LOGGER.atWarning().withCause(t).log("Failed to deregister packet interceptors.");
            }
        }

        try {
            super.shutdown();
        } catch (Throwable ignored) {

        }
    }

    private void applyWorldMapSettings(@Nonnull World world) {


        if(serverConfig==null||serverConfig.workarounds==null||!serverConfig.workarounds.disableWorldMap)return;
        world.execute(() -> {
            try {
                var manager = world.getWorldMapManager();
                if (manager == null) {
                    return;
                }

                boolean previousShouldTick = manager.shouldTick();

                var config = world.getWorldConfig();
                if (config != null && config.isCompassUpdating()) {
                    config.setCompassUpdating(false);
                    config.markChanged();
                }
                if (manager.isWorldMapEnabled()) {
                    manager.setGenerator(new DisabledWorldMapProvider().getGenerator(world));
                }
                manager.updateTickingState(previousShouldTick);
                debug.traceFileOnly(null, "WorldMap: disabled for world '" + world.getName() + "' (explicit workaround enabled).");
            } catch (Throwable t) {
                errors.report((com.hypixel.hytale.server.core.universe.PlayerRef) null, "Failed to apply world map settings.", t);
            }
        });
    }
}
