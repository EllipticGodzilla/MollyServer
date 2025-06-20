package gui.themes.standard_builders;

import files.Logger;
import gui.custom.MFileChooser;
import gui.themes.GraphicsSettings;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//todo che cazz è sta roba, da rifare tutto
public class ImageBuilder implements GraphicsOptionBuilder<ImageIcon> {
    private static final Pattern IMAGE_PATTERN = Pattern.compile("image\\(([a-zA-Z0-9/._-]+)\\)");
    private static final Map<String, String> ICONS_FILE_PATH = new LinkedHashMap<>();

    public static void init() { //inizializa tutte le posizioni delle immagini in resource/images
        if (ICONS_FILE_PATH.isEmpty()) {
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/add_server.png").getPath(), "resource./images/add_server.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/add_server_sel.png").getPath(), "resource./images/add_server_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/add_server_pres.png").getPath(), "resource./images/add_server_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/add_server_dis.png").getPath(), "resource./images/add_server_dis.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/apply.png").getPath(), "resource./images/apply.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/apply_sel.png").getPath(), "resource./images/apply_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/apply_pres.png").getPath(), "resource./images/apply_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/cancel.png").getPath(), "resource./images/cancel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/cancel_sel.png").getPath(), "resource./images/cancel_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/cancel_pres.png").getPath(), "resource./images/cancel_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/checkbox_sel.png").getPath(), "resource./images/checkbox_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/checkbox_dis.png").getPath(), "resource./images/checkbox_dis.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/exit.png").getPath(), "resource./images/exit.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/exit_pres.png").getPath(), "resource./images/exit_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/eye.png").getPath(), "resource./images/eye.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/eye_sel.png").getPath(), "resource./images/eye_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/eye_pres.png").getPath(), "resource./images/eye_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/files.png").getPath(), "resource./images/files.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/files_sel.png").getPath(), "resource./images/files_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/files_pres.png").getPath(), "resource./images/files_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/fullScreen.png").getPath(), "resource./images/fullScreen.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/fullScreen_pres.png").getPath(), "resource./images/fullScreen_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/molly.png").getPath(), "resource./images/molly.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/hide.png").getPath(), "resource./images/hide.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/hide_pres.png").getPath(), "resource./images/hide_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/icon_16.png").getPath(), "resource./images/icon_16.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/icon_32.png").getPath(), "resource./images/icon_32.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/icon_64.png").getPath(), "resource./images/icon_64.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/icon_128.png").getPath(), "resource./images/icon_128.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/invalid.png").getPath(), "resource./images/invalid.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/left_arrow.png").getPath(), "resource./images/left_arrow.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/left_arrow_sel.png").getPath(), "resource./images/left_arrow_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/left_arrow_pres.png").getPath(), "resource./images/left_arrow_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/menu_closed.png").getPath(), "resource./images/menu_closed.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/menu_open.png").getPath(), "resource./images/menu_open.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/minus.png").getPath(), "resource./images/minus.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/minus_sel.png").getPath(), "resource./images/minus_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/minus_pres.png").getPath(), "resource./images/minus_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/no_eye.png").getPath(), "resource./images/no_eye.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/no_eye_sel.png").getPath(), "resource./images/no_eye_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/no_eye_pres.png").getPath(), "resource./images/no_eye_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/ok.png").getPath(), "resource./images/ok.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/ok_sel.png").getPath(), "resource./images/ok_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/ok_pres.png").getPath(), "resource./images/ok_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/plus.png").getPath(), "resource./images/plus.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/plus_sel.png").getPath(), "resource./images/plus_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/plus_pres.png").getPath(), "resource./images/plus_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/power_off.png").getPath(), "resource./images/power_off.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/power_off_sel.png").getPath(), "resource./images/power_off_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/power_off_pres.png").getPath(), "resource./images/power_off_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/power_off_dis.png").getPath(), "resource./images/power_off_dis.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/power_on.png").getPath(), "resource./images/power_on.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/power_on_sel.png").getPath(), "resource./images/power_on_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/power_on_pres.png").getPath(), "resource./images/power_on_pres.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/power_on_dis.png").getPath(), "resource./images/power_on_dis.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/right_arrow.png").getPath(), "resource./images/right_arrow.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/right_arrow_sel.png").getPath(), "resource./images/right_arrow_sel.png");
            ICONS_FILE_PATH.put(ImageBuilder.class.getResource("/images/right_arrow_pres.png").getPath(), "resource./images/right_arrow_pres.png");
        }
    }

