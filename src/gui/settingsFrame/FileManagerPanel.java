package gui.settingsFrame;

import files.FileInterface;
import files.Pair;
import gui.custom.CascateAction;
import gui.custom.CascateItem;
import gui.custom.CascateMenu;
import gui.custom.MScrollPane;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileManagerPanel implements SettingsComponent {
    private static final JPanel main_panel = new JPanel();
    private static final FileEditorPanel content_panel = new FileEditorPanel();
    private static final CascateMenu file_tree = new CascateMenu("root", "", new CascateItem[1]);
    private static final MScrollPane menu_scroller = new MScrollPane(file_tree);

    private static final Map<String, Pair<String, Boolean>> changed_files = new LinkedHashMap<>(); //ricorda tutte le modifiche ai vari file aperti

    private static final ActionListener APPLY_LISTENER = _ -> {
        content_panel.save_changes(); //salva le ultime modifiche
        apply_changes(); //scrive le modifiche nei rispettivi files
    };

    private static final ActionListener OK_LISTENER = _ -> {
        APPLY_LISTENER.actionPerformed(null);
        SettingsFrame.close();
    };

    private static final CascateAction menu_buttons_action = item_path -> {
        content_panel.read_file(item_path.substring(6)); //rimuove "/root/" da item_path
    };

    public static void init() {
        main_panel.setOpaque(false);
        main_panel.setBorder(null);
        main_panel.setLayout(new GridBagLayout());
        file_tree.setOpaque(true);

        Color list_bg = (Color) GraphicsSettings.active_theme().get_value("list_background");
        file_tree.setBackground(list_bg);
        menu_scroller.setBorder(BorderFactory.createLineBorder(list_bg.darker()));
        menu_scroller.setPreferredSize(new Dimension(250, 0));
        menu_scroller.set_scrollbar_thickness(10);

        content_panel.setPreferredSize(new Dimension(550, 0));

        //aggiorna i colori di MENU quando si cambia tema colori
        GraphicsSettings.run_at_theme_change(FileManagerPanel::update_colors_static);

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;

        main_panel.add(menu_scroller, c);

        c.gridx = 1;
        c.weightx = 1;
        main_panel.add(content_panel, c);
    }

    public static void update_colors_static() {
        menu_scroller.update_colors();
        content_panel.update_colors();
        file_tree.update_colors();

        file_tree.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));
    }

    protected static void save_changes(String file_name, String content, boolean encoded) {
        changed_files.put(file_name, new Pair<>(content, encoded));
    }

    private static void apply_changes() {
        for (String file_name : changed_files.keySet()) {
            Pair<String, Boolean> changes = changed_files.get(file_name);

            FileInterface.set_encoded(file_name, changes.second());
            FileInterface.overwrite_file(file_name, changes.first().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public JPanel prepare() {
        String[] available_files = FileInterface.file_list();
        for (String file : available_files) {
            file_tree.add_item(file, menu_buttons_action);
        }

        SettingsFrame.set_action_listener(OK_LISTENER, SettingsFrame.OK_BUTTON);
        SettingsFrame.set_action_listener(APPLY_LISTENER, SettingsFrame.APPLY_BUTTON);

        return main_panel;
    }

    @Override
    public void close() {
        file_tree.reset();
        content_panel.reset();
        changed_files.clear();
    }

    @Override
    public void update_colors() {
        update_colors_static();
    }
}

class FileEditorPanel extends JPanel {
    private final JLabel info_field = new JLabel();
    private final JTextArea text_area = new JTextArea();
    private final MScrollPane text_area_scroller = new MScrollPane(text_area);
    private final JButton encoded_checkbox = new JButton();

    private static ImageIcon checkbox_dis = (ImageIcon) GraphicsSettings.active_theme().get_value("checkbox_off_icon");
    private static ImageIcon checkbox_sel = (ImageIcon) GraphicsSettings.active_theme().get_value("checkbox_on_icon");

    private String original_text = null;
    private String original_name = null;
    private boolean original_encoded = false;

    public FileEditorPanel() {
        this.setOpaque(false);
        this.setLayout(new GridBagLayout());

        info_field.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));
        info_field.setForeground((Color) GraphicsSettings.active_theme().get_value("text_color"));
        info_field.setText("file: , encoded:");

        text_area.setBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
        text_area.setForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));
        text_area.setBorder((Border) GraphicsSettings.active_theme().get_value("input_border"));
        text_area.setText("file content");
        text_area.setEditable(false);

        encoded_checkbox.setBorder(null);
        encoded_checkbox.setIcon(checkbox_dis);
        encoded_checkbox.addActionListener(CHECKBOX_LISTENER);
        encoded_checkbox.setOpaque(false);
        encoded_checkbox.setContentAreaFilled(false);

        text_area_scroller.set_scrollbar_thickness(10);
        text_area_scroller.setPreferredSize(new Dimension(538, 315)); //dimensioni di TEXT_AREA quando il frame è alle dimensioni minime

        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 0;
        c.insets = new Insets(5, 5, 5, 5);

        this.add(info_field);

        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 1;

        this.add(encoded_checkbox, c);

        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets.top = 0;

        this.add(text_area_scroller, c);
    }

    public void update_colors() {
        info_field.setForeground((Color) GraphicsSettings.active_theme().get_value("text_color"));
        text_area.setBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
        text_area.setForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));
        text_area.setBorder((Border) GraphicsSettings.active_theme().get_value("input_border"));
        text_area_scroller.update_colors();

        checkbox_dis = (ImageIcon) GraphicsSettings.active_theme().get_value("checkbox_off_icon");
        checkbox_sel = (ImageIcon) GraphicsSettings.active_theme().get_value("checkbox_on_icon");
    }

    private ActionListener CHECKBOX_LISTENER = _ -> {
        if (encoded_checkbox.getIcon().equals(checkbox_sel)) { //se il file ora è cifrato, si vuole impostare come in chiaro
            encoded_checkbox.setIcon(checkbox_dis);
        }
        else { //il file ora non è cifrato, si vuole iniziare a cifrare
            encoded_checkbox.setIcon(checkbox_sel);
        }
    };

    public void reset() {
        original_text = original_name = null;
        original_encoded = false;

        info_field.setText("file: , encoded:");
        text_area.setText("file content");
        text_area.setEditable(false);
    }

    public void save_changes() {
        boolean is_encoded = encoded_checkbox.getIcon().equals(checkbox_sel);

        if (original_text != null && (!original_text.equals(text_area.getText()) || original_encoded != is_encoded)) { //se ci sono state modifiche
            FileManagerPanel.save_changes(original_name, text_area.getText(), is_encoded); //salva le modifiche apportate al file
        }
    }

    public void read_file(String name) {
        byte[] file_content_bytes = FileInterface.read_file(name);
        if (file_content_bytes == null) {
            info_field.setText("file: " + name + ", encoded:");
            text_area.setText("File Not Found, or content unreadable");
            text_area.setEditable(false);

            return;
        }
        String file_content = new String(file_content_bytes);

        if (original_name != null) { //se prima era aperto un altro file, salva le eventuali modifiche
            save_changes();
        }

        boolean is_encoded = FileInterface.is_encoded(name) == 1; // non può ritornare -1 poichè già con file_cont != null sappiamo che il file esiste

        info_field.setText("file: " + name + ", encoded:");
        encoded_checkbox.setIcon(is_encoded? checkbox_sel : checkbox_dis);
        text_area.setText(file_content);
        text_area.setEditable(true);

        this.updateUI();

        original_name = name;
        original_encoded = is_encoded;
        original_text = file_content;
    }
}