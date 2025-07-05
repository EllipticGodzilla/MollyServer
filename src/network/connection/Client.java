package network.connection;

import files.Logger;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Rappresenta i client online, le istanze devono essere create dai Connector dopo che un client si è connesso a loro.
 * Ogni Connector dovrà creare una sua classe estensione di questa specificando come inviare e come ricevere dati dal
 * client, come chiudere la connessione.
 * Una volta specificato l encoder si dovrà occupare di inviare a ClientsInterface tutti i messaggi ricevuti.
 */
public abstract class Client {
    // todo solo per testing, quando true vengono stampate tutti i contenuti dei messaggi fra clients e server
    public static final boolean DEBUGGING = true;

    /**
     * Specifica il numero massimo di tentativi a generare un {@code cc} casuale per una nuova conversazione, se genera troppi
     * {@code cc} casuali bloccati di fila la funzione gen_cc() ritorna 0x00 indicando errore
     */
    private static int MAX_CC_GEN_TRY = 20;

    /// Utilizzato per generare cc casuali fra tutti i clients
    private static final SecureRandom random = new SecureRandom();

    /// Vettore con tutti i {@code cc} bloccati da conversazioni in corso con questo client
    private final Vector<Byte> locked_cc = new Vector<>();

    /// Mappa fra i {@code cc} bloccati e un oggetto OnArrival da eseguire quando si riceverà una risposta dal client a questo {@code cc}
    private final Map<Byte, OnArrival> waiting_actions = new LinkedHashMap<>();

    /// Mappa fra i {@code cc} bloccati e i Thread che attendono una risposta dal client a questi {@code cc}
    private final Map<Byte, Thread> waiting_threads = new LinkedHashMap<>();

    /**
     * Una volta ricevuta una risposta a un {@code cc} a cui un Thread era in attesa, per passare il messaggio fra i due Thread
     * viene aggiunto a questa mappa legato al {@code cc}, il Thread in attesa una volta notificato legge e rimuove i dati da
     * questa mappa
     */
    private final Map<Byte, byte[]> thread_bridge = new LinkedHashMap<>();

    /// Nome con cui viene identificato questo client o temp id se non è ancora stato eseguito il login
    private String client_name;

    /// True se non è ancora eseguito il login e in {@code client_name} è scritto un temp id, false se è scritto il nome
    private boolean is_temp_id = true;

    /// Encoder da utilizzare per cifrare la connessione con il client
    private Encoder encoder;

    /**
     * Quando si crea una nuova istanza di un Client non è ancora stato eseguito il login, viene inizializzato con un
     * intero random che dovrebbe essere unico fra gli utenti non registrati e può essere inteso come id.
     * Una volta registrato e impostato il nome del Client non è possibile modificarlo
     */
    public Client() {
        client_name = Integer.toString(new SecureRandom().nextInt());
    }

    /**
     * Imposta il nome di questo client una volta eseguito il login, è possibile specificarlo solo una volta e non è
     * modificabile in futuro.
     * @param uname nome del client con cui ha eseguito il login
     */
    public void set_uname(String uname) {
        if (!is_temp_id) {
            Logger.log("impossibile modificare il nome dell'utente: (" + client_name +")", true);
            return;
        }

        client_name = uname;
        is_temp_id = false;
    }

    /**
     * Distingue fra client che ancora non hanno eseguito il login / registrazione e client che sono entrati in un
     * utente del server
     * @return {@code true} se è in un utente, {@code false} se non ha ancora eseguito il login
     */
    public boolean is_logged() {
        return !is_temp_id;
    }

    /**
     * Ritorna il nome del client con cui ha eseguito il login se lo ha eseguito, altrimenti un temp id generato
     * casualmente alla creazione di questa istanza
     * @return Nome di questo client o temp id
     */
    public String get_name() {
        return client_name;
    }

