package gui;

import gui.custom.MLayeredPane;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;

public abstract class CentralPanel {
    private static final ImagePanel IMAGE_PANEL = new ImagePanel(new ImageIcon(CentralPanel.class.getResource("/images/molly.png")));
    private static final JPanel PROGRAMMABLE_PANEL = new JPanel();
    private static MLayeredPane layeredPane = null;
    private static final JPanel MAIN_PANEL = new JPanel();

    protected static JPanel init() {
        if (layeredPane == null) {
            layeredPane = new MLayeredPane();
            MAIN_PANEL.setLayout(new GridLayout(1, 0));
            MAIN_PANEL.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 10));
            MAIN_PANEL.setOpaque(false);

            update_colors();

            PROGRAMMABLE_PANEL.setVisible(false);
            PROGRAMMABLE_PANEL.setLayout(null);

            layeredPane.add_fullscreen(PROGRAMMABLE_PANEL, JLayeredPane.DEFAULT_LAYER);
            layeredPane.add_fullscreen(IMAGE_PANEL, JLayeredPane.POPUP_LAYER);

            MAIN_PANEL.add(layeredPane);
        }
        return MAIN_PANEL;
    }

    public static void update_colors() {
        IMAGE_PANEL.setBackground((Color) GraphicsSettings.active_theme().get_value("central_panel_background"));
        IMAGE_PANEL.set_icon((ImageIcon) GraphicsSettings.active_theme().get_value("central_panel_icon"));
    }

    public static JPanel get_programmable_panel() {
        return PROGRAMMABLE_PANEL;
    }
}

class ImagePanel extends JPanel {
    private ImageIcon image;

    public ImagePanel(ImageIcon image) {
        this.image = image;
        this.setBorder(null);
    }

    public void set_icon(ImageIcon image) {
        this.image = image;
        this.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        image.paintIcon(
                this,
                g,
                this.getWidth()/2 - image.getIconWidth()/2,
                this.getHeight()/2 - image.getIconHeight()/2
        );
    }
}