package thunder.hack.features.modules.combat;

import baritone.api.BaritoneAPI;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.jetbrains.annotations.NotNull;

import thunder.hack.ThunderHack;
import thunder.hack.core.Core;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.gui.notification.Notification;
import thunder.hack.injection.accesors.ILivingEntity;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.interfaces.IOtherClientPlayerEntity;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;
import thunder.hack.utility.render.animation.CaptureMark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.minecraft.util.UseAction.BLOCK;
import static net.minecraft.util.math.MathHelper.wrapDegrees;
import static thunder.hack.features.modules.client.ClientSettings.isRu;
import static thunder.hack.utility.math.MathUtility.random;

public class AuraV2 extends Module {

    /* =========================
       RANGE
       ========================= */

    public final Setting<Float> attackRange =
            new Setting<>("Range", 3.1f, 1f, 6.0f);

    public final Setting<Float> wallRange =
            new Setting<>("ThroughWallsRange", 3.1f, 0f, 6.0f);

    public final Setting<Boolean> elytra =
            new Setting<>("ElytraOverride", false);

    public final Setting<Float> elytraAttackRange =
            new Setting<>("ElytraRange", 3.1f, 1f, 6.0f,
                    v -> elytra.getValue());

    public final Setting<Float> elytraWallRange =
            new Setting<>("ElytraThroughWallsRange", 3.1f, 0f, 6.0f,
                    v -> elytra.getValue());

    public final Setting<WallsBypass> wallsBypass =
            new Setting<>("WallsBypass", WallsBypass.Off,
                    v -> getWallRange() > 0);

    public final Setting<Integer> fov =
            new Setting<>("FOV", 180, 1, 180);


    /* =========================
       ROTATION
       ========================= */

    public final Setting<Mode> rotationMode =
            new Setting<>("RotationMode", Mode.Track);

    public final Setting<Integer> interactTicks =
            new Setting<>("InteractTicks", 3, 1, 10,
                    v -> rotationMode.getValue() == Mode.Interact);


    /* =========================
       WEAPON
       ========================= */

    public final Setting<Switch> switchMode =
            new Setting<>("AutoWeapon", Switch.None);

    public final Setting<Boolean> onlyWeapon =
            new Setting<>("OnlyWeapon", false,
                    v -> switchMode.getValue() != Switch.Silent);


    /* =========================
       CRITICALS
       ========================= */

    public final Setting<BooleanSettingGroup> smartCrit =
            new Setting<>("SmartCrit", new BooleanSettingGroup(true));

    public final Setting<Boolean> onlySpace =
            new Setting<>("OnlyCrit", false)
                    .addToGroup(smartCrit);

    public final Setting<Boolean> autoJump =
            new Setting<>("AutoJump", false)
                    .addToGroup(smartCrit);


    /* =========================
       COMBAT
       ========================= */

    public final Setting<Boolean> shieldBreaker =
            new Setting<>("ShieldBreaker", true);

    public final Setting<Boolean> pauseWhileEating =
            new Setting<>("PauseWhileEating", false);

    public final Setting<Boolean> tpsSync =
            new Setting<>("TPSSync", false);

    public final Setting<Boolean> clientLook =
            new Setting<>("ClientLook", false);

    public final Setting<Boolean> pauseBaritone =
            new Setting<>("PauseBaritone", false);


    /* =========================
       OLD DELAY
       ========================= */

    public final Setting<BooleanSettingGroup> oldDelay =
            new Setting<>("OldDelay", new BooleanSettingGroup(false));

    public final Setting<Integer> minCPS =
            new Setting<>("MinCPS", 7, 1, 20)
                    .addToGroup(oldDelay);

    public final Setting<Integer> maxCPS =
            new Setting<>("MaxCPS", 12, 1, 20)
                    .addToGroup(oldDelay);


    /* =========================
       ESP
       ========================= */

    public final Setting<ESP> esp =
            new Setting<>("ESP", ESP.ThunderHack);

    public final Setting<SettingGroup> espGroup =
            new Setting<>("ESPSettings",
                    new SettingGroup(false, 0),
                    v -> esp.is(ESP.ThunderHackV2));

    public final Setting<Integer> espLength =
            new Setting<>("ESPLength", 14, 1, 40,
                    v -> esp.is(ESP.ThunderHackV2))
                    .addToGroup(espGroup);

    public final Setting<Integer> espFactor =
            new Setting<>("ESPFactor", 8, 1, 20,
                    v -> esp.is(ESP.ThunderHackV2))
                    .addToGroup(espGroup);

    public final Setting<Float> espShaking =
            new Setting<>("ESPShaking", 1.8f, 1.5f, 10f,
                    v -> esp.is(ESP.ThunderHackV2))
                    .addToGroup(espGroup);

    public final Setting<Float> espAmplitude =
            new Setting<>("ESPAmplitude", 3f, 0.1f, 8f,
                    v -> esp.is(ESP.ThunderHackV2))
                    .addToGroup(espGroup);


    /* =========================
       TARGET
       ========================= */

    public final Setting<Sort> sort =
            new Setting<>("Sort", Sort.LowestDistance);

    public final Setting<Boolean> lockTarget =
            new Setting<>("LockTarget", true);

    public final Setting<Boolean> elytraTarget =
            new Setting<>("ElytraTarget", true);


    /* =========================
       ADVANCED
       ========================= */

    public final Setting<SettingGroup> advanced =
            new Setting<>("Advanced",
                    new SettingGroup(false, 0));

    public final Setting<Float> aimRange =
            new Setting<>("AimRange", 3.1f, 0f, 6.0f)
                    .addToGroup(advanced);

    public final Setting<Boolean> randomHitDelay =
            new Setting<>("RandomHitDelay", false)
                    .addToGroup(advanced);

    public final Setting<Boolean> pauseInInventory =
            new Setting<>("PauseInInventory", true)
                    .addToGroup(advanced);

    public final Setting<Boolean> dropSprint =
            new Setting<>("DropSprint", true)
                    .addToGroup(advanced);

    public final Setting<Boolean> returnSprint =
            new Setting<>("ReturnSprint", true,
                    v -> dropSprint.getValue())
                    .addToGroup(advanced);

    public final Setting<Aura.RayTrace> rayTrace =
            new Setting<>("RayTrace", Aura.RayTrace.OnlyTarget)
                    .addToGroup(advanced);

    public final Setting<Boolean> grimRayTrace =
            new Setting<>("GrimRayTrace", true)
                    .addToGroup(advanced);