    /**
     * Imposta il numero massimo di tentativi disponibili a generare un nuovo {@code cc} per una conversazione prima di fallire,
     * se impostato a -1 questo limite viene disabilitato
     * @param max_cc_gen_try numero massimo di tentativi per generare nuovi {@code cc}, o -1 se si vuole disattivare questo
     *                       limite
     */
    public static void set_max_cc_gen_try(int max_cc_gen_try) {
        MAX_CC_GEN_TRY = max_cc_gen_try;
    }

    /**
     * Invia un messaggio al server senza pensare a {@code cc} o encoder, l'implementazione di questo metodo dipende dal
     * Connector da cui proviene
     * @param msg messaggio da inviare al client
     */
    public abstract void direct_send(byte[] msg);

    /**
     * Invia un messaggio al client specificando conversation code e oggetto OnAction da eseguire una volta ricevuta
     * una risposta allo stesso {@code cc}, o null se non si attende una risposta a questo {@code cc}.
     * Non è possibile registrare istanze di OnAction al {@code cc} = 0x00, essendo riservato a messaggi senza risposta attesa
     * @param msg    messaggio da inviare al client
     * @param cc     {@code cc} da utilizzare per la conversazione, se {@code cc} = 0x00 non attende una risposta, altrimenti blocca il
     *               {@code cc} e se specificato lega action a questo {@code cc} in attesa
     * @param action azione da eseguire una volta ricevuta una risposta a questo {@code cc}, o null se non se ne aspetta una
     * @throws RuntimeException in caso {@code cc} = 0x00 e action != null
     */
    public void send(byte[] msg, byte cc, OnArrival action) {
        if (cc == 0x00 && action != null) {
            Logger.log("tentativo di specificare un azione in risposta ad un messaggio con cc = 0x00 dal client: (" + client_name + ")", true);
            throw new RuntimeException("impossibile specificare un azione per il cc = 0x00 dal client: (" + client_name + ")");
        }

        //concatena cc e msg[] in un unico messaggio
        byte[] final_msg = new byte[msg.length + 1];
        final_msg[0] = cc;
        System.arraycopy(msg, 0, final_msg, 1, msg.length);

        if (DEBUGGING) { Logger.log("invio (" + client_name + ")[" + cc + "] -> " + new String(msg)); }

        if (encoder != null) {
            final_msg = encoder.encode(final_msg);
        }
        direct_send(final_msg);

        if (!register_action(cc, action)) { //fallisce a registrare action come azione da eseguire ricevuta la risposta
            Logger.log("inviato un messaggio al client: (" + client_name + ")[" + cc + "] ma non è possibile registrare OnArrival essendo il cc già occupato", true);
        }
    }

    /**
     * Invia un messaggio senza aspettarsi nessuna risposta, utilizza {@code cc} = 0x00
     * @param msg messaggio da inviare al client
     */
    public void send(byte[] msg) {
        send(msg, (byte) 0x00, null);
    }

    /**
     * Invia un messaggio al client registrando un OnArrival in un {@code cc} random che viene ritornato, nel caso in cui action
     * != null e riesca a generare il {@code cc}, questo viene bloccato.
     * @param msg    messaggio da inviare al client
     * @param action azione da eseguire una volta ricevuta una risposta
     * @return cc utilizzato per inviare il messaggio
     * @throws RuntimeException nel caso fallisca a generare un nuovo {@code cc}
     */
    public byte send(byte[] msg, OnArrival action) {
        byte cc = gen_random_cc();
        send(msg, cc, action);

        return cc;
    }

    /**
     * Invia un messaggio specificando {@code cc} e senza registrare nessun OnArrival, non viene bloccato il {@code cc}
     * @param msg messaggio da inviare al client
     * @param cc  {@code cc} utilizzato per inviare il messaggio
     */
    public void send(byte[] msg, byte cc) {
        send(msg, cc, null);
    }

