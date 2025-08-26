package gui.settingsFrame;

import files.FileInterface;
import files.Logger;
import files.Pair;
import gui.custom.*;
import gui.themes.GraphicsSettings;
import network.ClientsInterface;
import network.LoginManager;
import network.ServerManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Vector;

//pannello mostrato da MollySettingsPanel "server/network settings", permette di modificare le impostazioni su login
//manager, workers threads, connector
public class NetworkSettings implements SettingsComponent {
    private static final JPanel main_panel = new JPanel(), //main panel, viene ritornato da prepare()
                                login_manager_panel = new JPanel(), //impostazioni relative al login manager
                                worker_threads_panel = new JPanel(), //impostazioni relative ai worker threads
                                connector_manager_panel = new JPanel(); //impostazinoi relative ai connectors

    //scroller per il main panel
    private static final MScrollPane main_scroller = new MScrollPane(main_panel);

    //combo box utilizzata per scegliere il login manager da utilizzare
    private static final MComboBox login_manager_combobox = new MComboBox();

    //pannello che mostra tutti i clients registrati in un dato login manager
    private static RegisteredClientsList registered_clients_list;

    //field per scegliere il numero di worker threads
    private static final MIntegerField wt_number_filed = new MIntegerField(0, 64);

    //field per scegliere la capacità del backlog per i worker threads
    private static final MIntegerField wt_backlog_field = new MIntegerField(0, 10000);

    //scroll pane con la lista di tutti i connector che permette di modificare o visualizzare se sono attivi / accesi
    private static final ConnectorListDisplayer connector_displayer = new ConnectorListDisplayer(new JPanel());

    //labels per specificare a cosa si riferiscono vari componenti
    private static final JLabel lm_label = new JLabel("Login manager:"),
                                wtn_label = new JLabel("Worker threads number:"),
                                wtb_label = new JLabel("worker threads backlog capacity:"),
                                c_label = new JLabel("connector:");

    public static final ActionListener apply_listener = _ -> {
        //è possibile modificare login manager o worker threads solo a server spento
        if (!ServerManager.is_online()) {
            change_login_manager();
            change_worker_threads();
        }

        connector_displayer.apply_changes();
    };

    public static final ActionListener ok_listener = _ -> {
        apply_listener.actionPerformed(null);
        SettingsFrame.close();
    };

    /**
     * Applica tutte le modifiche apportate sul login manager attivo / utenti relativi a utenti in vari login manager
     */
    private static void change_login_manager() {
        String new_manager = (String) login_manager_combobox.getSelectedItem();
        if (new_manager == null) { //la combobox è vuota, non ci sono login manager fra cui scegliere
            return;
        }

        String current_manager = (ServerManager.get_login_manager() == null)? null : ServerManager.get_login_manager().get_name();
        if (!new_manager.equals(current_manager)) { //ha cambiato il login manager
            Logger.log("cambio il login manager da: (" + current_manager + ") a: (" + new_manager + ")");
            ServerManager.set_login_manager(new_manager);
        }
    }

    /// Applica tutte le modifiche apportate ai parametri per i worker threads, numero e backlog capacity
    private static void change_worker_threads() {
        ClientsInterface.set_workers_number(wt_number_filed.get_value());
        ClientsInterface.set_workers_backlog_capacity(wt_backlog_field.get_value());
    }

    /**
     * Inizializza tutte le componenti grafiche, senza specificare il contenuto, aggiungendole ai vari panel e
     * specificando le loro dimensioni. Se viene chiamato più di una volta mostra un errore e ignora la call.
     */
    public static void init() {
        if (main_panel.getComponentCount() != 0) { //è già stato inizializzato
            Logger.log("impossibile inizializzare più di una volta il network settings panel", true);
            return;
        }
        main_panel.setLayout(new GridBagLayout());

        init_login_panel();
        init_worker_panel();
        init_connector_panel();

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 0;

        main_panel.add(login_manager_panel, c);

        c.gridy = 1;

        main_panel.add(worker_threads_panel, c);

        c.gridy = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.FIRST_LINE_START;

        main_panel.add(connector_manager_panel, c);
    }

