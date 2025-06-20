package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

//permette di aggiornare i colori ai popup menu
public class MPopupMenu extends JPopupMenu {
    public MPopupMenu() {
        update_colors();
    }

    void update_colors() {
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("dropdown_background"));
        this.setBorder((Border) GraphicsSettings.active_theme().get_value("dropdown_border"));

        //aggiorna i colori di tutti i JMenuItem
        for (Component c : getComponents()) {
            if (c instanceof MMenuItem item) {
                item.update_colors();
            }
        }
    }
}
