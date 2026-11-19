package org.example.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;




public final class MorphBookModelState {

    private final Map<UUID, Model> baselineModelByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSkin> baselineSkinByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID,Model> observedModels=new ConcurrentHashMap<>();
    private final java.util.Set<UUID> transformed=ConcurrentHashMap.newKeySet();

    public void onPlayerReady(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull Ref<EntityStore> playerEntityRef
    ) {
        try {
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                return;
            }

            Store<EntityStore> store = playerEntityRef.getStore();
            if (store == null) {
                return;
            }

            var external = store.getExternalData();
            if (external == null) {
                return;
            }

            World world = external.getWorld();
            if (world == null) {
                return;
            }

            world.execute(() -> captureBaselineOnWorldThread(errors, debug, store, playerEntityRef));
        } catch (Throwable t) {
            errors.report((PlayerRef) null, "MorphBookModelState: failed to schedule baseline capture.", t);
        }
    }

    public void clear(@Nonnull UUID uuid) {
        baselineModelByPlayer.remove(uuid);
        baselineSkinByPlayer.remove(uuid);
        observedModels.remove(uuid);transformed.remove(uuid);
    }
    void transformed(UUID uuid,boolean active){if(active)transformed.add(uuid);else transformed.remove(uuid);}
    void observe(UUID uuid,Model model,PlayerSkin skin){
        if(transformed.contains(uuid)||model==null||WhisperionFlight.MODEL.equals(model.getModelAssetId()))return;
        if(observedModels.put(uuid,model)!=model)baselineModelByPlayer.put(uuid,new Model(model));


        if(!isBlankSkin(skin)&&!skin.equals(baselineSkinByPlayer.get(uuid)))baselineSkinByPlayer.put(uuid,new PlayerSkin(skin));
    }
    com.hypixel.hytale.component.system.tick.TickingSystem<EntityStore> observer(){return new com.hypixel.hytale.component.system.tick.TickingSystem<>(){
        @Override public void tick(float delta,int tick,@Nonnull Store<EntityStore> store){
            store.forEachChunk(com.hypixel.hytale.component.query.Query.and(PlayerRef.getComponentType(),ModelComponent.getComponentType(),PlayerSkinComponent.getComponentType()),(chunk,buffer)->{
                for(int i=0;i<chunk.size();i++)observe(chunk.getComponent(i,PlayerRef.getComponentType()).getUuid(),chunk.getComponent(i,ModelComponent.getComponentType()).getModel(),chunk.getComponent(i,PlayerSkinComponent.getComponentType()).getPlayerSkin());
            });
        }
    };}

    public @Nullable Model getBaselineModel(@Nonnull UUID uuid) {
        return baselineModelByPlayer.get(uuid);
    }

    public @Nullable PlayerSkin getBaselineSkin(@Nonnull UUID uuid) {
        return baselineSkinByPlayer.get(uuid);
    }

    public void captureBaselineIfAbsent(
        @Nonnull UUID uuid,
        @Nullable Model model
    ) {
        if (model == null) {
            return;
        }
        baselineModelByPlayer.computeIfAbsent(uuid, ignored -> new Model(model));
    }

    public void captureBaselineSkinIfAbsent(
        @Nonnull UUID uuid,
        @Nullable PlayerSkin skin
    ) {
        if (skin == null) {
            return;
        }

        baselineSkinByPlayer.compute(uuid, (ignored, existing) -> {
            if (existing == null) {
                return new PlayerSkin(skin);
            }
            if (isBlankSkin(existing) && !isBlankSkin(skin)) {
                return new PlayerSkin(skin);
            }
            return existing;
        });
    }

    private void captureBaselineOnWorldThread(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntityRef
    ) {
        PlayerRef playerRef = null;
        try {
            playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
            UUID uuid = playerRef != null ? playerRef.getUuid() : null;
            if (uuid == null) {
                return;
            }

            boolean hadModel = baselineModelByPlayer.containsKey(uuid);
            boolean hadSkin = baselineSkinByPlayer.containsKey(uuid);
            if (hadModel && hadSkin) {
                return;
            }

            ModelComponent modelComponent = store.getComponent(playerEntityRef, ModelComponent.getComponentType());
            Model model = modelComponent != null ? modelComponent.getModel() : null;
            boolean capturedModel = false;
            String baselineModelAssetId = null;
            if (!hadModel) {
                if (model != null) {
                    baselineModelByPlayer.put(uuid, new Model(model));
                    capturedModel = true;
                    baselineModelAssetId = model.getModelAssetId();
                }
            } else if (model != null) {
                baselineModelAssetId = model.getModelAssetId();
            }

            PlayerSkinComponent skinComponent = store.getComponent(playerEntityRef, PlayerSkinComponent.getComponentType());
            PlayerSkin skin = skinComponent != null ? skinComponent.getPlayerSkin() : null;
            boolean capturedSkin = false;
            String baselineSkinSummary = null;
            if (skin != null) {
                boolean beforeBlank = !hadSkin || isBlankSkin(baselineSkinByPlayer.get(uuid));
                captureBaselineSkinIfAbsent(uuid, skin);
                boolean afterBlank = isBlankSkin(baselineSkinByPlayer.get(uuid));
                capturedSkin = !hadSkin || (beforeBlank && !afterBlank);
                PlayerSkin stored = baselineSkinByPlayer.get(uuid);
                baselineSkinSummary = stored != null ? summarizeSkin(stored) : null;
            }

            debug.traceFileOnly(
                playerRef,
                "MorphBookBaseline event=PlayerReady captured=true"
                    + " baseline.model.captured=" + (capturedModel || hadModel)
                    + (baselineModelAssetId != null ? " baseline.modelAssetId=" + baselineModelAssetId : "")
                    + " baseline.skin.captured=" + (capturedSkin || hadSkin)
                    + (baselineSkinSummary != null ? " baseline.skin=" + baselineSkinSummary : "")
            );
        } catch (Throwable t) {
            errors.report(playerRef, "MorphBookModelState: failed to capture baseline model.", t);
        }
    }

    private static boolean isBlankSkin(@Nullable PlayerSkin skin) {
        if (skin == null) {
            return true;
        }
        return isBlank(skin.bodyCharacteristic)
            && isBlank(skin.underwear)
            && isBlank(skin.face)
            && isBlank(skin.eyes)
            && isBlank(skin.ears)
            && isBlank(skin.mouth)
            && isBlank(skin.facialHair)
            && isBlank(skin.haircut)
            && isBlank(skin.eyebrows)
            && isBlank(skin.pants)
            && isBlank(skin.overpants)
            && isBlank(skin.undertop)
            && isBlank(skin.overtop)
            && isBlank(skin.shoes)
            && isBlank(skin.headAccessory)
            && isBlank(skin.faceAccessory)
            && isBlank(skin.earAccessory)
            && isBlank(skin.skinFeature)
            && isBlank(skin.gloves)
            && isBlank(skin.cape);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static @Nonnull String summarizeSkin(@Nonnull PlayerSkin skin) {
        String body = skin.bodyCharacteristic != null ? skin.bodyCharacteristic : "null";
        String face = skin.face != null ? skin.face : "null";
        String hair = skin.haircut != null ? skin.haircut : "null";
        return "body=" + body + ",face=" + face + ",hair=" + hair;
    }
}
