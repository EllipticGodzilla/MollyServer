package network;

import files.Logger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Thread con l'incarico di processare tutti i messaggi ricevuti dai clients, una volta finito un incarico controllano
 * se il backlog è vuoto, in tal caso si interrompono in attesa di una notifica, altrimenti prendono un nuovo messaggio
 * e continuano con il processing.
 * <p>Istanze di questa classe vengono create e gestite da ClientsInterface in modo automatico.
 */
public class WorkerThread extends Thread {
    /// Mappa fra ogni prefisso e un vettore con tutte le azioni a lui registrate
    private static final Map<String, Vector<OnArrival>> prefix_actions = new LinkedHashMap<>();

    /// Index dell'istanza nell array in {@code ClientsInterface}, viene utilizzato per avvisare quando questo è attivo
    private final int index;

    //      PREFIX MANAGING

    /*
     * inizializza la mappa prefix_actions con i prefissi standard:
     *
     */
    static {

    }

    /**
     * Aggiunge una azione fra quelle da eseguire quando si riceve un messaggio da un client con il prefisso
     * specificato.
     * <p>Questa meccanica è in funzione solo una volta che il client ha eseguito il login, prima non viene neanche
     * controllato se delle azioni sono registrate.
     * @param prefix prefisso a cui aggiungere una nuova azione
     * @param action azione da aggiungere al prefisso
     */
    public static void add_prefix_action(String prefix, OnArrival action) {
        Vector<OnArrival> registered = prefix_actions.get(prefix);
        if (registered == null) {
            registered = new Vector<>();
            prefix_actions.put(prefix, registered);
        }

        registered.add(action);
    }

    //      WORK THREAD INSTANCES

    /**
     * Crea un nuovo WorkerThread e lo farà partire subito, rimanendo subito in attesa di una notifica poiché
     * {@code ClientsInterface} crea i nuovi workers thread quando il server è ancora spento.
     * Il thread continuerà a vivere finché non riceve una notifica con il server spento.
     * <ol>
     *     <li>
     *         Controllo se il server è attivo, in caso contrario ritorna e il thread muore
     *     </li>
     *     <li>
     *         Richiede da {@code ClientsInterface} un elemento dal {@code backlog}, se riceve {@code null} salta al
     *         punto {@code 4}, altrimenti continua
     *     </li>
     *     <li>
     *          Processa i dati ricevuti dal {@code backlog}, per le specifiche vedi {@code process()}
     *     </li>
     *     <li>
     *         Controlla se il {@code backlog} in {@code ClientsInterface} è vuoto e in tal caso continua, altrimenti
     *         torna al punto {@code 2}
     *     </li>
     *     <li>
     *         chiama {@code wait()} interrompendosi e attendendo che venga notificato per l'arrivo di un nuovo
     *         messaggio da processare.
     *         Una volta ricevuta la notifica ripartirà dal punto {@code 1}
     *     </li>
     * </ol>
     */
    public WorkerThread(int index) {
        this.index = index;
        start();
    }

    @Override
    public void run() {
        do {
            WorkData data = ClientsInterface.next_workers_data_backlog();
            //continua a processare messaggi finché non si esaurisce il backlog
            while (data != null) {
                process(data);
                data = ClientsInterface.next_workers_data_backlog();
            }

            try {
                synchronized (this) {
                    ClientsInterface.set_worker_status(index, false); //si imposta come thread in attesa
                    wait();
                }
            }
            catch (Exception e) {
                Logger.log("errore nell'attesa di una notifica in un worker thread\n\t\t\t\t" + e.getMessage(), true);
                break;
            }
        }
        while (true /* todo if server.is_alive */);

        Logger.log("un worker thread è stato spento");
    }

    /**
     * Processa messaggi ricevuti dai clients secondo la seguente tabella:
     * <ul>
     *     <li>
     *         Se è specificata un azione {@code OnArrival} la esegue chiamando il suo metodo {@code on_arrival}
     *     </li>
     *     <li>
     *         Se non è specificata un azione dipende dallo stato del client, se ha eseguito il login o meno
     *         <ul>
     *             <li>
     *                 Se non ha ancora eseguito il login gli unici prefissi che è permesso inviare al client sono
     *                 {@code login} e {@code register}, rispettivamente per richiedere le informazioni necessarie da
     *                 inviare per eseguire il login o registrare un nuovo account
     *             </li>
     *             <li>
     *                 Se ha eseguito il login, il messaggio viene scomposto in {@code prefix;payload} e processato di
     *                 conseguenza, per le specifiche di questo processo vedi {@code run_prefix()}
     *             </li>
     *         </ul>
     *     </li>
     * </ul>
     * @param data dati ricevuti dal client e che devono essere processati
     */
    private void process(WorkData data) {
        OnArrival action = data.get_action();
        Client client = data.get_client();
        byte[] msg = data.get_message();

        if (action != null) {
            action.on_arrival(client, data.get_cc(), msg);
            return;
        }

        if (client.is_logged()) {
            int prefix_len = find_prefix_len(msg);
            String prefix = new String(Arrays.copyOfRange(msg, 0, prefix_len));

            byte[] payload = null;
            if (prefix_len != msg.length - 1) {
                payload = Arrays.copyOfRange(msg, prefix_len + 1, msg.length);
            }

            run_prefix(client, prefix, payload, data.get_cc());
        }
        else if (Arrays.equals(msg, "login".getBytes())) {
            client.send(ClientsInterface.get_login_manager().get_login_request(), data.get_cc());
        }
        else if (Arrays.equals(msg, "register".getBytes())) {
            client.send(ClientsInterface.get_login_manager().get_register_request(), data.get_cc());
        }
        else { //client senza login ha inviato qualcosa di diverso da "login" o "register"
            Logger.log("il client: (" + client.get_name() + ") ha inviato: (" + new String(msg) + ") prima di eseguire il login", true);
        }
    }

    /**
     * Dato un messaggio ritorna l index della prima ricorrenza del carattere {@code ;} o {@code lunghezza - 1} se non
     * lo trova
     */
    private int find_prefix_len(byte[] msg) {
        for (int i = 0; i < msg.length; i++) {
            if (msg[i] == ';') {
                return i;
            }
        }

        return msg.length - 1;
    }

    /**
     * Per rispondere ai messaggi del client è possibile registrare delle azioni da eseguire quando si ricevono messaggi
     * da essi formattati come {@code prefix;payload} con un {@code prefix} predefinito. Ricevuto un messaggio esegue
     * tutti i metodi registrati al suo prefisso, o stampa un errore in caso non ce ne siano di registrati.
     * @param client  client che invia il messaggio
     * @param prefix  prefisso utilizzato in questo messaggio
     * @param payload contenuto del messaggio
     * @param cc      cc utilizzato per questo messaggio
     */
    private void run_prefix(Client client, String prefix, byte[] payload, byte cc) {
        Vector<OnArrival> actions = prefix_actions.get(prefix);
        if (actions == null) {
            Logger.log("il client: (" + client.get_name() + ") ha inviato un messaggio con il prefisso: (" + prefix + ") a cui non è legata nessuna azione", true);
            return;
        }

        for (OnArrival a : actions) {
            a.on_arrival(client, cc, payload);
        }
    }
}
