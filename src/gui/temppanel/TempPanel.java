package gui.temppanel;

import files.Pair;
import gui.MollyFrame;
import gui.custom.ButtonIcons;
import gui.custom.MComboBox;
import gui.custom.MPasswordField;
import gui.custom.MTextArea;
import gui.themes.GraphicsSettings;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Vector;

abstract public class TempPanel {
    private static final JButton OK_BUTTON = new JButton();
    private static final JButton ANNULLA_BUTTON = new JButton();
    private static final int MIN_WIDTH = 220; //ok_button.width + annulla_button.width + 30 (insects)
    private static final int MIN_HEIGHT = 40; //butto.height + 20 (insects)

    public static Vector<Component> input_array = new Vector<>();
    private static Vector<Object> threads_bridge; //vettore utilizzato per passare informazioni al thread in attesa della risposta a un pannello
    private static Object answer_notifier = null;
    private static Vector<Pair<TempPanel_info, Object>> queue = new Vector<>();

    private static JPanel temp_panel = null; //temp panel
    //fog panel utilizzato quando si vuole bloccare tutte le operazioni finché non si chiude il temp panel
    private static final JPanel fog_panel = new JPanel();

    private static final JPanel TXT_PANEL = new JPanel(); //pannello che contiene le txt area
    private static boolean visible = false;
    private static boolean accept_esc_or_enter = false; //se possono essere utilizzati i tasti esc o invio al posto di premere i bottoni annulla, ok