    /**
     * Attende che il client invii una risposta a uno specifico {@code cc} bloccato, ritorna il messaggio decifrato o null nel
     * caso in cui il {@code cc} specificato non sia stato bloccato, non riesca a registrare questo Thread come notifier, o
     * una volta messo il thread in attesa arriva un InterruptedException
     * @param cc {@code cc} da cui attendere una risposta
     * @return il messaggio decifrato, o null nel caso di un eccezione
     */
    public byte[] wait_for_reply(byte cc) {
        if (!locked_cc.contains(cc)) {
            return null;
        }

        Thread current_thread = Thread.currentThread();
        if (!register_action(cc, current_thread)) {
            Logger.log("errore nella registrazione del thread per attendere una risposta dal client: (" + client_name + ")[" + cc + "]", true);
            return null;
        }

        synchronized (current_thread) {
            try {
                current_thread.wait();
            }
            catch (InterruptedException e) {
                Logger.log("errore nell'attesa di una risposta dal client: (" + client_name + ")[" + cc + "] da thread", true);
                Logger.log(e.getMessage());

                return null;
            }
        }

        byte[] reply = thread_bridge.get(cc);
        thread_bridge.remove(cc);

        return reply;
    }

    /**
     * Blocca il {@code cc} specificato evitando che questo possa essere scelto per altre conversazioni
     * @param cc {@code cc} da bloccare
     * @return true se riesce a bloccare il {@code cc}, false se {@code cc} è già bloccato
     */
    public boolean lock_cc(byte cc) {
        if (locked_cc.contains(cc)) {
            return false;
        }

        locked_cc.add(cc);
        return true;
    }

    /**
     * Sblocca un {@code cc} una volta che la conversazione che avveniva su di esso è finita, in nessun caso questa operazione è
     * fatta in modo automatico e non sbloccare {@code cc} può causare problemi alla lunga
     * @param cc {@code cc} da sbloccare
     * @return true se è riuscito a sbloccare il {@code cc}, false se non era bloccato
     */
    public boolean unlock_cc(byte cc) {
        return locked_cc.removeElement(cc);
    }

    /**
     * Tenta di generare un {@code cc} random non bloccato utilizzando this.random, ogni tentativo fallisce se il {@code cc} generato è
     * già stato bloccato da una conversazione. Dopo MAX_CC_GEN_TRY tentativi falliti ritorna 0x00
     * @return cc random utilizzabile per una nuova conversazione o 0x00 se MAX_CC_GEN_TRY tentativi sono falliti
     */
    private byte gen_random_cc() {
        byte[] new_cc = new byte[1];
        for (int i = 0; i < MAX_CC_GEN_TRY; i++) {
            random.nextBytes(new_cc);

            if (!locked_cc.contains(new_cc[0])) {
                return new_cc[0];
            }
        }

        Logger.log("impossibile generare un nuovo cc per il client: (" + client_name + ")", true);
        return 0x00;
    }

    /**
     * Registra un oggetto in attesa di risposta a un {@code cc} specifico, può essere un istanza di Thread o di OnArrival. In
     * ogni caso potendoci essere solo un oggetto in attesa di risposta a un {@code cc}, ritorna false in caso trovi altri
     * oggetti in attesa al {@code cc} specificato.
     * In caso notifier = null non fa nulla, altrimenti blocca il {@code cc}
     * @param cc       {@code cc} a cui registrare action
     * @param notifier oggetto in attesa di risposta al {@code cc}
     * @return true se è riuscito a registrare action, false se ha trovato un altra azione legata a questo {@code cc}
     */
    private boolean register_action(byte cc, Object notifier) {
        if (waiting_threads.containsKey(cc) || waiting_actions.containsKey(cc)) {
            return false;
        }

        if (notifier instanceof OnArrival action) {
            waiting_actions.put(cc, action);
        }
        else if (notifier instanceof Thread thread) {
            waiting_threads.put(cc, thread);
        }
        else if (notifier != null) {
            Logger.log("impossibile registrare un azione ad un cc che non sia istanza di OnArrival o Thread", true);
            return false;
        }

        if (notifier != null && !locked_cc.contains(cc)) {
            locked_cc.add(cc);
        }

        return true;
    }

