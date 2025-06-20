package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class CascateItem extends JLabel {
    private final CascateAction ACTION;

    public CascateItem(String text, CascateAction action) {
        super(text);
        this.ACTION = action;
        this.setVerticalTextPosition(SwingConstants.BOTTOM);
        this.setIcon((ImageIcon) GraphicsSettings.active_theme().get_value("cascate_item_icon"));
        this.setForeground((Color) GraphicsSettings.active_theme().get_value("list_text_color"));
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("list_selected_background"));

        this.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {}
            @Override
            public void mouseReleased(MouseEvent e) {}
            @Override
            public void mouseEntered(MouseEvent e) {}
            @Override
            public void mouseExited(MouseEvent e) {}

            @Override
            public void mousePressed(MouseEvent e) {
                grab_focus();
            }
        });
    }

    private void grab_focus() {
        CascateMenu menu;
        if (getParent().getParent() instanceof CascateMenu) {
            menu = (CascateMenu) getParent().getParent();
        }
        else if (getParent() instanceof CascateMenu) {
            menu = (CascateMenu) getParent();
        }
        else {
            return;
        }

        ACTION.run(menu.get_full_path() + "/" + getText());

        menu.set_focussed_item(this);
        set_focussed();
    }

    public void set_focussed() {
        setOpaque(true);
        setForeground((Color) GraphicsSettings.active_theme().get_value("list_selected_text_color"));

        repaint();
    }

    public void set_unfocussed() {
        setOpaque(false);
        setForeground((Color) GraphicsSettings.active_theme().get_value("list_text_color"));

        repaint();
    }

    public void update_colors() {
        setBackground((Color) GraphicsSettings.active_theme().get_value("list_selected_background"));
        setForeground((Color) GraphicsSettings.active_theme().get_value("list_" + (isOpaque()? "selected_" : "") + "text_color"));
        setIcon((ImageIcon) GraphicsSettings.active_theme().get_value("cascate_item_icon"));
    }
}