package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import thunder.hack.events.impl.EventTick;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.InteractionUtility;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoDrain extends Module {

    private final Setting<DrainMode> drainMode =
            new Setting<>("Mode", DrainMode.Cobweb);

    private final Setting<Integer> range =
            new Setting<>("Range", 5, 1, 7);

    private final Setting<Integer> searchRadius =
            new Setting<>("Search Radius", 8, 2, 16);

    private final Setting<Integer> delaySetting =
            new Setting<>("Delay", 2, 0, 20);

    private final Setting<Boolean> prioritizeCobweb =
            new Setting<>("Prioritize Cobweb", false);

    /*
     * Current water target.
     */
    private BlockPos currentWater;

    /*
     * Cobweb target.
     */
    private BlockPos currentCobwebPlacement;

    private BlockPos cobwebSupport;

    private Direction cobwebFace;

    /*
     * Prevents repeatedly attacking the same block.
     */
    private final Map<BlockPos, Long> attempted =
            new ConcurrentHashMap<>();

    private int delay;

    /*
     * When >= 0, we have temporarily switched to this slot
     * and haven't restored the previous slot yet.
     */
    private int pendingRestoreSlot = -1;

    public static Timer inactivityTimer = new Timer();

    public AutoDrain() {
        super("AutoDrain", Category.COMBAT);
    }

    @Override
    public void onEnable() {

        attempted.clear();

        currentWater = null;

        currentCobwebPlacement = null;
        cobwebSupport = null;
        cobwebFace = null;

        delay = 0;

        pendingRestoreSlot = -1;

        findWater();
    }

    @Override
    public void onDisable() {

        /*
         * Restore the previous slot if we still have one.
         */
        restorePendingSlot();

        attempted.clear();

        currentWater = null;

        currentCobwebPlacement = null;
        cobwebSupport = null;
        cobwebFace = null;

        delay = 0;
        pendingRestoreSlot = -1;
    }

    @EventHandler
    public void onTick(EventTick event) {

        if (mc.player == null || mc.world == null)
            return;

        /*
         * Restore a slot from a previous interaction.
         *
         * We intentionally do this on a later tick instead of
         * immediately after sending the interaction.
         */
        if (pendingRestoreSlot != -1 && delay == 0) {

            restorePendingSlot();

            /*
             * Don't perform another interaction on the same tick.
             */
            return;
        }

        if (delay > 0) {
            delay--;
            return;
        }

        /*
         * ============================================================
         * WATER MODE (DISABLED - COMMENTED OUT)
         * ============================================================
         */
        /*
        if (drainMode.getValue() == DrainMode.Water) {

            if (currentWater == null
                    || !isWaterSource(currentWater)
                    || !inRange(currentWater)) {

                currentWater = findNearestWater();
            }

            if (currentWater != null) {

                if (pickUpWater(currentWater))
                    return;

                currentWater = null;
            }

            return;
        }
        */

        /*
         * ============================================================
         * COBWEB MODE
         * ============================================================
         */

        if (drainMode.getValue() == DrainMode.Cobweb) {

            if (currentWater == null
                    || !isWaterSource(currentWater)
                    || !inRange(currentWater)) {

                currentWater = findNearestWater();
            }

            if (currentWater == null)
                return;

            if (currentCobwebPlacement == null) {
                findCobwebPosition(currentWater);
            }

            if (currentCobwebPlacement != null) {

                if (placeCobweb())
                    return;

                clearCobwebTarget();
                currentWater = null;
            }

            return;
        }

        /*
         * ============================================================
         * BOTH MODE (DISABLED - COMMENTED OUT)
         * ============================================================
         */
        /*
        if (drainMode.getValue() == DrainMode.Both) {

            if (currentWater == null
                    || !isWaterSource(currentWater)
                    || !inRange(currentWater)) {

                currentWater = findNearestWater();
            }

            if (currentWater == null)
                return;

            if (prioritizeCobweb.getValue()) {

                if (currentCobwebPlacement == null) {
                    findCobwebPosition(currentWater);
                }

                if (currentCobwebPlacement != null) {

                    if (placeCobweb())
                        return;

                    clearCobwebTarget();
                }

                if (pickUpWater(currentWater))
                    return;

                currentWater = null;

                return;
            }

            if (hasEmptyBucket()) {

                if (pickUpWater(currentWater))
                    return;
            }

            if (currentCobwebPlacement == null) {
                findCobwebPosition(currentWater);
            }

            if (currentCobwebPlacement != null) {

                if (placeCobweb())
                    return;

                clearCobwebTarget();
            }

            currentWater = null;
        }
        */
    }

    /*
     * ============================================================
     * WATER
     * ============================================================
     */

    private boolean pickUpWater(BlockPos pos) {

        if (mc.player == null
                || mc.world == null
                || mc.interactionManager == null) {
            return false;
        }

        /*
         * Make sure this is actually a water source.
         */
        if (!isWaterSource(pos))
            return false;

        /*
         * Make sure we're close enough.
         */
        if (!inRange(pos))
            return false;

        /*
         * Find bucket.
         */
        int bucketSlot = getEmptyBucketSlot();

        if (bucketSlot == -1)
            return false;

        /*
         * Remember the current slot.
         */
        int previousSlot =
                mc.player.getInventory().selectedSlot;

        /*
         * If we're already holding the bucket, don't switch.
         */
        if (previousSlot != bucketSlot) {

            /*
             * Change the client inventory slot.
             */
            mc.player.getInventory().selectedSlot =
                    bucketSlot;

            /*
             * IMPORTANT:
             *
             * Explicitly tell the server that the selected
             * slot changed.
             */
            mc.player.networkHandler.sendPacket(
                    new UpdateSelectedSlotC2SPacket(bucketSlot)
            );
        }

        /*
         * Verify the client is holding a bucket.
         */
        ItemStack held =
                mc.player.getMainHandStack();

        if (held.isEmpty()
                || held.getItem() != Items.BUCKET) {

            /*
             * Restore if necessary.
             */
            if (previousSlot != bucketSlot) {

                mc.player.getInventory().selectedSlot =
                        previousSlot;

                mc.player.networkHandler.sendPacket(
                        new UpdateSelectedSlotC2SPacket(previousSlot)
                );
            }

            return false;
        }

        /*
         * ========================================================
         * WATER HIT
         * ========================================================
         *
         * Use the center of the water source.
         *
         * A bucket needs to interact with the source block itself.
         */
        Vec3d waterCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit =
                new BlockHitResult(
                        waterCenter,
                        Direction.UP,
                        pos,
                        false
                );

        /*
         * ========================================================
         * ACTUAL BUCKET INTERACTION
         * ========================================================
         *
         * Rotate to the water first, then interact.
         */
        float[] angle = InteractionUtility.calculateAngle(hit.getPos());
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(angle[0], angle[1], mc.player.isOnGround()));

        mc.player.swingHand(Hand.MAIN_HAND);

        InteractionUtility.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, id));

        /*
         * Mark the source.
         */
        attempted.put(
                pos.toImmutable(),
                System.currentTimeMillis()
        );

        /*
         * Clear target.
         */
        currentWater = null;

        /*
         * Wait for the server/world to process the interaction.
         */
        delay = Math.max(
                1,
                delaySetting.getValue()
        );

        inactivityTimer.reset();

        /*
         * IMPORTANT:
         *
         * DON'T immediately switch back.
         *
         * Keep the bucket selected until a later tick.
         */
        if (previousSlot != bucketSlot) {
            pendingRestoreSlot = previousSlot;
        }

        return true;
    }

    /*
     * Restore the slot from a previous interaction.
     */
    private void restorePendingSlot() {

        if (mc.player == null)
            return;

        if (pendingRestoreSlot < 0)
            return;

        if (pendingRestoreSlot > 8) {
            pendingRestoreSlot = -1;
            return;
        }

        mc.player.getInventory().selectedSlot =
                pendingRestoreSlot;

        mc.player.networkHandler.sendPacket(
                new UpdateSelectedSlotC2SPacket(
                        pendingRestoreSlot
                )
        );

        pendingRestoreSlot = -1;
    }

    /*
     * Find bucket in hotbar.
     */
    private int getEmptyBucketSlot() {

        if (mc.player == null)
            return -1;

        for (int i = 0; i < 9; i++) {

            ItemStack stack =
                    mc.player.getInventory().getStack(i);

            if (!stack.isEmpty()
                    && stack.getItem() == Items.BUCKET) {

                return i;
            }
        }

        return -1;
    }

    private boolean hasEmptyBucket() {
        return getEmptyBucketSlot() != -1;
    }

    /*
     * Check for an actual STILL water source.
     */
    private boolean isWaterSource(BlockPos pos) {

        if (pos == null || mc.world == null)
            return false;

        BlockState state =
                mc.world.getBlockState(pos);

        if (state.getBlock() != Blocks.WATER)
            return false;

        return state.getFluidState().isStill();
    }

    /*
     * ============================================================
     * COBWEB
     * ============================================================
     */

    private void findCobwebPosition(BlockPos water) {

        if (mc.world == null || mc.player == null)
            return;

        if (water == null)
            return;

        Direction[] directions = {
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST,
                Direction.UP,
                Direction.DOWN
        };

        for (Direction direction : directions) {

            BlockPos placePos =
                    water.offset(direction);

            if (!inRange(placePos))
                continue;

            if (!canPlaceCobwebAt(placePos))
                continue;

            if (!isValidSupportBlock(water))
                continue;

            if (recentlyAttempted(placePos))
                continue;

            currentCobwebPlacement =
                    placePos.toImmutable();

            cobwebSupport =
                    water.toImmutable();

            cobwebFace =
                    direction;

            return;
        }

        clearCobwebTarget();
    }

    private boolean canPlaceCobwebAt(BlockPos pos) {

        if (mc.world == null)
            return false;

        BlockState state =
                mc.world.getBlockState(pos);

        if (state.getBlock() == Blocks.COBWEB)
            return false;

        return state.isReplaceable();
    }

    private boolean isValidSupportBlock(BlockPos pos) {

        if (mc.world == null)
            return false;

        BlockState state =
                mc.world.getBlockState(pos);

        /*
         * Water source is allowed as the support.
         */
        if (state.getBlock() == Blocks.WATER) {
            return state.getFluidState().isStill();
        }

        if (state.isAir())
            return false;

        return !state.isReplaceable();
    }

    private int getCobwebSlot() {

        if (mc.player == null)
            return -1;

        ItemStack main =
                mc.player.getMainHandStack();

        if (!main.isEmpty()
                && main.getItem() == Items.COBWEB) {

            return mc.player.getInventory().selectedSlot;
        }

        for (int i = 0; i < 9; i++) {

            ItemStack stack =
                    mc.player.getInventory().getStack(i);

            if (!stack.isEmpty()
                    && stack.getItem() == Items.COBWEB) {

                return i;
            }
        }

        return -1;
    }

    private boolean placeCobweb() {

        if (mc.player == null
                || mc.world == null
                || mc.interactionManager == null) {
            return false;
        }

        if (currentCobwebPlacement == null)
            return false;

        if (cobwebSupport == null)
            return false;

        if (cobwebFace == null)
            return false;

        if (!canPlaceCobwebAt(currentCobwebPlacement)) {
            clearCobwebTarget();
            return false;
        }

        if (!isValidSupportBlock(cobwebSupport)) {
            clearCobwebTarget();
            return false;
        }

        int cobwebSlot =
                getCobwebSlot();

        if (cobwebSlot == -1)
            return false;

        int previousSlot =
                mc.player.getInventory().selectedSlot;

        /*
         * Use the normal inventory utility for cobwebs.
         */
        InventoryUtility.switchTo(cobwebSlot);

        ItemStack held =
                mc.player.getMainHandStack();

        if (held.isEmpty()
                || held.getItem() != Items.COBWEB) {

            InventoryUtility.switchTo(previousSlot);
            return false;
        }

        double x =
                cobwebSupport.getX()
                        + 0.5
                        + cobwebFace.getOffsetX() * 0.5;

        double y =
                cobwebSupport.getY()
                        + 0.5
                        + cobwebFace.getOffsetY() * 0.5;

        double z =
                cobwebSupport.getZ()
                        + 0.5
                        + cobwebFace.getOffsetZ() * 0.5;

        BlockHitResult hit =
                new BlockHitResult(
                        new Vec3d(x, y, z),
                        cobwebFace,
                        cobwebSupport,
                        false
                );

        /*
         * Place cobweb normally.
         */
        mc.interactionManager.interactBlock(
                mc.player,
                Hand.MAIN_HAND,
                hit
        );

        mc.player.swingHand(Hand.MAIN_HAND);

        attempted.put(
                currentCobwebPlacement.toImmutable(),
                System.currentTimeMillis()
        );

        delay = Math.max(
                1,
                delaySetting.getValue()
        );

        inactivityTimer.reset();

        clearCobwebTarget();

        if (drainMode.getValue() == DrainMode.Cobweb) {
            currentWater = null;
        }

        InventoryUtility.switchTo(previousSlot);

        return true;
    }

    private void clearCobwebTarget() {

        currentCobwebPlacement = null;
        cobwebSupport = null;
        cobwebFace = null;
    }

    /*
     * ============================================================
     * WATER SEARCH
     * ============================================================
     */

    private void findWater() {
        currentWater = findNearestWater();
    }

    private BlockPos findNearestWater() {

        if (mc.player == null || mc.world == null)
            return null;

        int radius =
                searchRadius.getValue();

        BlockPos playerPos =
                mc.player.getBlockPos();

        BlockPos nearest = null;

        double nearestDistance =
                Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {

            for (int y = -radius; y <= radius; y++) {

                for (int z = -radius; z <= radius; z++) {

                    BlockPos pos =
                            playerPos.add(x, y, z);

                    if (!isWaterSource(pos))
                        continue;

                    if (!inRange(pos))
                        continue;

                    if (recentlyAttempted(pos))
                        continue;

                    double distance =
                            mc.player.squaredDistanceTo(
                                    pos.getX() + 0.5,
                                    pos.getY() + 0.5,
                                    pos.getZ() + 0.5
                            );

                    if (distance < nearestDistance) {

                        nearestDistance = distance;
                        nearest = pos.toImmutable();
                    }
                }
            }
        }

        return nearest;
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

    private boolean inRange(BlockPos pos) {

        if (mc.player == null || pos == null)
            return false;

        double distance =
                mc.player.squaredDistanceTo(
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5
                );

        double max =
                range.getValue();

        return distance <= max * max;
    }

    private boolean recentlyAttempted(BlockPos pos) {

        if (pos == null)
            return false;

        Long timestamp =
                attempted.get(pos);

        if (timestamp == null)
            return false;

        /*
         * If the source is gone, forget it.
         */
        if (!isWaterSource(pos)) {

            attempted.remove(pos);
            return false;
        }

        if (System.currentTimeMillis() - timestamp > 750) {

            attempted.remove(pos);
            return false;
        }

        return true;
    }

    private void cleanupAttempted() {

        long now =
                System.currentTimeMillis();

        attempted.entrySet().removeIf(
                entry ->
                        now - entry.getValue() > 2000
        );
    }

    /*
     * ============================================================
     * RENDER
     * ============================================================
     */

    public void onRender3D(DrawContext context) {
        cleanupAttempted();
    }

    /*
     * ============================================================
     * MODE
     * ============================================================
     */

    private enum DrainMode {
        Cobweb
    }
}