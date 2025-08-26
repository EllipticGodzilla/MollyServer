package gui.settingsFrame;

import gui.custom.MComboBox;
import gui.custom.MScrollPane;
import gui.themes.GraphicsSettings;
import gui.themes.GraphicsTheme;
import gui.themes.ThemeChanges;
import gui.themes.standard_builders.GraphicsOptionBuilder;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

//pannello mostrato quando si aprono le impostazioni graphics->themes nel pannello MollySettings
public class ThemesSettings implements SettingsComponent {
    private static final JPanel main_panel = new JPanel();
    private static final ThemesDisplayer themes_displayer = new ThemesDisplayer();
    private static final MScrollPane themes_scroller = new MScrollPane(themes_displayer);

    private static final MComboBox themes_combobox = new MComboBox(new String[0]);
    private static final JTextArea new_themes_area = new JTextArea();
    private static final JLabel top_left_label = new JLabel("visualizza tema:");

    //contiene la versione originale di ogni tema prima di essere visualizzato da questo pannello
    private static GraphicsTheme original_theme;

    /*
     * per ogni tema modificato contiene un collegamento (nome)->(modifiche apportate), questi ThemeChanges rappresentano
     * le modifiche apportate rispetto alle specifiche dei temi in GraphicsSettings
     */
    private static final Map<String, ThemeChanges> changes_map = new LinkedHashMap<>();

    /*
     * viene chiamato quando si preme il tasto ok o apply nel settings frame, salva le modifiche del tema visualizzato al
     * momento nella mappa changes_map inviando tutti i ThemeChanges in changes_map a GraphicsSettings, e aggiorna il tema
     * attivo al momento con quello selezionato.
     */
    public static ActionListener apply_listener = _ -> {
        save_theme_changes();

        for (String name : changes_map.keySet()) {
            GraphicsSettings.add_changes(name, changes_map.get(name));
        }

        GraphicsSettings.set_active_theme(original_theme.get_name());
        original_theme = GraphicsSettings.active_theme();
    };

    public static ActionListener ok_listener = _ -> {
        apply_listener.actionPerformed(null);
        SettingsFrame.close();
    };

