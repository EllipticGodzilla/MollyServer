package gui.themes.standard_builders;

import files.Logger;
import gui.custom.ButtonIcons;
import gui.custom.MFileChooser;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ButtonIconsBuilder implements GraphicsOptionBuilder<ButtonIcons> {
    private static final Pattern ICONS_PATTERN = Pattern.compile("icons\\(([^,]+),([^,]+),([^,]+),([^,]+)\\)");
    private static final ImageBuilder image_graphics = new ImageBuilder();

    @Override
    public boolean equals(Object obj1, Object obj2) {
        ButtonIcons icons1 = (ButtonIcons) obj1;
        ButtonIcons icons2 = (ButtonIcons) obj2;

        return  image_graphics.equals(icons1.getStandardIcon(), icons2.getStandardIcon()) &&
                image_graphics.equals(icons1.getRolloverIcon(), icons2.getRolloverIcon()) &&
                image_graphics.equals(icons1.getPressedIcon(), icons2.getPressedIcon()) &&
                image_graphics.equals(icons1.getDisabledIcon(), icons2.getDisabledIcon());
    }

    @Override
    public ButtonIcons cast(String value_str) {
        Matcher matcher = ICONS_PATTERN.matcher(value_str);

        if (matcher.matches()) {
            ImageIcon std_icon, roll_icon, press_icon, dis_icon;

            std_icon = image_graphics.cast(matcher.group(1));

            if (std_icon == null) {
                Logger.log("impossibile inizializzare l'icona standard dalle info: " + value_str, true);
                return null;
            }

            roll_icon = image_graphics.cast(matcher.group(2));

            if (roll_icon == null) {
                Logger.log("impossibile inizializzare l'icona rollover dalle info: " + value_str, true);
                return null;
            }

            press_icon = image_graphics.cast(matcher.group(3));

            if (press_icon == null) {
                Logger.log("impossibile inizializzare l'icona pressed dalle info: " + value_str, true);
                return null;
            }


            dis_icon = image_graphics.cast(matcher.group(4));

            if (dis_icon == null) {
                Logger.log("impossibile inizializzare l'icona disabled dalle info: " + value_str, true);
                return null;
            }

            return new ButtonIcons(std_icon, roll_icon, press_icon, dis_icon);
        }

        Logger.log("impossibile comprendere la formattazione delle icone: " + value_str);
        return null;
    }

    @Override
    public String revert_cast(Object value) {
        ButtonIcons icons = (ButtonIcons) value;

        return "icons(" +
                image_graphics.revert_cast( icons.getStandardIcon() ) + "," +
                image_graphics.revert_cast( icons.getRolloverIcon() ) + "," +
                image_graphics.revert_cast( icons.getPressedIcon() ) + "," +
                image_graphics.revert_cast( icons.getDisabledIcon() ) + ")";
    }

    @Override
    public void display(JPanel panel, Object value) {
        ButtonIcons icons = (ButtonIcons) value;

        String std_path = icons.getStandardIcon().getDescription();
        String rol_path = icons.getRolloverIcon().getDescription();
        String pres_path = icons.getPressedIcon().getDescription();
        String dis_path = icons.getDisabledIcon().getDescription();

        JLabel std_field = new JLabel(std_path);
        JLabel rol_field = new JLabel(rol_path);
        JLabel pres_field = new JLabel(pres_path);
        JLabel dis_field = new JLabel(dis_path);

        MFileChooser std_chooser = new MFileChooser(std_field, "png or jpg image files", "jpg", "png");
        MFileChooser rol_chooser = new MFileChooser(rol_field, "png or jpg image files","jpg", "png");
        MFileChooser pres_chooser = new MFileChooser(pres_field, "png or jpg image files","jpg", "png");
        MFileChooser dis_chooser = new MFileChooser(dis_field, "png or jpg image files","jpg", "png");

        Color foreground = (Color) GraphicsSettings.active_theme().get_value("text_color");
        std_field.setForeground(foreground);
        rol_field.setForeground(foreground);
        pres_field.setForeground(foreground);
        dis_field.setForeground(foreground);

        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 0;
        panel.add(std_chooser, c);

        c.gridy = 1;
        panel.add(rol_chooser, c);

        c.gridy = 2;
        panel.add(pres_chooser, c);

        c.gridy = 3;
        panel.add(dis_chooser, c);

        c.gridy = 0;
        c.gridx = 1;
        c.weightx = 1;
        panel.add(std_field, c);

        c.gridy = 1;
        panel.add(rol_field, c);

        c.gridy = 2;
        panel.add(pres_field, c);

        c.gridy = 3;
        panel.add(dis_field, c);
    }

    @Override
    public void update(JPanel panel, Object value) {
        ButtonIcons icons = (ButtonIcons) value;

        String std_path = icons.getStandardIcon().getDescription();
        String rol_path = icons.getRolloverIcon().getDescription();
        String pres_path = icons.getPressedIcon().getDescription();
        String dis_path = icons.getDisabledIcon().getDescription();

        ((JLabel) panel.getComponent(4)).setText(std_path);
        ((JLabel) panel.getComponent(5)).setText(rol_path);
        ((JLabel) panel.getComponent(6)).setText(pres_path);
        ((JLabel) panel.getComponent(7)).setText(dis_path);
    }

    @Override
    public ButtonIcons new_value(JPanel panel) {
        Component[] cps = panel.getComponents();

        String std_path = ((JLabel) cps[4]).getText();
        String rol_path = ((JLabel) cps[5]).getText();
        String pres_path = ((JLabel) cps[6]).getText();
        String dis_path = ((JLabel) cps[7]).getText();

        return new ButtonIcons(
                new ImageIcon(std_path),
                new ImageIcon(rol_path),
                new ImageIcon(pres_path),
                new ImageIcon(dis_path)
        );
    }

    @Override
    public void update_colors(JPanel panel) {
        Color text_color = (Color) GraphicsSettings.active_theme().get_value("text_color");

        panel.getComponent(4).setForeground(text_color);
        panel.getComponent(5).setForeground(text_color);
        panel.getComponent(6).setForeground(text_color);
        panel.getComponent(7).setForeground(text_color);
    }
}
