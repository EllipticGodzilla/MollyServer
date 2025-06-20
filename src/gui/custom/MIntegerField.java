package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class MIntegerField extends JTextField {
    private final int MAX, MIN;
    private Runnable on_validation = null;

    private String prev_value = "";

    public MIntegerField(int min, int max) {
        super();
        this.MAX = max;
        this.MIN = min;

        setPreferredSize(new Dimension(35, 20));

        update_color();

        addFocusListener(FOCUS_LISTENER);
        addKeyListener(KEY_LISTENER);
    }

    public void update_color() {
        setForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));
        setBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
        setBorder((Border) GraphicsSettings.active_theme().get_value("input_border"));
    }

    public void on_validation(Runnable action) {
        on_validation = action;
    }

    public void set_value(int value) {
        if (value < MIN)
            value = MIN;

        if (value > MAX)
            value = MAX;

        setText(Integer.toString(value));
    }

    public int get_value() {
        String value = validate(getText());
        return Integer.parseInt(value);
    }

    private String validate(String value) {
        StringBuilder validated_input = new StringBuilder();

        char[] input_chars = value.toCharArray();
        for (int i = 0; i < value.length(); i++) {
            if (i == 0 && input_chars[i] == '-') { //il primo carattere può essere un - oltre a numero
                validated_input.append("-");
            } else if ('0' <= input_chars[i] && input_chars[i] <= '9') {
                validated_input.append(input_chars[i]);
            }
        }

        String input_str = validated_input.toString();

        if (!input_str.replaceAll("-", "").isEmpty()) { //se c'è almeno un numero controlla sia MIN <= x <= MAX
            int input_num;
            try {
                input_num = Integer.parseInt(input_str);
            }
            catch (NumberFormatException _) {
                return "0";
            }

            if (input_num > 255)
                input_num = 255;

            if (input_num < -1)
                input_num = -1;

            return Integer.toString(input_num);
        }

        return "0";
    }

    private final FocusListener FOCUS_LISTENER = new FocusListener() {
        @Override
        public void focusGained(FocusEvent focusEvent) {
            prev_value = getText();
        }
        @Override
        public void focusLost(FocusEvent focusEvent) {
            String validated_str = validate(getText());
            setText(validated_str);

            if (on_validation != null)
                on_validation.run();
        }
    };

    private final KeyListener KEY_LISTENER = new KeyListener() {
        @Override
        public void keyTyped(KeyEvent keyEvent) {}
        @Override
        public void keyReleased(KeyEvent keyEvent) {}
        @Override
        public void keyPressed(KeyEvent keyEvent) {
            if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
                String validated_txt = validate(getText());
                setText(validated_txt);

                if (on_validation != null)
                    on_validation.run();

                transferFocus();
            }
            else if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE) {
                setText(prev_value);
                transferFocus();
            }
        }
    };
}
