package gui.settingsFrame;

import files.Logger;
import gui.MollyFrame;
import gui.custom.ButtonIcons;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

//frame che mostra tutti i pannelli per le impostazioni
public abstract class SettingsFrame {
    private static final JFrame frame = new JFrame();
    private static SettingsPanel visible_panel;

    private static final JButton ok_button = new JButton();
    private static final JButton cancel_button = new JButton();
    private static final JButton apply_button = new JButton();

    public static final int OK_BUTTON = 0,
                            APPLY_BUTTON = 1;

    public static void init() {
        frame.setMinimumSize(new Dimension(800, 420));
        frame.getContentPane().setLayout(new GridBagLayout());
        frame.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {}
            @Override
            public void windowIconified(WindowEvent e) {}
            @Override
            public void windowDeiconified(WindowEvent e) {}
            @Override
            public void windowActivated(WindowEvent e) {}
            @Override
            public void windowDeactivated(WindowEvent e) {}
            @Override
            public void windowClosed(WindowEvent e) {}

            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });

        update_colors();
        GraphicsSettings.run_at_theme_change(SettingsFrame::update_colors);

        ok_button.setBorder(null);
        cancel_button.setBorder(null);
        apply_button.setBorder(null);

        ok_button.setOpaque(false);
        cancel_button.setOpaque(false);
        apply_button.setOpaque(false);
        ok_button.setContentAreaFilled(false);
        cancel_button.setContentAreaFilled(false);
        apply_button.setContentAreaFilled(false);

        cancel_button.addActionListener(_ -> close());

        JPanel separator = new JPanel();
        separator.setBackground(Color.BLACK);
        separator.setPreferredSize(new Dimension(0, 2));

        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 0;
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 3;

        frame.getContentPane().add(separator, c);

        c.fill = GridBagConstraints.VERTICAL;
        c.gridy = 2;
        c.gridwidth = 1;
        c.anchor = GridBagConstraints.FIRST_LINE_END;
        c.insets = new Insets(5, 0, 5, 5);

        frame.getContentPane().add(ok_button, c);

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 1;
        c.weightx = 0;

        frame.getContentPane().add(cancel_button, c);

        c.gridx = 2;

        frame.getContentPane().add(apply_button, c);

        MollySettingsPanel.init();
        FileManagerPanel.init();
        ModManagerPanel.init();
        ServerSettingsPanel.init();
        DnsSettingsPanel.init();
    }

    public static void update_colors() {
        frame.getContentPane().setBackground((Color) GraphicsSettings.active_theme().get_value("frame_background"));

        ButtonIcons ok_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("ok_icons");
        ButtonIcons annulla_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("annulla_icons");
        ButtonIcons apply_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("apply_icons");

        ok_button.setIcon(ok_icons.getStandardIcon());
        ok_button.setRolloverIcon(ok_icons.getRolloverIcon());
        ok_button.setPressedIcon(ok_icons.getPressedIcon());
        cancel_button.setIcon(annulla_icons.getStandardIcon());
        cancel_button.setRolloverIcon(annulla_icons.getRolloverIcon());
        cancel_button.setPressedIcon(annulla_icons.getPressedIcon());
        apply_button.setIcon(apply_icons.getStandardIcon());
        apply_button.setRolloverIcon(apply_icons.getRolloverIcon());
        apply_button.setPressedIcon(apply_icons.getPressedIcon());
    }

    public static void show(SettingsPanel panel_wrapper, String name) {
        if (visible_panel != null) {
            //anche se già visibile chiamare setVisible(true) porta il frame in primo piano
            frame.setVisible(true);
            return;
        }
        visible_panel = panel_wrapper;

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0 ;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = 3;

        frame.getContentPane().add(panel_wrapper.prepare(), c);
        frame.setTitle(name);

        update_position();
        frame.setVisible(true);
    }

    //centra questo JFrame con il GFrame
    private static void update_position() {
        Rectangle gframe_bounds = MollyFrame.get_bounds();
        frame.setLocation(
                gframe_bounds.x + gframe_bounds.width/2 - frame.getWidth()/2,
                gframe_bounds.y + gframe_bounds.height/2 - frame.getHeight()/2
        );
    }

    public static void close() {
        if (visible_panel != null) {
            visible_panel.close();
            frame.getContentPane().remove(4); //il 4 elemento è sempre il pannello visibile
            visible_panel = null;
        }

        if (ok_button.getActionListeners().length != 0) {
            ok_button.removeActionListener(ok_button.getActionListeners()[0]);
        }
        if (apply_button.getActionListeners().length != 0) {
            apply_button.removeActionListener(apply_button.getActionListeners()[0]);
        }

        frame.setVisible(false);
    }

    public static void set_action_listener(ActionListener listener, int button) {
        switch (button) {
            case OK_BUTTON:
                if (ok_button.getActionListeners().length != 0) {
                    ok_button.removeActionListener(ok_button.getActionListeners()[0]);
                }

                ok_button.addActionListener(listener);
                break;

            case APPLY_BUTTON:
                if (apply_button.getActionListeners().length != 0) {
                    apply_button.removeActionListener(apply_button.getActionListeners()[0]);
                }

                apply_button.addActionListener(listener);
                break;

            default:
                Logger.log("impossibile aggiungere un listener al pulsante: " + button + " nel settings frame", true);
        }
    }
}