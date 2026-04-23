package org.example.plugin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Thin adapter around the non-deprecated inventory ECS components.
 */
final class InventoryComponentAccess {

    private InventoryComponentAccess() {
    }

    @Nullable
    static InventoryComponent.Armor armorComponent(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        return accessor.getComponent(entityRef, InventoryComponent.Armor.getComponentType());
    }

    @Nullable
    static InventoryComponent.Hotbar hotbarComponent(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        return accessor.getComponent(entityRef, InventoryComponent.Hotbar.getComponentType());
    }

    @Nullable
    static InventoryComponent.Utility utilityComponent(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        return accessor.getComponent(entityRef, InventoryComponent.Utility.getComponentType());
    }

    @Nullable
    static InventoryComponent.Tool toolComponent(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        return accessor.getComponent(entityRef, InventoryComponent.Tool.getComponentType());
    }

    @Nullable
    static ItemContainer armor(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Armor component = armorComponent(accessor, entityRef);
        return component != null ? component.getInventory() : null;
    }

    @Nullable
    static ItemContainer hotbar(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Hotbar component = hotbarComponent(accessor, entityRef);
        return component != null ? component.getInventory() : null;
    }

    @Nullable
    static ItemContainer storage(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Storage component = accessor.getComponent(entityRef, InventoryComponent.Storage.getComponentType());
        return component != null ? component.getInventory() : null;
    }

    @Nullable
    static ItemContainer utility(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Utility component = utilityComponent(accessor, entityRef);
        return component != null ? component.getInventory() : null;
    }

    @Nullable
    static ItemContainer tools(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Tool component = toolComponent(accessor, entityRef);
        return component != null ? component.getInventory() : null;
    }

    @Nullable
    static ItemContainer backpack(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Backpack component = accessor.getComponent(entityRef, InventoryComponent.Backpack.getComponentType());
        return component != null ? component.getInventory() : null;
    }

    @Nullable
    static ItemStack itemInHand(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        return InventoryComponent.getItemInHand(accessor, entityRef);
    }

    @Nullable
    static ItemStack activeHotbarItem(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Hotbar component = hotbarComponent(accessor, entityRef);
        return component != null ? component.getActiveItem() : null;
    }

    @Nullable
    static ItemStack utilityItem(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Utility component = utilityComponent(accessor, entityRef);
        return component != null ? component.getActiveItem() : null;
    }

    @Nullable
    static ItemStack toolsItem(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Tool component = toolComponent(accessor, entityRef);
        return component != null ? component.getActiveItem() : null;
    }

    static byte activeHotbarSlot(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef
    ) {
        InventoryComponent.Hotbar component = hotbarComponent(accessor, entityRef);
        return component != null ? component.getActiveSlot() : InventoryComponent.INACTIVE_SLOT_INDEX;
    }

    static boolean setActiveHotbarSlot(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef,
        byte slot
    ) {
        InventoryComponent.Hotbar hotbar = hotbarComponent(accessor, entityRef);
        if (hotbar == null) {
            return false;
        }

        InventoryComponent.Tool tool = toolComponent(accessor, entityRef);
        if (tool != null) {
            tool.setUsingToolsItem(false);
            tool.markDirty();
        }

        hotbar.setActiveSlot(slot);
        hotbar.markDirty();
        return true;
    }

    @Nullable
    static String armorItemId(
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull ItemArmorSlot slot
    ) {
        ItemContainer armor = armor(accessor, entityRef);
        if (armor == null) {
            return null;
        }

        short slotIndex = (short) slot.getValue();
        if (slotIndex < 0 || slotIndex >= armor.getCapacity()) {
            return null;
        }

        ItemStack stack = armor.getItemStack(slotIndex);
        if (stack == null || !stack.isValid()) {
            return null;
        }

        return stack.getItemId();
    }
}