    public static void init() {
        main_panel.setOpaque(false);
        main_panel.setLayout(new GridBagLayout());

        //components
        JButton add_graphics = new JButton();
        JButton remove_graphics = new JButton();

        //components settings
        top_left_label.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));

        themes_displayer.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        new_themes_area.setVisible(false);
        new_themes_area.setFont(themes_combobox.getFont());

        update_colors_static();

        add_graphics.setIcon(new ImageIcon(MollySettingsPanel.class.getResource("/images/plus.png")));
        add_graphics.setRolloverIcon(new ImageIcon(MollySettingsPanel.class.getResource("/images/plus_sel.png")));
        add_graphics.setPressedIcon(new ImageIcon(MollySettingsPanel.class.getResource("/images/plus_pres.png")));
        remove_graphics.setIcon(new ImageIcon(MollySettingsPanel.class.getResource("/images/minus.png")));
        remove_graphics.setRolloverIcon(new ImageIcon(MollySettingsPanel.class.getResource("/images/minus_sel.png")));
        remove_graphics.setPressedIcon(new ImageIcon(MollySettingsPanel.class.getResource("/images/minus_pres.png")));

        add_graphics.setBorder(null);
        remove_graphics.setBorder(null);

        add_graphics.setOpaque(false);
        remove_graphics.setOpaque(false);

        add_graphics.setContentAreaFilled(false);
        remove_graphics.setContentAreaFilled(false);

        //buttons actions
        themes_combobox.addActionListener(_ -> {
            String settings_name = (String) themes_combobox.getSelectedItem();
            set_visible_theme(settings_name);
        });

        new_themes_area.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent focusEvent) {
            }

            @Override
            public void focusLost(FocusEvent focusEvent) {
                new_themes_area.setText("");

                new_themes_area.setVisible(false);
                themes_combobox.setVisible(true);
            }
        });

        new_themes_area.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent keyEvent) {
            }

            @Override
            public void keyReleased(KeyEvent keyEvent) {
            }

            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    new_themes_area.setVisible(false);
                    themes_combobox.setVisible(true);
                } else if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
                    String new_settings_name = new_themes_area.getText();
                    create_new_theme(new_settings_name);

                    new_themes_area.setVisible(false);
                    themes_combobox.setVisible(true);
                }
            }
        });

        add_graphics.addActionListener(_ -> {
            if (themes_combobox.isVisible()) {
                themes_combobox.setVisible(false);
                new_themes_area.setVisible(true);
                new_themes_area.requestFocus();
            }
        });

        remove_graphics.addActionListener(_ -> {
            //lascia sempre almeno un elemento nella lista e non elimina mai standard
            if (themes_combobox.getItemCount() != 1 && !Objects.equals(themes_combobox.getSelectedItem(), "standard theme")) {
                int selected_index = themes_combobox.getSelectedIndex();

                //rimuove dalla lista
                String options_name = themes_combobox.getItemAt(selected_index);
                themes_combobox.removeItemAt(selected_index);

                changes_map.put(options_name, new ThemeChanges(ThemeChanges.DELETE, null, null));
            }
        });

        //add to the panel
        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 0;

        main_panel.add(top_left_label, c);

        c.gridx = 1;
        c.insets.left = 10;

        main_panel.add(themes_combobox, c);

        c.gridx = 2;

        main_panel.add(new_themes_area, c);

        c.gridx = 4;
        c.weightx = 1;
        c.fill = GridBagConstraints.VERTICAL;
        c.anchor = GridBagConstraints.FIRST_LINE_END;
        c.insets.right = 5;
        c.insets.left = 0;

        main_panel.add(remove_graphics, c);

        c.gridx = 5;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0;
        c.insets.right = 15;

        main_panel.add(add_graphics, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weighty = 1;
        c.gridwidth = 6;

        main_panel.add(themes_scroller, c);
    }

    // salva lo stato del tema visibile e imposta un dato tema come quello visibile nel pannello
    private static void set_visible_theme(String theme_name) {
        save_theme_changes();

        GraphicsTheme theme = GraphicsSettings.get_theme(theme_name);
        if (theme == null) { //il tema è appena stato creato
            theme = new GraphicsTheme(theme_name);
        }

        ThemeChanges changes = changes_map.get(theme_name);
        if (changes != null) { //sono già state apportate delle modifiche rispetto al tema registrato in GraphicsSettings
            theme = GraphicsSettings.change_theme(theme, changes);
        }

        original_theme = theme;
        themes_displayer.display(theme);
    }

    private static void create_new_theme(String name) {
        if (!themes_combobox.contains(name)) {
            themes_combobox.addItem(name);
            changes_map.put(name, new ThemeChanges(ThemeChanges.CREATE, null, null));

            themes_combobox.setSelectedItem(name); //questo invoca l'action listener che mostra il nuovo theme
        }
    }

    // compila un ThemeChanges con tutte le modifiche apportate al tema visibile e lo aggiunge a changes_map
    private static void save_theme_changes() {
        if (!original_theme.get_name().equals("standard theme")) { //non salva il tema standard
            GraphicsTheme new_options = themes_displayer.get_updated(original_theme.get_name());
            //se non è ancora stato visualizzato nessun tema e theme_displayer.get_updated() ha ritornato null, non fa nulla
            if (new_options == null) {
                return;
            }

            ThemeChanges changes = original_theme.compile_changes(new_options);
            if (changes != null) { //se non sono state apportate modifiche non fa nulla
                ThemeChanges original_changes = changes_map.get(original_theme.get_name());

                //se delle modifiche a questo tema sono già registrate le concatena
                if (original_changes != null) {
                    changes_map.put(original_theme.get_name(), original_changes.merge_with(changes));
                } else {
                    changes_map.put(original_theme.get_name(), changes);
                }
            }
        }
    }

    public static void update_colors_static() {
        top_left_label.setForeground((Color) GraphicsSettings.active_theme().get_value("text_color"));
        new_themes_area.setBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
        new_themes_area.setForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));
        new_themes_area.setBorder((Border) GraphicsSettings.active_theme().get_value("input_border"));

        themes_scroller.update_colors();
        themes_combobox.update_colors();
        themes_displayer.update_color();
    }

    @Override
    public JPanel prepare() {
        themes_combobox.set_list(GraphicsSettings.theme_list());
        new_themes_area.setMinimumSize(themes_combobox.getPreferredSize());

        original_theme = GraphicsSettings.active_theme();
        themes_combobox.setSelectedItem(GraphicsSettings.active_theme().get_name());

        main_panel.repaint();

        return main_panel;
    }

    @Override
    public void close() {
        changes_map.clear();
    }

    @Override
    public void update_colors() {
        update_colors_static();
    }
}

