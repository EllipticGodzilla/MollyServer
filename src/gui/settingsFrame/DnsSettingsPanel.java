package gui.settingsFrame;

import files.Logger;
import gui.custom.*;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_info;
import gui.themes.GraphicsSettings;
import network.DnsInfo;
import network.ServerInterface;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DnsSettingsPanel implements SettingsPanel {
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

        GraphicsSettings.run_at_theme_change(DnsSettingsPanel::update_colors_static);

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

        DnsListEditor.init();
        DnsListEditor dns_editor = new DnsListEditor();

        add_item("dns/data editor", dns_editor, DnsListEditor.OK_LISTENER, DnsListEditor.APPLY_LISTENER);
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
            Logger.log("impossibile aggiungere CascateItem direttamente al DnsSettingsPanel panel, specificare un root menu per: " + path, true);
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

/*
 * pannello in DnsSettingsPanel per modificare o visualizzare tutti i dati relativi ai dns registrati in
 * ServerInterface, nonché unico pannello registrato in DnsSettingsPanel di standard.
 */
class DnsListEditor implements SettingsPanel {
    private static class DnsListPopup extends MPopupMenu {
        public DnsListPopup(MList parent_list) {
            MMenuItem add_dns = new MMenuItem("add dns");
            MMenuItem rem_dns = new MMenuItem("remove dns");

            /*
             * Per aggiungere un dns alla lista genera un nome "nuovo dns <num>", con <num> il più piccolo intero
             * positivo che lo renda unico fra i dns registrati in ServerInterface, e gli assegna tutti valori nulli.
             * Aggiungendo l'oggetto DnsInfo alla mappa dns_changes, nel caso in cui l'utente non specifichi tutte
             * le informazioni necessarie per un dns valido una volta inviata la modifica a ServerInterface questo
             * non la accetterà e verrà ignorata.
             */
            add_dns.addActionListener(_ -> {
                int name_dist = 1; //intero che rende unico il nome del server da creare
                Set<String> reg_dns = ServerInterface.get_dns_list();
                String dns_name;
                do {
                    dns_name = "nuovo dns " + name_dist;
                    name_dist++;
                }
                while (reg_dns.contains(dns_name) || dns_changes.containsKey(dns_name));

                dns_list.add(dns_name);
                dns_changes.put(
                        dns_name,
                        new DnsInfo(
                                dns_name,
                                null,
                                0,
                                "standard connector dns",
                                new byte[0]
                        )
                );
            });

            /*
             * per rimuovere dns basta legare il nome del dns a null nella mappa con le modifiche, una volta
             * inviata a ServerInterface eliminerà i dati del dns dai registrati
             */
            rem_dns.addActionListener(_ -> {
                String selected_dns = parent_list.getSelectedValue();
                if (selected_dns == null) { //non è selezionato nessun dns dalla lista
                    return;
                }

                dns_changes.put(selected_dns, null);
                dns_list.remove(selected_dns);
                data_displayer.reset();
            });

            this.add(add_dns);
            this.add(rem_dns);
        }
    }

    private static final JPanel main_panel = new JPanel();
    private static final DnsDataDisplayer data_displayer = new DnsDataDisplayer();
    private static final MList dns_list = new MList("dns settings list");
    private static final MScrollPane dns_scroller = new MScrollPane(dns_list),
                                     data_scroller = new MScrollPane(data_displayer);

    /*
     * Memorizza tutti i cambiamenti fatti ai dati dei dns prima di apportarli effettivamente alla lista in
     * ServerInterface, ogni DnsInfo legato al nome di un dns memorizza tutte le informazioni del dns sia
     * modificate che già registrate in ServerInterface.
     * Se invece si vuole eliminare un dns chiamato "d1" verrà salvato il collegamento "d1" -> null.
     * Per creare un nuovo dns chiamato "d1" viene salvato "<nome unico>" -> <tutti i dati di d1>, come per
     * modificare ogni campo di un dns già esistente, il campo <nome unico> è nella forma "nuovo dns <num>" e una
     * volta creato questo dns temporaneo è possibile modificare le sue caratteristiche per avere il dns con i
     * dati voluti. Una volta che ServerInterface riceve una modifica a un dns che non ha ancora registrato sa che si
     * tratta di uno nuovo
     */
    private static final Map<String, DnsInfo> dns_changes = new LinkedHashMap<>();

    /*
     * Quando il pannello viene chiuso normalmente resetta il contenuto di dns_changes. Ma se viene premuto il tasto
     * "ok", poiché il salvataggio delle informazioni è fatto su un altro thread, la mappa viene resettata prima che si
     * possano salvare le modifiche effettuate, impostando saving_changes = true la mappa dns_changes non viene
     * resettata a chiusura del pannello
     */
    private static boolean saving_changes = false;

    public static final ActionListener APPLY_LISTENER = _ -> {
        //salva i cambiamenti apportati al dns visualizzato in questo momento
        DnsInfo changed_data = data_displayer.extract_info();
        if (changed_data != null) { //se non stava mostrando nessun dato di dns, ritorna null
            dns_changes.put(data_displayer.current_dns(), changed_data);
        }

        //evita di creare più di un thread per salvare i dati delle modifiche
        if (saving_changes) {
            return;
        }

        //per eliminare dns richiede input dall'utente e attende una risposta, questo thread controlla la grafica
        //e fermandolo non si mostrerebbe il TempPanel
        new Thread(() -> {
            saving_changes = true;

            for (String changed_dns : dns_changes.keySet()) {
                ServerInterface.update_dns(
                        changed_dns,
                        dns_changes.get(changed_dns)
                );
            }

            dns_changes.clear();
            dns_list.setList(ServerInterface.get_dns_list());

            saving_changes = false;
        }).start();
    };

    public static final ActionListener OK_LISTENER = _ -> {
        APPLY_LISTENER.actionPerformed(null);
        saving_changes = true; //non resetta la mappa con le modifiche con SettingsFrame.close()

        SettingsFrame.close();
    };

    public static void init() {
        main_panel.setOpaque(false);
        main_panel.setLayout(new GridBagLayout());

        //la larghezza non è specificata essendo l'unico componente nella riga
        dns_scroller.setPreferredSize(new Dimension(0, 150));
        data_scroller.setPreferredSize(new Dimension(0, 150));

        update_colors_static();

        dns_list.set_popup(new DnsListPopup(dns_list));
        dns_list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                //salva i cambiamenti apportati al dns visualizzato in questo momento
                DnsInfo changed_data = data_displayer.extract_info();
                if (changed_data != null) { //se non stava mostrando nessun dato di dns, ritorna null
                    dns_changes.put(data_displayer.current_dns(), changed_data);
                }

                String new_name = dns_list.getSelectedValue();
                if (dns_changes.containsKey(new_name)) {
                    data_displayer.show_data_of(dns_changes.get(new_name));
                }
                else {
                    data_displayer.show_data_of(ServerInterface.get_dns_info(new_name));
                }
            }
        });

        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 0;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(10, 10, 10,10);

        main_panel.add(dns_scroller, c);

        c.weighty = 1;
        c.gridy = 1;
        c.insets.top = 0;

        main_panel.add(data_scroller, c);
    }

    private static void update_colors_static() {
        data_displayer.update_colors();
        data_scroller.update_colors();
        dns_scroller.update_colors();
        dns_list.update_colors();
    }

    @Override
    public JPanel prepare() {
        //imposta la lista di dns con quella contenuta in ServerInterface
        dns_list.setList(ServerInterface.get_dns_list());
        data_displayer.reset();
        data_displayer.update_comboBoxs();

        return main_panel;
    }

    @Override
    public void close() {
        if (!saving_changes) {
            dns_changes.clear();
        }
    }

    @Override
    public void update_colors() {
        update_colors_static();
    }
}

