package gui.themes;

import files.FileInterface;
import files.Logger;
import files.Pair;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_info;
import gui.themes.standard_builders.ImageBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Gestisce i temi contenendo una lista con tutti quelli utilizzabili contenuti in file <jarpath>/database/graphics/.gps
 * e un oggetto GraphicsTheme con la specifica del tema attivo al momento, e può generare nuovi oggetti GraphicsTheme a partire
 * da nomi di file leggendo le specifiche al loro interno. Si occupa di aggiornare il contenuto dei files .gps
 */
public abstract class GraphicsSettings {
    //lista con tutti i nomi di temi disponibili
    private static final Vector<String> available_themes = new Vector<>();

    /*
     * quando si modifica un tema non vengono scritte subito le modifiche sul disco, prima è generato un ThemeChanges e
     * viene aggiunto a questa mappa legato al nome del tema a cui è riferito, una volta che si vuole aggiornare tutti
     * i contenuti dei file vengono scritti sul disco. Ogni ThemeChange quindi rappresenta le modifiche dei temi rispetto
     * alle specifiche nei files
     */
    private static final Map<String, ThemeChanges> theme_changes = new LinkedHashMap<>();

    //lista di tutti i metodi da chiamare quando viene cambiato tema
    private static final Vector<Runnable> at_theme_change = new Vector<>();

    //tema che è utilizzato in questo momento
    private static GraphicsTheme active_theme;

    /*
     * file_loader e updater vengono aggiunti solo una volta decifrati tutti i file, poiché altrimenti verrebbero caricati
     * tutti i temi due volte a ogni startup
     */
    public static void add_file_managers() {
        FileInterface.add_file_updater(GraphicsSettings::update_files);
        FileInterface.add_file_loader(GraphicsSettings::load_from_files);
    }

    /**
     * Cerca fra tutti i file disponibili quelli con nome database/graphics/.gps e aggiunge il loro nome fra i temi disponibili,
     * se il nome coincide con quello in database/graphics/default.dat genera un GraphicsTheme con quelle specifiche e lo
     * imposta con tema attivo
     */
    public static void load_from_files() {
        //se si stanno ricaricando tutti i temi dai file rimuove tutti quelli memorizzati
        if (!available_themes.isEmpty()) {
            available_themes.clear();
        }

        ImageBuilder.init();
        available_themes.add("standard theme");
        active_theme = null;

        //aggiorno la lista con tutti i temi disponibili
        Logger.log("inizio la ricerca di file contenenti temi grafici");

        byte[] active_theme_name_bytes = FileInterface.read_file("database/graphics/default.dat");
        if (active_theme_name_bytes == null) {
            Logger.log("impossibile leggere il contenuto del file database/graphics/default.dat, utilizzo il tema default", true);
            active_theme_name_bytes = "standard theme".getBytes();
        }
        String active_theme_name = new String(active_theme_name_bytes);

        Pattern graphics_file_pattern = Pattern.compile("database/graphics/([a-zA-Z0-9_-]+)\\.gps");
        for (String file : FileInterface.file_list()) {
            Matcher file_name_matcher = graphics_file_pattern.matcher(file);

            if (file_name_matcher.matches()) { //se è un file con un tema grafico
                String theme_name = file_name_matcher.group(1);
                theme_name = theme_name.replaceAll("_", " ");

                available_themes.add(theme_name);

                //viene generato un GraphicsTheme solo per il tema da impostare come attivo
                if (theme_name.equals(active_theme_name)) {
                    active_theme = theme_from_file(file, theme_name);

                    if (active_theme == null) {
                        Logger.log("impossibile generare un oggetto GraphicsTheme a partire dal file: " + file, true);
                    }
                }
            }
        }
        Logger.log("tutti i temi sono stati importati");

        //non ha trovato la specifica per il tema segnato come attivo in default.dat
        if (active_theme == null) {
            if (!active_theme_name.equals("standard theme")) {
                Logger.log("non è stato trovato il file contenente le specifiche per il tema: " + active_theme_name + ", utilizzo il tema standard", true);
                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "impossibile trovare le specifiche per il tema: " + active_theme_name + " utilizzo lo standard"
                ), null);
            }

