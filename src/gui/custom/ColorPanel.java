package gui.custom;

import javax.swing.*;
import java.awt.*;

public class ColorPanel extends JPanel {
    private static final ImageIcon INVALID_COLOR_IMAGE = new ImageIcon(ColorPanel.class.getResource("/images/invalid.png"));
    private boolean invalid = true;

    public ColorPanel() {
        super();

        setBorder(null);
        setPreferredSize(new Dimension(20, 20));
    }

    public void set_color(int r, int g, int b) {
        if (r < 0 || g < 0 || b < 0) {
            invalid = true;
        }
        else {
            invalid = false;

            this.setBackground(new Color(r, g, b));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (invalid) {
            INVALID_COLOR_IMAGE.paintIcon(this, g, 0, 0);
        }
    }
}