package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.EventTick;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;
import org.jetbrains.annotations.Nullable;
import thunder.hack.core.manager.player.CombatManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static thunder.hack.utility.player.InteractionUtility.squaredDistanceFromEyes;

public final class AutoDrain extends Module {
    private final Setting<DrainMode> drainMode = new Setting<>("DrainMode", DrainMode.Cobweb);
    private final Setting<CombatManager.TargetBy> targetBy = new Setting<>("Target By", CombatManager.TargetBy.Distance);
    private final Setting<Boolean> targetMovingPlayers = new Setting<>("MovingPlayers", false);
    private final Setting<Integer> range = new Setting<>("Range", 5, 1, 7);
    private final Setting<Integer> placeWallRange = new Setting<>("WallRange", 5, 1, 7);
    private final Setting<PlaceTiming> placeTiming = new Setting<>("PlaceTiming", PlaceTiming.Default);
    private final Setting<Integer> blocksPerTick = new Setting<>("Block/Tick", 8, 1, 12, v -> placeTiming.getValue() == PlaceTiming.Default);
    private final Setting<Integer> placeDelay = new Setting<>("Delay/Place", 3, 0, 10);
    private final Setting<InteractionUtility.Interact> interact = new Setting<>("Interact", InteractionUtility.Interact.Strict);
    private final Setting<InteractionUtility.PlaceMode> placeMode = new Setting<>("PlaceMode", InteractionUtility.PlaceMode.Normal);
    private final Setting<InteractionUtility.Rotate> rotate = new Setting<>("Rotate", InteractionUtility.Rotate.None);
    private final Setting<SettingGroup> selection = new Setting<>("Selection", new SettingGroup(false, 0));
    private final Setting<Boolean> head = new Setting<>("Head", true).addToGroup(selection);
    private final Setting<Boolean> leggs = new Setting<>("Leggs", true).addToGroup(selection);
    private final Setting<Boolean> surround = new Setting<>("Surround", true).addToGroup(selection);
    private final Setting<Boolean> upperSurround = new Setting<>("UpperSurround", false).addToGroup(selection);
    private final Setting<SettingGroup> renderCategory = new Setting<>("Render", new SettingGroup(false, 0));
    private final Setting<RenderMode> renderMode = new Setting<>("Render Mode", RenderMode.Fade).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderFillColor = new Setting<>("Render Fill Color", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<ColorSetting> renderLineColor = new Setting<>("Render Line Color", new ColorSetting(HudEditor.getColor(0))).addToGroup(renderCategory);
    private final Setting<Integer> renderLineWidth = new Setting<>("Render Line Width", 2, 1, 5).addToGroup(renderCategory);
    private final Setting<Integer> effectDurationMs = new Setting<>("Effect Duration (MS)", 500, 0, 10000).addToGroup(renderCategory);
    private final Setting<Integer> waterSearchRadius = new Setting<>("WaterSearchRadius", 10, 3, 20);

    private final ArrayList<BlockPos> sequentialBlocks = new ArrayList<>();
    public static Timer inactivityTimer = new Timer();

    private final Map<BlockPos, Long> renderPoses = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> waterSources = new ConcurrentHashMap<>();

    private int delay = 0;
    private PlayerEntity target;
    private BlockPos currentWaterSource;

    public AutoDrain() {
        super("AutoDrain", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        sequentialBlocks.clear();
        renderPoses.clear();
        waterSources.clear();
        target = null;
        currentWaterSource = null;
        findWaterSources();
    }

    @Override
    protected boolean needNewTarget() {
        return target == null
                || target.distanceTo(mc.player) > range.getValue()
                || target.getHealth() + target.getAbsorptionAmount() <= 0
                || target.isDead();
    }

    @Override
    protected @Nullable PlayerEntity getTarget() {
        return Managers.COMBAT.getTarget(range.getValue(), targetBy.getValue(), p -> p.getVelocity().lengthSquared() < 0.08 || targetMovingPlayers.getValue());
    }

    private void findWaterSources() {
        waterSources.clear();
        int radius = waterSearchRadius.getValue();
        BlockPos playerPos = mc.player.getBlockPos();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    // Check if it's a water source block (not flowing water)
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.WATER) {
                        // Check if it's a source block (not flowing)
                        if (mc.world.getBlockState(pos).getFluidState().isStill()) {
                            // Check if it's not part of a natural lake/ocean by checking surroundings
                            if (isManMadeWaterSource(pos)) {
                                waterSources.put(pos, System.currentTimeMillis());
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isManMadeWaterSource(BlockPos pos) {
        // Check if the water is likely placed by a player rather than natural generation
        // Check if there's a solid block underneath
        BlockPos below = pos.down();
        BlockPos above = pos.up();
        BlockPos north = pos.north();
        BlockPos south = pos.south();
        BlockPos east = pos.east();
        BlockPos west = pos.west();
        
        // Natural water bodies usually have water on all sides at the same level
        // or are part of a larger body of water
        boolean hasWaterAbove = mc.world.getBlockState(above).getBlock() == Blocks.WATER;
        boolean hasWaterNorth = mc.world.getBlockState(north).getBlock() == Blocks.WATER;
        boolean hasWaterSouth = mc.world.getBlockState(south).getBlock() == Blocks.WATER;
        boolean hasWaterEast = mc.world.getBlockState(east).getBlock() == Blocks.WATER;
        boolean hasWaterWest = mc.world.getBlockState(west).getBlock() == Blocks.WATER;
        
        int waterNeighbors = 0;
        if (hasWaterNorth) waterNeighbors++;
        if (hasWaterSouth) waterNeighbors++;
        if (hasWaterEast) waterNeighbors++;
        if (hasWaterWest) waterNeighbors++;
        
        // If there's water above, it's likely part of a larger body (ocean/lake)
        if (hasWaterAbove) {
            return false;
        }
        
        // If the water has solid block below and not too many water neighbors,
        // it's likely player placed
        boolean hasSolidBelow = !mc.world.getBlockState(below).isAir() && mc.world.getBlockState(below).getBlock() != Blocks.WATER;
        
        // Also check if there's any non-water blocks around that would indicate artificial placement
        boolean hasNonWaterNeighbor = !hasWaterNorth || !hasWaterSouth || !hasWaterEast || !hasWaterWest;
        
        return hasSolidBelow && waterNeighbors <= 2 && hasNonWaterNeighbor;
    }

    private BlockPos getNearestWaterSource() {
        if (waterSources.isEmpty()) {
            findWaterSources();
            return null;
        }
        
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (BlockPos pos : waterSources.keySet()) {
            double dist = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        
        return nearest;
    }

    public void onRender3D(MatrixStack stack) {
        // Render water sources
        waterSources.forEach((pos, time) -> {
            if (System.currentTimeMillis() - time > 10000) {
                waterSources.remove(pos);
            } else {
                Render3DEngine.drawFilledBox(stack, new Box(pos), Render2DEngine.injectAlpha(0xFF00FF, 50));
                Render3DEngine.drawBoxOutline(new Box(pos), 0xFF00FF, 2);
            }
        });
        
        renderPoses.forEach((pos, time) -> {
            if (System.currentTimeMillis() - time > effectDurationMs.getValue()) {
                renderPoses.remove(pos);
            } else {
                switch (renderMode.getValue()) {
                    case Fade -> {
                        Render3DEngine.drawFilledBox(stack, new Box(pos), Render2DEngine.injectAlpha(renderFillColor.getValue().getColorObject(), (int) (100f * (1f - ((System.currentTimeMillis() - time) / 500f)))));
                        Render3DEngine.drawBoxOutline(new Box(pos), Render2DEngine.injectAlpha(renderLineColor.getValue().getColorObject(), (int) (100f * (1f - ((System.currentTimeMillis() - time) / 500f)))), renderLineWidth.getValue());
                    }
                    case Decrease -> {
                        float scale = 1 - (float) (System.currentTimeMillis() - time) / 500;
                        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());

                        Render3DEngine.drawFilledBox(stack, box.shrink(scale, scale, scale).offset(0.5 + scale * 0.5, 0.5 + scale * 0.5, 0.5 + scale * 0.5), Render2DEngine.injectAlpha(renderFillColor.getValue().getColorObject(), (int) (100f * (1f - ((System.currentTimeMillis() - time) / 500f)))));
                        Render3DEngine.drawBoxOutline(box.shrink(scale, scale, scale).offset(0.5 + scale * 0.5, 0.5 + scale * 0.5, 0.5 + scale * 0.5), renderLineColor.getValue().getColorObject(), renderLineWidth.getValue());
                    }
                }
            }
        });
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (needNewTarget()) {
            target = getTarget();
            if (target == null) return;
        }

        if (drainMode.getValue() == DrainMode.WaterBucket) {
            handleWaterDrain();
        } else if (drainMode.getValue() == DrainMode.Cobweb) {
            handleCobwebPlace();
        }
    }

    private void handleWaterDrain() {
        if (currentWaterSource == null || !isWaterSourceStillValid(currentWaterSource)) {
            currentWaterSource = getNearestWaterSource();
            if (currentWaterSource == null) {
                findWaterSources();
                return;
            }
        }

        BlockPos targetBlock = getSequentialPos();
        if (targetBlock == null) return;

        if (delay > 0) {
            delay--;
            return;
        }

        // Check if we have an empty bucket
        int emptyBucketSlot = getEmptyBucketSlot();
        if (emptyBucketSlot == -1) {
            // No empty bucket, try to use a water bucket and place it near the source
            handleWaterPlacement(targetBlock);
            return;
        }

        // Pick up water with empty bucket
        InventoryUtility.saveSlot();
        if (pickupWater(currentWaterSource, emptyBucketSlot)) {
            renderPoses.put(currentWaterSource, System.currentTimeMillis());
            delay = placeDelay.getValue();
            inactivityTimer.reset();
            // Remove the water source after picking it up
            waterSources.remove(currentWaterSource);
            currentWaterSource = null;
        }
        InventoryUtility.returnSlot();
    }

    private boolean isWaterSourceStillValid(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() == Blocks.WATER && 
               mc.world.getBlockState(pos).getFluidState().isStill();
    }

    private boolean pickupWater(BlockPos waterPos, int slot) {
        // Switch to empty bucket
        mc.player.getInventory().selectedSlot = slot;
        
        // Interact with the water source to pick it up
        Direction side = Direction.UP;
        BlockHitResult hitResult = new BlockHitResult(
            waterPos.toCenterPos(),
            side,
            waterPos,
            false
        );
        
        InteractionUtility.rightClickBlock(hitResult);
        return true;
    }

    private void handleWaterPlacement(BlockPos pos) {
        int waterBucketSlot = getWaterBucketSlot();
        if (waterBucketSlot == -1) return;

        InventoryUtility.saveSlot();
        if (InteractionUtility.placeBlock(pos, rotate.getValue(), interact.getValue(), placeMode.getValue(), waterBucketSlot, false, true)) {
            renderPoses.put(pos, System.currentTimeMillis());
            delay = placeDelay.getValue();
            inactivityTimer.reset();
        }
        InventoryUtility.returnSlot();
    }

    private void handleCobwebPlace() {
        BlockPos targetBlock = getSequentialPos();
        if (targetBlock == null) return;

        if (delay > 0) {
            delay--;
            return;
        }

        // Check if there's a water source nearby that we can place cobweb on
        BlockPos nearestWater = getNearestWaterSource();
        if (nearestWater != null && mc.player.squaredDistanceTo(nearestWater.getX() + 0.5, nearestWater.getY() + 0.5, nearestWater.getZ() + 0.5) < range.getValue() * range.getValue()) {
            // Place cobweb on the water source
            InventoryUtility.saveSlot();
            if (InteractionUtility.placeBlock(nearestWater, rotate.getValue(), interact.getValue(), placeMode.getValue(), getSlot(), false, true)) {
                renderPoses.put(nearestWater, System.currentTimeMillis());
                delay = placeDelay.getValue();
                inactivityTimer.reset();
                waterSources.remove(nearestWater);
            }
            InventoryUtility.returnSlot();
        } else {
            // Normal cobweb placement around target
            InventoryUtility.saveSlot();
            if (placeTiming.getValue() == PlaceTiming.Default) {
                int placed = 0;
                while (placed < blocksPerTick.getValue()) {
                    BlockPos blockPos = getSequentialPos();
                    if (blockPos == null) break;

                    if (InteractionUtility.placeBlock(blockPos, rotate.getValue(), interact.getValue(), placeMode.getValue(), getSlot(), false, true)) {
                        placed++;
                        renderPoses.put(blockPos, System.currentTimeMillis());
                        delay = placeDelay.getValue();
                        inactivityTimer.reset();
                    } else break;
                }
            } else if (placeTiming.getValue() == PlaceTiming.Vanilla) {
                if (InteractionUtility.placeBlock(targetBlock, rotate.getValue(), interact.getValue(), placeMode.getValue(), getSlot(), false, true)) {
                    sequentialBlocks.add(targetBlock);
                    renderPoses.put(targetBlock, System.currentTimeMillis());
                    delay = placeDelay.getValue();
                    inactivityTimer.reset();
                }
            }
            InventoryUtility.returnSlot();
        }
    }

    private BlockPos getSequentialPos() {
        if (target == null) return null;

        BlockPos targetBp = BlockPos.ofFloored(target.getPos());

        ArrayList<BlockPos> positions = new ArrayList<>();
        if (leggs.getValue())
            positions.add(targetBp);

        if (head.getValue())
            positions.add(targetBp.up());

        if (surround.getValue()) {
            positions.add(targetBp.east());
            positions.add(targetBp.west());
            positions.add(targetBp.south());
            positions.add(targetBp.north());
        }

        if (upperSurround.getValue()) {
            positions.add(targetBp.east().up());
            positions.add(targetBp.west().up());
            positions.add(targetBp.south().up());
            positions.add(targetBp.north().up());
        }

        for (BlockPos bp : positions) {
            BlockHitResult wallCheck = mc.world.raycast(new RaycastContext(InteractionUtility.getEyesPos(mc.player), bp.toCenterPos().offset(Direction.UP, 0.5f), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (wallCheck != null && wallCheck.getType() == HitResult.Type.BLOCK && wallCheck.getBlockPos() != bp)
                if (squaredDistanceFromEyes(bp.toCenterPos()) > placeWallRange.getPow2Value()) continue;
            if (InteractionUtility.canPlaceBlock(bp, interact.getValue(), true) && mc.world.getBlockState(bp).isReplaceable()) {
                return bp;
            }
        }

        return null;
    }

    private int getSlot() {
        List<Block> canUseBlocks = new ArrayList<>();
        
        if (drainMode.getValue() == DrainMode.Cobweb) {
            canUseBlocks.add(Blocks.COBWEB);
        } else if (drainMode.getValue() == DrainMode.WaterBucket) {
            return getWaterBucketSlot();
        }
        
        int slot = -1;
        final ItemStack mainhandStack = mc.player.getMainHandStack();
        if (mainhandStack != ItemStack.EMPTY && mainhandStack.getItem() instanceof BlockItem) {
            final Block blockFromMainhandItem = ((BlockItem) mainhandStack.getItem()).getBlock();
            if (canUseBlocks.contains(blockFromMainhandItem)) {
                slot = mc.player.getInventory().selectedSlot;
            }
        }
        if (slot == -1) {
            for (int i = 0; i < 9; i++) {
                final ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack != ItemStack.EMPTY && stack.getItem() instanceof BlockItem) {
                    final Block blockFromItem = ((BlockItem) stack.getItem()).getBlock();
                    if (canUseBlocks.contains(blockFromItem)) {
                        slot = i;
                        break;
                    }
                }
            }
        }
        return slot;
    }

    private int getWaterBucketSlot() {
        int slot = -1;
        final ItemStack mainhandStack = mc.player.getMainHandStack();
        if (mainhandStack != ItemStack.EMPTY && mainhandStack.getItem() == Items.WATER_BUCKET) {
            slot = mc.player.getInventory().selectedSlot;
        }
        if (slot == -1) {
            for (int i = 0; i < 9; i++) {
                final ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack != ItemStack.EMPTY && stack.getItem() == Items.WATER_BUCKET) {
                    slot = i;
                    break;
                }
            }
        }
        return slot;
    }

    private int getEmptyBucketSlot() {
        int slot = -1;
        final ItemStack mainhandStack = mc.player.getMainHandStack();
        if (mainhandStack != ItemStack.EMPTY && mainhandStack.getItem() == Items.BUCKET) {
            slot = mc.player.getInventory().selectedSlot;
        }
        if (slot == -1) {
            for (int i = 0; i < 9; i++) {
                final ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack != ItemStack.EMPTY && stack.getItem() == Items.BUCKET) {
                    slot = i;
                    break;
                }
            }
        }
        return slot;
    }

    private enum PlaceTiming {
        Default, Vanilla
    }

    private enum RenderMode {
        Fade, Decrease
    }

    private enum DrainMode {
        Cobweb, WaterBucket
    }
}