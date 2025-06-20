package gui.settingsFrame;

import files.Logger;
import gui.custom.*;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_info;
import gui.themes.GraphicsSettings;
import network.ServerInfo;
import network.ServerInterface;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ServerSettingsPanel implements SettingsPanel{
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

        GraphicsSettings.run_at_theme_change(ServerSettingsPanel::update_colors_static);

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

        ServerListEditor.init();
        ServerListEditor server_editor = new ServerListEditor();

        add_item("server/data editor", server_editor, ServerListEditor.OK_LISTENER, ServerListEditor.APPLY_LISTENER);
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
            Logger.log("impossibile aggiungere CascateItem direttamente al ServerSettingsPanel panel, specificare un root menu per: " + path, true);
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
 * pannello in ServerSettingsPanel per modificare o visualizzare tutti i dati relativi ai servers registrati in
 * ServerInterface, nonché unico pannello registrato in ServerSettingsPanel di standard.
 */
class ServerListEditor implements SettingsPanel {
    private static class ServerListPopup extends MPopupMenu {
        public ServerListPopup(MList parent_list) {
            MMenuItem add_server = new MMenuItem("add server");
            MMenuItem rem_server = new MMenuItem("remove server");

            /*
             * Per aggiungere un server alla lista genera un nome "nuovo server <num>", con <num> il più piccolo intero
             * positivo che lo renda unico fra i server registrati in ServerInterface, e gli assegna tutti valori nulli.
             * Aggiungendo l'oggetto ServerInfo alla mappa servers_changes nel caso in cui l'utente non specifichi tutte
             * le informazioni necessarie per un server valido, una volta inviata la modifica a ServerInterface questo
             * non la accetterà e verrà ignorata.
             */
            add_server.addActionListener(_ -> {
                int name_dist = 1; //intero che rende unico il nome del server da creare
                Set<String> reg_servers = ServerInterface.get_server_list();
                String server_name;
                do {
                    server_name = "nuovo server " + name_dist;
                    name_dist++;
                }
                while (reg_servers.contains(server_name) || servers_changes.containsKey(server_name));

                servers_list.add(server_name);
                servers_changes.put(
                        server_name,
                        new ServerInfo(
                                server_name,
                                null,
                                null,
                                0,
                                null,
                                "standard encoder",
                                "standard connector"
                        )
                );
            });

            /*
             * per rimuovere server basta legare il nome del server a null nella mappa con le modifiche, una volta
             * inviata a ServerInterface eliminerà i dati del server dai registrati
             */
            rem_server.addActionListener(_ -> {
                String selected_server = parent_list.getSelectedValue();
                if (selected_server == null) { //non è selezionato nessun server dalla lista
                    return;
                }

                servers_changes.put(selected_server, null);
                servers_list.remove(selected_server);
                data_displayer.reset();
            });

            this.add(add_server);
            this.add(rem_server);
        }
    }

    private static final JPanel main_panel = new JPanel();
    private static final ServerDataDisplayer data_displayer = new ServerDataDisplayer();
    private static final MList servers_list = new MList("server settings list");
    private static final MScrollPane server_scroller = new MScrollPane(servers_list),
                                     data_scroller = new MScrollPane(data_displayer);

    /*
     * Memorizza tutti i cambiamenti fatti ai dati dei server prima di apportarli effettivamente alla lista in
     * ServerInterface, ogni ServerInfo legato al nome di un server memorizza tutte le informazioni del server sia
     * modificate che già registrate in ServerInterface.
     * Se invece si vuole eliminare un server chiamato "s1" verrà salvato il collegamento "s1" -> null.
     * Per creare un nuovo server chiamato "s1" viene salvato "<nome unico>" -> <tutti i dati di s1>, come per
     * modificare ogni campo di un server già esistente, il campo <nome unico> è nella forma "nuovo server <num>" e una
     * volta creato questo server temporaneo è possibile modificare le sue caratteristiche per avere il server con i
     * dati voluti. Una volta che ServerInterface riceve una modifica a un server che non ha ancora registrato sa che si
     * tratta di uno nuovo
     */
    private static final Map<String, ServerInfo> servers_changes = new LinkedHashMap<>();

    /*
     * quando il pannello viene chiuso normalmente resetta il contenuto di server_changes. Ma se viene premuto il tasto
     * "ok", poiché il salvataggio delle informazioni è fatto su un altro thread, la mappa viene resettata prima che si
     * possano salvare le modifiche effettuate, impostando saving_changes = true la mappa servers_changes non viene
     * resettata a chiusura del pannello
     */
    private static boolean saving_changes = false;

    public static final ActionListener APPLY_LISTENER = _ -> {
        //salva i cambiamenti apportati al server visualizzato in questo momento
        ServerInfo changed_data = data_displayer.extract_info();
        if (changed_data != null) { //se non stava mostrando nessun dato di server, ritorna null
            servers_changes.put(data_displayer.current_server(), changed_data);
        }

        //evita di creare più di un thread per salvare i dati delle modifiche
        if (saving_changes) {
            return;
        }

        //per eliminare server richiede input dall'utente e attende una risposta, questo thread controlla la grafica
        //e fermandolo non si mostrerebbe il TempPanel
        new Thread(() -> {
            saving_changes = true;

            for (String changed_server : servers_changes.keySet()) {
                ServerInterface.update_server(
                        changed_server,
                        servers_changes.get(changed_server)
                );
            }

            servers_changes.clear();
            servers_list.setList(ServerInterface.get_server_list());

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
        server_scroller.setPreferredSize(new Dimension(0, 150));
        data_scroller.setPreferredSize(new Dimension(0, 150));

        update_colors_static();

        servers_list.set_popup(new ServerListPopup(servers_list));
        servers_list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                //salva i cambiamenti apportati al server visualizzato in questo momento
                ServerInfo changed_data = data_displayer.extract_info();
                if (changed_data != null) { //se non stava mostrando nessun dato di server, ritorna null
                    servers_changes.put(data_displayer.current_server(), changed_data);
                }

                String new_name = servers_list.getSelectedValue();
                if (servers_changes.containsKey(new_name)) {
                    data_displayer.show_data_of(servers_changes.get(new_name));
                }
                else {
                    data_displayer.show_data_of(ServerInterface.get_server_info(new_name));
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

        main_panel.add(server_scroller, c);

        c.weighty = 1;
        c.gridy = 1;
        c.insets.top = 0;

        main_panel.add(data_scroller, c);
    }

    private static void update_colors_static() {
        data_displayer.update_colors();
        data_scroller.update_colors();
        server_scroller.update_colors();
        servers_list.update_colors();
    }

    @Override
    public JPanel prepare() {
        //imposta la lista di server con quella contenuta in ServerInterface
        servers_list.setList(ServerInterface.get_server_list());
        data_displayer.reset();
        data_displayer.update_comboBoxs();

        return main_panel;
    }

    @Override
    public void close() {
        if (!saving_changes) {
            servers_changes.clear();
        }
    }

    @Override
    public void update_colors() {
        update_colors_static();
    }
}

/*
 * mostra e permette di modificare i dati dei server selezionati nel pannello ServerListEditor, compila ServerInfo con
 * tutte le modifiche apportate dall'utente
 */
class ServerDataDisplayer extends JPanel {
    private final JTextField name = new JTextField(),
                             ip = new JTextField(),
                             port = new JTextField(),
                             link = new JTextField();
    private final MComboBox dns = new MComboBox(),
                            encoder = new MComboBox(),
                            connector = new MComboBox();

    private final JLabel name_label = new JLabel("server name:"),
                         ip_label = new JLabel("server ip:"),
                         port_label = new JLabel("porta:"),
                         link_label = new JLabel("server link:"),
                         dns_label = new JLabel("server dns:"),
                         encoder_label = new JLabel("server encoder:"),
                         connector_label = new JLabel("server connector:");

    //nome del server di cui sta mostrando le informazioni
    private String displayed_name = null;

    public ServerDataDisplayer() {
        this.setLayout(new GridBagLayout());
        update_colors();
        update_comboBoxs();

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

        this.add(link_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(link, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;

        this.add(ip_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(ip, c);

        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0;

        this.add(port_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(port, c);

        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 0;

        this.add(dns_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(dns, c);

        c.gridx = 0;
        c.gridy = 5;
        c.weightx = 0;

        this.add(connector_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(connector, c);

        c.insets.bottom = 10;
        c.gridx = 0;
        c.gridy = 6;
        c.weighty = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.FIRST_LINE_START;

        this.add(encoder_label, c);

        c.gridx = 1;
        c.weightx = 1;

        this.add(encoder, c);
    }

    //mostra le informazioni di un server
    public void show_data_of(ServerInfo info) {
        displayed_name = info.SERVER_NAME;

        name.setText(info.SERVER_NAME);
        link.setText(info.serverLink());
        ip.setText(info.serverIp());
        port.setText(String.valueOf(info.SERVER_PORT));
        dns.setSelectedItem(info.serverDNS());
        encoder.setSelectedItem(info.ENCODER);
        connector.setSelectedItem(info.CONNECTOR);
    }

    //ritorna un oggetto ServerInfo con le caratteristiche specificate nei vari campi
    public ServerInfo extract_info() {
        if (displayed_name == null) { //non è mostrato nessun server
            return null;
        }

        try {
            return new ServerInfo(
                    name.getText(),
                    (link.getText().equals(">not defined<") || link.getText().isEmpty()) ? null : link.getText(),
                    (ip.getText().equals(">not defined<") || ip.getText().isEmpty()) ? null : ip.getText(),
                    Integer.parseInt(port.getText()),
                    Objects.equals(dns.getSelectedItem(), ">not defined<")? null : (String) dns.getSelectedItem(),
                    (String) encoder.getSelectedItem(),
                    (String) connector.getSelectedItem()
            );
        }
        catch (NumberFormatException _) { //parseInt(port.getText())
            Logger.log("impossibile calcolare il nuovo ServerInfo per il server (" + displayed_name + "), la porta specificata non è un intero: " + port.getText());
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "inserisci un numero nel campo \"porta\""
            ), null);

            return null;
        }
    }

    public String current_server() {
        return displayed_name;
    }

    //resetta tutti i campi come vuoti
    public void reset() {
        name.setText("");
        link.setText("");
        ip.setText("");
        port.setText("");
        dns.setSelectedItem(">not defined<");

        displayed_name = null;
    }

    public void update_colors() {
        Color text_color = (Color) GraphicsSettings.active_theme().get_value("text_color");
        Color input_tc = (Color) GraphicsSettings.active_theme().get_value("input_text_color");
        Color input_bg = (Color) GraphicsSettings.active_theme().get_value("input_background");
        Border input_border = (Border) GraphicsSettings.active_theme().get_value("input_border");

        this.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));

        this.name_label.setForeground(text_color);
        this.link_label.setForeground(text_color);
        this.ip_label.setForeground(text_color);
        this.port_label.setForeground(text_color);
        this.dns_label.setForeground(text_color);
        this.connector_label.setForeground(text_color);
        this.encoder_label.setForeground(text_color);
        this.name.setForeground(input_tc);
        this.link.setForeground(input_tc);
        this.ip.setForeground(input_tc);
        this.port.setForeground(input_tc);
        this.dns.setForeground(input_tc);
        this.connector.setForeground(input_tc);
        this.encoder.setForeground(input_tc);
        this.name.setBackground(input_bg);
        this.link.setBackground(input_bg);
        this.ip.setBackground(input_bg);
        this.port.setBackground(input_bg);
        this.dns.setBackground(input_bg);
        this.connector.setBackground(input_bg);
        this.encoder.setBackground(input_bg);
        this.name.setBorder(input_border);
        this.link.setBorder(input_border);
        this.ip.setBorder(input_border);
        this.port.setBorder(input_border);
        this.dns.setBorder(input_border);
        this.connector.setBorder(input_border);
        this.encoder.setBorder(input_border);
    }

    public void update_comboBoxs() {
        dns.removeAllItems();
        encoder.removeAllItems();
        connector.removeAllItems();

        dns.addItem(">not defined<");
        for (String dns_name : ServerInterface.get_dns_list()) {
            dns.addItem(dns_name);
        }
        for (String encoder_name : ServerInterface.get_encoder_list()) {
            encoder.addItem(encoder_name);
        }
        for (String connector_name : ServerInterface.get_server_connectors_list()) {
            connector.addItem(connector_name);
        }
    }
}