            active_theme = new GraphicsTheme("standard theme");
        }
    }

    // aggiorna il contenuto di tutti i file contenenti specifiche per temi applicando le modifiche in theme_changes
    private static void update_files() {
        for (String theme_name : theme_changes.keySet()) {
            String file_name = "database/graphics/" + theme_name.replaceAll(" ", "_") + ".gps";

            ThemeChanges changes = theme_changes.get(theme_name);
            theme_changes.remove(theme_name);

            if (changes.ACTION == ThemeChanges.DELETE) {
                FileInterface.delete_file(file_name);
            }
            else if (changes.ACTION == ThemeChanges.CREATE) {
                if (FileInterface.exist(file_name)) {
                    FileInterface.overwrite_file(file_name, new byte[0]);
                }
                else {
                    FileInterface.create_file(file_name, false);
                }

                for (Pair<String, String> pair_value : changes.changes_vector) {
                    FileInterface.append_to(file_name, (pair_value.el1 + ":" + pair_value.el2 + "\n").getBytes());
                }
            }
            else {
                update_file_content(file_name, changes.changes_vector);
            }
        }

        //salva il theme utilizzato in questo momento come default
        FileInterface.overwrite_file("database/graphics/default.dat", active_theme.get_name().getBytes());
        Logger.log("aggiornati tutti i file contenenti temi grafici");
    }

    //aggiorna il contenuto di un file aggiungendo/modificando/eliminando valori ad alcune key
    private static void update_file_content(String file_name, Vector<Pair<String, String>> changes) {
        byte[] file_content_bytes = FileInterface.read_file(file_name);
        if (file_content_bytes == null) {
            Logger.log("impossibile aggiornare le specifiche del tema contenuto in: " + file_name + " errore nella lettura", true);
            return;
        }
        String[] file_lines = new String(file_content_bytes).split("\n");

        Map<String, String> new_values = change_file_values(changes, file_lines);

        String[] new_key_list = new_values.keySet().toArray(new String[0]);
        String[] new_value_list = new_values.values().toArray(new String[0]);

        FileInterface.overwrite_file(file_name, new byte[0]);
        for (int i = 0; i < new_key_list.length; i++) {
            FileInterface.append_to(file_name, (new_key_list[i] + ":" + new_value_list[i] + "\n").getBytes());
        }
    }

    /*
     * date tutte le modifiche da fare alle key di un tema e le linee contenute nel file in cui viene specificato, combina
     * le due trovando una mappa che lega tutte le chiavi modificate dal tema ai loro valori
     */
    private static Map<String, String> change_file_values(Vector<Pair<String, String>> changes, String[] file_lines) {
        Map<String, String> new_values = new LinkedHashMap<>();

        //inizializza la mappa new_values con tutti i valori scritti nel file
        for (String line : file_lines) {
            int key_len = line.indexOf(':');
            new_values.put(line.substring(0, key_len), line.substring(key_len + 1));
        }

        //completa la mappa new_values con tutti i valori in changes
        for (Pair<String, String> new_pair : changes) {
            if (new_pair.el2 == null) { //resetta il valore della key e viene rimossa dalla mappa
                new_values.remove(new_pair.el1);
            }
            else {
                new_values.put(new_pair.el1, new_pair.el2);
            }
        }

        return new_values;
    }

    //ritorna l'oggetto GraphicsTheme legato al tema con il nome specificato
    public static GraphicsTheme get_theme(String theme_name) {
        if (theme_name.equals("standard theme")) {
            return new GraphicsTheme("standard theme");
        }

        ThemeChanges changes = theme_changes.get(theme_name);

        //se deve resettare il tema non ha senso tentare di caricarlo da un file
        if (changes != null && changes.ACTION == ThemeChanges.CREATE) {
            return change_theme(new GraphicsTheme(theme_name), changes);
        }

        //genera il nome del file in cui dovrebbero essere contenute le specifiche di questo tema e crea il GraphicsTheme
        String file_name = "database/graphics/" + theme_name.replaceAll(" ", "_") + ".gps";
        return theme_from_file(file_name, theme_name);
    }

    //genera un oggetto GraphicsTheme con le specifiche trovate nel file file_name
    private static GraphicsTheme theme_from_file(String file_name, String theme_name) {
        byte[] file_content_bytes = FileInterface.read_file(file_name);
        if (file_content_bytes == null) {
            Logger.log("impossibile leggere le specifiche del tema contenute nel file: " + file_name, true);
            return null;
        }
        String file_content = new String(file_content_bytes);

        String[] lines = file_content.split("\n");

        GraphicsTheme theme = new GraphicsTheme(theme_name);
        for (int i = 0; i < lines.length; i++) {
            int key_len = lines[i].indexOf(':');

            //ogni riga deve avere la forma <key>:<value>, se non trova nessun ':' è impossibile interpretare la riga
            if (key_len == -1) {
                Logger.log("impossibile comprendere la linea (" + (i + 1) + "): " + lines[i] + " nel file contenente tema grafico: " + file_name, true);
            }
            else {
                String key = lines[i].substring(0, key_len);
                String value = lines[i].substring(key_len + 1);

                theme.set_value(key, value);
            }
        }

        ThemeChanges changes = theme_changes.get(theme_name);
        if (changes != null) {
            theme = change_theme(theme, changes);
        }

        return theme;
    }

    //applica i cambiamenti specificati in ThemeChanges al tema, ritorna null nel caso changes specifichi di eliminarlo
    public static GraphicsTheme change_theme(GraphicsTheme theme, ThemeChanges changes) {
        //il tema è stato rimosso ma non è stato ancora eliminato il file
        if (changes.ACTION == ThemeChanges.DELETE) {
            return null;
        }

        //il tema è appena stato creato, viene resettato rispetto a quello scritto sul file
        if (changes.ACTION == ThemeChanges.CREATE) {
            theme = new GraphicsTheme(theme.get_name());
        }
        else { //se deve modificare dei valori al tema, viene clonato per non modificarli al quello originale
            theme = theme.clone();
        }

        for (Pair<String, String> change_pair : changes.changes_vector) {
            if (change_pair.el2 == null) { //deve resettare il valore di questa chiave
                theme.reset_key(change_pair.el1);
            }
            else { //imposta un nuovo valore a questa chiave
                theme.set_value(change_pair.el1, change_pair.el2);
            }
        }

        return theme;
    }

    //registra delle modifiche a un tema
    public static void add_changes(String theme_name, ThemeChanges new_changes) {
        ThemeChanges changes = theme_changes.get(theme_name);

        //è la prima modifica che si fa a questo tema ed è stato creato
        if (changes == null) {
            theme_changes.put(theme_name, new_changes);
        }
        else  { //devono essere concatenate a delle vecchie modifiche
            theme_changes.put(theme_name, changes.merge_with(new_changes));
        }

        //se è stato creato un nuovo tema viene aggiunto a quelli disponibili
        if (!available_themes.contains(theme_name)) {
            available_themes.add(theme_name);
        }

        //se viene rimosso un tema lo toglie dalla lista con tutti quelli disponibili
        if (new_changes.ACTION == ThemeChanges.DELETE) {
            available_themes.remove(theme_name);
        }
    }

    public static String[] theme_list() {
        return available_themes.toArray(new String[0]);
    }

    //cambia tema e chiama tutti i Runnable in at_theme_change
    public static void set_active_theme(String theme_name) {
        active_theme = get_theme(theme_name);

        for (Runnable runnable : at_theme_change) {
            runnable.run();
        }

        Logger.log("impostato come tema attivo: " + theme_name);
    }

    //aggiunge un Runnable al vettore con tutti quelli da chiamare ogni volta cambiato tema
    public static void run_at_theme_change(Runnable runnable) {
        at_theme_change.add(runnable);
    }

    public static GraphicsTheme active_theme() {
        return active_theme;
    }
}