package gui.settingsFrame;

import files.Logger;
import gui.custom.CascateAction;
import gui.custom.CascateItem;
import gui.custom.CascateMenu;
import gui.custom.MScrollPane;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/*
 * permette alle mod di aggiungere delle loro impostazioni, il funzionamento è esattamente come MollySettingsPanel
 * potendo registrare SettingsPanel che vengono selezionati da un CascateMenu e visualizzati in questo pannello
 */
public class ModManagerPanel implements SettingsPanel {
    private static final JPanel main_panel = new JPanel();
    private static final JPanel menu_container = new JPanel();
    private static final MScrollPane menu_scroller = new MScrollPane(menu_container);

    private static SettingsPanel visible_panel;

    public static void init() {
        main_panel.setLayout(new GridBagLayout());
        menu_container.setLayout(new BoxLayout(menu_container, BoxLayout.PAGE_AXIS));
        menu_container.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));

        main_panel.setOpaque(false);

        Color list_bg = (Color) GraphicsSettings.active_theme().get_value("list_background");
        menu_scroller.getViewport().setBackground(list_bg);
        menu_scroller.setBorder(BorderFactory.createLineBorder(list_bg.darker()));
        menu_scroller.setPreferredSize(new Dimension(250, 0));
        menu_scroller.set_scrollbar_thickness(10);

        JPanel filler = new JPanel();
        filler.setOpaque(false);

        GraphicsSettings.run_at_theme_change(ModManagerPanel::update_colors_static);

        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 1;
        c.weightx = 0;

        main_panel.add(menu_scroller, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;

        main_panel.add(filler, c);
    }

    public static void update_colors_static() {
        menu_container.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));
        menu_scroller.update_colors();

        for (Component c : menu_container.getComponents()) {
            if (c instanceof CascateMenu menu) {
                menu.update_colors();
            }
        }
    }

    public static boolean add_item(String path, SettingsPanel panel, ActionListener ok_listener, ActionListener apply_listener) {
        CascateAction action = _ -> {
            if (visible_panel != null) {
                visible_panel.close();
                main_panel.remove(2); //il secondo componente di main_panel è sempre il pannello visibile
            }
            visible_panel = panel;

            JPanel visible_panel = panel.prepare();
            visible_panel.setPreferredSize(new Dimension(550, 0));

            GridBagConstraints c = new GridBagConstraints();

            c.gridx = 1;
            c.gridy = 0;
            c.weightx = 1;
            c.weighty = 1;
            c.fill = GridBagConstraints.BOTH;

            main_panel.add(visible_panel, c);
            main_panel.updateUI();

            SettingsFrame.set_action_listener(ok_listener, SettingsFrame.OK_BUTTON);
            SettingsFrame.set_action_listener(apply_listener, SettingsFrame.APPLY_BUTTON);
        };
        int root_menu_len = path.indexOf('/');

        if (root_menu_len == -1) {
            Logger.log("impossibile aggiungere CascateItem direttamente al ModSettings panel, specificare un root menu per: " + path, true);
            return false;
        } else {
            String root_menu_name = path.substring(0, root_menu_len);
            String remaining_path = path.substring(root_menu_len + 1);

            for (Component c : menu_container.getComponents()) {
                if (c instanceof CascateMenu menu && menu.get_text().equals(root_menu_name)) {
                    return menu.add_item(remaining_path, action);
                }
            }

            //non ha trovato nessun CascateMenu in menu_container con il nome root_menu_name, deve crearne uno nuovo
            CascateMenu new_menu = new CascateMenu(root_menu_name, "", new CascateItem[1]);
            menu_container.add(new_menu);

            return new_menu.add_item(remaining_path, action);
        }
    }

    @Override
    public JPanel prepare() {
        for (Component c : menu_container.getComponents()) {
            if (c instanceof CascateMenu menu) {
                menu.close();
            }
        }

        return main_panel;
    }

    @Override
    public void close() {
        if (visible_panel != null) {
            visible_panel.close();
            main_panel.remove(2); //il secondo elemento in main_panel è sempre il pannello programmabile
            visible_panel = null;
        }
    }

    @Override
    public void update_colors() {
        update_colors_static();
    }
}
