package gui.custom;

import files.Logger;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_info;

import java.lang.reflect.Method;

public class ButtonInfo {
    public static final int ALWAYS = 0, CONNECTED = 1, INCLASS = 2;

    private final Method OnPress, Stop;
    public final String name;
    public final int ActiveWhen;

    public ButtonInfo(String name, Method OnPress, Method Stop, int ActiveWhen) {
        this.OnPress = OnPress;
        this.Stop = Stop;
        this.name = name;
        this.ActiveWhen = ActiveWhen;
    }

    public void pressed() {
        try {
            OnPress.invoke(null);
        }
        catch (Exception e) {
            Logger.log("impossibile invocare il metodo OnPress del pulsante: " + name, true);
            Logger.log(e.getMessage(), true);

            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile eseguire questa operazione"
            ), null);
        }
    }

    public void stop() {
        try {
            Stop.invoke(null);
        }
        catch (Exception e) {
            Logger.log("impossibile invocare il metodo Stop del pulsante: " + name, true);
            Logger.log(e.getMessage(), true);

            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile eseguire questa operazione"
            ), null);
        }
    }
}
