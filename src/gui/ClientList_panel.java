package gui;

import gui.custom.ClientsList;
import gui.custom.MScrollPane;

import javax.swing.*;
import java.awt.*;

public abstract class ClientList_panel {
    //lista con tutti i client registrati in questo server o classi attive
    private static ClientsList clients_list = new ClientsList("client list");
    private static JPanel client_panel = null;

    protected static JPanel init() {
        if (client_panel != null) { //evita di inizializzare tutto più volte
            return client_panel;
        }

        client_panel = new JPanel();
        client_panel.setLayout(new GridLayout());
        client_panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 5));
        client_panel.setOpaque(false);
        client_panel.setPreferredSize(new Dimension(250, 200));

        //inizializza tutti i componenti della gui
        MScrollPane clients_scroller = new MScrollPane(clients_list);
        JPanel spacer = new JPanel();

        spacer.setFocusable(false);
        spacer.setOpaque(false);
        spacer.setBorder(null);

        client_panel.add(clients_scroller);

        update_colors();
        return client_panel;
    }

    /// Quando si cambia theme aggiorna i colori della lista
    public static void update_colors() {
        clients_list.update_colors();
    }

    /**
     * Tenta di aggiungere un utente alla lista come utente offline
     * @param uname nome dell'utente da registrare
     * @return true se riesce ad aggiungere l'utente, false se ne esiste già uno con questo nome
     */
    public static boolean register_client(String uname) {
        return clients_list.add(uname, ClientsList.OFFLINE_CLIENT_CELL);
    }

    /**
     * Tenta di spostare un utente dalla lista offline a quella online
     * @param uname nome dell'utente da impostare online
     * @return true se riesce a trovare l'utente offline, false se non lo trova
     */
    public static boolean set_online(String uname) {
        if (clients_list.remove(uname, ClientsList.OFFLINE_CLIENT_CELL)) {
            return clients_list.add(uname, ClientsList.ONLINE_CLIENT_CELL);
        }

        return false;
    }

    /**
     * Tenta di spostare un utente dalla lista online a quella offline
     * @param uname nome dell'utente da impostare offline
     * @return true se riesce a trovare l'utente online, false se non lo trova
     */
    public static boolean set_offline(String uname) {
        if (clients_list.remove(uname, ClientsList.ONLINE_CLIENT_CELL)) {
            return clients_list.add(uname, ClientsList.OFFLINE_CLIENT_CELL);
        }

        return false;
    }
}