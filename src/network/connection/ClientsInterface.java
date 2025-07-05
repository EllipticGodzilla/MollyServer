package network.connection;

import files.FileInterface;
import files.Logger;
import gui.ClientList_panel;
import network.ServerManager;

import javax.crypto.Cipher;
import java.io.FileInputStream;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Gestisce tutte le connessioni con i client e attivazione/disattivazione dei {@code Connector}.
 * Ogni client nuovo collegato al server, dopo aver stabilito con il {@code Connector} una chiave di sessione con
 * l handshake, dovrà essere inviato a questa classe tramite {@code new_client()} dove sarà eseguito l encoder
 * agreement e login/reg.
 * <p>Contiene tutte le liste con {@code Connector} attivi/disattivati, client online, {@code Encoder}, accessibili
 * tramite i vari metodi.
 */
public abstract class ClientsInterface {
    /// Mappa fra il nome del {@code Connector} a cui sono legati e l'istanza di {@code Client} dei clients online
    private static final Map<String, Vector<Client>> online_clients = new LinkedHashMap<>();

    /// Mappa fra il nome di ogni clients registrato nel server, e l hash della sua password
    private static final Map<String, byte[]> clients_credentials = new LinkedHashMap<>();

    /// Array di threads che rispondono ai messaggi ricevuti dai clients
    private static Thread[] threads_workers = new Thread[0];

    /**
     * Array di Boolean che rispecchiano se il worker thread allo stesso index in {@code threads_workers} è attivo, ed
     * in tal caso vale {@code true}, o è in attesa di nuovo lavoro e vale {@code false}
      */
    private static boolean[] workers_status = new boolean[0];

    /**
     * Queue con ordinamento FIFO in cui vengono inseriti tutti i dati dei messaggi da clients che ancora non sono stati
     * processati. La dimensione della queue è fissata e modificabile dall'utente a piacimento nelle impostazioni
     */
    private static ArrayBlockingQueue<WorkData> workers_backlog = new ArrayBlockingQueue<>(1);

    //      WORKERS BACKLOG CAPACITY

    /**
     * Tenta di modificare la dimensione massima del backlog concesso ai worker threads, sono permessi interi
     * {@code > 0}, queue molto piccole potrebbero causare una perdita di messaggi poiché se alla ricezione di un
     * messaggio è piena questo verrà ignorato dal server.
     * <p>Questo paramento può essere modificato solo a server spento
     *
     * @param size nuova capacità del backlog
     */
    public static void set_workers_backlog_capacity(int size) {
        if (ServerManager.is_online()) {
            Logger.log("impossibile modificare la capacità del workers backlog con il server attivo", true);
            return;
        }

        if (size <= 0) {
            Logger.log("impossibile impostare la dimensione del backlog dei workers threads ad un numero negativo: " + size, true);
            return;
        }

        workers_backlog = new ArrayBlockingQueue<>(size);
        workers_status = new boolean[size]; //viene inizializzato con tutti false, tutti i thread non sono attivi
    }

    /// Ritorna la capacità massima del backlog permesso per i worker threads
    public static int get_workers_backlog_capacity() {
        return workers_backlog.remainingCapacity() + workers_backlog.size();
    }

    //      WORKER THREADS

    /**
     * Tenta di reimpostare il numero di Thread creati per rispondere ai messaggi dei clients, è possibile modificare
     * questo parametro solo quando il server è spento, dovrà essere specificato un numero {@code > 0} o l'operazione
     * verrà ignorata
     * @param num intero {@code > 0} di worker threads da impostare
     */
    public static void set_workers_number(int num) {
        if (ServerManager.is_online()) {
            Logger.log("impossibile modificare il numero di worker threads a server attivo", true);
            return;
        }

        if (num <= 0) {
            return;
        }

        //a server spento threads_workers è vuoto, viene popolato all'accensione
        threads_workers = new Thread[num];
    }

    /// Ritorna il numero di worker threads creati per rispondere ai messaggi dei clients
    public static int get_workers_number() {
        return threads_workers.length;
    }

    /// Ritorna il numero di messaggi ricevuti dai clients che i worker threads devono ancora processare
    public static int get_workers_backlog_size() {
        return workers_backlog.size();
    }

