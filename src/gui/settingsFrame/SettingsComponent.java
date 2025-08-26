package gui.settingsFrame;

import java.awt.*;

//template che devono seguire tutti i pannelli per essere visualizzati dal SettingsFrame
public interface SettingsComponent {
    Component prepare();
    void close();
    void update_colors();
}
