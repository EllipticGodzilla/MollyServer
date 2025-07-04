package gui.custom;

import files.Logger;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Estensione di JList che mostra la lista di clients registrati / online e di classi attive nel server, gestisce icons
 * e tutte le operazioni possibili sugli elementi della lista con PopupMenu
 */
public class ClientsList extends JList<String> {
    //A ogni lista viene assegnato un nome in modo da essere riconoscibile in caso di messaggi di errore
    private final String list_name;

    private final DefaultListModel<String> list_model = new DefaultListModel<>();

    //costanti utilizzate per specificare che tipo di cella si sta aggiungendo alla lista
    public static final int CLASS_CELL = 0,
                            ONLINE_CLIENT_CELL = 1,
                            OFFLINE_CLIENT_CELL = 2;

    /*
     * per distinguere fra celle che rappresentano classi, client online, client offline ricorda in questo array il
     * numero di classi attive e il numero di client online
     */
    protected int[] section_sizes = new int[] {0, 0};

    public ClientsList(String list_name) {
        super();

        this.list_name = list_name;
        this.setModel(list_model);
        this.setCellRenderer(new ClientCellRenderer());
        update_colors();
    }

    /// Modificato il theme deve aggiornare i colori della lista
    public void update_colors() {
        ClientCellRenderer.update_icons();
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));

        this.repaint(); //aggiorna tutte le celle
    }

    /**
     * Aggiunge una cella del tipo specificato alla lista, controllando di non aggiungere duplicati per cui non ci
     * possono essere due classi con lo stesso nome o due client con lo stesso nome
     * @param cell_name nome della cella da aggiungere
     * @param cell_type valore fra ClientList.CLASS_CELL, ClientList.ONLINE_CLIENT_CELL, ClientList.OFFLINE_CLIENT_CELL
     * @return true se la casella è stata aggiunta con successo, false se esiste già una casella dello stesso tipo
     *         e con lo stesso nome
     */
    public boolean add(String cell_name, int cell_type) {
        if (cell_type == CLASS_CELL) {
            int index = list_model.indexOf(cell_name);

            //controlla non ci sia una classe con lo stesso nome già attiva
            if (index != -1 && index < section_sizes[0]) {
                Logger.log("impossibile aggiungere una classe con il nome: (" + cell_name + ") alla lista: (" + list_name + "), una è già presente", true);
                return false;
            }

            section_sizes[0]++;
            list_model.add(section_sizes[0]-1, cell_name);
        }
        else {
            //controlla non ci sia un altro client con lo stesso nome
            if (list_model.indexOf(cell_name) >= section_sizes[0]) {
                Logger.log("impossibile aggiungere un client di nome: (" + cell_name + ") alla lista: (" + list_name + "), uno è già registrato", true);
                return false;
            }

            if (cell_type == ONLINE_CLIENT_CELL) {
                section_sizes[1]++;
                list_model.add(section_sizes[0]+section_sizes[1]-1, cell_name);
            }
            else {
                list_model.addElement(cell_name);
            }
        }

        return true;
    }

    /**
     * Prova a rimuovere la cella specificata
     * @param cell_name nome della cella da rimuovere
     * @param cell_type tipo della cella da rimuovere
     * @return true se è riuscito a rimuovere una cella, false se non ha trovato nessuna cella corrispondete a quella
     * specificata
     */
    public boolean remove(String cell_name, int cell_type) {
        int index = list_model.indexOf(cell_name);

        if (cell_type == CLASS_CELL) {
            //controlla ci sia una cella con questo nome fra quelle rappresentanti le classi
            if (index == -1 || index >= section_sizes[0]) {
                Logger.log("impossibile rimuovere la classe: (" + cell_name + ") dalla lista: (" + list_name + "), cella non trovata", true);
                return false;
            }

            section_sizes[0]--;
            list_model.removeElementAt(index);
        }
        else if (cell_type == ONLINE_CLIENT_CELL) {
            //controlla ci sia una cella con questo nome fra quelle rappresentanti clients online
            if (index < section_sizes[0] || index >= section_sizes[0] + section_sizes[1]) {
                Logger.log("impossibile rimuovere il client online: (" + cell_name + ") dalla lista: (" + list_name + "), cella non trovata", true);
                return false;
            }

            section_sizes[1]--;
            list_model.removeElementAt(index);
        }
        else {
            //controlla ci sia una cella con questo nome fra quelle rappresentanti client offline
            if (index < section_sizes[0] + section_sizes[1]) {
                Logger.log("impossibile rimuovere il client offline: (" + cell_name + ") dalla lista: (" + list_name + "), cella non trovata", true);
                return false;
            }

            list_model.removeElementAt(index);
        }

        return true;
    }
}

class ClientCellRenderer extends JLabel implements ListCellRenderer<String> {
    //per evitare di richiedere le icone per tutte le celle vengono richieste solo quando si cambia theme
    private static ImageIcon class_icon, online_icon, offline_icon;

    public static void update_icons() {
        class_icon = (ImageIcon) GraphicsSettings.active_theme().get_value("list_class_icon");
        online_icon = (ImageIcon) GraphicsSettings.active_theme().get_value("list_online_client_icon");
        offline_icon = (ImageIcon) GraphicsSettings.active_theme().get_value("list_offline_client_icon");
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
        //imposta il testo e i colori della cella
        this.setText(value);

        if (isSelected) {
            this.setOpaque(true);

            this.setBackground((Color) GraphicsSettings.active_theme().get_value(cellHasFocus? "list_selected_background" : "list_selected_nofocus_background"));
            this.setForeground((Color) GraphicsSettings.active_theme().get_value("list_selected_text_color"));
            this.setBorder((Border) GraphicsSettings.active_theme().get_value("list_selected_border"));
        }
        else {
            this.setBorder(BorderFactory.createEmptyBorder(3, 3, 0, 0));
            this.setOpaque(false);
            this.setForeground((Color) GraphicsSettings.active_theme().get_value("list_text_color"));
        }

        //imposta l'icona della cella
        ClientsList client_list = (ClientsList) list;

        if (client_list.section_sizes[0] > index) { //rappresenta una classe
            this.setIcon(class_icon);
        }
        else if (client_list.section_sizes[0] + client_list.section_sizes[1] > index) { //rappresenta un client online
            this.setIcon(online_icon);
        }
        else { //rappresenta un client offline
            this.setIcon(offline_icon);
        }

        return this;
    }
}