//pannello che mostra i dati dei dns
class DnsDataDisplayer extends JPanel {
    private final JTextField name = new JTextField(),
                             ip = new JTextField(),
                             port = new JTextField();
    private final JLabel name_label = new JLabel("dns name:"),
                         ip_label = new JLabel("dns ip:"),
                         port_label = new JLabel("porta:"),
                         connector_label = new JLabel("dns connector:"),
                         pkey_label = new JLabel("dns public key:");

    private final JTextArea pKey = new JTextArea("");
    private final MComboBox connector = new MComboBox();

    private String displayed_name = null;

    public DnsDataDisplayer() {
        this.setLayout(new GridBagLayout());
        update_colors();
        update_comboBoxs();

        pKey.setLineWrap(true);

        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(10, 10, 0, 10);
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 0;

        this.add(name_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(name, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;

        this.add(ip_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(ip, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;

        this.add(port_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(port, c);

        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0;

        this.add(connector_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(connector, c);

        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.FIRST_LINE_START;

        this.add(pkey_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(pKey, c);
    }

    //mostra le informazioni di un dns
    public void show_data_of(DnsInfo info) {
        displayed_name = info.NAME;

        name.setText(info.NAME);
        ip.setText(info.IP);
        port.setText(String.valueOf(info.PORT));
        pKey.setText(Base64.getEncoder().encodeToString(info.get_pkey()));
        connector.setSelectedItem(info.CONNECTOR);
    }

    //ritorna una lista con tutti i cambiamenti apportati ai dati di questo server o null se nessun dato di server è mostrato
    public DnsInfo extract_info() {
        if (displayed_name == null) { //non è mostrato nessun info di dns
            return null;
        }

        try {
            return new DnsInfo(
                    name.getText(),
                    ip.getText(),
                    Integer.parseInt(port.getText()),
                    (String) connector.getSelectedItem(),
                    Base64.getDecoder().decode(pKey.getText())
            );
        }
        catch (Exception e) { //Integer.parseInt o Base64.getDecoder().decode
            Logger.log("impossibile calcolare il nuovo ServerInfo per il server (" + displayed_name + ")");
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "i dati inseriti non sono validi"
            ), null);

            return null;
        }
    }

    public String current_dns() {
        return displayed_name;
    }

    //resetta tutti i campi come vuoti
    public void reset() {
        name.setText("");
        ip.setText("");
        port.setText("");
        pKey.setText("");

        displayed_name = null;
    }

    public void update_colors() {
        Color text_color = (Color) GraphicsSettings.active_theme().get_value("text_color");
        Color input_tc = (Color) GraphicsSettings.active_theme().get_value("input_text_color");
        Color input_bg = (Color) GraphicsSettings.active_theme().get_value("input_background");
        Border input_border = (Border) GraphicsSettings.active_theme().get_value("input_border");

        this.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));

        this.name_label.setForeground(text_color);
        this.ip_label.setForeground(text_color);
        this.port_label.setForeground(text_color);
        this.connector_label.setForeground(text_color);
        this.pkey_label.setForeground(text_color);
        this.name.setForeground(input_tc);
        this.ip.setForeground(input_tc);
        this.port.setForeground(input_tc);
        this.connector.setForeground(input_tc);
        this.pKey.setForeground(input_tc);
        this.name.setBackground(input_bg);
        this.ip.setBackground(input_bg);
        this.port.setBackground(input_bg);
        this.connector.setBackground(input_bg);
        this.pKey.setBackground(input_bg);
        this.name.setBorder(input_border);
        this.ip.setBorder(input_border);
        this.port.setBorder(input_border);
        this.connector.setBorder(input_border);
        this.pKey.setBorder(input_border);
    }

    public void update_comboBoxs() {
        connector.removeAllItems();

        for (String connector_name : ServerInterface.get_dns_connectors_list()) {
            connector.addItem(connector_name);
        }
    }
}