    public static JPanel init() {
        if (temp_panel == null) {
            //imposta layout, background, border dei JPanel
            temp_panel = new JPanel();
            temp_panel.setLayout(new GridBagLayout());
            TXT_PANEL.setLayout(new GridBagLayout());
            TXT_PANEL.setOpaque(false);
            TXT_PANEL.setBorder(null);

            //todo imposta il fog panel con uno sfondo semitrasparente e fai in modo che i pulsanti non siano attivi quando questo è visibile

            //imposta i colori e le icone dei pulsanti
            update_colors();
            GraphicsSettings.run_at_theme_change(TempPanel::update_colors);

            //inizializza i bottoni ok e annulla
            OK_BUTTON.addActionListener(OK_LISTENER);
            ANNULLA_BUTTON.addActionListener(annulla_listener);

            OK_BUTTON.setBorder(null);
            ANNULLA_BUTTON.setBorder(null);

            OK_BUTTON.setOpaque(false);
            ANNULLA_BUTTON.setOpaque(false);
            OK_BUTTON.setContentAreaFilled(false);
            ANNULLA_BUTTON.setContentAreaFilled(false);

            OK_BUTTON.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {}
                @Override
                public void keyReleased(KeyEvent e) {}

                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == 10 && accept_esc_or_enter) { //se viene premuto invio è come premere ok
                        OK_BUTTON.doClick();
                    }
                    else if (ANNULLA_BUTTON.isVisible() && e.getKeyCode() == 27 && accept_esc_or_enter) { //se viene premuto esc è come premere annulla
                        ANNULLA_BUTTON.doClick();
                    }
                }
            });

            //aggiunge gli elementi al tempPanel
            GridBagConstraints c = new GridBagConstraints();

            c.fill = GridBagConstraints.BOTH;
            c.insets = new Insets(10, 10, 0, 0);
            c.weighty = 1;
            c.weightx = 1;
            c.gridx = 0;
            c.gridy = 0;
            c.gridwidth = 2;
            temp_panel.add(TXT_PANEL, c);

            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.FIRST_LINE_START;
            c.insets = new Insets(0, 10, 10, 10);
            c.weighty = 0;
            c.gridy = 1;
            c.gridwidth = 1;
            temp_panel.add(ANNULLA_BUTTON, c);

            c.anchor = GridBagConstraints.FIRST_LINE_END;
            c.insets.left = 0;
            c.gridx = 1;
            temp_panel.add(OK_BUTTON, c);
        }

        return temp_panel;
    }

    /**
     * Quando si mostra un messaggio con TempPanel è possibile richiedere che tutte le operazioni sul main frame siano
     * interrotte, questo pannello viene reso visibile al di sopra di esso bloccando tutte le interazioni.
     * @return JPanel utilizzato per bloccare le interazioni con il main frame
     */
    public static JPanel get_fog_panel() {
        return fog_panel;
    }

    public static void update_colors() {
        Color fog_background_noalpha = (Color) GraphicsSettings.active_theme().get_value("temp_panel_fog_background");
        Color fog_background = new Color( //aggiunge alpha
                fog_background_noalpha.getRed(),
                fog_background_noalpha.getGreen(),
                fog_background_noalpha.getBlue(),
                30
        );
        fog_panel.setBackground(fog_background);

        temp_panel.setBackground((Color) GraphicsSettings.active_theme().get_value("frame_background"));
        temp_panel.setBorder((Border) GraphicsSettings.active_theme().get_value(("temp_panel_border")));

        ButtonIcons ok_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("ok_icons");
        ButtonIcons annulla_icons = (ButtonIcons) GraphicsSettings.active_theme().get_value("annulla_icons");

        OK_BUTTON.setIcon(ok_icons.getStandardIcon());
        OK_BUTTON.setPressedIcon(ok_icons.getPressedIcon());
        OK_BUTTON.setRolloverIcon(ok_icons.getRolloverIcon());
        ANNULLA_BUTTON.setIcon(annulla_icons.getStandardIcon());
        ANNULLA_BUTTON.setPressedIcon(annulla_icons.getPressedIcon());
        ANNULLA_BUTTON.setRolloverIcon(annulla_icons.getRolloverIcon());

        temp_panel.repaint();
    }

    public static void recenter_in_frame() {
        Rectangle frame_bounds = MollyFrame.get_bounds();

        temp_panel.setLocation(
                (int) (frame_bounds.getWidth()) / 2 - temp_panel.getWidth() / 2,
                (int) (frame_bounds.getHeight()) / 2 - temp_panel.getHeight() / 2
        );
    }

    private static final ActionListener OK_LISTENER = _ -> {
        accept_esc_or_enter = false; //non permette più di utilizzare i tasti invio ed esc

        //copia tutti i testi contenuti nelle input area in questo array, utilizza Object perchè per le password non sono String ma char[]
        Vector<Object> input_vector = new Vector<>();

        for (Component comp : input_array) {
            if (comp instanceof JPasswordField) {
                input_vector.add(((JPasswordField) comp).getPassword());
            }
            else if (comp instanceof JTextField) {
                input_vector.add(((JTextField) comp).getText());
            }
            else if (comp instanceof JComboBox) {
                input_vector.add(((JComboBox<?>) comp).getSelectedItem());
            }
        }
        input_array.clear();

        Object answer_notifier = TempPanel.answer_notifier; //memorizza l'azione da eseguire per questo panel
        reset(); //resetta tutta la grafica e fa partire il prossimo in coda

        //c'è un thread in attesa della risposta
        if (answer_notifier instanceof Thread thread) {
            threads_bridge = input_vector;

            //notifica il thread in attesa
            synchronized (thread) {
                thread.notify();
            }
        }
        else if (answer_notifier instanceof TempPanel_action action) { //fa partire una TempPanel_action alla risposta
            action.input = input_vector;
            new Thread(action::success).start();
        }
    };

    private static final ActionListener annulla_listener = _ -> {
        input_array.removeAllElements(); //rimuove tutti gli input precedenti

        Object answer_notifier = TempPanel.answer_notifier;
        reset(); //resetta tutta la grafica e fa partire il prossimo in coda

        if (answer_notifier instanceof Thread thread) { //thread in attesa della risposta
            threads_bridge = null;

            synchronized (thread) {
                thread.notify();
            }
        }
        else if (answer_notifier instanceof TempPanel_action action) { //TempPanel_action da eseguire
            action.input = new Vector<>();
            new Thread(action::annulla).start();
        }
    };

    /*
     * mostra un temp panel con i dati specificati in info e una volta che l'utente avrà risposto invierà una notifica
     * secondo il modo specificato da answer_notifier, può essere uno dei tre seguenti oggetti:
     * null - non viene notificato nessuno, può essere utilizzato per mostrare notifiche
     * TempPanel_action - verranno chiamati i metodi success() se viene premuto "ok", annulla() se "annulla"
     * Thread - è l'unico oggetto con cui il vettore ritornato ha un senso, in questo caso verrà attesa una risposta
     *          dall'utente e ritornato un oggetto null nel caso sia stato premuto annulla, altrimenti il solito vettore
     *          con tutti gli inputs
     */
    public static Vector<Object> show(TempPanel_info info, Object answer_notifier) {
        if (!visible && temp_panel != null) {
            //imposta questa azione come quella da eseguire una volta chiusa la finestra
            TempPanel.answer_notifier = answer_notifier;

            //imposta la visibilità del bottone annulla
            ANNULLA_BUTTON.setVisible(info.annulla_vis());

            //distingue nei vari tipi di finestra
            int panel_type = info.get_type();
            if (panel_type == TempPanel_info.INPUT_MSG) { //se richiede degli input
                request_input(info.get_txts(), info.request_psw(), info.get_requests_info(), info);
            } else if (panel_type == TempPanel_info.SINGLE_MSG) { //se mostra un singolo messaggio
                show_msg(info.get_txts());
            } else if (panel_type == TempPanel_info.DOUBLE_COL_MSG) {
                show_dual_col_msg(info.get_txts());
            }

            //attende 0.2s e poi permette l'utilizzo dei tasti esc e invio
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException _) {
                }

                accept_esc_or_enter = true;
            }).start();
        }
        else {
            queue.add(new Pair<>(info, answer_notifier)); //aggiunge questa richiesta alla coda
        }

        //se questo thread deve attendere la risposta
        if (answer_notifier instanceof Thread thread) {
            try {
                synchronized (thread) {
                    thread.wait();
                }
            }
            catch (InterruptedException _) {
                return null;
            }

            Vector<Object> answer = threads_bridge;
            threads_bridge = null;

            return answer;
        }

        return null; //se non è un thread che deve aspettare la risposta
    }

    private static void show_msg(String[] txts) { //mostra un messaggio
        //aggiunge tutte le linee a txt_panel
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 10, 10);

        for (int i = 0; i < txts.length; i++) {
            MTextArea line_area = new MTextArea(txts[i]);

            c.insets.bottom = (i == txts.length - 1)? 10 : 0;
            TXT_PANEL.add(line_area, c);

            c.gridy ++;
        }

        show_panel(TXT_PANEL.getPreferredSize().width + 20, TXT_PANEL.getPreferredSize().height + 10); //rende visibile il pannello
        OK_BUTTON.requestFocus(); //richiede il focus, in modo che se premuto invio appena il popup compare equivale a premere "ok"
    }

    private static void show_dual_col_msg(String[] txts) {
        //aggiunge tutte le linee a txt_panel
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 10, 10);

        for (int i = 0; i < txts.length; i++) {
            MTextArea line_area1 = new MTextArea(txts[i]);
            MTextArea line_area2 = new MTextArea(txts[++i]);

            c.insets.bottom = (i == txts.length - 1)? 10 : 0;
            c.gridx = 0;
            TXT_PANEL.add(line_area1, c);

            c.gridx = 1;
            TXT_PANEL.add(line_area2, c);

            c.gridy ++;
        }

        show_panel(TXT_PANEL.getPreferredSize().width + 20, TXT_PANEL.getPreferredSize().height + 10); //rende visibile il pannello
        OK_BUTTON.requestFocus(); //richiede il focus, in modo che se premuto invio appena il popup compare equivale a premere "ok"
    }

    private static void request_input(String[] requests, boolean request_psw, int[] req_types, TempPanel_info info) {
        int max_width = 0; //contiene la lunghezza della JTextArea che contiene il messaggio più lungo

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 10, 10);

        //genera e aggiunge al pannello txt_panel tutti le JTextArea
        for (c.gridy = 0; c.gridy < requests.length; c.gridy++) {
            //genera il JTextField che mostra il messaggio per richiedere l'input e lo aggiunge al pannello
            MTextArea msg_area = new MTextArea(requests[c.gridy]);
            if (msg_area.getPreferredSize().width > max_width) {
                max_width = msg_area.getPreferredSize().width;
            }

            c.weightx = 1;
            c.gridx = 0;
            TXT_PANEL.add(msg_area, c);

            Component input_comp = null;
            //aggiunge il TextField dove poter inserire l'input richiesto
            if (req_types[c.gridy] == TempPanel_info.NORMAL_REQUEST) { //se è una richiesta con JTextFiled normale
                JTextField input_field = new EditTextField(c.gridy);
                input_comp = input_field;

                //aggiunge al pannello input_field
                c.weightx = 0;
                c.gridx = 1;
                c.gridwidth = (request_psw)? 2 : 1; //se richiede delle password i campi di inserimento normali si estendono anche nella colonna del pulsante per mostrare il testo delle password
                TXT_PANEL.add(input_field, c);

                c.gridwidth = 1; //resetta gridwidth
            }
            else if (req_types[c.gridy] == TempPanel_info.PASSWORD_REQUEST) { //richiede una password
                MPasswordField input_field = new MPasswordField();
                input_field.addKeyListener(new Enter_listener(c.gridy));
                input_comp = input_field;

                //aggiunge al pannello PasswordField ed il pulsante per togglare la visibilità della scritta
                c.weightx = 0;
                c.gridx = 1;
                c.insets.right = 3;
                TXT_PANEL.add(input_field, c);

                c.gridx = 2;
                c.insets.right = 10;
                TXT_PANEL.add(input_field.get_toggle_button(), c);
            }
            else if (req_types[c.gridy] == TempPanel_info.COMBO_BOX_REQUEST) { //richiede una combo box
                JComboBox<String> combo_box = new MComboBox(info.get_cbox_info(c.gridy));
                combo_box.addKeyListener(new Enter_listener(c.gridy));

                input_comp = combo_box;

                //aggiunge il combo box al pannello
                c.weightx = 0;
                c.gridx = 1;
                c.gridwidth = (request_psw)? 2 : 1; //se richiede delle password ci si deve espandere nella colonna del pulsante per mostrare il testo delle password
                TXT_PANEL.add(combo_box, c);

                c.gridwidth = 1; //resetta gridwidth
            }

            input_array.add(input_comp); //aggiunge gli input_field in un vettore per poi ricavarne i testi inseriti
        }

        show_panel(max_width + EditTextField.WIDTH + 30, (EditTextField.HEIGHT + 10) * input_array.size());
        input_array.elementAt(0).requestFocusInWindow(); //richiede il focus nella prima input area
    }

    private static void show_panel(int comp_width, int comp_height) {
        //calcola le dimensioni
        temp_panel.setSize(
                Math.max(comp_width, MIN_WIDTH),
                comp_height + MIN_HEIGHT
        );

        recenter_in_frame();

        //mostra il pannello
        temp_panel.setVisible(true);
        temp_panel.updateUI();
        visible = true;

        //controlla che il GFrame sia visibile
        MollyFrame.request_focus();
    }

    private static void reset() {
        //resetta il pannello e lo rende invisibile
        visible = false;
        ANNULLA_BUTTON.setVisible(true);
        temp_panel.setVisible(false);
        TXT_PANEL.removeAll();

        if (!queue.isEmpty()) {
            run_next_in_queue();
        }
    }

    private static void run_next_in_queue() {
        //memorizza il primo elemento nella coda e lo elimina da queue
        Pair<TempPanel_info, Object> next_info = queue.firstElement();
        queue.removeFirst();

        if (next_info.second() instanceof Thread thread) { //c'è già un thread che sta attendendo la risposta a questo pannello
            show(next_info.first(), null); //mostra il pannello senza specificare nessuna azione, altrimenti interrompe anche questo thread
            answer_notifier = thread;
        }
        else { //ci sarà da far partire un TempPanel_action o non è stato specificato nessun codice
            show(next_info.first(), next_info.second());
        }
    }

    private static class EditTextField extends JTextField {
        protected static final int WIDTH  = 150;
        protected static final int HEIGHT = 20;

        public EditTextField(int index) {
            super();

            this.setBackground((Color) GraphicsSettings.active_theme().get_value("input_background"));
            this.setBorder((Border) GraphicsSettings.active_theme().get_value("input_border"));
            this.setFont(new Font("Arial", Font.BOLD, 14));
            this.setForeground((Color) GraphicsSettings.active_theme().get_value("input_text_color"));

            this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
            this.setMinimumSize(this.getPreferredSize());

            this.addKeyListener(new Enter_listener(index));
        }
    }

    private static class Enter_listener implements KeyListener {
        private final int INDEX;

        public Enter_listener(int index) {
            this.INDEX = index;
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == 10) { //10 -> enter
                try {
                    Component input_cmp = input_array.elementAt(INDEX + 1);
                    if (input_cmp instanceof JTextField) { //passa il focus all'input successivo
                        ((JTextField) input_cmp).grabFocus();
                    }
                    else if (input_cmp instanceof JComboBox<?>) {
                        ((JComboBox<?>) input_cmp).grabFocus();
                    }
                }
                catch (Exception ex) { //se non esiste un input con index > di questo
                    if (accept_esc_or_enter) {
                        OK_BUTTON.doClick(); //simula il tasto "ok"
                    }
                }
            }
            else if (e.getKeyCode() == 27) { //27 -> esc
                if (ANNULLA_BUTTON.isVisible() && accept_esc_or_enter) {
                    ANNULLA_BUTTON.doClick();
                }
            }
        }
        @Override
        public void keyTyped(KeyEvent e) {}

        @Override
        public void keyPressed(KeyEvent e) {}
    }
}