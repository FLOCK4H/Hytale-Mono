package org.example.plugin;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.CraftingBench;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Appends Axo Tales categories onto the currently loaded vanilla/current Arcane bench
 * instead of shipping a full Bench_Arcane override.
 */
public final class ArcaneWorkbenchCategoryPatcher {

    private static final String ARCANE_BENCH_BLOCK_ID = "Bench_Arcane";
    private static final String SPELLBLADES_CATEGORY_ID = "Arcane_AxoTales_Spellblades";
    private static final String SPELLBLADES_CATEGORY_NAME = "server.benchCategories.axoTalesSpellblades";
    private static final String SPELLBLADES_CATEGORY_ICON = "Icons/CraftingCategories/AxoTales/Spellblades.png";
    private static final String ARMOR_CATEGORY_ID = "Arcane_AxoTales_Armor";
    private static final String ARMOR_CATEGORY_NAME = "server.benchCategories.axoTalesArmor";
    private static final String ARMOR_CATEGORY_ICON = "Icons/CraftingCategories/AxoTales/Armor.png";

    private final PluginErrorReporter errors;
    private final PluginDebugReporter debug;
    private final Field craftingBenchCategoriesField;
    private final Field blockTypeCachedPacketField;

    public ArcaneWorkbenchCategoryPatcher(
        @Nonnull PluginErrorReporter errors,
        @Nonnull PluginDebugReporter debug
    ) {
        this.errors = errors;
        this.debug = debug;
        this.craftingBenchCategoriesField = requireField(CraftingBench.class, "categories");
        this.blockTypeCachedPacketField = requireField(BlockType.class, "cachedPacket");
    }

    public void onLoadedAssets(@Nonnull LoadedAssetsEvent<?, ?, ?> event) {
        if (!BlockType.class.equals(event.getAssetClass())) {
            return;
        }

        Map<?, ?> loadedAssets = event.getLoadedAssets();
        boolean benchLoadedThisPass = loadedAssets != null && loadedAssets.containsKey(ARCANE_BENCH_BLOCK_ID);
        if (!event.isInitial() && !benchLoadedThisPass) {
            return;
        }

        try {
            BlockType blockType = BlockType.getAssetMap().getAsset(ARCANE_BENCH_BLOCK_ID);
            if (blockType == null) {
                debug.traceFileOnly(
                    null,
                    "ArcaneWorkbenchCategoryPatcher event=skip reason=benchMissing initial=" + event.isInitial()
                );
                return;
            }

            Bench bench = blockType.getBench();
            if (!(bench instanceof CraftingBench craftingBench)) {
                debug.traceFileOnly(
                    null,
                    "ArcaneWorkbenchCategoryPatcher event=skip reason=benchNotCrafting benchClass="
                        + (bench != null ? bench.getClass().getName() : "null")
                );
                return;
            }

            CraftingBench.BenchCategory[] existingCategories = craftingBench.getCategories();
            List<CraftingBench.BenchCategory> mergedCategories = new ArrayList<>();
            if (existingCategories != null) {
                for (CraftingBench.BenchCategory category : existingCategories) {
                    if (category != null) {
                        mergedCategories.add(category);
                    }
                }
            }

            boolean addedSpellblades = ensureCategory(
                mergedCategories,
                SPELLBLADES_CATEGORY_ID,
                SPELLBLADES_CATEGORY_NAME,
                SPELLBLADES_CATEGORY_ICON
            );
            boolean addedArmor = ensureCategory(
                mergedCategories,
                ARMOR_CATEGORY_ID,
                ARMOR_CATEGORY_NAME,
                ARMOR_CATEGORY_ICON
            );

            if (!addedSpellblades && !addedArmor) {
                debug.traceFileOnly(
                    null,
                    "ArcaneWorkbenchCategoryPatcher event=skip reason=alreadyPresent categoryCount="
                        + mergedCategories.size()
                );
                return;
            }

            craftingBenchCategoriesField.set(
                craftingBench,
                mergedCategories.toArray(CraftingBench.BenchCategory[]::new)
            );
            blockTypeCachedPacketField.set(blockType, null);

            debug.trace(
                null,
                "ArcaneWorkbenchCategoryPatcher event=patched bench=" + ARCANE_BENCH_BLOCK_ID
                    + " initial=" + event.isInitial()
                    + " addedSpellblades=" + addedSpellblades
                    + " addedArmor=" + addedArmor
                    + " categoryCount=" + mergedCategories.size()
            );
        } catch (Throwable t) {
            errors.report(
                (com.hypixel.hytale.server.core.universe.PlayerRef) null,
                "ArcaneWorkbenchCategoryPatcher: failed to append categories onto Bench_Arcane.",
                t
            );
        }
    }

    private static boolean ensureCategory(
        @Nonnull List<CraftingBench.BenchCategory> categories,
        @Nonnull String id,
        @Nonnull String name,
        @Nonnull String icon
    ) {
        for (CraftingBench.BenchCategory category : categories) {
            if (category != null && id.equals(category.getId())) {
                return false;
            }
        }

        categories.add(new CraftingBench.BenchCategory(id, name, icon, null));
        return true;
    }

    @Nonnull
    private static Field requireField(@Nonnull Class<?> owner, @Nonnull String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Failed to resolve reflective field " + owner.getName() + "." + name,
                e
            );
        }
    }
}
