package gui.custom;

import files.FileInterface;
import files.Logger;
import gui.settingsFrame.*;
import gui.themes.GraphicsSettings;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MFrame extends JFrame {
    private final JMenuBar menu_bar = new JMenuBar();
    private final Map<String, MMenu> available_menu = new LinkedHashMap<>();
    private final JButton iconize = new JButton(),
            fullscreen = new JButton(),
            exit = new JButton();

    private final FileManagerPanel file_manager_panel = new FileManagerPanel();
    private final MollySettingsPanel settings_panel = new MollySettingsPanel();
    private final ModManagerPanel mod_settings_panel = new ModManagerPanel();

    public MFrame() {
        super("Molly server");

        Vector<BufferedImage> icons = new Vector<>();

        icons.add(load_icon("images/icon_16.png"));
        icons.add(load_icon("images/icon_32.png"));
        icons.add(load_icon("images/icon_64.png"));
        icons.add(load_icon("images/icon_128.png"));

        super.setIconImages(icons);

        //volendo un JFrame undecorated ma che può essere ridimensionato dal mouse imposta una decorazione e poi rimuove la grafica
        this.setUndecorated(true);
        this.getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
        this.getRootPane().setBorder(null);

        MLayeredPane layeredPane = new MLayeredPane();
        layeredPane.set_title("Molly server");
        this.setLayeredPane(layeredPane); //permette di aggiungere elementi in full screen e supporta la menu bar

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setMinimumSize(new Dimension(900, 500));

        menu_bar.addMouseListener(menu_bar_mouse_listener);
        menu_bar.setBorderPainted(false);
        menu_bar.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        update_colors();

        //inizializza tutti i componenti
        iconize.setOpaque(false);
        fullscreen.setOpaque(false);
        exit.setOpaque(false);
        iconize.setContentAreaFilled(false);
        fullscreen.setContentAreaFilled(false);
        exit.setContentAreaFilled(false);
        iconize.setBorder(null);
        fullscreen.setBorder(null);
        exit.setBorder(null);

        iconize.addActionListener(_ -> this.setState(JFrame.ICONIFIED));
        fullscreen.addActionListener(_ -> this.setExtendedState(this.getExtendedState() ^ JFrame.MAXIMIZED_BOTH));
        exit.addActionListener(_ -> System.exit(0));

        menu_bar.add(exit);
        menu_bar.add(fullscreen);
        menu_bar.add(iconize);

        menu_bar.add(Box.createHorizontalGlue());

        init_menu_bar();

        layeredPane.set_menu_bar(menu_bar);
    }

    private static BufferedImage load_icon(String icon_path) {
        try {
            return ImageIO.read((InputStream) Thread.currentThread().getContextClassLoader().getResourceAsStream(icon_path));
        }
        catch (Exception e) {
            Logger.log("impossibile caricare l'icona: (" + icon_path + ") per MFrame", true);
            Logger.log(e.getMessage(), true);

            return null;
        }
    }

    public void update_colors() {
        this.setBackground((Color) GraphicsSettings.active_theme().get_value("frame_background"));
        menu_bar.setBackground((Color) GraphicsSettings.active_theme().get_value("title_bar_background"));
        ((MLayeredPane) this.getLayeredPane()).update_colors();

        for (Component menu : menu_bar.getComponents()) {
            if (menu instanceof MMenu m) {
                m.update_colors();
            }
            else if (menu instanceof MMenuItem i) {
                i.update_colors();
            }
        }

        update_buttons_icons();
    }

    private void update_buttons_icons() {
        ButtonIcons max_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("title_bar_maximize");
        ButtonIcons min_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("title_bar_iconize");
        ButtonIcons close_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("title_bar_close");

        fullscreen.setIcon(max_icons.getStandardIcon());
        fullscreen.setRolloverIcon(max_icons.getRolloverIcon());
        fullscreen.setPressedIcon(max_icons.getPressedIcon());
        iconize.setIcon(min_icons.getStandardIcon());
        iconize.setRolloverIcon(min_icons.getRolloverIcon());
        iconize.setPressedIcon(min_icons.getPressedIcon());
        exit.setIcon(close_icons.getStandardIcon());
        exit.setRolloverIcon(close_icons.getRolloverIcon());
        exit.setPressedIcon(close_icons.getPressedIcon());
    }

    private void init_menu_bar() {
        add_menu("connection/server manager", () -> {});
        add_menu("connection/dns manager", () -> {});

        add_menu("mod/manager", () -> SettingsFrame.show(mod_settings_panel, "mod manager"));

        add_menu("file/manage files", () -> SettingsFrame.show(file_manager_panel, "file manager"));
        add_menu("file/reload all", FileInterface::load_from_disk);
        add_menu("file/update files", FileInterface::update_files);
        add_menu("file/settings", () -> SettingsFrame.show(settings_panel, "settings"));
        add_menu("file/exit", () -> System.exit(0));
    }

    private final MouseListener menu_bar_mouse_listener = new MouseListener() {
        private boolean follow = false;
        private boolean first_click = false;
        private boolean double_click = false;
        private Point click_point;

        private final ScheduledExecutorService scheduled_executor = Executors.newScheduledThreadPool(2);
        private ScheduledFuture<?> mouse_schedule;

        @Override
        public void mouseClicked(MouseEvent mouseEvent) {}
        @Override
        public void mouseEntered(MouseEvent mouseEvent) {}
        @Override
        public void mouseExited(MouseEvent mouseEvent) {}

        @Override
        public void mousePressed(MouseEvent mouseEvent) {
            //inizia a muovere il frame per seguire il mouse
            follow = true;
            click_point = mouseEvent.getPoint();

            mouse_schedule = scheduled_executor.scheduleAtFixedRate(follow_cursor,0, 10, TimeUnit.MILLISECONDS);

            //controlla per il doppio click
            if (first_click) {
                double_click = true;
            }
            else {
                first_click = true;
                scheduled_executor.schedule(check_double_click, 200, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void mouseReleased(MouseEvent mouseEvent) { //smette di seguire il mouse
            follow = false;
        }

        private final Runnable check_double_click = () -> {
            if (double_click) {
                setExtendedState(getExtendedState() ^ JFrame.MAXIMIZED_BOTH);
            }

            first_click = double_click = false; //resetta tutto
        };

        private final Runnable follow_cursor = () -> {
            if (follow) {
                Point mouse_position = MouseInfo.getPointerInfo().getLocation();

                setLocation(
                        mouse_position.x - click_point.x,
                        mouse_position.y - click_point.y
                );
            }
            else {
                mouse_schedule.cancel(true);
            }
        };
    };

    /**
     * Aggiunge un elemento al menu specificando la sua path, "<menu1>/<menu2>/.." e quale azione eseguire quando si preme
     * ritorna true se è riuscito ad aggiungere il nuovo menu, false altrimenti
     */
    public boolean add_menu(String menu_path, Runnable action) {
        int root_menu_len = menu_path.indexOf('/');
        if (root_menu_len == -1) { //vuole aggiungere un menu item direttamente alla menu bar
            MMenuItem new_item = new MMenuItem(menu_path);
            new_item.addActionListener((_) -> action.run());
            new_item.setBorder(BorderFactory.createEmptyBorder(4, 0, 0,0));

            menu_bar.add(new_item);
            return true;
        }
        else { //aggiunge un menu item in uno dei menu nella menu bar
            String root_menu_name = menu_path.substring(0, root_menu_len);
            String remaining_path = menu_path.substring(root_menu_len + 1);

            MMenu root_menu = available_menu.get(root_menu_name);
            if (root_menu == null) { //non è ancora stato aggiunto un menu con quel nome alla menu bar
                root_menu = new MMenu(root_menu_name);
                available_menu.put(root_menu_name, root_menu);
                root_menu.setBorder(BorderFactory.createEmptyBorder(4, 0, 0,0));

                menu_bar.add(root_menu);
            }

            return root_menu.add(remaining_path, action);
        }
    }
}