    /// Inizializza tutte le componenti relative alle impostazioni sul login manager e aggiunge al login_manager_panel
    private static void init_login_panel() {
        login_manager_panel.setOpaque(false);
        login_manager_panel.setLayout(new GridBagLayout());

        //init components
        MList rc_list = new MList("network settings, registered clients list");
        registered_clients_list = new RegisteredClientsList(rc_list);

        login_manager_combobox.setPreferredSize(new Dimension(150, 20));
        login_manager_combobox.addActionListener(_ -> {
            registered_clients_list.display_login_manager((String) login_manager_combobox.getSelectedItem());
        });
        registered_clients_list.setPreferredSize(new Dimension(0, 150));

        //add components
        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(10, 10, 10, 10);

        login_manager_panel.add(lm_label, c);

        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.fill = GridBagConstraints.VERTICAL;
        c.insets.left = 0;

        login_manager_panel.add(login_manager_combobox, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 10, 10, 10);

        login_manager_panel.add(registered_clients_list, c);
    }

    /// Per ogni connector aggiorna le componenti grafiche mostrando se questo è attivo / acceso
    public static void update_connectors_status() {
        connector_displayer.update();
    }

    /// Inizializza tutte le componenti relative alle impostazioni sui worker threads e le aggiunge al worker_threads_panel
    private static void init_worker_panel() {
        worker_threads_panel.setOpaque(false);
        worker_threads_panel.setLayout(new GridBagLayout());

        wt_backlog_field.setPreferredSize(new Dimension(70, 20));
        wt_number_filed.setPreferredSize(new Dimension(70, 20));

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(10, 10, 10, 10);

        worker_threads_panel.add(wtn_label, c);

        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.fill = GridBagConstraints.VERTICAL;
        c.insets.left = 0;

        worker_threads_panel.add(wt_number_filed, c);

        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0;
        c.insets = new Insets(0, 10, 10, 10);

        worker_threads_panel.add(wtb_label, c);

        c.gridwidth = 1;
        c.gridx = 2;
        c.fill = GridBagConstraints.VERTICAL;
        c.weightx = 1;
        c.insets.left = 0;

        worker_threads_panel.add(wt_backlog_field, c);
    }

    /// Inizializza tutte le componenti relative alle impostazioni sui connector e le aggiunge al connector_manager_panel
    private static void init_connector_panel() {
        connector_manager_panel.setOpaque(false);
        connector_manager_panel.setLayout(new GridBagLayout());

        connector_displayer.setPreferredSize(new Dimension(0, 150));

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.VERTICAL;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = new Insets(10, 10, 10, 10);

        connector_manager_panel.add(c_label, c);

        c.gridy = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets.top = 0;

        connector_manager_panel.add(connector_displayer, c);
    }

    @Override
    public Component prepare() {
        update_colors();

        login_manager_combobox.set_list(ServerManager.get_available_login_managers().toArray(new String[0]));
        LoginManager manager = ServerManager.get_login_manager();
        if (manager != null) {
            login_manager_combobox.setSelectedItem(manager.get_name());
        }

        registered_clients_list.display_login_manager((String) login_manager_combobox.getSelectedItem());
        wt_number_filed.set_value(ClientsInterface.get_workers_number());
        wt_backlog_field.set_value(ClientsInterface.get_workers_backlog_capacity());

        update_connectors_status();

        return main_scroller;
    }

    @Override
    public void close() {

    }

