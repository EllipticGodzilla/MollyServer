package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;

public class MMenuItem extends JMenuItem {
    public MMenuItem(String txt) {
        super(txt);
        this.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        this.setOpaque(false);
        this.setForeground((Color) GraphicsSettings.active_theme().get_value("dropdown_text_color"));
    }

    public void update_colors() {
        update_colors(this.isSelected());
    }

    @Override
    public void menuSelectionChanged(boolean isIncluded) {
        update_colors(isIncluded);
    }

    private void update_colors(boolean active) {
        if (active) {
            setOpaque(true);
            this.setBackground((Color) GraphicsSettings.active_theme().get_value("dropdown_selected_background"));
            this.setForeground((Color) GraphicsSettings.active_theme().get_value("dropdown_selected_text_color"));
        } else {
            setOpaque(false);
            this.setForeground((Color) GraphicsSettings.active_theme().get_value("dropdown_text_color"));
        }
    }
}

