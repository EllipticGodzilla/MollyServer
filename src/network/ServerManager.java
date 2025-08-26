package network;

import files.FileInterface;
import files.Logger;
import files.Pair;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_info;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Interfaccia per gestire l'attività del server, attivazione e spegnimento dei connectors, e alcuni dei suoi parametri.
 * Contiene le mappe e i metodi per gestire Connectors, Encoders e LoginManagers
 */
public class ServerManager {
    /**
     * Mappa fra il nome assegnato a ogni {@code Connector} registrato e una coppia composta dal suo wrapper, e un bool
     * che rappresenta il suo status a server attivo, cioè {@code true} se è un connector da attivare con l'accensione
     * del server, {@code false} se è un connector da ignorare
     */
    private static final Map<String, Pair<Connector, Boolean>> registered_connectors = new LinkedHashMap<>();

    /// Mappa fra il nome di ogni {@code Encoder} registrato e il constructor della sua classe
    private static final Map<String, Constructor<? extends Encoder>> registered_encoders = new LinkedHashMap<>();

    /// Istanza di un login manager utilizzata per gestire login / registrazioni di utenti nel server
    private static LoginManager active_login_manager;

    /// Mappa fra il nome di ogni login manager disponibile e la sua implementazione
    private static final Map<String, LoginManager> login_managers = new LinkedHashMap<>();

    /**
     * Memorizza la stato del server, dove {@code false} significa che è spento e nessun client si può collegare,
     * {@code true} rappresenta il server attivo e in attesa di nuove connessioni dai clients
     */
    private static boolean server_status = false;

    //      LOGIN_MANAGER CHANGES

    /**
     * Tenta di aggiungere un login manager nominato {@code name} fra quelli disponibili per gestire i login.
     * @param name    nome con cui ci si riferisce al login manager
     * @param manager implementazione del manager
     * @return {@code true} se è stato aggiunto, {@code false} se è già presente un manager con questo nome
     */
    public static boolean register_login_manager(String name, LoginManager manager) {
        if (login_managers.containsKey(name)) {
            return false;
        }

        login_managers.put(name, manager);
        return true;
    }

    /**
     * Prova a modificare il login manager attivo e caricare tutte le credenziali di clients registrati con esso, per
     * modificarlo il server deve essere spento.
     * <p>Se era già impostato un login manager salva le credenziali dei suoi utenti prima di cambiarlo
     * @param manager_name nome del nuovo manager da utilizzare
     * @return {@code true} se riesce a impostare il manager, {@code false} se il server è ancora attivo
     */
    public static boolean set_login_manager(String manager_name) {
        Logger.log("provo a modificare il login manager attivo in: (" + manager_name + ")");

        if (is_online()) {
            Logger.log("impossibile modificare il LoginManager quando il server è attivo", true);
            return false;
        }

        LoginManager manager = login_managers.get(manager_name);
        if (manager == null) {
            Logger.log("impossibile trovare un manager registrato con il nome: (" + manager_name + ")", true);
            return false;
        }

        if (active_login_manager != null) { //salva le credenziali del vecchio LoginManager
            ClientsInterface.update_credential_file();
        }

        active_login_manager = manager;
        Logger.log("impostato il login manager: (" + manager_name + ") come attivo");

        return ClientsInterface.load_credential_file(manager_name);
    }

    /**
     * Ritorna il login manager utilizzato in questo momento
     * @return login manager in utilizzo
     */
    public static LoginManager get_login_manager() {
        return active_login_manager;
    }

    /**
     * Ritorna una lista con tutti i nomi dei login managers registrati
     * @return nomi dei login managers disponibili
     */
    public static Set<String> get_available_login_managers() {
        return login_managers.keySet();
    }

    /**
     * Se il server è spento legge il nome del manager da utilizzare di default dal file
     * {@code database/users/default.dat} e lo imposta come login manager attivo, se un altro login manager era
     * impostato salverà prima le sue credenziali.
     * <p>Se il server è attivo non è possibile modificare il login manager e non farà nulla
     * @return {@code true} se il login manager è stato modificato con successo, altrimenti {@code false}
     */
    public static boolean use_default_login_manager() {
        Logger.log("cerco di impostare il login manager default come attivo");

        byte[] data = FileInterface.read_file("database/users/default.dat");
        if (data == null) {
            Logger.log("impossibile leggere il contenuto del file: database/users/default.dat", true);

            return false;
        }

        return set_login_manager(new String(data));
    }

    //      METODI IO PER LE MAPPE CONNECTOR E ENCODER

