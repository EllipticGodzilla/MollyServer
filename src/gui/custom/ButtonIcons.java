package gui.custom;

import javax.swing.*;

public class ButtonIcons {
    private final ImageIcon STD_ICON;
    private final ImageIcon ROLLOVER_ICON;
    private final ImageIcon PRESSED_ICON;
    private final ImageIcon DISABLED_ICON;

    public ButtonIcons(ImageIcon icon, ImageIcon rollover, ImageIcon pressed, ImageIcon disabled) {
        this.STD_ICON = icon;
        this.ROLLOVER_ICON = rollover;
        this.PRESSED_ICON = pressed;
        this.DISABLED_ICON = disabled;
    }

    public ImageIcon getStandardIcon() {
        return STD_ICON;
    }

    public ImageIcon getRolloverIcon() {
        return ROLLOVER_ICON;
    }

    public ImageIcon getPressedIcon() {
        return PRESSED_ICON;
    }

    public ImageIcon getDisabledIcon() {
        return DISABLED_ICON;
    }
}