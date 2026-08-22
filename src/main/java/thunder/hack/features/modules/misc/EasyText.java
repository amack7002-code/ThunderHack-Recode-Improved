package thunder.hack.features.modules.misc;

import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class EasyText extends Module {

    // Configurable message in the ClickGUI
    private final Setting<String> message =
            new Setting<>("Message", "Hello from ThunderHack!");

    public EasyText() {
        super("EasyText", Category.MISC);
    }

    @Override
    public void onEnable() {
        if (fullNullCheck()) {
            toggle();
            return;
        }

        // Send the message directly to the server chat
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatMessage(message.getValue());
        }

        // Automatically toggles off after sending once
        toggle();
    }
}