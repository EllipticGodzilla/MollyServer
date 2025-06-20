package network;

import files.Logger;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/*
 * gestisce le conversazioni e l'invio o ricevimento dei tutti i messaggi con il server
 */
public abstract class Connection {
    //mostra tutti i messaggi ricevuti / inviati nel log e tutti i cc bloccati / sbloccati
    public static final boolean DEBUGGING = true;

    /*
     * specifica il numero massimo di tentativi a generare un cc casuale per una nuova conversazione, se genera troppi
     * cc casuali bloccati di fila la funzione gen_cc() ritorna 0x00 indicando errore
     */
    private static final int MAX_CC_GEN_TRY = 20;

    //utilizzato solamente per generare cc casuali quando si iniziano nuove conversazioni
    private static final SecureRandom random = new SecureRandom();

    //una volta connesso a un server e inizializzati connector ed encoder, vengono memorizzati qui
    private static ConnectorWrapper current_connector = null;
    private static EncodersWrapper current_encoder = null;

    //thread che legge tutti i messaggi in arrivo dal server
    private static Thread conv_reader;

    /*
     * per permettere una connessione dinamica vengono utilizzate le conversazioni, basate sui conversation code (cc)
     * che sono dei byte, ogni volta che si invia un messaggio al server o si riceve un messaggio è iniziata una nuova
     * conversazione e il primo byte rappresenterà il cc assegnato da chi l'ha iniziata.
     * Tutti i cc in cui è attiva una conversazione devono essere bloccati aggiungendoli a locked_cc, è un operazione automatica,
     * la loro liberazione però non lo è quindi un volta finita la conversazione dovranno essere sbloccati con unlock_cc(byte).
     * In pending_action verranno memorizzate tutte le azioni da eseguire una volta ricevuta una risposta dal server a delle
     * conversazioni, si registrato e rimuovono automaticamente una volta utilizzate.
     * in waiting_thread saranno registrati tutti i thread in attesa di ricevere una risposta dal server, anche in questa
     * mappa registrazione e rimozioni sono automatizzate.
     * thread_reply funziona da tramite frà il thread che legge la risposta dal server e notifica il thread in attesa,
     * ed il thread che attende.
     */
    private static Vector<Byte> locked_cc = new Vector<>();
    private static Map<Byte, OnArrival> pending_action = new LinkedHashMap<>();
    private static Map<Byte, Thread> waiting_thread = new LinkedHashMap<>();
    private static Map<Byte, byte[]> thread_reply = new LinkedHashMap<>();

    /*
     * per ogni prefisso si possono registrare più di un OnArrival, devono essere aggiunti al vettore in questa mappa e
     * verranno eseguiti tutti ogni volta che viene ricevuto un messaggio non appartenente a nessuna conversazione e con
     * quel prefisso.
     */
    private static Map<String, Vector<OnArrival>> prefix_actions = new LinkedHashMap<>();

    /*
     * inizializza random con un seed casuale.
     */
    static {
        random.setSeed(System.currentTimeMillis());
    }

    /*
     * se non è già in ascolto utilizzando un altro connector cambia connector ed encoder e inizia ad ascoltare
     */
    protected static boolean init(ConnectorWrapper connector, EncodersWrapper encoder) {
        if (conv_reader != null && conv_reader.isAlive()) {
            Logger.log("impossibile modificare connector ed encoder di Connection mentre si è ancora in ascolto con un server", true);
            return false;
        }

        current_connector = connector;
        current_encoder = encoder;

        if (conv_reader == null) {
            conv_reader = new Thread(Connection::read_conv);
        }
        conv_reader.start();

        return true;
    }

