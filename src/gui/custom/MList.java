package gui.custom;

import files.Logger;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Collection;
import java.util.Set;

public class MList extends JList<String> {
    //permette di modificare il contenuto della lista in modo più tipo Vector<>
    private final DefaultListModel<String> list_model = new DefaultListModel<>();

    //nome utilizzato per mostrare errori e messaggi relativi a questa lista, non viene visualizzato nella gui
    private final String LIST_NAME;

    public MList(String list_name) {
        super();
        super.setModel(list_model);
        super.setCellRenderer(new MCellRenderer());
        this.LIST_NAME = list_name;
    }

    public void update_colors() {
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("list_background"));
        this.repaint(); //chiama il cell renderer per tutte le celle della lista

        //il popupmenu di questa lista deve essere impostato con set_popup() che accetta solo GPopupMenu, se sono definiti
        //aggiorna i loro colori
        if (this.getComponentPopupMenu() != null) {
            ((MPopupMenu) this.getComponentPopupMenu()).update_colors();
        }
    }

    public void add(String name) {
        if (list_model.contains(name)) {
            Logger.log("impossibile aggiungere l'elemento: (" + name + ") alla lista: (" + LIST_NAME + ")", true);
            return;
        }

        list_model.addElement(name);
    }

    //sostituisce setListData per non resettare il list model
    public void set_list(Collection<? extends String> data) {
        list_model.clear();
        list_model.addAll(data);
    }

    public void rename_element(String old_name, String new_name) {
        int item_index = list_model.indexOf(old_name);
        if (item_index == -1) {
            Logger.log("impossibile trovare un item chiamato: (" + old_name + ") da rinominare in: (" + new_name + ") nella lista: (" + LIST_NAME + ")", true);
            return;
        }

        list_model.removeElement(old_name);
        list_model.insertElementAt(new_name, item_index);
    }


    public int get_size() {
        return list_model.getSize();
    }

    public void remove(String name) {
        list_model.removeElement(name);
    }

    public void clear() {
        list_model.clear();
    }
}

class MCellRenderer extends JLabel implements ListCellRenderer<String> {
    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
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

        return this;
    }
}