    /**
     * All'accensione del server l array {@code threads_workers} sarà vuoto o conterrà threads vecchi e viene popolato
     * con una nuova generazione di threads.
     * <p>Questa operazione può essere eseguita solo a server acceso, e si deve aver specificato prima un numero
     * {@code > 0} di worker threads
     * @return {@code true} se l'operazione è andata a buon fine, {@code false} se il server è attivo o se il numero
     * di worker threads è impostato a {@code 0}
     */
    public static boolean populate_worker_threads() {
        if (!ServerManager.is_online()) {
            Logger.log("impossibile ripopolare i worker threads con il server attivo", true);
            return false;
        }

        if (threads_workers.length == 0) {
            return false;
        }

        for (int i = 0; i < threads_workers.length; i++) {
            threads_workers[i] = new WorkerThread(i);
        }
        return true;
    }

    /**
     * Libera tutti i threads worker notificando tutti quelli che erano in attesa di nuovi messaggi da clients.
     * Utilizzabile solo una volta spento il server, altrimenti non avrà nessun effetto
     */
    public static void free_workers() {
        if (ServerManager.is_online()) {
            Logger.log("impossibile liberare tutti i thread worker finchè il server è attivo", true);
            return;
        }

        //notifica tutti i worker threads in attesa facendoli terminare, tutti gli altri si spegneranno una volta finito
        for (int i = 0; i < threads_workers.length; i++) {
            if (!workers_status[i]) {
                Thread worker = threads_workers[i];

                synchronized (worker) {
                    worker.notify();
                }
            }
        }

        Logger.log("liberati tutti i thread workers del server");
    }

    /**
     * Modifica lo stato di un thread worker con quello specificato, gli unici a poter modificare questo parametro sono
     * i worker stessi.
     * @param index  index del worker a cui si moifica lo stato
     * @param status nuovo stato del worker, {@code true} vuol dire che il thread sta lavorando, {@code false} è in
     *               attesa di un nuovo messaggio
     */
    protected static void set_worker_status(int index, boolean status) {
        workers_status[index] = status;
    }

    /**
     * Ritorna l'elemento di testa del backlog dei dati per i threads workers, o {@code null} nel caso il backlog sia
     * vuoto.
     * <p>Non è possibile per le mod richiedere accesso ai messaggi ricevuti dal server così liberamente, poiché
     * causerebbe il loro mancato processa dai {@code workers threads}
     * @return head element del backlog
     */
    protected static WorkData next_workers_data_backlog() {
        return workers_backlog.poll();
    }

    /**
     * Cerca di processare un messaggio ricevuto da un client aggiungendolo al backlog dei worker threads e, in caso ce
     * ne sia uno libero, notificando che è arrivato un nuovo messaggio da processare. Se tutti i workers sono già
     * attivi verrà solo aggiunto al backlog in attesa che si liberino per essere processato.
     * <p>In caso la queue sia piena viene stampato un errore nei log e il messaggio verrà perso
     * @param data nuovo messaggio da processare
     */
    public static void process_client_message(WorkData data) {
        if (!workers_backlog.offer(data)) { //queue piena
            Logger.log("backlog dei worker threads pieno, dimensione: " + workers_backlog.size(), true);
            return;
        }

        for (int i = 0; i < threads_workers.length; i++) {
            Thread worker = threads_workers[i];

            synchronized (worker) {
                //i thread in attesa hanno come status false
                if (!workers_status[i]) {
                    workers_status[i] = true; //imposta il thread come al lavoro

                    worker.notify();
                    return;
                }
            }
        }

        //tutti i workers stanno già lavorando, il messaggio verrà processato automaticamente quando uno si libera
    }

    //      METODI IO PER LE MAPPE DEI CLIENTS

    /**
     * Ritorna la lista di tutti i nomi dei Clients registrati in questo server
     * @return Array con tutti i clients registrati
     */
    public static String[] get_clients_list() {
        return clients_credentials.keySet().toArray(new String[0]);
    }

    /**
     * Ritorna la lista di tutti i nomi degli utenti online e che hanno già eseguito il login o registrazione nel server
     * @return lista di nomi degli utenti online
     */
    public static String[] get_online_clients_list() {
        Vector<String> clients = new Vector<>();

        for (Vector<Client> vector : online_clients.values()) {
            for (Client client : vector) {
                clients.add(client.get_name());
            }
        }

        return clients.toArray(new String[0]);
    }

    /**
     * Test se un client con il nome utente specificato è online
     * @param uname nome del client
     * @return {@code true} se l'utente è online, {@code false} se non è online
     */
    public static boolean is_online(String uname) {
        return online_clients.containsKey(uname);
    }