    /*
     * invia un messaggio al server specificando che cc utilizzare e in che modo attendere una risposta da esso:
     * cc: cc da utilizzare per il messaggio, deve essere sempre specificato e se null da errore
     * notifier: oggetto da utilizzare per specificare come reagire una volta ricevuta una risposta dal sever, può essere
     * un OnArrival, e in tal caso viene registrato in pending_action, e se non lo è già il cc viene bloccato, se invece
     * è null non è attesa nessuna risposta da parte del server e il cc non viene bloccato.
     */
    protected static void send(byte[] msg, byte cc, OnArrival action) {
        //non è possibile registrare OnArrival o Thread in attesa al cc 0x00
        if (action != null && cc == 0x00) {
            Logger.log("impossibile registrare OnArrival in attesa per il messaggio: " + new String(msg) + " al cc = 0x00", true);
            return;
        }

        byte[] final_msg = new byte[msg.length + 1];
        final_msg[0] = cc;
        System.arraycopy(msg, 0, final_msg, 1, msg.length);

        if (DEBUGGING) { Logger.log("inviato al server [" + cc + "]: " + new String(msg)); }

        final_msg = current_encoder.encode(final_msg);
        current_connector.send(final_msg);

        if (!register_action(cc, action)) { //fallisce a registare il notifier action, c'è un notifier registrato a questo cc
            Logger.log("inviato il messaggio: " + new String(msg) + " con cc = " + cc + " ma è impossibile registrare l'azione poichè qualcun'altro è in attesa a questo cc", true);
        }
    }

    protected static void unlock_cc(byte cc) {
        Object obj = cc; //utilizzando byte viene confuso per un index in .remove()

        if (!locked_cc.contains(obj)) {
            Logger.log("impossibile sbloccare il cc: " + cc + " non essendo bloccato", true);
        }
        else {
            locked_cc.remove(obj);
            if (DEBUGGING) { Logger.log("sbloccato il cc: " + cc); }
        }
    }

    /*
     * se cc è bloccato registra il current thread a waiting_threads e attende che venga notificato quando viene ricevuta
     * una risposta dal server.
     * Viene ritornato null se cc non è bloccato o se fallisce a registrare il current thread come notifier, altrimenti
     * il messaggio ricevuto in risposta dal server.
     */
    protected static byte[] wait_for_reply(byte cc) {
        if (!locked_cc.contains(cc)) {
            Logger.log("impossibile attendere una riposta ad un cc non bloccato", true);
            return null;
        }

        Thread current_thread = Thread.currentThread();
        if (!register_action(cc, current_thread)) {
            Logger.log("impossibile registrare il current thread come notifier al cc = " + cc + ", un altro notifier è in attesa", true);
            return null;
        }

        synchronized (current_thread) {
            try {
                current_thread.wait();
            }
            catch (InterruptedException e) {
                Logger.log("interrupted exception attendendo una risposta dal server al cc = " + cc, true);
                Logger.log(e.getMessage(), true);
            }
        }

        byte[] reply = thread_reply.get(cc);
        thread_reply.remove(cc);

        return reply;
    }

    //genera un cc casuale che non sia bloccato, se genera MAX_CC_GEN_TRY cc casuali generati bloccati di fila ritorna 0x00
    protected static byte gen_cc() {
        byte[] cc_array = new byte[MAX_CC_GEN_TRY];
        random.nextBytes(cc_array);

        for (byte cc : cc_array) {
            if (!locked_cc.contains(cc)) {
                locked_cc.add(cc);
                if (DEBUGGING) { Logger.log("bloccato il cc: " + cc); }

                return cc;
            }
        }

        return 0x00;
    }

    /*
     * registra l'azione da eseguire una volta ricevuta una risposta dal server a uno specifico cc, notifier può essere
     * OnArrival: viene aggiunto a pending_actions e verrà eseguito una volta ricevuta la risposta
     * Thread: viene aggiunto a waiting_threads e notificato una volta ricevuta la risposta
     * null: non fa nulla
     * il notifier viene aggiunto a una qualsiasi delle due mappe solo se non c'è nessun altro notifier registrato
     * a quel cc, anche se sono due notifier di tipi diversi.
     */
    private static boolean register_action(byte cc, Object notifier) {
        //se un altro notifier è già registrato a questo cc è l'unico modo in cui questo metodo può fallire e ritorna false
        if (waiting_thread.containsKey(cc) || pending_action.containsKey(cc)) {
            return false;
        }

        if (notifier instanceof OnArrival action) {
            pending_action.put(cc, action);
        }
        else if (notifier instanceof Thread thread) {
            waiting_thread.put(cc, thread);
        }

        return true;
    }

