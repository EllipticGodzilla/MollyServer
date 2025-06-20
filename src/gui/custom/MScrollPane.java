package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

public class MScrollPane extends JScrollPane {
    public MScrollPane(Component c) {
        super(c);

        this.getViewport().setOpaque(false);
        this.setAutoscrolls(true);
        this.setBorder(null);
    }

    public void update_colors() {
        ((GScrollBarUI) this.getHorizontalScrollBar().getUI()).update_colors();
        ((GScrollBarUI) this.getVerticalScrollBar().getUI()).update_colors();

        Color rail_color = (Color) GraphicsSettings.active_theme().get_value("scroll_bar_rail_color");
        this.getHorizontalScrollBar().setBackground(rail_color);
        this.getVerticalScrollBar().setBackground(rail_color);
        this.getHorizontalScrollBar().setBorder(BorderFactory.createLineBorder(rail_color.darker()));
        this.getVerticalScrollBar().setBorder(BorderFactory.createLineBorder(rail_color.darker()));

        this.getHorizontalScrollBar().repaint();
    }

    public void set_scrollbar_thickness(int thickness) {
        this.getVerticalScrollBar().setPreferredSize(new Dimension(
                thickness,
                this.getVerticalScrollBar().getPreferredSize().height
        ));
        this.getHorizontalScrollBar().setPreferredSize(new Dimension(
                this.getHorizontalScrollBar().getPreferredSize().width,
                thickness
        ));
    }

    @Override
    public JScrollBar createVerticalScrollBar() {
        JScrollBar scrollBar = super.createVerticalScrollBar();

        scrollBar.setUI(new GScrollBarUI());

        Color rail_color = (Color) GraphicsSettings.active_theme().get_value("scroll_bar_rail_color");
        scrollBar.setBackground(rail_color);
        scrollBar.setBorder(BorderFactory.createLineBorder(rail_color.darker()));

        return scrollBar;
    }

    @Override
    public JScrollBar createHorizontalScrollBar() {
        JScrollBar scrollBar = super.createHorizontalScrollBar();

        scrollBar.setUI(new GScrollBarUI());

        scrollBar.setBackground(new Color(128, 131, 133));
        scrollBar.setBorder(BorderFactory.createLineBorder(new Color(72, 74, 75)));

        return scrollBar;
    }

    private static class GScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            update_colors();
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            super.paintThumb(g, c, thumbBounds);
        }

        public void update_colors() {
            this.thumbColor = (Color) GraphicsSettings.active_theme().get_value("scroll_bar_thumb_color");
            this.thumbDarkShadowColor = (Color) GraphicsSettings.active_theme().get_value("scroll_bar_thumb_darkshadow_color");
            this.thumbHighlightColor = (Color) GraphicsSettings.active_theme().get_value("scroll_bar_thumb_highlight_color");
        }

        static class null_button extends JButton {
            public null_button() {
                super();
                this.setPreferredSize(new Dimension(0, 0));
            }
        }

        @Override
        protected JButton createDecreaseButton(int orientation) { return new null_button(); }
        @Override
        protected JButton createIncreaseButton(int orientation) { return new null_button(); }
    }
}