    /**
     * Test se un client con il nome specificato è già stato creato o meno
     * @param uname nome utente
     * @return {@code true} se esiste un utente, {@code false} se non esiste un utente con quel nome
     */
    public static boolean exist_user(String uname) {
        return clients_credentials.containsKey(uname);
    }

    //      CLIENTS CONNECTION START / END

    /**
     * Riceve un {@code Client} e {@code session key}, arrivati dall handshake con il connector, e dovrà eseguire
     * l encoder agreement, inizializzare l encoder scelto, inizializzare il client con l encoder, impostarlo in
     * ascolto e attendere esegua il login. Una volta eseguito il login sarà aggiunto ai client online.
     * @param connector_name nome del connector da cui proviene il client
     * @param client         client appena collegato da un connector
     * @param session_key    chiavi di sessione arrivate dall handshake
     */
    public static boolean new_client(String connector_name, Client client, Cipher[] session_key) {
        //concorda un encoder con il client
        String encoder_name = encoder_agreement(client, session_key);
        if (encoder_name == null) {
            Logger.log("encoder agreement fallito con il client: (" + client.get_name() + ")", true);
            client.close();

            return false;
        }

        Encoder encoder = ServerManager.get_encoder_instance(encoder_name);
        if (encoder == null) {
            Logger.log("impossibile creare una nuova istanza dell encoder: (" + encoder_name + ")", true);
            client.close();

            return false;
        }

        if (!init_encoder(client, session_key, encoder)) {
            Logger.log("il client: (" + client.get_name() + ") ha fallito a inizializzare l encoder: (" + encoder_name + ")", true);
            client.close();

            return false;
        }

        //imposta l encoder per il client e inizia a utilizzare le conversazioni
        client.set_encoder(encoder);

        //esegue il login del client
        Logger.log("attendo richiesta di login / registrazione dal client: (" + client.get_name() + ")");
        if (ServerManager.get_login_manager().login_client(client)) {
            Logger.log("l'utente: (" + client.get_name() + ") è online");
            online_clients.get(connector_name).add(client);
            ClientList_panel.set_online(client.get_name());

            return true;
        }
        else {
            Logger.log("errore nell'attesa del login dal client: (" + client.get_name() + ")", true);
            client.close();

            return false;
        }
    }

    /**
     * Tutta la seguente connessione è realizzata cifrando e decifrando con {@code session_key}.
     * <ul>
     *     <li>
     *         Riceve il nome dell encoder scelto dal client e controlla se è presente un encoder con lo
     *         stesso nome, in questa versione encoder compatibili devono essere nominati con lo stesso nome
     *     </li>
     *     <li>
     *         A seconda se l encoder è supportato dal server procede in modi diversi:
     *         <ul>
     *             <li>
     *                 Se l encoder scelto dal client è compatibile invia "{@code ok}" e ritorna il nome dell
     *                 encoder
     *             </li>
     *             <li>
     *                 Se l encoder scelto dal client non è compatibile invia "{@code ns}" e continua
     *             </li>
     *         </ul>
     *     </li>
     *     <li>
     *          L encoder scelto inizialmente dal client non è supportato dal server, passa client e session key a
     *          {@code changing_encoder()} concordando un nuovo encoder da usare
     *     </li>
     * </ul>
     * @param client      client con cui concordare l encoder
     * @param session_key session key concordata con il client
     * @return il nome dell encoder concordato con il client o {@code null} se è fallito
     */
    private static String encoder_agreement(Client client, Cipher[] session_key) {
        byte[] client_encoder_bytes = client.read_message();
        if (client_encoder_bytes == null) {
            Logger.log("errore nell'attesa dell encoder scelto dal client: (" + client.get_name() + ")", true);
            return null;
        }

        try {
            client_encoder_bytes = session_key[1].doFinal(client_encoder_bytes);
        }
        catch (Exception e) {
            Logger.log("impossibile decifrare il messaggio ricevuto dal client: (" + client.get_name() + ") con l encoder scelto\n\t\t\t\t" + e.getMessage(), true);
            return null;
        }

        String client_encoder = new String(client_encoder_bytes);
        if (ServerManager.exist_encoder(client_encoder)) { //encoder supportato
            Logger.log("il client: (" + client.get_name() + ") ha scelto l encoder: (" + client_encoder + "), encoder supportato");

            //conferma la scelta dell encoder al client
            try {
                client.send(session_key[0].doFinal("ok".getBytes()));
            }
            catch (Exception e) {
                Logger.log("impossibile confermare al client: (" + client.get_name() + ") l encoder: (" + client_encoder + ")\n\t\t\t\t" + e.getMessage(), true);
                return null;
            }

            return client_encoder;
        }
        else { //encoder non supportato
            return changing_encoder(client, session_key);
        }
    }

