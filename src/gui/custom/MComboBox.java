package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.metal.MetalComboBoxButton;
import java.awt.*;

public class MComboBox extends JComboBox<String> {
    public MComboBox(String[] list) {
        super(list);

        init_graphics();
    }

    public MComboBox() {
        init_graphics();
    }

    public void init_graphics() {
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
        this.setForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));
        this.setBorder((Border) GraphicsSettings.active_theme().get_value(("input_border")));

        ComboBoxRenderer renderer = new ComboBoxRenderer();
        renderer.setPreferredSize(new Dimension(100, 16));
        renderer.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        this.setRenderer(renderer);
        ((MetalComboBoxButton) this.getComponents()[0]).setBorder(null);
    }

    public void update_color() {
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
        this.setForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));
        this.setBorder((Border) GraphicsSettings.active_theme().get_value(("input_border")));

        ((ComboBoxRenderer) this.getRenderer()).list_init = true; //aggiorna i colori della lista
    }

    public void set_list(String[] new_list) {
        DefaultComboBoxModel<String> new_model = new DefaultComboBoxModel<>(new_list);
        this.setModel(new_model);
    }

    public boolean contains(String item) {
        return ((DefaultComboBoxModel<String>) getModel()).getIndexOf(item) != -1;
    }

    static class ComboBoxRenderer extends JLabel implements ListCellRenderer<String> {
        boolean list_init = true;

        public ComboBoxRenderer() {
            setOpaque(true);
            setHorizontalAlignment(CENTER);
            setVerticalAlignment(CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            if (list_init) {
                list.setSelectionBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
                list.setSelectionForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));
                list.setBorder(null);

                list_init = false;
            }

            if (isSelected) {
                setBackground((Color) GraphicsSettings.active_theme().get_value("dropdown_selected_background"));
                setForeground((Color) GraphicsSettings.active_theme().get_value("dropdown_selected_text_color"));
            }
            else {
                setBackground((Color) GraphicsSettings.active_theme().get_value("dropdown_background"));
                setForeground((Color) GraphicsSettings.active_theme().get_value("dropdown_text_color"));
            }

            setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            setText(value);

            return this;
        }
    }
}