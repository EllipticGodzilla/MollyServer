package files;

import network.ClientsInterface;
import network.LoginManager;
import network.ServerManager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interfaccia per tutti i files contenuti nella cartella in cui è contenuto il progetto, ogni file viene individuato
 * con la sua relative path partendo dalla cartella in cui viene messo il progetto.
 * Ogni file è impostabile come {@code encoded}, e il suo contenuto sarà cifrato, tenere conto che il contenuto di file
 * encoded sarà disponibile solo essere stati sbloccati dall'utente.
 * È possibile specificare metodi da eseguire per aggiornare il contenuto di file / ricaricare i dati dai files custom
 */
public abstract class FileInterface {
    /// Posizione assoluta del progetto sul disco
    public static final String jar_path;

    /// Distingue quando l'inizializzazione è finita, cioè sono stati sbloccati i file cifrati
    private static boolean initialized = false;

    /// Mappa fra la relative path di ogni files trovato e l'istanza {@code SecureFile} che lo rappresenta
    private static final Map<String, SecureFile> loaded_files = new LinkedHashMap<>();

    /**
     * Runnable da eseguire quando si vuole aggiornare i dati sui files, vengono chiamati anche a chiusura del
     * programma. Per registrare updaters vedi {@code add_file_updater()}
     */
    private static Runnable[] file_updaters = new Runnable[0];

    /**
     * Runnable da eseguire quando si vuole aggiornare il contenuto di tutti i files, vengono chiamati anche una volta
     * decodificati tutti i file cifrati. Per registrare loaders vedi {@code add_file_loader()}
     */
    private static Runnable[] file_loaders = new Runnable[0];

    /// Costanti utilizzate per segnalare errori da {@code is_encoded()}
    public static final int FILE_NOT_FOUND = -1,
                            FILE_CLEAR = 0,
                            FILE_ENCODED = 1;

    /*
     * Inizializza jar_path trovando la posizione assoluta del progetto sul disco.
     * Aggiunge tutti gli updaters dei files standard:
     * 1) credenziali dei files per il LoginManager attivo
     *
     * Controlla che i file necessari per il funzionamento base di Molly siano presenti
     */
    static {
        String tmp_jar_path = FileInterface.class.getProtectionDomain().getCodeSource().getLocation().getPath(); //calcola l'abs path del jar
        tmp_jar_path = tmp_jar_path.substring(0, tmp_jar_path.length() - 1); //rimuove l'ultimo /
        jar_path = tmp_jar_path.substring(0, tmp_jar_path.lastIndexOf('/')); //rimuove Molly.jar dalla fine della path

        add_file_updater(ClientsInterface::update_credential_file);
        add_file_loader(() -> {
            LoginManager manager = ServerManager.get_login_manager();
            if (manager != null) {
                ClientsInterface.load_credential_file(manager.get_name());
            }
        });

        Logger.init();
    }

    //      FILE UPDATER/LOADER MANAGING

    public static void load_from_disk() {
        Logger.log("inizio a caricare tutti i dati dai file");
        for (Runnable loader : file_loaders) {
            loader.run();
        }
        Logger.log("dati aggiornati");

        initialized = true;
    }

    public static void update_files() {
        /*
         * se il programma viene chiuso prima di inserire la password non vengono mai chiamati i file_loaders, quindi non
         * ha nessuna informazione da salvare e chiamare gli updaters eliminerebbe ogni dati nei files
         */
        if (!initialized) {
            return;
        }

        Logger.log("aggiorno tutti i dati contenuti nei file");
        for (Runnable updater : file_updaters) {
            updater.run();
        }
        Logger.log("file aggiornati");
    }

    public static void add_file_updater(Runnable updater) {
        file_updaters = Arrays.copyOf(file_updaters, file_updaters.length + 1);
        file_updaters[file_updaters.length - 1] = updater;
    }

    public static void add_file_loader(Runnable loader) {
        file_loaders = Arrays.copyOf(file_loaders, file_loaders.length + 1);
        file_loaders[file_loaders.length - 1] = loader;
    }

