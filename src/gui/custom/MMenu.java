package gui.custom;

import files.Logger;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

class MMenu extends JMenu {
    private final Map<String, MMenu> available_menu = new LinkedHashMap<>();

    public MMenu(String txt) {
        super(txt);

        this.setOpaque(false);
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("dropdown_selected_background"));
        this.setForeground((Color) GraphicsSettings.active_theme().get_value("dropdown_text_color"));
        this.getPopupMenu().setBorder((Border) GraphicsSettings.active_theme().get_value("dropdown_border"));
        this.getPopupMenu().setBackground((Color) GraphicsSettings.active_theme().get_value("dropdown_background"));
    }

    public void update_colors() {
        this.getPopupMenu().setBorder((Border) GraphicsSettings.active_theme().get_value("dropdown_border"));
        this.getPopupMenu().setBackground((Color) GraphicsSettings.active_theme().get_value("dropdown_background"));
        setSelected(isSelected()); //aggiorna tutti i colori

        for (Component component : getPopupMenu().getComponents()) {
            if (component instanceof MMenu menu) {
                menu.update_colors();
            }
            else if (component instanceof MMenuItem item) {
                item.update_colors();
            }
        }

        this.repaint();
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            this.setOpaque(true);
            this.setBackground((Color) GraphicsSettings.active_theme().get_value("dropdown_selected_background"));
            this.setForeground((Color) GraphicsSettings.active_theme().get_value("dropdown_selected_text_color"));
        } else {
            this.setOpaque(false);
            this.setForeground((Color) GraphicsSettings.active_theme().get_value("dropdown_text_color"));
        }
        this.repaint();
    }

    public boolean add(String path, Runnable action) {
        int root_menu_len = path.indexOf('/');
        if (root_menu_len == -1) { //viene aggiunto un nuovo menu item a questo menu
            //controlla non ce ne sia già uno con questo nome
            for (Component c : getPopupMenu().getComponents()) {
                if (c instanceof MMenuItem item && item.getText().equals(path)) {
                    Logger.log("impossibile aggiungere un menu: " + path + " al menu: " + getText(), true);
                    return false;
                }
            }

            MMenuItem new_item = new MMenuItem(path);
            new_item.addActionListener((_) -> action.run());

            this.add(new_item);
            return true;
        }
        else { //aggiunge un nuovo menu item ad un menu in questo menu
            String root_menu_name = path.substring(0, root_menu_len);
            String remaining_path = path.substring(root_menu_len + 1);

            MMenu root_menu = available_menu.get(root_menu_name);
            if (root_menu == null) {
                root_menu = new MMenu(root_menu_name);
                available_menu.put(root_menu_name, root_menu);
                this.add(root_menu);
            }

            return root_menu.add(remaining_path, action);
        }
    }
}