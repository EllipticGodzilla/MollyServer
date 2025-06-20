package gui.custom;

import files.Logger;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;

public class CascateMenu extends JPanel {
    private CascateItem label;
    private final JPanel submenu_container = new JPanel();
    private final JPanel item_container = new JPanel();
    private final String FULL_PATH;

    private final CascateItem[] focussed_item;

    private CascateAction toggle_submenu = _ -> {
        if (submenu_container.isVisible()) { //i submenu sono visibili e deve nasconderli
            label.setIcon((ImageIcon) GraphicsSettings.active_theme().get_value("settings_dropdown_list_closed_icon"));

            submenu_container.setVisible(false);
            item_container.setVisible(false);

            //chiude tutti i submenu
            for (Component c : submenu_container.getComponents()) {
                if (c instanceof CascateMenu menu) {
                    menu.close();
                }
            }
        }
        else { //i submenu non sono visibili e deve mostrarli
            label.setIcon((ImageIcon) GraphicsSettings.active_theme().get_value("settings_dropdown_list_opened_icon"));

            submenu_container.setVisible(true);
            item_container.setVisible(true);
        }
    };

    public CascateMenu(String name, String parent_path, CascateItem[] focussed_item) {
        if (focussed_item.length != 1) {
            Logger.log("focussed item array invalido per il cascate menu: " + name  + " lunghezza != 1", true);

            FULL_PATH = "";
            this.focussed_item = null;

            return;
        }

        this.focussed_item = focussed_item;
        this.FULL_PATH = parent_path + "/" + name;
        label = new CascateItem(name, toggle_submenu);
        label.setIcon((ImageIcon) GraphicsSettings.active_theme().get_value("settings_dropdown_list_closed_icon"));
        JPanel spacer = new JPanel();

        submenu_container.setLayout(new BoxLayout(submenu_container, BoxLayout.PAGE_AXIS));
        item_container.setLayout(new GridLayout(0, 1));

        this.setOpaque(false);
        submenu_container.setOpaque(false);
        item_container.setOpaque(false);
        spacer.setOpaque(false);

        spacer.setPreferredSize(new Dimension(20, 0));

        submenu_container.setVisible(false);
        item_container.setVisible(false);

        this.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weighty = 0;
        c.weightx = 1;

        this.add(label, c);

        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 2;
        c.weightx = 0;
        c.weighty = 1;

        this.add(spacer, c);

        c.gridx = 1;
        c.gridheight = 1;
        c.weighty = 0;
        c.weightx = 1;

        this.add(submenu_container, c);

        c.gridy = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.FIRST_LINE_START;

        this.add(item_container, c);
    }

    public void update_colors() {
        label.update_colors();
        label.setIcon((ImageIcon) GraphicsSettings.active_theme().get_value("settings_dropdown_list_" + (submenu_container.isVisible()? "opened" : "closed") + "_icon"));

        for (Component c : submenu_container.getComponents()) {
            if (c instanceof CascateMenu menu) {
                menu.update_colors();
            }
        }

        for (Component c : item_container.getComponents()) {
            if (c instanceof CascateItem item) {
                item.update_colors();
            }
        }
    }

    /*
     * chiude questo menu e tutti i submenu al suo interno, notare che venendo chiamato ogni volta che si chiude un menu
     * non ha senso cercare di chiudere i submenu di un menu chiuso, saranno già tutti chiusi
     */
    public void close() {
        //se l'item con il focus è uno in questo menu viene perso il focus
        if (focussed_item[0] != null && focussed_item[0].getParent().getParent().equals(this)) {
            focussed_item[0].set_unfocussed();
            focussed_item[0] = null;
        }

        if (submenu_container.isVisible()) {
            toggle_submenu.run("");

            for (Component c : submenu_container.getComponents()) {
                if (c instanceof CascateMenu menu) {
                    menu.close();
                }
            }
        }
    }

    public boolean add_item(String path, CascateAction action) {
        int root_menu_len = path.indexOf('/');
        if (root_menu_len == -1) { //deve aggiungere un item a questo menu
            //controlla non ci sia già un item con questo nome
            for (Component c : item_container.getComponents()) {
                if (c instanceof CascateItem item && item.getText().equals(path)) {
                    Logger.log("impossibile aggiungere un doppio item: " + path + " al CascateMenu: " + label.getText(), true);
                    return false;
                }
            }

            CascateItem new_item = new CascateItem(path, action);
            item_container.add(new_item);

            return true;
        }
        else { //aggiunge un item a un submenu
            String root_menu_name = path.substring(0, root_menu_len);
            String remaining_path = path.substring(root_menu_len + 1);

            //cerca fra i submenu uno che abbia il nome di root_menu_name
            for (Component c : submenu_container.getComponents()) {
                if (c instanceof CascateMenu menu && menu.get_text().equals(root_menu_name)) {
                    return menu.add_item(remaining_path, action);
                }
            }

            //crea un nuovo submenu con il nome di root_menu_name e ci aggiunge il nuovo item
            CascateMenu new_submenu = new CascateMenu(root_menu_name, FULL_PATH, focussed_item);
            submenu_container.add(new_submenu);

            return new_submenu.add_item(remaining_path, action);
        }
    }

    public boolean remove(String path) {
        int root_menu_len = path.indexOf('/');
        if (root_menu_len == -1) { //deve rimuovere un item da questo menu
            for (Component c : item_container.getComponents()) {
                if (c instanceof CascateItem item && item.getText().equals(path)) {
                    item_container.remove(c);
                    check_if_empty();

                    return true;
                }
            }

            for (Component c : submenu_container.getComponents()) {
                if (c instanceof CascateMenu menu && menu.get_text().equals(path)) {
                    submenu_container.remove(c);
                    check_if_empty();

                    return true;
                }
            }

            Logger.log("impossibile rimuovere un item: " + path + " dal cascate menu: " + get_text() + " nessun item ha quel nome", true);
            return false;
        }
        else { //rimuove un item da un submenu
            String root_menu_name = path.substring(0, root_menu_len);
            String remaining_path = path.substring(root_menu_len + 1);

            for (Component c : submenu_container.getComponents()) {
                if (c instanceof CascateMenu menu && menu.get_text().equals(root_menu_name)) {
                    boolean result = menu.remove(remaining_path);

                    if (result) { check_if_empty(); }
                    return result;
                }
            }

            Logger.log("impossibile rimuovere l'item: " + path + " dal cascate menu: " + get_text() + " root menu non trovato", true);
            return false;
        }
    }

    public void reset() {
        item_container.removeAll();
        submenu_container.removeAll();
    }

    private void check_if_empty() {
        if (item_container.getComponentCount() == 0 && submenu_container.getComponentCount() == 0 && getParent().getParent() instanceof CascateMenu parent) {
            parent.remove(get_text());
        }
    }

    public String get_text() {
        return label.getText();
    }

    public String get_full_path() {
        return FULL_PATH;
    }

    public void set_focussed_item(CascateItem item) {
        if (focussed_item[0] != null) {
            focussed_item[0].set_unfocussed();
        }

        focussed_item[0] = item;
    }

    public CascateItem get_focussed_item() {
        return focussed_item[0];
    }
}