    public final Setting<Boolean> unpressShield =
            new Setting<>("UnpressShield", true)
                    .addToGroup(advanced);

    public final Setting<Boolean> deathDisable =
            new Setting<>("DisableOnDeath", true)
                    .addToGroup(advanced);

    public final Setting<Boolean> tpDisable =
            new Setting<>("TPDisable", false)
                    .addToGroup(advanced);

    public final Setting<Boolean> pullDown =
            new Setting<>("FastFall", false)
                    .addToGroup(advanced);

    public final Setting<Boolean> onlyJumpBoost =
            new Setting<>("OnlyJumpBoost", false,
                    v -> pullDown.getValue())
                    .addToGroup(advanced);

    public final Setting<Float> pullValue =
            new Setting<>("PullValue", 3f, 0f, 20f,
                    v -> pullDown.getValue())
                    .addToGroup(advanced);

    public final Setting<AttackHand> attackHand =
            new Setting<>("AttackHand", AttackHand.MainHand)
                    .addToGroup(advanced);

    public final Setting<Aura.Resolver> resolver =
            new Setting<>("Resolver", Aura.Resolver.Advantage)
                    .addToGroup(advanced);

    public final Setting<Integer> backTicks =
            new Setting<>("BackTicks", 4, 1, 20,
                    v -> resolver.is(Aura.Resolver.BackTrack))
                    .addToGroup(advanced);

    public final Setting<Boolean> resolverVisualisation =
            new Setting<>("ResolverVisualisation", false,
                    v -> !resolver.is(Aura.Resolver.Off))
                    .addToGroup(advanced);

    public final Setting<AccelerateOnHit> accelerateOnHit =
            new Setting<>("AccelerateOnHit", AccelerateOnHit.Off)
                    .addToGroup(advanced);

    public final Setting<Integer> minYawStep =
            new Setting<>("MinYawStep", 65, 1, 180)
                    .addToGroup(advanced);

    public final Setting<Integer> maxYawStep =
            new Setting<>("MaxYawStep", 75, 1, 180)
                    .addToGroup(advanced);

    public final Setting<Float> aimedPitchStep =
            new Setting<>("AimedPitchStep", 1f, 0f, 90f)
                    .addToGroup(advanced);

    public final Setting<Float> maxPitchStep =
            new Setting<>("MaxPitchStep", 8f, 1f, 90f)
                    .addToGroup(advanced);

    public final Setting<Float> pitchAccelerate =
            new Setting<>("PitchAccelerate", 1.65f, 1f, 10f)
                    .addToGroup(advanced);

    public final Setting<Float> attackCooldown =
            new Setting<>("AttackCooldown", 0.9f, 0.5f, 1f)
                    .addToGroup(advanced);

    public final Setting<Float> attackBaseTime =
            new Setting<>("AttackBaseTime", 0.5f, 0f, 2f)
                    .addToGroup(advanced);

    public final Setting<Integer> attackTickLimit =
            new Setting<>("AttackTickLimit", 11, 0, 20)
                    .addToGroup(advanced);

    public final Setting<Float> critFallDistance =
            new Setting<>("CritFallDistance", 0f, 0f, 1f)
                    .addToGroup(advanced);


    /* =========================
       TARGET FILTERS
       ========================= */

    public final Setting<SettingGroup> targets =
            new Setting<>("Targets",
                    new SettingGroup(false, 0));

    public final Setting<Boolean> Players =
            new Setting<>("Players", true)
                    .addToGroup(targets);

    public final Setting<Boolean> Mobs =
            new Setting<>("Mobs", true)
                    .addToGroup(targets);

    public final Setting<Boolean> Animals =
            new Setting<>("Animals", true)
                    .addToGroup(targets);

    public final Setting<Boolean> Villagers =
            new Setting<>("Villagers", true)
                    .addToGroup(targets);

    public final Setting<Boolean> Slimes =
            new Setting<>("Slimes", true)
                    .addToGroup(targets);

    public final Setting<Boolean> hostiles =
            new Setting<>("Hostiles", true)
                    .addToGroup(targets);

    public final Setting<Boolean> onlyAngry =
            new Setting<>("OnlyAngryHostiles", true,
                    v -> hostiles.getValue())
                    .addToGroup(targets);

    public final Setting<Boolean> Projectiles =
            new Setting<>("Projectiles", true)
                    .addToGroup(targets);

    public final Setting<Boolean> ignoreInvisible =
            new Setting<>("IgnoreInvisibleEntities", false)
                    .addToGroup(targets);

    public final Setting<Boolean> ignoreNamed =
            new Setting<>("IgnoreNamed", false)
                    .addToGroup(targets);

    public final Setting<Boolean> ignoreTeam =
            new Setting<>("IgnoreTeam", false)
                    .addToGroup(targets);

    public final Setting<Boolean> ignoreCreative =
            new Setting<>("IgnoreCreative", true)
                    .addToGroup(targets);

    public final Setting<Boolean> ignoreNaked =
            new Setting<>("IgnoreNaked", false)
                    .addToGroup(targets);

    public final Setting<Boolean> ignoreShield =
            new Setting<>("AttackShieldingEntities", true)
                    .addToGroup(targets);


    /* =========================
       STATE
       ========================= */

    public static Entity target;

    public float rotationYaw;
    public float rotationPitch;

    public float pitchAcceleration = 1f;

    private Vec3d rotationPoint = Vec3d.ZERO;
    private Vec3d rotationMotion = Vec3d.ZERO;

    private int hitTicks;
    private int trackticks;

    private boolean lookingAtHitbox;

    private final Timer delayTimer = new Timer();
    private final Timer pauseTimer = new Timer();

    public Box resolvedBox;

    private static boolean wasTargeted = false;


    public AuraV2() {
        super("AuraV2", Category.COMBAT);
    }


    private float getRange() {
        if (mc.player == null)
            return attackRange.getValue();

        if (elytra.getValue() && mc.player.isFallFlying())
            return elytraAttackRange.getValue();

        return attackRange.getValue();
    }


    private float getWallRange() {
        if (mc.player == null)
            return wallRange.getValue();

        if (elytra.getValue() && mc.player.isFallFlying())
            return elytraWallRange.getValue();

        return wallRange.getValue();
    }


