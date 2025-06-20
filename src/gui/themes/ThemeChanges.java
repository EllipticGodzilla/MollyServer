package gui.themes;

import files.Logger;
import files.Pair;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

//contiene informazioni rispetto a tutti i cambiamenti apportati a un tema grafico e che vanno ancora salvati nel file
public class ThemeChanges {
    /* azione eseguita su questo tema, può assumere tre valori:
     * 0) sono stati modificati dei valori nel tema
     * 1) è un tema nuovo appena creato, o è un tema che è stato resettato
     * 2) questo tema è stato eliminato
     */
    public final int ACTION;
    public static final int UPDATE = 0,
                            CREATE = 1,
                            DELETE = 2;

    /*
     * lista con le specifiche dei nuovi valori assegnati a ogni chiave modificata, se la stringa con il valore da assegnare
     * alla chiave vale null significa che va resettata al suo valore standard
     */
    public final Vector<Pair<String, String>> changes_vector = new Vector<>();

    public ThemeChanges(int action, String[] changed_keys, String[] changed_values) {
        this.ACTION = action;
        if (this.ACTION < UPDATE || this.ACTION > DELETE) { //non è stato inserito un valore valido per action
            Logger.log("è stato inserito un valore invalido come azione per una modifica a un tema: " + action, true);
            return;
        }

        if (changed_keys == null || changed_values == null) {
            return;
        }

        if (changed_keys.length != changed_values.length) {
            Logger.log("impossibile inizializzare un ThemeChanges con due array changed_keys e changed_values di lunghezze diverse", true);
            return;
        }

        for (int i = 0; i < changed_keys.length; i++) {
            changes_vector.add(new Pair<>(changed_keys[i], changed_values[i]));
        }
    }

    /*
     * ritorna un ThemeChanges contenente tutte le modifiche da apportare al tema equivalenti alla combinazione di quelle
     * contenute in after_changes applicate dopo le modifiche in questo oggetto
     */
    public ThemeChanges merge_with(ThemeChanges after_changes) {
        if (after_changes.ACTION == DELETE) { //se la seconda azione è di eliminare il tema, il risultato è la sua eliminazione
            return new ThemeChanges(DELETE, null, null);
        }

        //se è stato eliminato e poi sono state apportate delle modifiche, queste devono averlo creato nuovamente e quindi resettato
        if (this.ACTION == DELETE) {
            return after_changes;
        }

        /*
         * se è arrivato qui significa che in nessuno dei due cambiamenti questo tema è stato eliminato, quindi deve unire
         * i due vettori con le modifiche, dando priorità ai valori in after_changes
         */
        Map<String, String> joined_pairs = new LinkedHashMap<>();
        for (Pair<String, String> first_values : this.changes_vector) {
            joined_pairs.put(first_values.el1, first_values.el2);
        }
        for (Pair<String, String> final_values : after_changes.changes_vector) {
            joined_pairs.put(final_values.el1, final_values.el2);
        }

        String[] joined_keys = joined_pairs.keySet().toArray(new String[0]);
        String[] joined_values = joined_pairs.values().toArray(new String[0]);

        return new ThemeChanges((this.ACTION == CREATE)? CREATE : UPDATE, joined_keys, joined_values);
    }
}