    /**
     * Aggiunge un nuovo {@code Connector} utilizzando i metodi specificati da una mod, viene registrato come connector
     * disattivato, cioè che a meno di interventi da parte dell'utente non verrà attivato all'accensione del server
     * @param connector istanza del connector da registrare
     * @param name nome del {@code Connector}
     */
    public static void register_connector(Connector connector, String name) {
        if (registered_connectors.containsKey(name)) {
            Logger.log("impossibile registrare più di un connector con il nome: (" + name + ")", true);
            return;
        }

        registered_connectors.put(name, new Pair<>(connector, false));
        Logger.log("registrato un nuovo connector: (" + name + ")");
    }

    /**
     * Ritorna una lista con tutti i nomi dei {@code connector} registrati
     * @return vettore con i nomi dei {@code Connector} registrati
     */
    public static String[] get_connectors_list() {
        return registered_connectors.keySet().toArray(new String[0]);
    }

    /**
     * Ritorna l'oggetto {@code ConnectorWrapper} rappresentando il connector con il nome specificato, o {@code null}
     * se non trova nessun {@code Connector} registrato con quel nome
     * @param name nome del connector
     * @return oggetto {@code ConnectorWrapper} rappresentante il connector richiesto
     */
    public static Connector get_connector(String name) {
        return registered_connectors.get(name).first();
    }

    /**
     * Ritorna {@code true} se è stato registrato un connector con il nome specificato, {@code false} se non trova
     * nessun connector legato a questo nome
     */
    public static boolean exist_connector(String connector_name) {
        return registered_connectors.containsKey(connector_name);
    }

    /**
     * Dalla definizione di una classe che estende {@code Encoder} trova il constructor e lo registra fra gli encoder
     * disponibili per le connessioni
     * @param encoder_class classe che specifica un encoder
     * @param encoder_name  nome dell encoder da aggiungere
     */
    public static void register_encoder(Class<? extends Encoder> encoder_class, String encoder_name) {
        if (registered_encoders.containsKey(encoder_name)) {
            Logger.log("impossibile registrare più di un Encoder con il nome: (" + encoder_name + ")", true);
            return;
        }

        try {
            registered_encoders.put(encoder_name, encoder_class.getConstructor());
        }
        catch (Exception e) {
            Logger.log("impossibile trovare il constructor per l'encoder: (" + encoder_name + ")", true);
            Logger.log(e.getMessage(), true);
        }
    }

    /**
     * Ritorna la lista con tutti i nomi dei {@code Encoder} registrati
     * @return vettore con i nomi dei {@code Encoder}
     */
    public static String[] get_encoders_list() {
        return registered_encoders.keySet().toArray(new String[0]);
    }

    /**
     * Costruisce una nuova istanza dell {@code Encoder} registrato con il nome specificato e la ritorna. In caso non
     * sia stato registrato nessun encoder con il nome specificato, o fallisce la creazione di una nuova istanza dell
     * encoder, ritorna {@code null}
     * @param encoder_name nome dell encoder da inizializzare
     * @return istanza di {@code Encoder} o {@code null} se l'operazione è fallita
     */
    public static Encoder get_encoder_instance(String encoder_name) {
        Constructor<? extends Encoder> constructor = registered_encoders.get(encoder_name);
        if (constructor == null) {
            Logger.log("impossibile trovare un encoder con il nome: (" + encoder_name + ")", true);
            return null;
        }

        try {
            return constructor.newInstance();
        }
        catch (Exception e) {
            Logger.log("impossibile creare una nuova istanza dell encoder: (" + encoder_name + ")", true);
            Logger.log(e.getMessage(), true);

            return null;
        }
    }

    /**
     * Ritorna {@code true} se è stato registrato un encoder con il nome specificato, {@code false} se non trova nessun
     * encoder legato a questo nome
     */
    public static boolean exist_encoder(String encoder_name) {
        return registered_encoders.containsKey(encoder_name);
    }

    /**
     * Lo stato di ogni connector è definito da due bool:
     * <ul>
     *     <li>
     *         {@code active:} se il connector è attivo vuol dire che verrà acceso e spento assieme al server, mentre
     *         i connector disattivi vengono ignorati anche all'accensione del server
     *     </li>
     *     <li>
     *         {@code power:} se il connector è acceso vuol dire che il server è acceso ed è in attesa di nuovi client,
     *         un connector spento non è in attesa di nuovi client ma chiaramente può essere attivo.
     *     </li>
     * </ul>
     * @param connector_name nome del connector di cui si vuole sapere lo status
     * @return una coppia di bool rappresentanti rispettivamente {@code active status}, {@code power status}
     */
    public static Pair<Boolean, Boolean> get_connector_status(String connector_name) {
        Pair<Connector, Boolean> data = registered_connectors.get(connector_name);
        return new Pair<>(
                data.second(),
                data.first().get_status()
        );
    }