    private void auraLogic() {
        if (mc.player == null
                || mc.world == null
                || mc.interactionManager == null) {

            clearTarget();
            return;
        }

        if (!haveWeapon()) {
            clearTarget();
            return;
        }

        handleKill();
        updateTarget();

        if (target == null) {
            lookingAtHitbox = false;
            return;
        }

        if (autoJump.getValue()
                && !mc.options.jumpKey.isPressed()
                && mc.player.isOnGround()) {

            mc.player.jump();
        }

        boolean canAttack = autoCrit();

        calcRotations(canAttack);

        boolean readyForAttack =
                canAttack
                        && (lookingAtHitbox || skipRayTraceCheck());

        if (!readyForAttack)
            return;

        if (shieldBreaker(false))
            return;

        boolean[] state = preAttack();

        try {
            boolean shieldTarget =
                    target instanceof PlayerEntity player
                            && player.isUsingItem()
                            && (player.getOffHandStack().isOf(Items.SHIELD)
                            || player.getMainHandStack().isOf(Items.SHIELD));

            if (!shieldTarget || ignoreShield.getValue())
                attack();

        } finally {
            postAttack(state[0], state[1]);
        }
    }


    private boolean haveWeapon() {
        if (mc.player == null)
            return false;

        if (!onlyWeapon.getValue())
            return true;

        Item item = mc.player.getMainHandStack().getItem();

        if (switchMode.getValue() == Switch.None) {
            return item instanceof SwordItem
                    || item instanceof AxeItem
                    || item instanceof TridentItem;
        }

        return InventoryUtility.getSwordHotBar().found()
                || InventoryUtility.getAxeHotBar().found();
    }


    private boolean skipRayTraceCheck() {
        if (mc.player == null || mc.world == null)
            return true;

        if (rotationMode.is(Mode.None))
            return true;

        if (rayTrace.is(Aura.RayTrace.OFF))
            return true;

        if (rotationMode.is(Mode.Grim))
            return true;

        if (rotationMode.is(Mode.Interact)) {
            if (interactTicks.getValue() <= 1)
                return true;

            return mc.world.getBlockCollisions(
                    mc.player,
                    mc.player.getBoundingBox()
                            .expand(-0.25, 0.0, -0.25)
                            .offset(0.0, 1.0, 0.0)
            ).iterator().hasNext();
        }

        return false;
    }


    private void attack() {
        if (mc.player == null
                || mc.interactionManager == null
                || target == null
                || !target.isAlive())
            return;

        int previousSlot = -1;

        Criticals.cancelCrit = true;

        try {
            ModuleManager.criticals.doCrit();

            previousSlot = switchMethod();

            mc.interactionManager.attackEntity(mc.player, target);

            swingHand();

            hitTicks = getHitTicks();

            delayTimer.reset();

        } finally {
            Criticals.cancelCrit = false;

            if (previousSlot != -1)
                InventoryUtility.switchTo(previousSlot);
        }
    }


    private int switchMethod() {
        if (mc.player == null
                || switchMode.is(Switch.None))
            return -1;

        SearchInvResult weapon;

        weapon = InventoryUtility.getSwordHotBar();

        if (target instanceof PlayerEntity player
                && player.isUsingItem()
                && (player.getMainHandStack().isOf(Items.SHIELD)
                || player.getOffHandStack().isOf(Items.SHIELD))) {

            SearchInvResult axe = InventoryUtility.getAxe();

            if (axe.found())
                weapon = axe;
        }

        if (!weapon.found()) {
            SearchInvResult axe =
                    InventoryUtility.getAxeHotBar();

            if (axe.found())
                weapon = axe;
        }

        if (!weapon.found())
            return -1;

        int previousSlot =
                switchMode.is(Switch.Silent)
                        ? mc.player.getInventory().selectedSlot
                        : -1;

        weapon.switchTo();

        return previousSlot;
    }


    private int getHitTicks() {
        if (oldDelay.getValue().isEnabled()) {

            int min = Math.min(
                    minCPS.getValue(),
                    maxCPS.getValue()
            );

            int max = Math.max(
                    minCPS.getValue(),
                    maxCPS.getValue()
            );

            return 1 + (int) (
                    20f / random(min, max)
            );
        }

        if (shouldRandomizeDelay())
            return (int) MathUtility.random(11, 13);

        return attackTickLimit.getValue();
    }


    private boolean @NotNull [] preAttack() {

        if (mc.player == null)
            return new boolean[]{false, false};

        boolean blocking =
                mc.player.isUsingItem()
                        && mc.player.getActiveItem()
                        .getItem()
                        .getUseAction(mc.player.getActiveItem()) == BLOCK;

        if (blocking && unpressShield.getValue()) {

            sendPacket(
                    new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                            BlockPos.ORIGIN,
                            Direction.DOWN
                    )
            );
        }

        boolean sprint = Core.serverSprint;

        if (sprint && dropSprint.getValue())
            disableSprint();

        if (rotationMode.is(Mode.Grim)) {

            sendPacket(
                    new PlayerMoveC2SPacket.Full(
                            mc.player.getX(),
                            mc.player.getY(),
                            mc.player.getZ(),
                            rotationYaw,
                            rotationPitch,
                            mc.player.isOnGround()
                    )
            );
        }