    //cerca tutti i file nella cartella jar_path e li aggiunge alla mappa loaded_files
    public static void search_files() {
        File[] files = new File(jar_path).listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                search_folder(file);
            }
            else if (!file.getName().equals("Molly.jar")) {
                add_file(file);
            }
        }

        check_essential_files();
    }

    /// Controlla ci siano tutti i files necessari per il funzionamento base di Molly, nel caso manchi qualcuno lo crea
    private static void check_essential_files() {
        new File(jar_path + "/database").mkdir();
        new File(jar_path + "/database/graphics").mkdir();
        new File(jar_path + "/database/users").mkdir();
        new File(jar_path + "/mods").mkdir();

        create_file("database/graphics/default.dat", false);
        create_file("database/users/default.dat", false);
        create_file("database/certificate.dat", false);
        create_file("database/TerminalLog.dat", false);
    }

    private static void search_folder(File folder) {
        if (folder.listFiles() != null) { //se questa cartella non è vuota
            for (File file : folder.listFiles()) {
                if (file.isDirectory()) {
                    search_folder(file);
                } else {
                    add_file(file);
                }
            }
        }
    }

    private static void add_file(File file) {
        String file_name = file.getAbsolutePath().replaceAll(jar_path + "/", "");
        loaded_files.put(file_name, new SecureFile(file.getPath()));
    }

    public static byte[] read_file(String file_name) {
        SecureFile file = loaded_files.get(file_name);

        if (file == null) {
            Logger.log("impossibile leggere il contenuto del file: " + file_name + ", file non esistente", true);
            return null;
        }
        else {
            return file.read();
        }
    }

    public static void append_to(String file_name, byte[] data) {
        SecureFile file = loaded_files.get(file_name);
        if (file == null) {
            Logger.log("impossibile aggiungere il testo: " + new String(data) + " al file: " + file_name + ", file non esistente", true);
        }
        else {
            file.append(data);
        }
    }

    public static void overwrite_file(String file_name, byte[] data) {
        SecureFile file = loaded_files.get(file_name);

        if (file == null) {
            Logger.log("impossibile scrivere nel file: " + file_name + " file non esistente", true);
            return;
        }

        file.replace(data);
    }

    /**
     * Crea un nuovo file accessibile da FileInterface specificando se cifrare il contenuto in esso o meno. Nel caso
     * in cui esista già un file con stesso nome e sicurezza questo comando verrà ignorato, se esiste già il file ma
     * il livello di sicurezza è diverso verrà modificato.
     * @param file_name relative path del file da creare, ex: {@code database/graphics/default.dat}
     * @param encoded {@code true} se il contenuto deve essere cifrato, {@code false} se viene lasciato in chiaro
     * @return {@code true} se ha creato o cambiato la sicurezza con successo del nuovo file, {@code false} se non è
     * possibile creare il nuovo file
     */
    public static boolean create_file(String file_name, boolean encoded) {
        if (loaded_files.containsKey(file_name) && (loaded_files.get(file_name).is_protected() == encoded)) { //se esiste già un file con questo nome e sicurezza
            return true;
        }
        else if (loaded_files.containsKey(file_name)) { //modifica la sicurezza del file già esistente
            loaded_files.get(file_name).set_encoded(encoded);
            return true;
        }

        try {
            SecureFile new_file = new SecureFile(jar_path + "/" + file_name, encoded);
            loaded_files.put(file_name, new_file);
        }
        catch (IOException e) {
            Logger.log("impossibile creare un nuovo file: (" + file_name + ") con sicurezza: (" + (encoded? "encoded" : "clear") + ")");
            Logger.log(e.getMessage(), false, '\n', false);

            return false;
        }

        return true;
    }

    public static void delete_file(String file_name) {
        if (!loaded_files.containsKey(file_name)) {
            Logger.log("impossibile eliminare il file: " + file_name + ", file non esistente", true);
            return;
        }

        loaded_files.get(file_name).delete();
        loaded_files.remove(file_name);
    }

    public static void set_encoded(String file_name, boolean encoded) {
        SecureFile file = loaded_files.get(file_name);

        if (file == null) {
            Logger.log("impossibile cambiare la sicurezza del file: " + file_name + ", file non esistente", true);
            return;
        }

        file.set_encoded(encoded);
    }

    public static boolean exist(String file_name) {
        return loaded_files.containsKey(file_name);
    }

    //ritorna -1 = file non esistente, 0 = file non cifrato, 1 = file cifrato
    public static int is_encoded(String file_name) {
        SecureFile file = loaded_files.get(file_name);

        if (file == null) {
            return FILE_NOT_FOUND;
        }
        else {
            return file.is_protected()? FILE_ENCODED : FILE_CLEAR;
        }
    }

    public static String[] file_list() {
        return loaded_files.keySet().toArray(new String[0]);
    }

    public static void close() {
        for (SecureFile file : loaded_files.values()) {
            file.close();
        }
    }
}