    @Override
    public void update_colors() {
        login_manager_combobox.update_colors();
        wt_number_filed.update_color();
        wt_backlog_field.update_color();
        registered_clients_list.update_colors();

        Color text_color = (Color) GraphicsSettings.active_theme().get_value("text_color");
        lm_label.setForeground(text_color);
        wtb_label.setForeground(text_color);
        wtn_label.setForeground(text_color);
        c_label.setForeground(text_color);

        main_panel.setBackground((Color) GraphicsSettings.active_theme().get_value("frame_background"));
    }
}

//pannello che mostra la lista di tutti i clients registrati nei vari login manager
class RegisteredClientsList extends MScrollPane {
    private final MList clients_list;

    public RegisteredClientsList(Component c) {
        super(c);
        clients_list = (MList) c;
    }

    /**
     * Resetta la lista di clients registrati mostrata con quella di un nuovo login manager
     * @param manager_name nome del manager da cui prendere i clients
     */
    public void display_login_manager(String manager_name) {
        Vector<String> users = get_registered_users_list(manager_name);

        if (users.isEmpty()) {
            users.add(">no users registered<");
        }

        clients_list.set_list(users);
    }

    /**
     * Legge dal file {@code database/users/credentials_<name>.dat} la lista di clients registrati a un dato login
     * manager
     * @param manager_name nome del login manager
     * @return vettore con tutti i nomi utente registrati, o un vettore vuoto se non trova il file
     */
    private Vector<String> get_registered_users_list(String manager_name) {
        String file_name = "database/users/credentials_" + manager_name + ".dat";
        Vector<String> clients = new Vector<>();

        if (!FileInterface.exist(file_name)) { //non sono mai stati salvati degli utenti per questo manager
            return clients;
        }

        byte[] file_data = FileInterface.read_file(file_name);
        if (file_data == null) {
            return clients;
        }

        String[] file_lines = new String(file_data).split("\n");
        for (String line : file_lines) {
            int name_len = line.indexOf(';');
            if (name_len > 0) { // != -1 && != 0
                clients.add(line.substring(0, name_len));
            }
        }

        return clients;
    }

    @Override
    public void update_colors() {
        super.update_colors();
        clients_list.update_colors();
    }
}

//mostra una lista con tutti i nomi dei connector registrati e permette di vedere per ognuno se è acceso o attivo
class ConnectorListDisplayer extends MScrollPane {
    //pannello contenuto in questo scroll pane
    private final JPanel contained_panel;

    private static Icon checkbox_on = (ImageIcon) GraphicsSettings.active_theme().get_value("checkbox_on_icon");
    private static Icon checkbox_off = (ImageIcon) GraphicsSettings.active_theme().get_value("checkbox_off_icon");

    private static Icon green_dot = (ImageIcon) GraphicsSettings.active_theme().get_value("green_dot_icon");
    private static Icon red_dot = (ImageIcon) GraphicsSettings.active_theme().get_value("red_dot_icon");

