package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import thunder.hack.events.impl.EventAttack;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EbatteSratte extends Module {

    private final Setting<Integer> delay =
            new Setting<>("Delay", 5, 1, 30);

    private final Setting<Server> server =
            new Setting<>("Server", Server.FunnyGame);

    private final Setting<Messages> mode =
            new Setting<>("Mode", Messages.Default);

    /*
     * Built-in Russian messages.
     * Replace these with whatever messages you want.
     */
    private static final String[] WORDS = new String[]{
            "GG",
            "1v1?",
            "Good fight",
            "Nice hit",
            "Too easy",
            "Better luck next time"
    };

    /*
     * Alternate Russian message set.
     */
    private static final String[] ULYBAKA1337 = new String[]{
            "хахаха",
            "ну что?",
            "gg",
            "1v1?",
            "не повезло",
            "попробуй еще раз"
    };

    /*
     * Built-in English messages.
     */
    private static final String[] WORDSENG = new String[]{
            "GG",
            "1v1?",
            "Good fight",
            "Nice hit",
            "Too easy",
            "Better luck next time"
    };

    /*
     * Alternate English message set.
     */
    private static final String[] ULYBAKA1337Eng = new String[]{
            "haha",
            "well then?",
            "gg",
            "1v1?",
            "unlucky",
            "try again"
    };

    private final Timer timer = new Timer();

    /*
     * Custom messages loaded from:
     *
     * ThunderHackRecode/misc/EbatteSratte.txt
     */
    private volatile List<String> words = new ArrayList<>();

    public EbatteSratte() {
        super("EbatteSratte", Module.Category.MISC);

        loadEZ();
    }

    @Override
    public void onEnable() {
        timer.reset();
        loadEZ();
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onAttackEntity(@NotNull EventAttack event) {

        /*
         * Only run after the attack.
         */
        if (event.isPre()) {
            return;
        }

        /*
         * Only respond to attacks against players.
         */
        if (!(event.getEntity() instanceof PlayerEntity entity)) {
            return;
        }

        /*
         * Respect the configured delay.
         */
        if (!timer.passedS(delay.getValue())) {
            return;
        }

        Messages currentMode = mode.getValue();

        /*
         * Custom mode.
         */
        if (currentMode == Messages.Custom) {

            List<String> customMessages = words;

            if (customMessages == null || customMessages.isEmpty()) {
                return;
            }

            String message = customMessages.get(
                    ThreadLocalRandom.current()
                            .nextInt(customMessages.size())
            );

            sendMessageToTarget(entity, message);

            timer.reset();

            return;
        }

        /*
         * Select language.
         */
        String[] messages;

        if (isRu()) {

            if (currentMode == Messages.UlybakaHuevo) {
                messages = ULYBAKA1337;
            } else {
                messages = WORDS;
            }

        } else {

            if (currentMode == Messages.UlybakaHuevo) {
                messages = ULYBAKA1337Eng;
            } else {
                messages = WORDSENG;
            }
        }

        if (messages.length == 0) {
            return;
        }

        /*
         * Select random message.
         */
        String message = messages[
                ThreadLocalRandom.current()
                        .nextInt(messages.length)
        ];

        sendMessageToTarget(entity, message);

        timer.reset();
    }

    /**
     * Fabric 1.21:
     *
     * getLanguage() already returns a String.
     *
     * Do NOT call .getCode() here.
     */
    private boolean isRu() {

        if (mc == null || mc.getLanguageManager() == null) {
            return false;
        }

        String language = mc.getLanguageManager().getLanguage();

        return language != null
                && language.toLowerCase().startsWith("ru");
    }

    /**
     * Sends the message according to the selected server mode.
     */
    private void sendMessageToTarget(
            PlayerEntity entity,
            String message
    ) {

        if (mc == null) {
            return;
        }

        if (mc.getNetworkHandler() == null) {
            return;
        }

        if (message == null || message.isBlank()) {
            return;
        }

        String targetName = entity.getName().getString();

        switch (server.getValue()) {

            case FunnyGame -> {

                mc.getNetworkHandler().sendChatMessage(
                        "!" + targetName + " " + message
                );
            }

            case OldServer -> {

                mc.getNetworkHandler().sendChatMessage(
                        ">" + targetName + " " + message
                );
            }

            case DirectMessage -> {

                /*
                 * IMPORTANT:
                 *
                 * sendChatCommand() expects:
                 *
                 * msg Player message
                 *
                 * NOT:
                 *
                 * /msg Player message
                 */
                mc.getNetworkHandler().sendChatCommand(
                        "msg "
                                + targetName
                                + " "
                                + message
                );
            }

            case Local -> {

                mc.getNetworkHandler().sendChatMessage(
                        targetName + " " + message
                );
            }
        }
    }

    /**
     * Loads custom messages from:
     *
     * ThunderHackRecode/misc/EbatteSratte.txt
     *
     * Each normal line is treated as a message.
     *
     * Blank lines can also be used to separate
     * multi-line messages.
     */
    public void loadEZ() {

        File directory =
                new File("ThunderHackRecode/misc");

        File file =
                new File(
                        directory,
                        "EbatteSratte.txt"
                );

        /*
         * Create directory.
         */
        if (!directory.exists()) {

            if (!directory.mkdirs()) {

                sendMessage(
                        "§cCouldn't create ThunderHackRecode/misc"
                );

                return;
            }
        }

        /*
         * Create message file.
         */
        if (!file.exists()) {

            try {

                if (!file.createNewFile()) {

                    sendMessage(
                            "§cCouldn't create EbatteSratte.txt"
                    );

                    return;
                }

            } catch (IOException e) {

                sendMessage(
                        "§cError creating EbatteSratte.txt"
                );

                return;
            }
        }

        /*
         * Read file asynchronously.
         */
        new Thread(() -> {

            List<String> loadedMessages =
                    new ArrayList<>();

            try (
                    FileInputStream fis =
                            new FileInputStream(file);

                    InputStreamReader isr =
                            new InputStreamReader(
                                    fis,
                                    StandardCharsets.UTF_8
                            );

                    BufferedReader reader =
                            new BufferedReader(isr)
            ) {

                List<String> lines =
                        new ArrayList<>();

                String line;

                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }

                /*
                 * Determine whether blank-line
                 * paragraph formatting is being used.
                 */
                boolean hasBlankLines = false;

                for (String currentLine : lines) {

                    if (currentLine.isBlank()) {

                        hasBlankLines = true;

                        break;
                    }
                }

                /*
                 * Standard format:
                 *
                 * message 1
                 * message 2
                 * message 3
                 */
                if (!hasBlankLines) {

                    for (String currentLine : lines) {

                        String message =
                                currentLine.trim();

                        if (!message.isEmpty()) {
                            loadedMessages.add(message);
                        }
                    }

                } else {

                    /*
                     * Paragraph format:
                     *
                     * message line 1
                     * message line 2
                     *
                     * message 2 line 1
                     * message 2 line 2
                     */
                    StringBuilder currentMessage =
                            new StringBuilder();

                    for (String currentLine : lines) {

                        if (currentLine.isBlank()) {

                            if (!currentMessage.isEmpty()) {

                                String message =
                                        currentMessage
                                                .toString()
                                                .trim();

                                if (!message.isEmpty()) {
                                    loadedMessages.add(message);
                                }

                                currentMessage.setLength(0);
                            }

                        } else {

                            if (!currentMessage.isEmpty()) {
                                currentMessage.append(' ');
                            }

                            currentMessage.append(
                                    currentLine.trim()
                            );
                        }
                    }

                    /*
                     * Add the final message.
                     */
                    if (!currentMessage.isEmpty()) {

                        String message =
                                currentMessage
                                        .toString()
                                        .trim();

                        if (!message.isEmpty()) {
                            loadedMessages.add(message);
                        }
                    }
                }

                /*
                 * Replace the list after loading.
                 */
                words = loadedMessages;

            } catch (Exception ignored) {

                words = new ArrayList<>();
            }

        }, "EbatteSratte-Loader").start();
    }

    public enum Server {
        FunnyGame,
        DirectMessage,
        OldServer,
        Local
    }

    public enum Messages {
        Default,
        UlybakaHuevo,
        Custom
    }
}