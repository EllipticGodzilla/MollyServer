package gui.custom;

import gui.themes.GraphicsSettings;

import javax.swing.*;
import java.awt.*;
import java.util.Vector;

//permette di ridimensionare componenti in modo che abbiamo sempre la sua stessa dimensione
public class MLayeredPane extends JLayeredPane {
    Vector<Component> full_screen = new Vector<>();
    private JMenuBar menuBar = null;
    private JLabel title = new JLabel();
    private int menuBar_height = 0;

    public MLayeredPane() {
        super();
        this.setOpaque(false);

        title.setFont(Font.decode(null).deriveFont(Font.BOLD, 15));
        title.setForeground((Color) GraphicsSettings.active_theme().get_value("frame_title_color"));
        this.add(title, JLayeredPane.POPUP_LAYER);
    }

    /// Quando si cambia il theme modifica il colore del titolo
    public void update_colors() {
        title.setForeground((Color) GraphicsSettings.active_theme().get_value("frame_title_color"));
    }

    /**
     * Imposta una JMenuBar da visualizzare in cima al pannello, tutti i componenti full screen vengono spostati in giù
     * automaticamente per evitare clipping.
     * Una volta impostata non è possibile rimuoverla o impostare una nuova JMenuBar al suo posto.
     * Imposta l'altezza del titolo uguale a quella di questa JMenuBar in modo da centrare il testo verticalmente.
     * @param menuBar JMenuBar da visualizzare in questo pannello
     */
    public void set_menu_bar(JMenuBar menuBar) {
        //è possibile aggiungere una sola menu bar
        if (this.menuBar != null) {
            return;
        }

        this.add(menuBar, JLayeredPane.FRAME_CONTENT_LAYER);

        this.menuBar = menuBar;
        menuBar_height = menuBar.getPreferredSize().height;

        title.setSize(
                title.getSize().width,
                menuBar_height
        );
    }

    /**
     * Specifica il titolo di questo pannello, viene visualizzato con una JLabel centrata sulle x, e se è presente una
     * JMenuBar si imposta l'altezza di questa JLabel uguale a quella della JMenuBar in modo da centrare il testo anche
     * verticalmente all'interno della barra.
     * @param title testo da visualizzare come titolo
     */
    public void set_title(String title) {
        this.title.setText(title);
        this.title.setSize(
                this.title.getPreferredSize().width,
                (menuBar != null)? menuBar_height : this.title.getPreferredSize().height
        );
    }

    @Override
    public void setBounds(int x, int y, int width, int height) { //in questo modo si elimina il delay che si avrebbe utilizzando un component listener
        super.setBounds(x, y, width, height);

        if (menuBar != null) {
            menuBar.setSize(width, menuBar_height);
        }

        if (title != null) {
            title.setLocation(
                    this.getWidth()/2 - title.getWidth()/2,
                    0
            );
        }

        for (Component cmp : full_screen) {
            cmp.setBounds(0, menuBar_height, width, height - menuBar_height);
        }
    }

    /**
     * Aggiunge una componente come full screen, ogni volta che questo pannello verrà ridimensionato tutti i componenti
     * aggiunti da questo metodo verranno ridimensionati automaticamente
     * @param comp componente da aggiungere
     * @param index layer index del JLayerPane
     */
    public void add_fullscreen(Component comp, int index) {
        super.add(comp, index);
        full_screen.add(comp);
    }
}