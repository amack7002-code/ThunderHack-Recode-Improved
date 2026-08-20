package thunder.hack.features.modules.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class MaceSwap extends Module {

    public enum Mode {
        AUTO,
        MANUAL
    }

    private final Setting<Mode> mode =
            new Setting<>("Mode", Mode.AUTO);

    private final Setting<Integer> cooldown =
            new Setting<>("Cooldown", 3, 0, 20);

    private final Setting<Integer> maceHoldTicks =
            new Setting<>("MaceHold", 1, 1, 5);

    private final Setting<Integer> autoDelay =
            new Setting<>("AutoDelay", 2, 0, 20);

    /*
     * General sequence state.
     */
    private boolean sequenceActive = false;
    private boolean hasSwappedBack = false;

    /*
     * Manual mode state.
     */
    private boolean lastAttackState = false;
    private boolean manualSwingDetected = false;

    /*
     * Timers.
     */
    private int maceTicks = 0;
    private int cooldownTicks = 0;
    private int autoDelayTicks = 0;

    /*
     * Inventory state.
     */
    private int originalSlot = -1;
    private int maceSlot = -1;
    private int temporaryMaceSlot = -1;

    private static final float MANUAL_ATTACK_THRESHOLD = 0.95f;

    public MaceSwap() {
        super("MaceSwap", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        reset();

        if (mc.player != null) {
            lastAttackState =
                    mc.player.getAttackCooldownProgress(0.0f)
                            < MANUAL_ATTACK_THRESHOLD;
        }
    }

    @Override
    public void onDisable() {
        restoreOriginalSlot();
        reset();
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        /*
         * =========================================
         * COOLDOWN
         * =========================================
         */
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        /*
         * =========================================
         * MACE HOLD TIMER
         * =========================================
         */
        if (maceTicks > 0) {
            maceTicks--;

            if (maceTicks <= 0
                    && sequenceActive
                    && !hasSwappedBack) {

                restoreOriginalSlot();

                hasSwappedBack = true;
                sequenceActive = false;
            }
        }

        /*
         * Don't start another attack while the
         * current mace sequence is running.
         */
        if (sequenceActive || cooldownTicks > 0) {
            return;
        }

        /*
         * =========================================
         * AUTO MODE
         * =========================================
         */
        if (mode.getValue() == Mode.AUTO) {
            handleAutoMode();
            return;
        }

        /*
         * =========================================
         * MANUAL MODE
         * =========================================
         */
        if (mode.getValue() == Mode.MANUAL) {
            handleManualMode();
        }
    }

    /**
     * AUTO MODE
     *
     * Automatically:
     *
     * 1. Finds the entity under the crosshair.
     * 2. Checks for sword/axe.
     * 3. Waits for attack cooldown.
     * 4. Waits AutoDelay ticks.
     * 5. Finds mace.
     * 6. Swaps to mace.
     * 7. Attacks.
     * 8. Swaps back.
     */
    private void handleAutoMode() {
        if (mc.player == null) {
            return;
        }

        /*
         * Get target.
         */
        Entity target = getEntityTarget();

        if (target == null) {
            autoDelayTicks = 0;
            return;
        }

        /*
         * Must be holding sword or axe.
         */
        ItemStack mainHand =
                mc.player.getMainHandStack();

        if (!isSwordOrAxe(mainHand)) {
            autoDelayTicks = 0;
            return;
        }

        /*
         * Wait until the normal attack cooldown
         * is fully charged.
         */
        float attackCooldown =
                mc.player.getAttackCooldownProgress(0.0f);

        if (attackCooldown < 0.99f) {
            autoDelayTicks = 0;
            return;
        }

        /*
         * =========================================
         * AUTO DELAY
         * =========================================
         *
         * Example:
         *
         * AutoDelay = 0
         * -> attack immediately
         *
         * AutoDelay = 2
         * -> wait 2 ticks (~100 ms)
         *
         * AutoDelay = 4
         * -> wait 4 ticks (~200 ms)
         */
        if (autoDelayTicks < autoDelay.getValue()) {
            autoDelayTicks++;
            return;
        }

        /*
         * Find mace.
         */
        maceSlot = findMaceSlot();

        if (maceSlot < 0) {
            autoDelayTicks = 0;
            return;
        }

        /*
         * Perform attack.
         */
        performMaceAttack(target);

        /*
         * Reset delay for the next attack.
         */
        autoDelayTicks = 0;
    }

    /**
     * MANUAL MODE
     *
     * Waits for the player to swing their sword/axe.
     */
    private void handleManualMode() {
        if (mc.player == null) {
            return;
        }

        boolean currentAttackState =
                isPlayerAttacking();

        /*
         * Detect transition into attack state.
         */
        if (currentAttackState && !lastAttackState) {
            manualSwingDetected = true;
        }

        lastAttackState = currentAttackState;

        if (!manualSwingDetected) {
            return;
        }

        manualSwingDetected = false;

        /*
         * Get target.
         */
        Entity target = getEntityTarget();

        if (target == null) {
            return;
        }

        /*
         * Must be holding sword/axe.
         */
        ItemStack mainHand =
                mc.player.getMainHandStack();

        if (!isSwordOrAxe(mainHand)) {
            return;
        }

        /*
         * Find mace.
         */
        maceSlot = findMaceSlot();

        if (maceSlot < 0) {
            return;
        }

        /*
         * Perform mace attack.
         */
        performMaceAttack(target);
    }

    /**
     * Detects a manual attack.
     */
    private boolean isPlayerAttacking() {
        if (mc.player == null) {
            return false;
        }

        float attackCooldown =
                mc.player.getAttackCooldownProgress(0.0f);

        return attackCooldown < MANUAL_ATTACK_THRESHOLD;
    }

    /**
     * Performs:
     *
     * Sword/Axe
     *     ↓
     * Mace
     *     ↓
     * Attack
     *     ↓
     * Sword/Axe
     */
    private void performMaceAttack(Entity target) {
        if (mc.player == null) {
            return;
        }

        /*
         * Remember current hotbar slot.
         */
        originalSlot =
                mc.player.getInventory().selectedSlot;

        /*
         * Reset temporary slot.
         */
        temporaryMaceSlot = -1;

        sequenceActive = true;
        hasSwappedBack = false;

        /*
         * =========================================
         * MACE ALREADY IN SELECTED SLOT
         * =========================================
         */
        if (maceSlot == originalSlot) {

            maceTicks =
                    maceHoldTicks.getValue();

            sendAttackPacket(target);

            cooldownTicks =
                    cooldown.getValue();

            return;
        }

        /*
         * =========================================
         * MACE IN HOTBAR
         * =========================================
         */
        if (maceSlot >= 0 && maceSlot <= 8) {

            selectHotbarSlot(maceSlot);

            maceTicks =
                    maceHoldTicks.getValue();

            /*
             * Attack while mace is selected.
             */
            sendAttackPacket(target);

            cooldownTicks =
                    cooldown.getValue();

            return;
        }

        /*
         * =========================================
         * MACE IN MAIN INVENTORY
         * =========================================
         */
        int freeSlot =
                findFreeHotbarSlot();

        if (freeSlot < 0) {
            /*
             * Hotbar is full.
             *
             * Don't replace another item.
             */
            sequenceActive = false;
            return;
        }

        temporaryMaceSlot = freeSlot;

        /*
         * Move mace into hotbar.
         */
        moveInventoryItemToHotbar(
                maceSlot,
                temporaryMaceSlot
        );

        /*
         * Select mace.
         */
        selectHotbarSlot(
                temporaryMaceSlot
        );

        maceTicks =
                maceHoldTicks.getValue();

        /*
         * Attack with mace.
         */
        sendAttackPacket(target);

        cooldownTicks =
                cooldown.getValue();
    }

    /**
     * Gets the entity currently under the crosshair.
     */
    private Entity getEntityTarget() {
        if (mc.player == null
                || mc.world == null) {
            return null;
        }

        HitResult hit =
                mc.crosshairTarget;

        if (!(hit instanceof EntityHitResult entityHit)) {
            return null;
        }

        Entity target =
                entityHit.getEntity();

        /*
         * Don't target ourselves.
         */
        if (target instanceof LivingEntity
                && target != mc.player) {

            return target;
        }

        return null;
    }

    /**
     * Sends attack packet.
     */
    private void sendAttackPacket(Entity target) {
        if (mc.player == null
                || mc.getNetworkHandler() == null) {
            return;
        }

        mc.getNetworkHandler().sendPacket(
                PlayerInteractEntityC2SPacket.attack(
                        target,
                        mc.player.isOnGround()
                )
        );

        /*
         * Swing animation.
         */
        mc.player.swingHand(
                Hand.MAIN_HAND
        );
    }

    /**
     * Checks whether item is sword or axe.
     */
    private boolean isSwordOrAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem;
    }

    /**
     * Checks whether item is mace.
     */
    private boolean isMace(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        return stack.getItem() instanceof MaceItem;
    }

    /**
     * Finds mace.
     *
     * Hotbar is searched first.
     */
    private int findMaceSlot() {
        if (mc.player == null) {
            return -1;
        }

        /*
         * =========================================
         * HOTBAR
         * =========================================
         */
        for (int i = 0; i < 9; i++) {

            ItemStack stack =
                    mc.player.getInventory()
                            .getStack(i);

            if (isMace(stack)) {
                return i;
            }
        }

        /*
         * =========================================
         * MAIN INVENTORY
         * =========================================
         */
        for (int i = 9;
             i < mc.player.getInventory().size();
             i++) {

            ItemStack stack =
                    mc.player.getInventory()
                            .getStack(i);

            if (isMace(stack)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Selects a hotbar slot.
     */
    private void selectHotbarSlot(int slot) {
        if (mc.player == null
                || mc.getNetworkHandler() == null
                || slot < 0
                || slot > 8) {
            return;
        }

        mc.player.getInventory().selectedSlot =
                slot;

        mc.getNetworkHandler().sendPacket(
                new UpdateSelectedSlotC2SPacket(slot)
        );
    }

    /**
     * Moves an inventory item into a hotbar slot.
     */
    private void moveInventoryItemToHotbar(
            int inventorySlot,
            int hotbarSlot
    ) {
        if (mc.player == null
                || mc.interactionManager == null) {
            return;
        }

        if (inventorySlot < 9
                || hotbarSlot < 0
                || hotbarSlot > 8) {
            return;
        }

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                inventorySlot,
                hotbarSlot,
                net.minecraft.screen.slot.SlotActionType.SWAP,
                mc.player
        );
    }

    /**
     * Finds an empty hotbar slot.
     */
    private int findFreeHotbarSlot() {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {

            if (mc.player.getInventory()
                    .getStack(i)
                    .isEmpty()) {

                return i;
            }
        }

        return -1;
    }

    /**
     * Restores original weapon.
     */
    private void restoreOriginalSlot() {
        if (mc.player == null) {
            return;
        }

        /*
         * If mace was moved from inventory into a
         * temporary hotbar slot, move it back.
         */
        if (temporaryMaceSlot >= 0
                && maceSlot >= 9
                && mc.interactionManager != null) {

            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    maceSlot,
                    temporaryMaceSlot,
                    net.minecraft.screen.slot.SlotActionType.SWAP,
                    mc.player
            );

            temporaryMaceSlot = -1;
        }

        /*
         * Return to original weapon.
         */
        if (originalSlot >= 0
                && originalSlot <= 8) {

            selectHotbarSlot(
                    originalSlot
            );
        }
    }

    /**
     * Reset all state.
     */
    private void reset() {
        sequenceActive = false;
        hasSwappedBack = false;

        lastAttackState = false;
        manualSwingDetected = false;

        maceTicks = 0;
        cooldownTicks = 0;
        autoDelayTicks = 0;

        originalSlot = -1;
        maceSlot = -1;
        temporaryMaceSlot = -1;
    }
}