    @Override
    public boolean equals(Object obj1, Object obj2) {
        ImageIcon img1 = (ImageIcon) obj1;
        ImageIcon img2 = (ImageIcon) obj2;

        return img1.getDescription().equals(img2.getDescription());
    }

    @Override
    public ImageIcon cast(String value_str) {
        Matcher matcher = IMAGE_PATTERN.matcher(value_str);

        if (matcher.matches()) {
            ImageIcon image = null;

            try {
                URL url = get_resource(matcher.group(1));

                if (url != null) {
                    image = new ImageIcon(ImageIO.read(new File(url.getPath())));
                    image.setDescription(url.getPath());
                    ICONS_FILE_PATH.put(image.getDescription(), matcher.group(1));
                }
            }
            catch (Exception _) {
                Logger.log("impossibile inizializzare l'immagine dalle info: " + value_str);
                return null;
            }

            return image;
        }

        Logger.log("impossibile comprendere la formattazione dell'immagine: " + value_str);
        return null;
    }

    @Override
    public String revert_cast(Object value) {
        ImageIcon icon = (ImageIcon) value;
        String path = ICONS_FILE_PATH.get(icon.getDescription());

        if (path == null) {
            path = icon.getDescription();
        }

        return "image(" + path + ")";
    }

    @Override
    public void display(JPanel panel, Object value) {
        ImageIcon image = (ImageIcon) value;
        String url = image.getDescription();

        JLabel url_txt = new JLabel(url);
        JButton chooser = new MFileChooser(url_txt, "png or jpg image files", "jpg", "png");

        url_txt.setForeground((Color) GraphicsSettings.active_theme().get_value("text_color"));

        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        panel.add(chooser, c);

        c.gridx = 1;
        c.weightx = 1;
        panel.add(url_txt, c);
    }

    @Override
    public void update(JPanel panel, Object value) {
        ImageIcon image = (ImageIcon) value;
        String url = image.getDescription();

        ((JLabel) panel.getComponent(1)).setText(url);
    }

    @Override
    public ImageIcon new_value(JPanel panel) {
        JLabel url_field = (JLabel) panel.getComponent(1);
        String image_url = url_field.getText();
        ImageIcon icon;

        try {
            icon = new ImageIcon(ImageIO.read(new File(image_url)));
            icon.setDescription(image_url);
        }
        catch (IOException _) { //errore nella lettura dell'immagine
            Logger.log("Image_builder, impossibile leggere l'immagine: " + url_field.getText() + " dal JPanel", true);
            return null;
        }

        if (!ICONS_FILE_PATH.containsKey(icon.getDescription())) {
            ICONS_FILE_PATH.put(icon.getDescription(), url_field.getText());
        }

        return icon;
    }

    @Override
    public void update_colors(JPanel panel) {
        panel.getComponent(1).setForeground((Color) GraphicsSettings.active_theme().get_value("text_color"));
    }

    private static URL get_resource(String res_info) {
        Pattern res_pattern = Pattern.compile("(resource\\.)?([a-zA-Z0-9/._-]+)");
        Matcher matcher = res_pattern.matcher(res_info);

        try {
            if (matcher.matches()) {
                if (matcher.group(1) != null) {
                    return GraphicsSettings.class.getResource(matcher.group(2));
                } else {
                    return new File(matcher.group(2)).toURI().toURL();
                }
            }
        }
        catch (Exception _) {
            Logger.log("impossibile inizializzare la risorsa dalle info: " + res_info);
            return null;
        }

        Logger.log("impossibile comprendere la formattazione delle informazioni per la risorsa: " + res_info);
        return null;
    }
}