//pannello che mostra tutti i valori delle variabili nei themes
class ThemesDisplayer extends JPanel {
    private final Map<String, JPanel> VALUE_PANELS = new LinkedHashMap<>(); //mappa fra il nome dell'opzione e il pannello in cui viene mostrato il suo valore

    public ThemesDisplayer() {
        this.setLayout(new GridBagLayout());
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("frame_background"));

        update_color();
    }

    public void update_color() {
        Color encapsulator_bg = (Color) GraphicsSettings.active_theme().get_value("frame_background");
        Color foreground = (Color) GraphicsSettings.active_theme().get_value("text_color");

        this.setBackground(encapsulator_bg);
        encapsulator_bg = encapsulator_bg.darker();

        for (Component comp : getComponents()) { //cambia il background a tutti gli encapsulator
            JComponent encapsulator = (JComponent) comp;

            encapsulator.setBackground(encapsulator_bg);
            encapsulator.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(encapsulator_bg.darker()),
                    BorderFactory.createEmptyBorder(5, 5, 5, 0)
            ));

            JLabel label = (JLabel) encapsulator.getComponent(0);
            JPanel panel = (JPanel) encapsulator.getComponent(1);

            label.setForeground(foreground);
            String key_name = label.getText().substring(0, label.getText().length() - 1); //rimuove ":" dalla fine

            GraphicsTheme.get_builder(key_name).update_colors(panel);
        }
    }

    public void display(GraphicsTheme options) {
        String[] options_list = GraphicsTheme.get_key_list(); //lista di tutte le opzioni grafiche modificate in questa opzione

        for (String opt_name : options_list) {
            Object value = options.get_value(opt_name);
            JPanel value_panel = VALUE_PANELS.get(opt_name);

            display_val(value_panel, opt_name, value); //aggiorna value_panel per mostrare value
        }

        this.updateUI();
    }

    public GraphicsTheme get_updated(String name) {
        if (VALUE_PANELS.isEmpty()) { //non è ancora stato visualizzato nessun tema
            return null;
        }

        GraphicsTheme new_options = new GraphicsTheme(name);

        for (String opt_name : VALUE_PANELS.keySet()) {
            JPanel value_panel = VALUE_PANELS.get(opt_name);

            GraphicsOptionBuilder<?> builder = GraphicsTheme.get_builder(opt_name);
            Object value = builder.new_value(value_panel);

            new_options.set_value(opt_name, value);
        }

        return new_options;
    }

    private void display_val(JPanel panel, String name, Object value) {
        if (panel == null) { //se non si è ancora mai visualizzato il valore di questa impostazione
            panel = new JPanel();

            panel.setLayout(new GridBagLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));
            panel.setOpaque(false);

            VALUE_PANELS.put(name, panel);
            GraphicsTheme.get_builder(name).display(panel, value);

            add_panel(panel, name);
        } else {
            //aggiorna il valore nel pannello con quello nuovo
            GraphicsTheme.get_builder(name).update(panel, value);
        }
    }

    private void add_panel(JPanel panel, String name) {
        JPanel encapsulator = new JPanel();
        JLabel name_label = new JLabel(name + ":");
        Color enc_bg = ((Color) GraphicsSettings.active_theme().get_value("frame_background")).darker();

        encapsulator.setBackground(enc_bg);
        encapsulator.setLayout(new GridBagLayout());
        encapsulator.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(enc_bg.darker()),
                BorderFactory.createEmptyBorder(5, 5, 5, 0)
        ));

        name_label.setForeground((Color) GraphicsSettings.active_theme().get_value("text_color"));

        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 0;
        c.weightx = 0;
        c.insets = new Insets(9, 0, 0, 5);
        encapsulator.add(name_label, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets.top = 0;
        encapsulator.add(panel, c);

        c.gridy = VALUE_PANELS.size() - 1;
        c.gridx = 0;
        c.insets = new Insets(0, 5, 5, 5);
        this.add(encapsulator, c);
    }
}
