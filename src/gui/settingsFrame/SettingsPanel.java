package gui.settingsFrame;

import javax.swing.*;

//template che devono seguire tutti i pannelli per essere visualizzati dal SettingsFrame
public interface SettingsPanel {
    JPanel prepare();
    void close();
    void update_colors();
}
