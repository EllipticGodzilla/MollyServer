package gui;

import files.Logger;
import gui.custom.ButtonIcons;
import gui.custom.MList;
import gui.custom.MScrollPane;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_info;
import gui.themes.GraphicsSettings;
import network.ServerInterface;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;

public abstract class ClientList_panel {
    private static JButton connect = null;
    private static JButton disconnect = null;
    private static MList clients_list = null;
    private static JPanel client_panel = null;

    protected static JPanel init() {
        if (client_panel == null) {
            client_panel = new JPanel();
            client_panel.setLayout(new GridBagLayout());
            client_panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 5));
            client_panel.setOpaque(false);

            //inizializza tutti i componenti della gui
            connect = new JButton();
            disconnect = new JButton();
            clients_list = new MList("client list");
            MScrollPane clients_scroller = new MScrollPane(clients_list);
            JPanel spacer = new JPanel();

            disconnect.setEnabled(false);

            spacer.setFocusable(false);
            spacer.setOpaque(false);

            connect.setBorder(null);
            disconnect.setBorder(null);
            spacer.setBorder(null);

            connect.addActionListener(join_class);
            disconnect.addActionListener(exit_class);

            connect.setOpaque(false);
            disconnect.setOpaque(false);
            connect.setContentAreaFilled(false);
            disconnect.setContentAreaFilled(false);

            GridBagConstraints c = new GridBagConstraints();

            c.fill = GridBagConstraints.BOTH;
            c.weighty = 0; //i due pulsanti non si ridimensionano
            c.weightx = 0;

            c.gridx = 0;
            c.gridy = 0;
            c.insets = new Insets(0, 0, 5, 5);
            client_panel.add(disconnect, c);

            c.gridx = 2;
            c.gridy = 0;
            c.insets = new Insets(0, 5, 5, 0);
            client_panel.add(connect, c);

            c.weightx = 1; //lo spacer dovrà allungarsi sulle x per permettere ai pulsanti di rimanere delle stesse dimensioni

            c.gridx = 1;
            c.gridy = 0;
            c.insets = new Insets(0, 5, 5, 5);
            client_panel.add(spacer, c);

            c.weighty = 1; //la lista di client dovrà allungarsi sulle y per compensare i pulsanti

            c.gridx = 0;
            c.gridy = 1;
            c.gridwidth = 3;
            c.insets = new Insets(5, 0, 0, 0);
            client_panel.add(clients_scroller, c);

            update_colors();
        }

        return client_panel;
    }

    public static void update_colors() {
        clients_list.update_colors();

        ButtonIcons connect_icon = (ButtonIcons) GraphicsSettings.active_theme().get_value("client_panel_connect");
        ButtonIcons disconnect_icon = (ButtonIcons) GraphicsSettings.active_theme().get_value("client_panel_disconnect");

        connect.setIcon(connect_icon.getStandardIcon());
        connect.setRolloverIcon(connect_icon.getRolloverIcon());
        connect.setPressedIcon(connect_icon.getPressedIcon());
        connect.setDisabledIcon(connect_icon.getDisabledIcon());
        disconnect.setIcon(disconnect_icon.getStandardIcon());
        disconnect.setRolloverIcon(disconnect_icon.getRolloverIcon());
        disconnect.setPressedIcon(disconnect_icon.getPressedIcon());
        disconnect.setDisabledIcon(disconnect_icon.getDisabledIcon());
    }

    public static ActionListener join_class = _ -> {
        //TODO rimuovi i commenti, una volta aggiunto il supporto alle classi
//        String pair_usr = clients_list.get_selected_value();
//
//        if (!pair_usr.isEmpty() && !ServerManager.is_paired()) { //se è selezionato un client e non è appaiato con nessun altro client
//            ServerManager.pair_with(pair_usr);
//            update_buttons();
//        }
//        else if (ServerManager.is_paired()) { //se è già connesso a un altro client
//            Logger.log("tentativo di collegarsi al client: " + pair_usr + " mentre si è già collegati con: " + ServerManager.get_paired_usr(), true);
//            TempPanel.show(new TempPanel_info(
//                    TempPanel_info.SINGLE_MSG,
//                    false,
//                    "impossibile collegarsi a più di un client"
//            ), null);
//        }
//        else { //se non è selezionato nessun client dalla lista
//            Logger.log("non è stato selezionato nessun client a cui collegarsi dalla lista", true);
//            TempPanel.show(new TempPanel_info(
//                    TempPanel_info.SINGLE_MSG,
//                    false,
//                    "selezionale il client a cui collegarsi"
//            ), null);
//        }
    };

    public static ActionListener exit_class = _ -> {
        if (!ServerInterface.is_connected() || !ServerInterface.is_in_class()) {
            Logger.log("impossibile uscire da una classe, non si è in nessuna", true);
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile uscire da una classe, non si è dentro nessuna"
            ), null);

            return;
        }


        update_buttons();

        TempPanel.show(new TempPanel_info(
                TempPanel_info.SINGLE_MSG,
                false,
                "disconnessione dal client avvenuta con successo"
        ), null);
    };

    public static void update_buttons() {
        disconnect.setEnabled(ServerInterface.is_in_class());
        connect.setEnabled(!ServerInterface.is_in_class());
    }

    /*
     * Ricevuta dal server la lista con tutti gli utenti collegati <usr1>;<usr2>;... aggiorna gli elementi in questa lista
     * rimuovendo dalla lista il proprio nome
     */
    public static void update_list(String list) {
        clients_list.clear();

        String[] names = list.split(";");
        String my_name = ServerInterface.get_id();

        for (String name : names) {
            if (!name.isEmpty() && !name.equals(my_name)) {
                clients_list.add(name);
            }
        }

        clients_list.repaint();
    }
}