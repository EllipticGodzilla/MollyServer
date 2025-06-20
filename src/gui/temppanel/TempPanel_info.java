package gui.temppanel;

import java.util.LinkedHashMap;

public class TempPanel_info {
    public static final int INPUT_MSG = 0,
                            SINGLE_MSG = 1,
                            DOUBLE_COL_MSG = 2,
                            NORMAL_REQUEST = 0,
                            PASSWORD_REQUEST = 1,
                            COMBO_BOX_REQUEST = 2;
    private final int TYPE;

    private final boolean SHOW_ANNULLA; //imposta la visibilità del pulsante "annulla"
    private boolean request_psw = false; //false = non richiede nessuna password, true richiede delle password
    private final String[] MSG_TEXT; //contiene tutti i messaggi da mostrare nel temp_panel
    private final int[] REQ_TYPE; //per ogni richiesta memorizza 0 = richiesta normale, 1 = password, 2 = JComboBox

    private final LinkedHashMap<Integer, String[]> CBOX_INFO = new LinkedHashMap<>();

    public TempPanel_info(int type, boolean show_annulla, String... txts) {
        this.TYPE = type;
        this.MSG_TEXT = txts;
        this.SHOW_ANNULLA = show_annulla;

        REQ_TYPE = new int[txts.length];
    }

    public TempPanel_info set_psw_indices(int... psw_indices) { //specifica quali fra le richieste che ha inserito richiedono delle password
        request_psw = true;

        for (int index : psw_indices) {
            REQ_TYPE[index] = PASSWORD_REQUEST;
        }

        return this;
    }

    public TempPanel_info set_combo_box(int[] indices, String[]... cbox_list) {
        for (int i = 0; i < indices.length; i++) {
            REQ_TYPE[indices[i]] = COMBO_BOX_REQUEST;
            CBOX_INFO.put(indices[i], cbox_list[i]);
        }

        return this;
    }

    public String[] get_cbox_info(int index) {
        return CBOX_INFO.get(index);
    }

    public int[] get_requests_info() { return REQ_TYPE; }
    public boolean annulla_vis() { return SHOW_ANNULLA; }
    public int get_type() { return TYPE; }
    public boolean request_psw() { return request_psw; }
    public String[] get_txts() { return MSG_TEXT; }
}