        return new boolean[]{blocking, sprint};
    }


    private void postAttack(
            boolean blocking,
            boolean sprint) {

        if (mc.player == null)
            return;

        if (sprint
                && returnSprint.getValue()
                && dropSprint.getValue()) {

            enableSprint();
        }

        if (blocking && unpressShield.getValue()) {

            sendSequencedPacket(id ->
                    new PlayerInteractItemC2SPacket(
                            Hand.OFF_HAND,
                            id,
                            rotationYaw,
                            rotationPitch
                    ));
        }

        if (rotationMode.is(Mode.Grim)) {

            sendPacket(
                    new PlayerMoveC2SPacket.Full(
                            mc.player.getX(),
                            mc.player.getY(),
                            mc.player.getZ(),
                            mc.player.getYaw(),
                            mc.player.getPitch(),
                            mc.player.isOnGround()
                    )
            );
        }
    }


    private void disableSprint() {
        if (mc.player == null)
            return;

        mc.player.setSprinting(false);
        mc.options.sprintKey.setPressed(false);

        sendPacket(
                new ClientCommandC2SPacket(
                        mc.player,
                        ClientCommandC2SPacket.Mode.STOP_SPRINTING
                )
        );
    }


    private void enableSprint() {
        if (mc.player == null)
            return;

        mc.player.setSprinting(true);
        mc.options.sprintKey.setPressed(true);

        sendPacket(
                new ClientCommandC2SPacket(
                        mc.player,
                        ClientCommandC2SPacket.Mode.START_SPRINTING
                )
        );
    }


    private void swingHand() {
        if (mc.player == null)
            return;

        switch (attackHand.getValue()) {
            case MainHand ->
                    mc.player.swingHand(Hand.MAIN_HAND);

            case OffHand ->
                    mc.player.swingHand(Hand.OFF_HAND);

            case None -> {
            }
        }
    }


    private boolean shieldBreaker(boolean instant) {

        if (!shieldBreaker.getValue()
                || mc.player == null
                || mc.interactionManager == null
                || !(target instanceof PlayerEntity player))
            return false;

        boolean shielding =
                player.isUsingItem()
                        && (player.getOffHandStack().isOf(Items.SHIELD)
                        || player.getMainHandStack().isOf(Items.SHIELD));

        if (!shielding && !instant)
            return false;

        SearchInvResult axeResult =
                InventoryUtility.getAxe();

        if (!axeResult.found())
            return false;

        int axeSlot = axeResult.slot();

        if (axeSlot < 0)
            return false;

        int previousSlot =
                mc.player.getInventory().selectedSlot;

        try {

            if (axeSlot >= 9) {

                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        axeSlot,
                        previousSlot,
                        SlotActionType.SWAP,
                        mc.player
                );

                sendPacket(
                        new CloseHandledScreenC2SPacket(
                                mc.player.currentScreenHandler.syncId
                        )
                );

                mc.interactionManager.attackEntity(
                        mc.player,
                        player
                );

                mc.player.swingHand(Hand.MAIN_HAND);

                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        axeSlot,
                        previousSlot,
                        SlotActionType.SWAP,
                        mc.player
                );

                sendPacket(
                        new CloseHandledScreenC2SPacket(
                                mc.player.currentScreenHandler.syncId
                        )
                );

            } else {

                sendPacket(
                        new UpdateSelectedSlotC2SPacket(
                                axeSlot
                        )
                );

                mc.interactionManager.attackEntity(
                        mc.player,
                        player
                );

                mc.player.swingHand(Hand.MAIN_HAND);

                sendPacket(
                        new UpdateSelectedSlotC2SPacket(
                                previousSlot
                        )
                );
            }

        } finally {

            hitTicks = 10;
            delayTimer.reset();
        }

        return true;
    }


    private boolean autoCrit() {

        if (mc.player == null)
            return false;

        if (hitTicks > 0)
            return false;

        if (pauseInInventory.getValue()
                && Managers.PLAYER.inInventory)
            return false;

        if (!smartCrit.getValue().isEnabled())
            return true;

        if (mc.player.getAbilities().flying)
            return true;

        if (mc.player.isFallFlying()
                || ModuleManager.elytraPlus.isEnabled())
            return true;

        if (mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING))
            return true;

        if (Managers.PLAYER.isInWeb())
            return true;

        if (!oldDelay.getValue().isEnabled()
                && getAttackCooldown() < attackCooldown.getValue())
            return false;

        if (ModuleManager.criticals.isEnabled()
                && ModuleManager.criticals.mode.is(Criticals.Mode.Grim))
            return true;

        boolean targetStrafe =
                !ModuleManager.targetStrafe.isEnabled()
                        || !ModuleManager.targetStrafe.jump.getValue();

        boolean speed =
                !ModuleManager.speed.isEnabled()
                        || mc.player.isOnGround();

        if (!mc.options.jumpKey.isPressed()
                && targetStrafe
                && speed
                && !onlySpace.getValue()
                && !autoJump.getValue())
            return true;

        if (mc.player.isInLava()
                || mc.player.isSubmergedInWater())
            return true;

        if (!mc.options.jumpKey.isPressed()
                && isAboveWater())
            return true;

        if (mc.player.fallDistance > 1f
                && mc.player.fallDistance < 1.14f)
            return false;

        if (mc.player.isOnGround())
            return false;

        return mc.player.fallDistance >
                (shouldRandomizeFallDistance()
                        ? MathUtility.random(0.15f, 0.7f)
                        : critFallDistance.getValue());
    }


    @EventHandler
    public void onUpdate(PlayerUpdateEvent event) {

        if (mc.player == null || mc.world == null)
            return;

        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.player.isUsingItem()
                && pauseWhileEating.getValue()) {

            clearTarget();
            return;
        }

        handleBaritone();

        if (hitTicks > 0)
            hitTicks--;

        resolvePlayers();

        try {
            auraLogic();
        } finally {
            restorePlayers();
        }
    }


    private void handleBaritone() {

        if (!pauseBaritone.getValue()
                || !ThunderHack.baritone)
            return;

        boolean targeted = target != null;

        if (targeted && !wasTargeted) {

            BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getCommandManager()
                    .execute("pause");

            wasTargeted = true;

        } else if (!targeted && wasTargeted) {

            BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getCommandManager()
                    .execute("resume");

            wasTargeted = false;
        }
    }


    @EventHandler
    public void onSync(EventSync event) {

        if (mc.player == null
                || mc.world == null
                || !pauseTimer.passedMs(1000))
            return;

        if (mc.player.isUsingItem()
                && pauseWhileEating.getValue())
            return;

        if (oldDelay.getValue().isEnabled()
                && minCPS.getValue() > maxCPS.getValue()) {

            minCPS.setValue(maxCPS.getValue());
        }

        if (!haveWeapon()) {

            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();

            return;
        }

        if (target != null
                && rotationMode.getValue() != Mode.None
                && rotationMode.getValue() != Mode.Grim) {

            mc.player.setYaw(rotationYaw);
            mc.player.setPitch(rotationPitch);

        } else {

            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();
        }

        if (target != null
                && pullDown.getValue()
                && (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)
                || !onlyJumpBoost.getValue())) {

            mc.player.addVelocity(
                    0f,
                    -pullValue.getValue() / 1000f,
                    0f
            );
        }
    }


    @EventHandler
    public void onPacketSend(
            PacketEvent.@NotNull Send event) {

        if (event.getPacket()
                instanceof PlayerInteractEntityC2SPacket packet) {

            if (Criticals.getInteractType(packet)
                    != Criticals.InteractType.ATTACK
                    && target != null) {

                event.cancel();
            }
        }
    }


    @EventHandler
    public void onPacketReceive(
            PacketEvent.@NotNull Receive event) {

        if (mc.world == null || mc.player == null)
            return;

        if (event.getPacket()
                instanceof EntityStatusS2CPacket status) {

            Entity entity = status.getEntity(mc.world);

            if (status.getStatus() == 30
                    && entity != null
                    && target != null
                    && entity == target) {

                Managers.NOTIFICATION.publicity(
                        "Aura",
                        isRu()
                                ? "Успешно сломали щит игроку "
                                + target.getName().getString()
                                : "Successfully destroyed "
                                + target.getName().getString()
                                + "'s shield",
                        2,
                        Notification.Type.SUCCESS
                );
            }

            if (status.getStatus() == 3
                    && entity == mc.player
                    && deathDisable.getValue()) {

                disable(
                        isRu()
                                ? "Отключаю из-за смерти!"
                                : "Disabling due to death!"
                );
            }
        }

        if (event.getPacket()
                instanceof PlayerPositionLookS2CPacket
                && tpDisable.getValue()) {

            disable(
                    isRu()
                            ? "Отключаю из-за телепортации!"
                            : "Disabling due to tp!"
            );
        }
    }


    private void updateTarget() {

        Entity candidate = findTarget();

        if (candidate == null) {

            if (target == null || skipEntity(target))
                clearTarget();

            return;
        }

        if (target == null || skipEntity(target)) {

            target = candidate;
            resetAimPoint();

            return;
        }

        if (candidate instanceof ProjectileEntity) {

            if (target != candidate)
                resetAimPoint();

            target = candidate;
            return;
        }

        if (!lockTarget.getValue()
                || sort.is(Sort.FOV)
                || !isInRange(target)) {

            if (target != candidate)
                resetAimPoint();

            target = candidate;
        }
    }


    private void clearTarget() {
        target = null;
        lookingAtHitbox = false;
        resetAimPoint();
    }


    private void resetAimPoint() {
        rotationPoint = Vec3d.ZERO;
        rotationMotion = Vec3d.ZERO;
        pitchAcceleration = 1f;
    }


    private void handleKill() {

        if (!(target instanceof LivingEntity living))
            return;

        if (living.getHealth() <= 0
                || living.isDead()) {

            Managers.NOTIFICATION.publicity(
                    "Aura",
                    isRu()
                            ? "Цель успешно нейтрализована!"
                            : "Target successfully neutralized!",
                    3,
                    Notification.Type.SUCCESS
            );

            clearTarget();
        }
    }


    private void calcRotations(boolean ready) {

        if (mc.player == null
                || mc.world == null
                || target == null) {

            lookingAtHitbox = false;
            return;
        }

        if (ready) {

            boolean blocked =
                    mc.world.getBlockCollisions(
                            mc.player,
                            mc.player.getBoundingBox()
                                    .expand(-0.25, 0.0, -0.25)
                                    .offset(0.0, 1.0, 0.0)
                    ).iterator().hasNext();

            trackticks =
                    blocked
                            ? 1
                            : interactTicks.getValue();

        } else if (trackticks > 0) {

            trackticks--;
        }

        Vec3d targetVec;

        if (mc.player.isFallFlying()
                || ModuleManager.elytraPlus.isEnabled()) {

            targetVec = target.getEyePos();

        } else {

            targetVec = getLegitLook(target);
        }

        if (targetVec == null) {

            lookingAtHitbox = false;
            return;
        }

        boolean visible =
                Managers.PLAYER.checkRtx(
                        rotationYaw,
                        rotationPitch,
                        getRange() + aimRange.getValue(),
                        getRange() + aimRange.getValue(),
                        rayTrace.getValue()
                );

        if (visible) {

            pitchAcceleration =
                    aimedPitchStep.getValue();

        } else {

            pitchAcceleration =
                    Math.min(
                            maxPitchStep.getValue(),
                            Math.max(
                                    aimedPitchStep.getValue(),
                                    pitchAcceleration
                                            * pitchAccelerate.getValue()
                            )
                    );
        }

        double dx =
                targetVec.x - mc.player.getX();

        double dy =
                targetVec.y
                        - (mc.player.getY()
                        + mc.player.getEyeHeight(
                        mc.player.getPose()
                ));

        double dz =
                targetVec.z - mc.player.getZ();

        float desiredYaw =
                (float) (
                        Math.toDegrees(
                                Math.atan2(dz, dx)
                        ) - 90.0
                );

        float desiredPitch =
                (float) -Math.toDegrees(
                        Math.atan2(
                                dy,
                                Math.sqrt(
                                        dx * dx + dz * dz
                                )
                        )
                );

        float deltaYaw =
                wrapDegrees(
                        desiredYaw - rotationYaw
                );

        float deltaPitch =
                desiredPitch - rotationPitch;

        if (wallsBypass.is(WallsBypass.V2)
                && !ready
                && !mc.player.canSee(target)) {

            deltaYaw =
                    wrapDegrees(
                            deltaYaw + 20f
                    );
        }

        float yawStep;

        if (rotationMode.getValue() != Mode.Track) {

            yawStep = 360f;

        } else {

            int min =
                    Math.min(
                            minYawStep.getValue(),
                            maxYawStep.getValue()
                    );

            int max =
                    Math.max(
                            minYawStep.getValue(),
                            maxYawStep.getValue()
                    );

            yawStep =
                    Math.max(
                            1f,
                            random(min, max)
                    );
        }

        float pitchStep;

        if (rotationMode.getValue() != Mode.Track) {

            pitchStep = 180f;

        } else if (Managers.PLAYER.ticksElytraFlying > 5) {

            pitchStep = 180f;

        } else {

            pitchStep =
                    Math.max(
                            1f,
                            pitchAcceleration
                                    + random(-1f, 1f)
                    );
        }

        if (ready) {

            switch (accelerateOnHit.getValue()) {

                case Yaw -> yawStep = 180f;

                case Pitch -> pitchStep = 90f;

                case Both -> {
                    yawStep = 180f;
                    pitchStep = 90f;
                }

                case Off -> {
                }
            }
        }

        float appliedYaw =
                MathHelper.clamp(
                        deltaYaw,
                        -yawStep,
                        yawStep
                );

        float appliedPitch =
                MathHelper.clamp(
                        deltaPitch,
                        -pitchStep,
                        pitchStep
                );

        float newYaw =
                rotationYaw + appliedYaw;

        float newPitch =
                MathHelper.clamp(
                        rotationPitch + appliedPitch,
                        -90f,
                        90f
                );

        double gcdFix =
                Math.max(
                        0.0001,
                        Math.pow(
                                mc.options
                                        .getMouseSensitivity()
                                        .getValue()
                                        * 0.6
                                        + 0.2,
                                3.0
                        ) * 1.2
                );

        if (trackticks > 0
                || rotationMode.is(Mode.Track)) {

            rotationYaw =
                    (float) (
                            newYaw
                                    - (newYaw - rotationYaw)
                                    % gcdFix
                    );

            rotationPitch =
                    (float) (
                            newPitch
                                    - (newPitch - rotationPitch)
                                    % gcdFix
                    );

        } else {

            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();
        }

        rotationPitch =
                MathHelper.clamp(
                        rotationPitch,
                        -90f,
                        90f
                );

        if (!rotationMode.is(Mode.Grim))
            ModuleManager.rotations.fixRotation =
                    rotationYaw;

        lookingAtHitbox =
                Managers.PLAYER.checkRtx(
                        rotationYaw,
                        rotationPitch,
                        getRange(),
                        getWallRange(),
                        rayTrace.getValue()
                );
    }


    public Vec3d getLegitLook(Entity entity) {

        if (mc.player == null || entity == null)
            return null;

        double lengthX =
                entity.getBoundingBox().getLengthX();

        double lengthY =
                entity.getBoundingBox().getLengthY();

        double lengthZ =
                entity.getBoundingBox().getLengthZ();

        if (rotationMotion.equals(Vec3d.ZERO)) {

            rotationMotion =
                    new Vec3d(
                            random(-0.03f, 0.03f),
                            random(-0.02f, 0.02f),
                            random(-0.03f, 0.03f)
                    );
        }

        rotationPoint =
                rotationPoint.add(rotationMotion);

        double halfX =
                Math.max(
                        0.01,
                        (lengthX - 0.05) / 2.0
                );

        double halfZ =
                Math.max(
                        0.01,
                        (lengthZ - 0.05) / 2.0
                );

        if (rotationPoint.x >= halfX
                || rotationPoint.x <= -halfX) {

            rotationMotion =
                    new Vec3d(
                            -rotationMotion.x,
                            rotationMotion.y,
                            rotationMotion.z
                    );
        }

        if (rotationPoint.y >= lengthY
                || rotationPoint.y <= 0.05) {

            rotationMotion =
                    new Vec3d(
                            rotationMotion.x,
                            -rotationMotion.y,
                            rotationMotion.z
                    );
        }

        if (rotationPoint.z >= halfZ
                || rotationPoint.z <= -halfZ) {

            rotationMotion =
                    new Vec3d(
                            rotationMotion.x,
                            rotationMotion.y,
                            -rotationMotion.z
                    );
        }

        rotationPoint =
                new Vec3d(
                        MathHelper.clamp(
                                rotationPoint.x
                                        + random(-0.01f, 0.01f),
                                -halfX,
                                halfX
                        ),

                        MathHelper.clamp(
                                rotationPoint.y,
                                0.05,
                                Math.max(
                                        0.05,
                                        lengthY
                                )
                        ),

                        MathHelper.clamp(
                                rotationPoint.z
                                        + random(-0.01f, 0.01f),
                                -halfZ,
                                halfZ
                        )
                );

        if (!mc.player.canSee(entity)
                && wallsBypass.is(WallsBypass.V1)) {

            return entity.getPos().add(
                    random(-0.15, 0.15),
                    lengthY,
                    random(-0.15, 0.15)
            );
        }

        Vec3d center =
                entity.getPos().add(
                        0,
                        entity.getEyeHeight(
                                entity.getPose()
                        ) * 0.5,
                        0
                );

        if (PlayerUtility.squaredDistanceFromEyes(center)
                <= attackRange.getPow2Value()) {

            float[] rotation =
                    Managers.PLAYER.calcAngle(center);

            if (Managers.PLAYER.checkRtx(
                    rotation[0],
                    rotation[1],
                    getRange(),
                    0,
                    rayTrace.getValue()
            )) {

                rotationPoint =
                        new Vec3d(
                                random(-0.1f, 0.1f),
                                entity.getEyeHeight(
                                        entity.getPose()
                                ) / random(1.8f, 2.5f),
                                random(-0.1f, 0.1f)
                        );

                return entity
                        .getPos()
                        .add(rotationPoint);
            }
        }

        double halfBoxX =
                lengthX / 2.0;

        double halfBoxZ =
                lengthZ / 2.0;

        for (double x = -halfBoxX;
             x <= halfBoxX;
             x += 0.15) {

            for (double z = -halfBoxZ;
                 z <= halfBoxZ;
                 z += 0.15) {

                for (double y = 0.05;
                     y <= lengthY;
                     y += 0.20) {

                    Vec3d point =
                            entity.getPos().add(
                                    x,
                                    y,
                                    z
                            );

                    if (PlayerUtility.squaredDistanceFromEyes(point)
                            > attackRange.getPow2Value())
                        continue;

                    float[] rotation =
                            Managers.PLAYER.calcAngle(point);

                    if (Managers.PLAYER.checkRtx(
                            rotation[0],
                            rotation[1],
                            getRange(),
                            0,
                            rayTrace.getValue()
                    )) {

                        rotationPoint =
                                new Vec3d(
                                        x,
                                        y,
                                        z
                                );

                        return point;
                    }
                }
            }
        }

        return entity.getPos()
                .add(rotationPoint);
    }


    public boolean isInRange(Entity entity) {

        if (mc.player == null
                || entity == null
                || !entity.isAlive())
            return false;

        double maxDistance =
                getSquaredRotateDistance();

        if (maxDistance <= 0)
            return false;

        Vec3d eyePoint =
                entity.getEyePos();

        if (PlayerUtility.squaredDistanceFromEyes(
                eyePoint
        ) <= maxDistance) {

            float[] rotation =
                    Managers.PLAYER.calcAngle(
                            eyePoint
                    );

            if (Managers.PLAYER.checkRtx(
                    rotation[0],
                    rotation[1],
                    (float) Math.sqrt(maxDistance),
                    getWallRange(),
                    rayTrace.getValue()
            ))
                return true;
        }

        double halfX =
                entity.getBoundingBox()
                        .getLengthX() / 2.0;

        double halfZ =
                entity.getBoundingBox()
                        .getLengthZ() / 2.0;

        double height =
                entity.getBoundingBox()
                        .getLengthY();

        for (double x = -halfX;
             x <= halfX;
             x += 0.20) {

            for (double z = -halfZ;
                 z <= halfZ;
                 z += 0.20) {

                for (double y = 0.05;
                     y <= height;
                     y += 0.30) {

                    Vec3d point =
                            entity.getPos()
                                    .add(x, y, z);

                    if (PlayerUtility
                            .squaredDistanceFromEyes(point)
                            > maxDistance)
                        continue;

                    float[] rotation =
                            Managers.PLAYER
                                    .calcAngle(point);

                    if (Managers.PLAYER.checkRtx(
                            rotation[0],
                            rotation[1],
                            (float) Math.sqrt(maxDistance),
                            getWallRange(),
                            rayTrace.getValue()
                    ))
                        return true;
                }
            }
        }

        return false;
    }


    public Entity findTarget() {

        if (mc.world == null || mc.player == null)
            return null;

        List<LivingEntity> entities =
                new ArrayList<>();

        for (Entity entity :
                mc.world.getEntities()) {

            if ((entity instanceof ShulkerBulletEntity
                    || entity instanceof FireballEntity)
                    && entity.isAlive()
                    && Projectiles.getValue()
                    && isInRange(entity)) {

                return entity;
            }

            if (skipEntity(entity))
                continue;

            if (!(entity instanceof LivingEntity living))
                continue;

            entities.add(living);
        }

        return switch (sort.getValue()) {

            case LowestDistance ->
                    entities.stream()
                            .min(
                                    Comparator.comparing(
                                            e -> mc.player
                                                    .squaredDistanceTo(e)
                                    )
                            )
                            .orElse(null);

            case HighestDistance ->
                    entities.stream()
                            .max(
                                    Comparator.comparing(
                                            e -> mc.player
                                                    .squaredDistanceTo(e)
                                    )
                            )
                            .orElse(null);

            case FOV ->
                    entities.stream()
                            .min(
                                    Comparator.comparing(
                                            this::getFOVAngle
                                    )
                            )
                            .orElse(null);

            case LowestHealth ->
                    entities.stream()
                            .min(
                                    Comparator.comparing(
                                            e -> e.getHealth()
                                                    + e.getAbsorptionAmount()
                                    )
                            )
                            .orElse(null);

            case HighestHealth ->
                    entities.stream()
                            .max(
                                    Comparator.comparing(
                                            e -> e.getHealth()
                                                    + e.getAbsorptionAmount()
                                    )
                            )
                            .orElse(null);

            case LowestDurability ->
                    entities.stream()
                            .min(
                                    Comparator.comparing(
                                            this::getArmorDurability
                                    )
                            )
                            .orElse(null);

            case HighestDurability ->
                    entities.stream()
                            .max(
                                    Comparator.comparing(
                                            this::getArmorDurability
                                    )
                            )
                            .orElse(null);
        };
    }


    private float getArmorDurability(
            LivingEntity entity) {

        float value = 0f;

        for (ItemStack armor :
                entity.getArmorItems()) {

            if (armor == null
                    || armor.isEmpty())
                continue;

            if (armor.getMaxDamage() > 0) {

                value +=
                        (armor.getMaxDamage()
                                - armor.getDamage())
                                / (float) armor.getMaxDamage();
            }
        }

        return value;
    }


    private boolean skipEntity(Entity entity) {

        if (entity == null
                || mc.player == null)
            return true;

        if (isBullet(entity))
            return false;

        if (!(entity instanceof LivingEntity living))
            return true;

        if (!living.isAlive()
                || living.isDead())
            return true;

        if (entity instanceof ArmorStandEntity)
            return true;

        if (entity instanceof CatEntity)
            return true;

        if (skipNotSelected(entity))
            return true;

        if (!InteractionUtility.isVecInFOV(
                entity.getPos(),
                fov.getValue()
        ))
            return true;

        if (entity instanceof PlayerEntity player) {

            if (ModuleManager.antiBot.isEnabled()
                    && AntiBot.bots.contains(entity))
                return true;

            if (player == mc.player)
                return true;

            if (Managers.FRIEND.isFriend(player))
                return true;

            if (player.isCreative()
                    && ignoreCreative.getValue())
                return true;

            if (player.getArmor() == 0
                    && ignoreNaked.getValue())
                return true;

            if (player.isInvisible()
                    && ignoreInvisible.getValue())
                return true;

            if (player.getTeamColorValue()
                    == mc.player.getTeamColorValue()
                    && ignoreTeam.getValue()
                    && mc.player.getTeamColorValue()
                    != 16777215)
                return true;
        }

        if (entity.hasCustomName()
                && ignoreNamed.getValue())
            return true;

        return !isInRange(entity);
    }


    private boolean isBullet(Entity entity) {

        return (entity instanceof ShulkerBulletEntity
                || entity instanceof FireballEntity)
                && entity.isAlive()
                && PlayerUtility.squaredDistanceFromEyes(
                entity.getPos()
        ) < getSquaredRotateDistance()
                && Projectiles.getValue();
    }


    private boolean skipNotSelected(Entity entity) {

        if (entity instanceof SlimeEntity
                && !Slimes.getValue())
            return true;

        if (entity instanceof HostileEntity hostile) {

            if (!hostiles.getValue())
                return true;

            if (onlyAngry.getValue()
                    && !hostile.isAngryAt(mc.player))
                return true;
        }

        if (entity instanceof PlayerEntity
                && !Players.getValue())
            return true;

        if (entity instanceof VillagerEntity
                && !Villagers.getValue())
            return true;

        if (entity instanceof MobEntity
                && !Mobs.getValue())
            return true;

        return entity instanceof AnimalEntity
                && !Animals.getValue();
    }


    private float getFOVAngle(
            @NotNull LivingEntity entity) {

        if (mc.player == null)
            return 180f;

        double dx =
                entity.getX() - mc.player.getX();

        double dz =
                entity.getZ() - mc.player.getZ();

        float yaw =
                (float) (
                        Math.toDegrees(
                                Math.atan2(dz, dx)
                        ) - 90.0
                );

        return Math.abs(
                MathHelper.wrapDegrees(
                        yaw - mc.player.getYaw()
                )
        );
    }


    public float getAttackCooldownProgressPerTick() {

        if (mc.player == null)
            return 1f;

        double attackSpeed =
                mc.player.getAttributeValue(
                        EntityAttributes.GENERIC_ATTACK_SPEED
                );

        if (attackSpeed <= 0.0)
            return 1f;

        float tpsFactor =
                tpsSync.getValue()
                        ? Managers.SERVER.getTPSFactor()
                        : 1f;

        return (float) (
                (20.0
                        * ThunderHack.TICK_TIMER
                        * tpsFactor)
                        / attackSpeed
        );
    }


    public float getAttackCooldown() {

        if (mc.player == null)
            return 0f;

        float ticks =
                (float) (
                        ((ILivingEntity) mc.player)
                                .getLastAttackedTicks()
                                + attackBaseTime.getValue()
                );

        return MathHelper.clamp(
                ticks
                        / getAttackCooldownProgressPerTick(),
                0f,
                1f
        );
    }


    public boolean isAboveWater() {

        if (mc.player == null
                || mc.world == null)
            return false;

        return mc.player.isSubmergedInWater()
                || mc.world.getBlockState(
                BlockPos.ofFloored(
                        mc.player.getPos()
                                .add(0, -0.4, 0)
                )
        ).getBlock() == Blocks.WATER;
    }


    public float getSquaredRotateDistance() {

        if (mc.player == null)
            return 0f;

        float distance = getRange();

        distance += aimRange.getValue();

        if ((mc.player.isFallFlying()
                || ModuleManager.elytraPlus.isEnabled())
                && target != null) {

            distance += 4f;
        }

        if (ModuleManager.strafe.isEnabled())
            distance += 4f;

        if (rotationMode.getValue() != Mode.Track
                || rayTrace.is(Aura.RayTrace.OFF)) {

            distance = getRange();
        }

        return distance * distance;
    }


    public void resolvePlayers() {

        if (mc.world == null)
            return;

        if (resolver.not(Aura.Resolver.Off)) {

            for (PlayerEntity player :
                    mc.world.getPlayers()) {

                if (player instanceof OtherClientPlayerEntity other) {

                    ((IOtherClientPlayerEntity) other)
                            .resolve(resolver.getValue());
                }
            }
        }
    }


    public void restorePlayers() {

        if (mc.world == null)
            return;

        if (resolver.not(Aura.Resolver.Off)) {

            for (PlayerEntity player :
                    mc.world.getPlayers()) {

                if (player instanceof OtherClientPlayerEntity other) {

                    ((IOtherClientPlayerEntity) other)
                            .releaseResolver();
                }
            }
        }
    }


    public void pause() {
        pauseTimer.reset();
    }


    private boolean shouldRandomizeDelay() {

        if (mc.player == null
                || !randomHitDelay.getValue())
            return false;

        return mc.player.isOnGround()
                || mc.player.fallDistance < 0.12f
                || mc.player.isSwimming()
                || mc.player.isFallFlying();
    }


    private boolean shouldRandomizeFallDistance() {

        return mc.player != null
                && randomHitDelay.getValue()
                && !shouldRandomizeDelay();
    }


    public void onRender3D(MatrixStack stack) {

        if (mc.player == null
                || mc.world == null
                || target == null
                || !haveWeapon())
            return;

        if ((resolver.is(Aura.Resolver.BackTrack)
                || resolverVisualisation.getValue())
                && resolvedBox != null) {

            Render3DEngine.OUTLINE_QUEUE.add(
                    new Render3DEngine.OutlineAction(
                            resolvedBox,
                            HudEditor.getColor(0),
                            1
                    )
            );
        }

        switch (esp.getValue()) {

            case CelkaPasta ->
                    Render3DEngine.drawOldTargetEsp(
                            stack,
                            target
                    );

            case NurikZapen ->
                    CaptureMark.render(target);

            case ThunderHackV2 ->
                    Render3DEngine.renderGhosts(
                            espLength.getValue(),
                            espFactor.getValue(),
                            espShaking.getValue(),
                            espAmplitude.getValue(),
                            target
                    );

            case ThunderHack ->
                    Render3DEngine.drawTargetEsp(
                            stack,
                            target
                    );

            case Off -> {
            }
        }

        if (clientLook.getValue()
                && rotationMode.getValue() != Mode.None) {

            mc.player.setYaw(
                    (float) Render2DEngine.interpolate(
                            mc.player.prevYaw,
                            rotationYaw,
                            Render3DEngine.getTickDelta()
                    )
            );

            mc.player.setPitch(
                    (float) Render2DEngine.interpolate(
                            mc.player.prevPitch,
                            rotationPitch,
                            Render3DEngine.getTickDelta()
                    )
            );
        }
    }


    @Override
    public void onEnable() {

        target = null;
        lookingAtHitbox = false;

        rotationPoint = Vec3d.ZERO;
        rotationMotion = Vec3d.ZERO;

        hitTicks = 0;
        trackticks = 0;

        pitchAcceleration = 1f;

        resolvedBox = null;

        if (mc.player != null) {

            rotationYaw =
                    mc.player.getYaw();

            rotationPitch =
                    mc.player.getPitch();
        }

        delayTimer.reset();
        pauseTimer.reset();

        wasTargeted = false;
    }


    @Override
    public void onDisable() {

        clearTarget();

        hitTicks = 0;
        trackticks = 0;

        resolvedBox = null;

        if (pauseBaritone.getValue()
                && ThunderHack.baritone
                && wasTargeted) {

            BaritoneAPI.getProvider()
                    .getPrimaryBaritone()
                    .getCommandManager()
                    .execute("resume");

            wasTargeted = false;
        }
    }


    public static class Position {

        private final double x;
        private final double y;
        private final double z;

        private int ticks;

        public Position(
                double x,
                double y,
                double z) {

            this.x = x;
            this.y = y;
            this.z = z;
        }

        public boolean shouldRemove(int maxTicks) {
            return ticks++ > maxTicks;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }
    }


    public enum Sort {
        LowestDistance,
        HighestDistance,
        LowestHealth,
        HighestHealth,
        LowestDurability,
        HighestDurability,
        FOV
    }

    public enum Switch {
        Normal,
        None,
        Silent
    }

    public enum Mode {
        Interact,
        Track,
        Grim,
        None
    }

    public enum AttackHand {
        MainHand,
        OffHand,
        None
    }

    public enum ESP {
        Off,
        ThunderHack,
        NurikZapen,
        CelkaPasta,
        ThunderHackV2
    }

    public enum AccelerateOnHit {
        Off,
        Yaw,
        Pitch,
        Both
    }

    public enum WallsBypass {
        Off,
        V1,
        V2
    }
}