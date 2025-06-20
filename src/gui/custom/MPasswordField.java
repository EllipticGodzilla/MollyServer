package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;

public class MPasswordField extends JPasswordField {
    private static final int WIDTH  = 127;
    private static final int HEIGHT = 20;

    private JButton toggle_button = null;

    private boolean clear_text = false;

    public MPasswordField() {
        super();

        this.setBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
        this.setBorder((Border) GraphicsSettings.active_theme().get_value("input_border"));
        this.setFont(new Font("Arial", Font.BOLD, 14));
        this.setForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));

        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setMinimumSize(this.getPreferredSize());

        this.setEchoChar('*'); //nasconde il testo
        gen_toggle_button(); //genera il pulsante per togglare la visibilità del testo
    }

    public JButton get_toggle_button() {
        return toggle_button;
    }

    private void gen_toggle_button() { //genera un pulsante che premuto toggla la visibilità del testo
        toggle_button = new JButton();

        //inizializza la grafica del pulsante con le icone dell'occhio senza la barra
        ButtonIcons show_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("temp_panel_show_password_button");

        toggle_button.setIcon(show_icons.getStandardIcon());
        toggle_button.setPressedIcon(show_icons.getPressedIcon());
        toggle_button.setRolloverIcon(show_icons.getRolloverIcon());

        toggle_button.setBorder(null);
        toggle_button.setOpaque(false);
        toggle_button.setContentAreaFilled(false);

        //aggiunge action listener e ritorna il pulsante
        toggle_button.addActionListener(TOGGLE_LISTENER);
    }

    private final ActionListener TOGGLE_LISTENER = _ -> {
        if (clear_text) { //se in questo momento il testo si vede in chiaro
            setEchoChar('*'); //nasconde il testo

            //modifica le icone del pulsante
            ButtonIcons show_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("temp_panel_show_password_button");

            toggle_button.setIcon(show_icons.getStandardIcon());
            toggle_button.setPressedIcon(show_icons.getPressedIcon());
            toggle_button.setRolloverIcon(show_icons.getRolloverIcon());
        }
        else { //se in questo momeno il testo è nascosto
            setEchoChar((char) 0); //mostra il testo

            //modifica le icone del pulsante
            ButtonIcons show_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("temp_panel_hide_password_button");

            toggle_button.setIcon(show_icons.getStandardIcon());
            toggle_button.setPressedIcon(show_icons.getPressedIcon());
            toggle_button.setRolloverIcon(show_icons.getRolloverIcon());
        }

        clear_text = !clear_text;
    };
}