    /**
     * Nell encoder agreement la prima proposta di encoder non è supportata dal server, dovrà trovare un altro encoder:
     * <ul>
     *     <li>
     *          Non supportando lo stesso encoder invia al client una lista con tutti gli encoder che supporta il server
     *          formattata come: {@code nome_encoder1;nome_encoder2;...}
     *      </li>
     *      <li>
     *          A seconda di come il client risponde procede in modi diversi:
     *          <ul>
     *              <li>
     *                  Se riceve "{@code EC}" il cambio di encoder è stato annullato e chiude la connessione, ritorna
     *                  {@code null}
     *              </li>
     *              <li>
     *                  Se riceve il nome di un encoder registrato in questo server invia "{@code ack}" e ritorna il
     *                  nome di questo encoder
     *              </li>
     *              <li>
     *                  Se riceve qualcosa che non è il nome di un encoder registrato in questo server invia
     *                  "{@code nop}" e ritorna {@code null}
     *              </li>
     *          </ul>
     *      </li>
     * </ul>
     * @param client      client con cui concordare un nuovo encoder
     * @param session_key session key del client
     * @return il nome del nuovo encoder concordato o null se il protocollo è fallito
     */
    private static String changing_encoder(Client client, Cipher[] session_key) {
        String[] supported_encoders = ServerManager.get_encoders_list();
        StringBuilder encoders_list = new StringBuilder();

        for (String encoder : supported_encoders) {
            encoders_list.append(encoder).append(";");
        }

        try {
            client.send(session_key[0].doFinal(encoders_list.toString().getBytes()));
        }
        catch (Exception e) {
            Logger.log("impossibile cifrare e inviare al client: (" + client.get_name() + ") la lista di encoder supportati\n\t\t\t\t" + e.getMessage(), true);
            return null;
        }
        Logger.log("inviata la lista di encoder supportati al client: (" + client.get_name()+ ")");

        byte[] client_reply_bytes = client.read_message();
        if (client_reply_bytes == null) {
            Logger.log("errore aspettando una risposta alla lista del client: (" + client.get_name() + ") alla lista di encoder supportati", true);
            return null;
        }

        try {
            client_reply_bytes = session_key[1].doFinal(client_reply_bytes);
        }
        catch (Exception e) {
            Logger.log("errore decifrando la risposta del client: (" + client.get_name() + ") alla lista di encoder supportati\n\t\t\t\t" + e.getMessage(), true);
            return null;
        }

        String client_reply = new String(client_reply_bytes);
        if (client_reply.equals("EC")) { //client ha rifiutato il cambio di encoder
            Logger.log("il client: (" + client.get_name() + ") ha rifiutato il cambio di encoder");

            return null;
        }
        else if (ServerManager.exist_encoder(client_reply)) { //client ha scelto un encoder supportato
            Logger.log("il client: (" + client.get_name() + ") ha scelto di usare l encoder: (" + client_reply + ")");

            try {
                client.send(session_key[0].doFinal("ack".getBytes()));
            }
            catch (Exception e) {
                Logger.log("impossibile confermare al client: (" + client.get_name() + ") la scelta dell encoder\n\t\t\t\t" + e.getMessage(), true);
                return null;
            }

            return client_reply;
        }
        else { //ha scelto un encoder non supportato
            Logger.log("il client: (" + client.get_name() + ") ha scelto l encoder: (" + client_reply + ") non supportato", true);

            try {
                client.send(session_key[0].doFinal("nop".getBytes()));
            }
            catch (Exception e) {
                Logger.log("impossibile comunicare al client: (" + client.get_name() + ") il fallito encoder agreement\n\t\t\t\t" + e.getMessage(), true);
            }

            return null;
        }
    }

