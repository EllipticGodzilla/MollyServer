package gui.custom;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionListener;

public class MFileChooser extends JButton {
    private JLabel output;
    private final JFrame CHOOSER_FRAME = new JFrame();
    private final JFileChooser CHOOSER_FILE = new JFileChooser();

    public MFileChooser(JLabel output, String description, String... extensions) {
        super();
        this.output = output;

        this.setBorder(null);

        this.setIcon(new ImageIcon(MFileChooser.class.getResource("/images/files.png")));
        this.setRolloverIcon(new ImageIcon(MFileChooser.class.getResource("/images/files_sel.png")));
        this.setPressedIcon(new ImageIcon(MFileChooser.class.getResource("/images/files_pres.png")));

        this.setOpaque(false);
        this.setContentAreaFilled(false);
        this.addActionListener(BUTTON_LISTENER);

        //init chooser
        CHOOSER_FILE.setFileSelectionMode(JFileChooser.FILES_ONLY);
        CHOOSER_FILE.setFileFilter(new FileNameExtensionFilter(description, extensions));
    }

    private final ActionListener BUTTON_LISTENER = _ -> {
        int status = CHOOSER_FILE.showOpenDialog(CHOOSER_FRAME);
        if (status == JFileChooser.APPROVE_OPTION) {
            output.setText(CHOOSER_FILE.getSelectedFile().getPath());
        }
    };
}