    public ConnectorListDisplayer(Component com) {
        super(com);
        contained_panel = (JPanel) com;

        contained_panel.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));
        contained_panel.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        c.insets = new Insets(5, 0, 0, 5);
        c.weighty = 0;

        Color text_color = (Color) GraphicsSettings.active_theme().get_value("text_color");
        String[] connector_list = ServerManager.get_connectors_list();
        for(int i = 0; i < connector_list.length; i++) {
            Pair<Boolean, Boolean> connector_status = ServerManager.get_connector_status(connector_list[i]);
            add_connector(i, connector_list[i], c, connector_status.first(), connector_status.second(), text_color);
        }

        //riempe lo spazio alla fine del contained_panel
        JPanel filler = new JPanel();
        filler.setOpaque(false);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;

        contained_panel.add(filler, c);
    }

    /**
     * Aggiunge una linea al {@code contained_panel} con una JCheckBox, JLabel, JLabel che rappresentino lo status di un
     * nuovo connector
     * @param index index del connector nella lista {@code ServerManager.get_connector_list()}
     * @param name nome del connector
     * @param c contrains per il GridBagLayout in contained_panel
     * @param active active status del connector
     * @param power power status del connector
     * @param text_color text color nelle JLabel
     */
    private void add_connector(int index, String name, GridBagConstraints c, boolean active, boolean power, Color text_color) {
        JCheckBox active_connector = new JCheckBox(checkbox_off, false);
        active_connector.setSelectedIcon(checkbox_on);
        active_connector.setOpaque(false);
        active_connector.setContentAreaFilled(false);

        if (active) {
            active_connector.setSelected(true);
        }

        JLabel connector_name = new JLabel(name);
        connector_name.setForeground(text_color);
        connector_name.setBorder(new EmptyBorder(3, 0, 0, 0));

        JLabel connector_status = new JLabel(power? green_dot : red_dot);

        c.gridy = index;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.BOTH;

        contained_panel.add(active_connector, c);

        c.gridx = 1;

        contained_panel.add(connector_name, c);

        c.gridx = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.VERTICAL;
        c.anchor = GridBagConstraints.FIRST_LINE_START;

        contained_panel.add(connector_status, c);
    }

    /**
     * Per ogni connector richiede il suo status nell'istante della chiamata e aggiorna le componenti grafiche perchè
     * lo rispecchino
     */
    public void update() {
        String[] connector_list = ServerManager.get_connectors_list();
        for(int i = 0; i < connector_list.length; i++) {
            Pair<Boolean, Boolean> connector_status = ServerManager.get_connector_status(connector_list[i]);
            update_connector(i, connector_status.first(), connector_status.second());
        }
    }

    /**
     * Aggiorna le componenti grafiche per ogni connector in modo che rispecchino se questo è attivo / acceso in questo
     * istante.
     * @param index index del connector da modificare nell array {@code ServerManager.get_connectors_list()}
     * @param active active status da impostare
     * @param powered power status da impostare
     */
    private void update_connector(int index, boolean active, boolean powered) {
        JCheckBox checkbox;
        JLabel power_status;
        try {
            checkbox = (JCheckBox) contained_panel.getComponent(3 * index);
            power_status = (JLabel) contained_panel.getComponent(3*index + 2);
        }
        catch (ArrayIndexOutOfBoundsException _) {
            Logger.log("NetworkSettings connector panel, impossibile aggiornare lo status del connector n: " + index + ", connector non trovato nella lista", true);
            return;
        }

        checkbox.setSelected(active);
        power_status.setIcon(powered? green_dot : red_dot);
    }

    /// Applica tutte le modifiche ai parametri dei connector attivando e disattivandoli in base alle checkbox
    public void apply_changes() {
        String[] connector_list = ServerManager.get_connectors_list();

        for (int i = 0; i < connector_list.length; i++) {
            JCheckBox active = (JCheckBox) contained_panel.getComponent(3*i);

            if (active.isSelected()) {
                ServerManager.activate_connector(connector_list[i]);
            }
            else {
                ServerManager.deactivate_connector(connector_list[i]);
            }
        }
    }

    @Override
    public void update_colors() {
        super.update_colors();
        contained_panel.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));
        checkbox_on = (ImageIcon) GraphicsSettings.active_theme().get_value("checkbox_on");
        checkbox_off = (ImageIcon) GraphicsSettings.active_theme().get_value("checkbox_off");
        Color text_color = (Color) GraphicsSettings.active_theme().get_value("text_color");
        green_dot = (ImageIcon) GraphicsSettings.active_theme().get_value("green_dot_icon");
        red_dot = (ImageIcon) GraphicsSettings.active_theme().get_value("red_dot_icon");

        for (Component c : contained_panel.getComponents()) {
            if (c instanceof JLabel label) {
                label.setForeground(text_color);
            }
            else if (c instanceof JCheckBox checkBox) {
                //todo dovrebbe cambiare le icone dei checkbox, ma ora non so come si faccia
            }
        }
    }
}