    /**
     * Riceve dal client dei bytes random con cui deve inizializzare l encoder scelto durante l encoder agreement
     * @param client      client per cui sta inizializzando l encoder
     * @param session_key session key del client
     * @param encoder encoder da inizializzare
     * @return {@code true} se riesce a inizializzare l encoder, {@code false} se è riscontrato un errore
     */
    private static boolean init_encoder(Client client, Cipher[] session_key, Encoder encoder) {
        byte[] random_bytes = client.read_message();
        if (random_bytes == null) {
            Logger.log("errore nell'attesa dei bytes random dal client: (" + client.get_name() + ") per inizializzare l encoder", true);
            return false;
        }

        try {
            random_bytes = session_key[1].doFinal(random_bytes);
        }
        catch (Exception e) {
            Logger.log("impossibile decifrare i bytes random dal client: (" + client.get_name() + ") per inizializzare l encoder\n\t\t\t" + e.getMessage(), true);
            return false;
        }

        if (!encoder.init(random_bytes)) { //fallisce a inizializzare l encoder
            Logger.log("impossibile inizializzare l encoder per il client: (" + client.get_name() + ")", true);
            return false;
        }

        if (test_encoder(client, session_key, encoder)) {
            Logger.log("il client: (" + client.get_name() + ") ha inizializzato l encoder con successo");
            return true;
        }
        else {
            Logger.log("il client: (" + client.get_name() + ") non ha passato il test dell encoder", true);
            return false;
        }
    }

    /**
     * Inizializzato l encoder per controllare che client e server lo sappiano utilizzare correttamente entrambi
     * generano {@code 32 bytes} random e li inviano all altro cifrati con la session key, ricevuti i bytes devono
     * essere decifrati e cifrati utilizzando l encoder per poi essere rinviati.
     * <ul>
     *     <li>
     *         Se riceve i suoi stessi bytes random, cifrati con l encoder, invia al client "{@code ack}" e attende una
     *         risposta dal client, se riceve "{@code ack}" il test è passato, altrimenti è fallito
     *     </li>
     *     <li>
     *         Se riceve dei bytes diversi invia al client "{@code abt}" e il test è fallito
     *     </li>
     * </ul>
     * @param client      client con cui controllare l encoder
     * @param session_key session key legata al client
     * @param encoder     encoder da testare
     * @return {@code true} se il test è passato, {@code false} se non è passato
     */
    private static boolean test_encoder(Client client, Cipher[] session_key, Encoder encoder) {
        byte[] random_bytes = new byte[32];
        new SecureRandom().nextBytes(random_bytes);

        try {
            client.direct_send(session_key[0].doFinal(random_bytes));
        }
        catch (Exception e) {
            Logger.log("impossibile cifrare i bytes random per il test dell encoder per il client: (" + client.get_name() + ")\n\t\t\t\t" + e.getMessage(), true);
            return false;
        }

        byte[] received_bytes = client.read_message();
        if (received_bytes == null) {
            Logger.log("errore nell'attesa dei random bytes dal client: (" + client.get_name() + ") per il test dell encoder", true);
            return false;
        }

        try {
            received_bytes = session_key[1].doFinal(received_bytes);
        }
        catch (Exception e) {
            Logger.log("impossibile decifrare i bytes ricevuti dal client: (" + client.get_name() + ") per il test dell encoder\n\t\t\t\t" + e.getMessage(), true);
            return false;
        }

        received_bytes = encoder.encode(received_bytes);
        if (received_bytes == null) {
            Logger.log("impossibile cifrare i bytes del client: (" + client.get_name() + ") usando il suo encoder", true);
            return false;
        }

        client.send(received_bytes);

        //si sono scambiati i random bytes, controlla che il client gli invii in dietro gli stessi
        byte[] test_bytes = client.read_message();
        if (test_bytes == null) {
            Logger.log("errore nell'attesa dei test bytes dal client: (" + client.get_name() + ") per il test dell encoder", true);
            return false;
        }

        test_bytes = encoder.decode(test_bytes);
        if (test_bytes == null) {
            Logger.log("impossibile decifrare i test bytes del client: (" + client.get_name() + ") con il suo encoder", true);
            return false;
        }

        if (Arrays.equals(test_bytes, random_bytes)) { //il client ha inviato i bytes corretti
            try {
                client.send(session_key[0].doFinal("ack".getBytes()));
            }
            catch (Exception e) {
                Logger.log("impossibile inviare la conferma del test dell encoder al client: (" + client.get_name() + ")\n\t\t\t\t" + e.getMessage(), true);
                return false;
            }

            byte[] client_result = client.read_message();
            if (client_result == null) {
                Logger.log("errore nell'attesa del risultato del client: (" + client.get_name() + ") per il test dell encoder", true);
                return false;
            }

            try {
                client_result = session_key[1].doFinal(client_result);
            }
            catch (Exception e) {
                Logger.log("impossibile decifrare il risultato del client: (" + client.get_name() + ") per il test dell encoder\n\t\t\t\t" + e.getMessage(), true);
                return false;
            }

            if (Arrays.equals(client_result, "ack".getBytes())) {
                Logger.log("il client: (" + client.get_name() + ") ha passato il test dell encoder");
                return true;
            }
            else {
                Logger.log("il client: (" + client.get_name() + ") non ha passato il test dell encoder", true);
                return false;
            }
        }
        else { //il client non ha inviato i bytes corretti
            Logger.log("il client: (" + client.get_name() + ") non ha passato il test dell encoder", true);

            try {
                client.direct_send(session_key[0].doFinal("abt".getBytes()));
            }
            catch (Exception e) {
                Logger.log("impossibile inviare al client: (" + client.get_name() + ") il rifiuto del test dell encoder", true);
            }

            return false;
        }
    }