    /*
     * Riceve tutti i messaggi dal server ed esegue il notifier, o se non è una conversazione ma utilizza i prefissi
     * eseguirà tutti i codici legati a tale prefisso.
     * Continuerà a richiedere messaggi dal connector finché non verrà chiuso, quando viene chiuso la chiamata read() ritorna
     * null e si esce dal while.
     */
    private static void read_conv() {
        //continua ad aspettare nuovi messaggi finché current_connector.read() non ritorna null
        while (true) {
            byte[] server_msg = current_connector.read();
            if (server_msg == null) {
                break;
            }

            server_msg = current_encoder.decode(server_msg);
            byte cc = server_msg[0];
            server_msg = Arrays.copyOfRange(server_msg, 1, server_msg.length);

            if (DEBUGGING) { Logger.log("ricevuto dal server [" + cc + "] " + new String(server_msg)); }

            //per non rallentare il ricevimento di messaggi li processa in un altro thread
            final byte[] final_msg = server_msg;
            new Thread(() ->
                process_msg(final_msg, cc)
            ).start();
        }

        Logger.log("il connector è stato chiuso, il thread per leggere conversazioni è stoppato");
    }

    /*
     * Ricevuto un messaggio dal server può cadere in tre categorie, e non può essere in più di una di queste
     * 1) c'è un OnArrival registrato con questo cc, in tal caso lo esegue e rimuove da waiting_actions
     * 2) c'è un Thread che attendeva risposta a questo cc, in tal caso aggiunge questa risposta a thread_reply e lo notifica
     * 3) non c'è OnArrival o Thread registrati in questo cc, in tal caso msg sarà nella forma <prefix>:<payload>, vengono
     *    separati e verranno chiamati tutti gli OnArrival registrati a questo prefisso
     */
    private static void process_msg(byte[] msg, byte cc) {
        OnArrival action = pending_action.get(cc);
        if (action != null) {
            action.on_arrival(cc, msg);

            return;
        }

        Thread thread = waiting_thread.get(cc);
        if (thread != null) {
            thread_reply.put(cc, msg);
            synchronized (thread) {
                thread.notify();
            }

            return;
        }

        //trova l'index del primo carattare ':' in msg[] per separare il prefisso dal payload, se non c'è nessun ':' trova msg.length
        int comma_index;
        for (comma_index = 0; comma_index < msg.length || msg[comma_index] != ':'; comma_index++) {}

        String prefix = new String(Arrays.copyOfRange(msg, 0, comma_index));
        byte[] payload = Arrays.copyOfRange(msg, Math.min(comma_index + 1, msg.length), msg.length);

        Vector<OnArrival> actions = prefix_actions.get(prefix);
        if (actions == null) {
            Logger.log("ricevuto dal server [" + cc + "]: " + new String(msg) + ", ma nessun OnArrival o Thread è in attesa, e niente è registrato a questo prefisso", true);
            return;
        }

        for (OnArrival a : actions) {
            a.on_arrival(cc, payload);
        }
    }

    // aggiunge una nuova azione da eseguire quando si riceve un messaggio dal server con un dato prefisso
    public static void register_prefix_action(String prefix, OnArrival action) {
        Vector<OnArrival> actions_vector = prefix_actions.get(prefix);

        if (actions_vector == null) { //è la prima azione a essere registrata per questo prefisso
            actions_vector = new Vector<>();
            actions_vector.add(action);

            prefix_actions.put(prefix, actions_vector);
        }
        else {
            actions_vector.add(action);
        }
    }

    /*
     * chiude la connessione con il server
     */
    public static void close() {
        current_connector.close();
    }
}