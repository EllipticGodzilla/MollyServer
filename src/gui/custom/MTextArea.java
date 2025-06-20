package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;

public class MTextArea extends JTextArea {
    private static final int MAX_WIDTH = 600;
    private static final int MAX_HEIGHT = 100;
    private static final int LINE_HEIGHT = 17;

    public MTextArea(String txt) {
        super(txt);

        this.setOpaque(false);
        this.setBorder(null);
        this.setFont(new Font("Charter Bd BT", Font.PLAIN, 13));
        this.setForeground((Color) GraphicsSettings.active_theme().get_value("text_color"));

        calculate_size();

        this.setEditable(false);
    }

    private void calculate_size() {
        if(this.getPreferredSize().width > MAX_WIDTH) {
            this.setLineWrap(true);

            FontMetrics fm = this.getFontMetrics(this.getFont());
            this.setPreferredSize(new Dimension(
                    MAX_WIDTH,
                    (fm.stringWidth(this.getText()) / MAX_WIDTH + 1) * LINE_HEIGHT
            ));
        }
        else {
            this.setPreferredSize(new Dimension(
                    this.getPreferredSize().width,
                    Math.min(this.getPreferredSize().height, MAX_HEIGHT)
            ));
        }
    }
}