    /**
     * Memorizza, per un nuovo utente appena registrato, il codice segreto legato a ogni utente e che viene utilizzato
     * dal {@code LoginManager} per verificare la correttezza delle login request dei clients.
     * Questo metodo viene invocato quando un {@code LoginManager} approva una register request a un client.
     * <p>Il significato effettivo di {@code psw} dipende completamente dall'uso che ne fa il {@code LoginManager} che
     * lo registra, infatti ogni {@code LoginManager} ha legato a se stesso gli utenti che si sono registrati con esso
     * e di default questi non si possono condividere fra più {@code LoginManager}
     * @param uname nome utente da registrare
     * @param psw   codice segreto da legare all'utente
     */
    protected static void register_user(String uname, byte[] psw) {
        if (clients_credentials.containsKey(uname)) {
            Logger.log("impossibile registrare nuove credenziali per un utente già esistente", true);
            return;
        }

        clients_credentials.put(uname, psw);
    }

    /**
     * Ritorna il codice segreto registrato dal {@code LoginManager} alla creazione dell'utente {@code uname}, che
     * dovrebbe essere utilizzato per verificare la correttezza di una login request
     * @param uname nome dell'utente di cui si vogliono le credenziali
     * @return credenziali dell'utente, o {@code null} se non trova un utente registrato con il nome {@code uname}
     */
    public static byte[] get_user_credential(String uname) {
        return clients_credentials.get(uname);
    }

    /// Sconnette tutti i clients dal server notificandoli con {@code EOC}, "End Of Connection"
    public static void disconnect_all() {
        for (Vector<Client> clients_vector : online_clients.values()) {
            for (Client client : clients_vector) {
                client.send("EOC".getBytes());
                client.close();
            }
        }
        online_clients.clear();

        Logger.log("disconnessi tutti i clients collegati al server");
    }

    /**
     * Sconnette tutti i clients che si sono collegati attraverso un dato connector notificano prima ognuno con
     * {@code EOC}, "End Of Connection"
     * @param connector_name nome del connector
     */
    public static void disconnect_all(String connector_name) {
        Vector<Client> online = online_clients.get(connector_name);
        if (online == null) { //nessun client online
            return;
        }

        for (Client client : online) {
            client.send("EOC".getBytes());
            client.close();
        }

        online_clients.remove(connector_name);
    }

    /**
     * Se è impostato un LoginManager controlla che esista un file con path {@code /database/users_<name>.dat}
     * dove {@code <name>} è il nome del LoginManager, in cui scriverà tutte le credenziali degli utenti registrati da
     * questo, formattate con in ogni riga i dati di un solo utente, e ogni linea sarà nella forma:
     * {@code <nome>;<Base64 encoded psw>}
     */
    public static void update_credential_file() {
        LoginManager manager = ServerManager.get_login_manager();
        if (manager == null) {
            return;
        }

        String manager_file_name = "database/users_" + manager.get_name().replace(' ', '_') + ".dat";
        if (!FileInterface.exist(manager_file_name)) {
            FileInterface.create_file(manager_file_name, true);
        }

        StringBuilder builder = new StringBuilder();

        for (String client : clients_credentials.keySet()) {
            builder .append(client)
                    .append(';')
                    .append(Base64.getEncoder().encodeToString(clients_credentials.get(client)))
                    .append('\n');
        }

        FileInterface.overwrite_file(manager_file_name, builder.toString().getBytes());
    }
}