    //      SERVER STATUS

    /// Ritorna {@code true} se il server è attivo, {@code false} se spento e non raggiungibile da nessun clients
    public static boolean is_online() {
        return server_status;
    }

    /**
     * Chiude tutti i connectors e scollega tutti i clients dal sever notificando ognuno con {@code EOC}, "End Of
     * Connection", imposta questo come spento permettendo la modifica di alcuni parametri come {@code LoginManager},
     * {@code WorkerThreads}
     */
    public static void power_off() {
        if (!server_status) { //il server è già spento
            Logger.log("tentativo di spegnere il server mentre questo è già spento", true);
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile spegnere il server, è già spento"
            ), null);

            return;
        }

        for (Pair<Connector, Boolean> pair : registered_connectors.values()) {
            if (pair.second()) { //spegne tutti i connector attivi e ignora quelli già spenti
                pair.first().stop();
                Logger.log("spento il connector: (" + pair.first().get_name() + ")");
            }
        }
        Logger.log("stoppati tutti i connectors attivi");

        ClientsInterface.disconnect_all();

        server_status = false;
        ClientsInterface.free_workers();
    }

    /**
     * Accende tutti i connector attivi fra quelli registrati permettendo a nuovi clients di collegarsi, infine
     * ripopola ClientsInterface con nuovi worker threads e imposta lo stato del server a {@code true}
     * @return {@code true} se l'operazione è andata a buon fine e si è ora in attesa di connessioni dai clients.
     * {@code false} se è fallita, e in tal caso viene mostrato un errore in {@code TempPanel}
     */
    public static boolean power_on() {
        if (server_status) { //il server è già attivo
            Logger.log("impossibile accendere il server, è già attivo", true);
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile accendere il server, già attivo"
            ), null);
            return false;
        }

        power_on_connectors();
        Logger.log("accesi tutti i connectors");
        server_status = true; //impostato ora altrimenti in caso di errore con i worker threads power_off() fallisce

        //tenta di popolare la pool con worker threads
        if (!ClientsInterface.populate_worker_threads()) {
            Logger.log("impossibile popolare ClientsInterface con nuovi worker threads", true);
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impostare un numero di worker threads > 0"
            ), null);

            power_off();
            return false;
        }
        Logger.log("ripopolato il server con nuovi worker threads");

        return true;
    }

    /**
     * Nel processo di accendere il server accende tutti i connectors rendendosi visibile alla rete e i clients
     */
    private static void power_on_connectors() {
        boolean no_connector = true; //se non ha connector attivi mostra un avviso

        for (Pair<Connector, Boolean> pair : registered_connectors.values()) {
            if (pair.second()) { //il connector è attivo
                pair.first().start();
                Logger.log("acceso il connector: (" + pair.first().get_name() + ")");

                no_connector = false;
            }
        }

        if (no_connector) { //nessun connector è attivo
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "il server è acceso ma nessun connector è attivo"
            ), null);
        }
    }

    /**
     * Imposta il connector registrato con il nome {@code name} come attivo, e se il server è acceso lo fa partire
     * @param name nome del connector
     */
    public static void activate_connector(String name) {
        Pair<Connector, Boolean> connector_pair = registered_connectors.get(name);
        if (connector_pair == null) {
            Logger.log("impossibile attivare il connector: (" + name + "), connector non esistente", true);
            return;
        }

        Pair<Connector, Boolean> new_pair = new Pair<>(connector_pair.first(), true);
        registered_connectors.put(name, new_pair);

        if (server_status && !connector_pair.first().get_status()) {
            new_pair.first().start();
        }
    }

    /**
     * Imposta il connector registrato con il nome {@code name} come disattivato, se il server è acceso lo stopperà e
     * scollega tutti i client collegati attraverso di esso
     * @param name nome del connector
     */
    public static void deactivate_connector(String name) {
        Pair<Connector, Boolean> connector_pair = registered_connectors.get(name);
        if (connector_pair == null) {
            Logger.log("impossibile disattivare il connector: (" + name + "), connector non esistente", true);
            return;
        }

        Pair<Connector, Boolean> new_pair = new Pair<>(connector_pair.first(), false);
        registered_connectors.put(name, new_pair);

        if (server_status && connector_pair.first().get_status()) {
            connector_pair.first().stop();
            ClientsInterface.disconnect_all(name);
        }
    }
}