    /**
     * Imposta l encoder da utilizzare per cifrare la connessione con questo client, una volta specificato viene fatto
     * partire un Thread continuamente in attesa di ricevere messaggi dal client.
     * Non è possibile modificare encoder una volta specificato, ritorna true se riesce a impostare l encoder, false se
     * uno era già specificato
     * @param encoder encoder per cifrare la connessione
     * @return true se riesce a impostarlo, false se un encoder era già specificato
     */
    public boolean set_encoder(Encoder encoder) {
        if (this.encoder != null) {
            Logger.log("impossibile modificare encoder al client: (" + client_name + ")", true);
            return false;
        }

        this.encoder = encoder;

        //inizia ad ascoltare per messaggi da parte del client
        new Thread(this::listener).start();

        return true;
    }

    /**
     * Attende e ritorna messaggi da parte del client, nel caso in cui durante l'attesa di un messaggio il client sia
     * chiuso dovrà interrompersi e ritornare {@code null}.
     * La specifica di questo metodo dipende dal Connector da cui arriva
     * @return messaggio da parte del client, o {@code null} se il client è stato chiuso
     */
    public abstract byte[] read_message();

    /**
     * Continua ad attendere messaggi dal client, una volta ricevuto un messaggio e decifrato, a seconda del {@code cc}:
     *
     * <p>Se al {@code cc} è in attesa un Thread o legato un action vengono eseguiti quelli.
     *
     * <p>Se Al {@code cc} non è in attesa nessun Thread e non è legato nessun action, viene inoltrato al ClientsInterface per
     * essere processato come messaggio normale, a meno che il {@code cc} non sia bloccato e in tal caso si aggiunge un errore
     * ai log e il messaggio ignorato.
     */
    public void listener() {
        while(true) { //continua finche msg == null
            byte[] msg = read_message();
            if (msg == null) { //client chiuso
                break;
            }

            //decifra il messaggio e divide cc dal payload
            if (encoder != null) {
                msg = encoder.decode(msg);
            }
            byte cc = msg[0];
            //viene passata nella lamba quindi deve essere final
            msg = Arrays.copyOfRange(msg, 1, msg.length);

            if (DEBUGGING) { Logger.log("ricevuto (" + client_name + ")[" + cc + "] -> " + new String(msg)); }

            if (locked_cc.contains(cc)) {
                notify_reply_to(cc, msg);
            }
            else {
                ClientsInterface.process_client_message(new WorkData(this, msg, cc));
            }
        }

        Logger.log("il client: (" + client_name + ") è stato fermato");
    }

    /**
     * Ha ricevuto una risposta a un {@code cc} bloccato, cerca se ha un oggetto in attesa di questa risposta e nel caso
     * lo avvisa. Nel caso in cui nessun oggetto sia in attesa di una risposta viene aggiunto un errore ai Log e il
     * messaggio ignorato
     * @param cc  {@code cc} da cui proviene la risposta
     * @param msg risposta del client
     */
    private void notify_reply_to(byte cc, byte[] msg) {
        if (waiting_threads.containsKey(cc)) {
            Thread thread = waiting_threads.get(cc);
            waiting_threads.remove(cc);

            thread_bridge.put(cc, msg);
            synchronized (thread) {
                thread.notify();
            }
        }
        else if (waiting_actions.containsKey(cc)) {
            OnArrival action = waiting_actions.get(cc);
            waiting_actions.remove(cc);

            ClientsInterface.process_client_message(new WorkData(this, msg, cc, action));
        }
        else {
            //nessuna delle due mappe conteneva oggetti legati al cc
            Logger.log("ricevuta una risposta a (" + client_name + ")[" + cc + "] ma nessun oggetto è in attesa", true);
        }
    }

    /**
     * Definisce come chiudere la connessione con un client, la specifica di questo metodo dipende dal Connector da cui
     * arriva.
     * Chiamare questo metodo mentre si è in ascolto deve portare {@code read_message()} a interrompersi e ritornare {@code null}
     */
    public abstract void close();
}
