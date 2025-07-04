package network;

import files.Logger;
import network.connection.ConnectorWrapper;
import network.connection.Encoder;
import network.connection.LoginManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Gestisce l'attività del server, accensione e spegnimento dei connector.
 * Contiene tutti i parametri del server con liste dei connector, encoder, login manager.
 * todo che cazz fa sta classe? scrivi meglio
 */
public class ServerManager {
    /// Mappa fra il nome assegnato a ogni {@code Connector} registrato e il suo wrapper
    private static final Map<String, ConnectorWrapper> registered_connectors = new LinkedHashMap<>();

    /// Mappa fra il nome di ogni {@code Encoder} registrato e il constructor della sua classe
    private static final Map<String, Constructor<? extends Encoder>> registered_encoders = new LinkedHashMap<>();

    /// Istanza di un login manager utilizzata per gestire login / registrazioni di utenti nel server
    private static LoginManager active_login_manager;

    /// Mappa fra il nome di ogni login manager disponibile e la sua implementazione
    private static final Map<String, LoginManager> login_managers = new LinkedHashMap<>();

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
     * Prova a modificare il login manager attivo, per modificarlo il server deve essere spento.
     * @param manager_name nome del nuovo manager da utilizzare
     * @return {@code true} se riesce a impostare il manager, {@code false} se il server è ancora attivo
     */
    public static boolean set_login_manager(String manager_name) {
        //todo if (server.is_active())

        LoginManager manager = login_managers.get(manager_name);
        if (manager == null) {
            Logger.log("impossibile impostare il login manager: (" + manager_name + ") come attivo, manager non trovato", true);
            return false;
        }

        active_login_manager = manager;
        return true;
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

    //      METODI IO PER LE MAPPE CONNECTOR E ENCODER

    /**
     * Aggiunge un nuovo {@code Connector} utilizzando i metodi specificati da una mod
     * @param starter metodo per fare partire il {@code Connector}
     * @param stopper metodo per stoppare il {@code Connector}
     * @param name nome del {@code Connector}
     */
    public static void register_connector(Method starter, Method stopper, String name) {
        if (registered_connectors.containsKey(name)) {
            Logger.log("impossibile registrare più di un connector con il nome: (" + name + ")", true);
            return;
        }

        registered_connectors.put(name, new ConnectorWrapper(starter, stopper, name));
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
    public static ConnectorWrapper get_connector(String name) {
        return registered_connectors.get(name);
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
}
