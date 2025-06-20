package network;

import files.Logger;
import files.ModLoader;
import files.annotations.Connector;
import files.annotations.EncoderDefinition;
import gui.ButtonTopBar_panel;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_action;
import gui.temppanel.TempPanel_info;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//gestisce la connessione a server
public abstract class ServerInterface {
    /*
     * tutti i connectors, encoders, dns, server disponibili devono essere registrati in queste mappe che legano ognuno
     * al proprio nome in modo da essere riconoscibili.
     */
    private static final Map<String, ConnectorWrapper> registered_server_connector = new LinkedHashMap<>();
    private static final Map<String, ConnectorWrapper> registered_dns_connector = new LinkedHashMap<>();
    private static final Map<String, EncodersWrapper> registered_encoders = new LinkedHashMap<>();
    private static final Map<String, DnsInfo> registered_dns = new LinkedHashMap<>();
    private static final Map<String, ServerInfo> registered_servers = new LinkedHashMap<>();

    //indica se in questo momento si è connessi a un server o meno
    private static boolean connected = false;

    //una volta entrati in un utente nel server ci viene fornito un id, che solitamente dovrebbe essere il nome utente
    private static String client_id;
    /*
     * Il server permette di creare delle classi di utenti per permettere di comunicare fra di loro, una volta entrati
     * in una di queste classi, o creata una nuova, class_id contiene il suo nome dato dal creatore.
     * Mentre class_members contiene un vettore con gli id di tutti gli utenti all'interno della stessa classe
     */
    private static String class_id = "";
    private static final Vector<String> class_members = new Vector<>();

    /*
     * Ogni server può decidere le informazioni da richiedere agli utenti per registrare un nuovo utente o eseguire il login, una volta ricevute
     * le formatta in un TempPanel_info, viene svuotato ogni volta che si esegue il login con successo in un utente o ci si
     * disconnette dal server.
     * per richieder il format delle informazioni si invia "register" o "login" senza nessun payload.
     */
    private static TempPanel_info register_requests = null;
    private static TempPanel_info login_requests = null;
    //utilizzando lo stesso TempPanel_action per mandare al server i dati, deve essere specificato qui che prefisso utilizzare
    private static byte[] sender_prefix;

    ///Inizializza e aggiunge alla lista dei registrati l encoder standard e i due connector, per server e dns
    public static void init_standards() {
        Logger.log("carico tutti gli standard per le connessioni");

        Class<?> std_encoder = StdProtocol.class;
        Class<?> std_server_connector = STDConnector.class;
        Class<?> std_dns_connector = STDConnector_DNS.class;

        ModLoader.load_encoder(std_encoder);
        ModLoader.load_connector(std_dns_connector);
        ModLoader.load_connector(std_server_connector);
    }

    /**
     * Tenta la connessione con un server, se è stato registrato l'indirizzo ip chiama direttamente il connector inizializzando
     * la connessione, se è registrato il link dovrà prima trovare l'indirizzo ip collegandosi al DNS.
     * Una volta finito l'handshake riceve la lista di encoders supportati dal server, deve trovarne uno in comune e inviarlo
     * al connector che lo inizializza. Dovrà anche occuparsi del login in un utente.
     * Ritorna true se la connessione è andata a buon fine, false se ha riscontrato un errore.
     */
    public static boolean connect(String server_name) {
        ServerInfo info = registered_servers.get(server_name);
        if (info == null) {
            Logger.log("impossibile connettersi al server: " + server_name + ", nessuno è registrato con questo nome", true);
            return false;
        }

        String server_ip;
        if (info.SERVER_IP == null) { //non è stato registrato l'ip e deve collegarsi al dns
            Logger.log("cerco l'indirizzo ip del server (" + server_name + ") collegandomi al dns (" + info.DNS_NAME + ")");
            server_ip = retrive_ip(info.DNS_NAME, info.SERVER_LINK);

            if (server_ip == null) {
                Logger.log("errore nella ricerca dell'indirizzo ip del server: " + server_name + " nel dns: " + info.DNS_NAME, true);
                return false;
            }
            Logger.log("indirizzo ip del server (" + server_name + ") trovato con successo (" + server_ip + ")");
        }
        else {
            server_ip = info.SERVER_IP;
        }

        ConnectorWrapper server_connector = registered_server_connector.get(info.CONNECTOR);
        if (server_connector == null) {
            Logger.log("impossibile utilizzare il connector: " + info.CONNECTOR + " per il server:  " + server_name + ", nessuno è registrato con questo nome", true);
            return false;
        }
        Logger.log("trovato il connector (" + info.CONNECTOR + ") per il server (" + server_name + ")");

        Cipher[] session_cipher = server_connector.server_handshake(server_ip, info);
        if (session_cipher == null) {
            Logger.log("errore durante l'handshake con il server, impossibile inizializzare un encoder", true);
            return false;
        }
        Logger.log("handshake con il server (" + server_name + ") andato a buon fine");

        // se il server non ha installato nessun encoder che supporti quello specificato nelle server info avvisa
        // l'utente e chiede se vuole cambiare encoder legato a questo server
        String agreed_encoder_name = encoder_agreement(server_connector, session_cipher, info);
        if (agreed_encoder_name == null) {
            Logger.log("il server (" + server_name + ") non ha installato nessun encoder compatibile con quelli installati");
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile scegliere un encoder con il server: " + server_name
            ), null);
            return false;
        }
        Logger.log("scelto l'encoder (" + agreed_encoder_name + ") per cifrare la connessione con il server (" + server_name + ")");

        //tenta di inizializzare l'encoder condividendo con il server un numero specificato di byte
        EncodersWrapper server_encoder = init_encoder(server_connector, session_cipher, agreed_encoder_name);
        if (server_encoder == null) {
            Logger.log("impossibile inizializzare l'encoder (" + agreed_encoder_name + ")", true);
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile inizializzare l'encoder: " + agreed_encoder_name
            ), null);
            return false;
        }
        Logger.log("inizializzato l'encoder (" + agreed_encoder_name + ") con successo");

        if (!Connection.init(server_connector, server_encoder)) {
            Logger.log("inizializzazione di Connection fallita, chiudo la connessione con il server", true);
            close(true);

            return false;
        }
        Logger.log("una connessione sicura con il server (" + server_name + ") è stata instaurata con successo");

        connected = true;
        log(); //inizia la sequenza per richiedere all'utente di eseguire il login

        ButtonTopBar_panel.update_active_buttons();

        return true;
    }

    //si connette al dns in cui dovrebbe essere registrato il server per trovare il suo indirizzo ip
    private static String retrive_ip(String dns_name, String server_link) {
        DnsInfo info = registered_dns.get(dns_name);
        if (info == null) {
            Logger.log("impossibile contattare il dns: " + dns_name + ", nessun dns è registrato con questo nome", true);
            return null;
        }

        ConnectorWrapper dns_connector = registered_dns_connector.get(info.CONNECTOR);
        if (dns_connector == null) {
            Logger.log("impossibile utilizzare il connector: " + info.CONNECTOR + ", nessuno è registrato con questo nome", true);
            return null;
        }

        String server_ip = dns_connector.dns_handshake(info.IP, server_link);
        dns_connector.close();

        return server_ip;
    }

    /*
     * Controlla se l'encoder specificato nelle ServerInfo è supportato dal server, per evitare confusioni da
     * ora in poi mi riferisco a encoders installati sul server con sencoders e agli encoders sul client cencoders, inoltre
     * per pencoder si intende l'encoder scelto dall'utente e messo nel ServerInfo.
     * o) invia al server il nome del pencoder
     * o) se riceve dal server "ok", il pencoder è supportato e viene utilizzato, altrimenti riceve "ns" (not supported)
     *    assieme a una lista di tutti gli sencoders, controlla se ha installato un encoder che sia compatibile con
     *    uno di questi
     * o) una volta trovata la lista con tutti i cencoder utilizzabili con questo server, se la lista è vuota avvisa
     *    l'utente e ritorna null annullando la connessione, altrimenti chiede all'utente se desidera cambiare pencoder
     */
    private static String encoder_agreement(ConnectorWrapper connector, Cipher[] session_cipher, ServerInfo server_info) {
        try {
            byte[] pencoder_msg = session_cipher[0].doFinal(server_info.ENCODER.getBytes());
            connector.send(pencoder_msg);
        }
        catch (Exception _) {
            Logger.log("impossibile utilizzare il cipher specificato per cifrare il nome dell encoder durante l'encoder agreement", true);
            return null;
        }

        String server_reply;
        try {
             server_reply = new String(connector.read());
        }
        catch (Exception e) {
            Logger.log("impossibile comprendere la risposta al server durante l'encoder agreement", true);
            Logger.log(e.getMessage(), true);
            return null;
        }

        return switch (server_reply) {
            case "ok" -> {
                Logger.log("l'encoder specificato nelle ServerInfo è supportato, utilizzo (" + server_info.ENCODER + ")");
                yield server_info.ENCODER;
            }
            case "ns" -> {
                Logger.log("l'encoder specificato nelle ServerInfo (" + server_info.ENCODER + ") non è supportato dal server");
                yield propose_encoder_change(connector, session_cipher, server_info);
            }
            default -> {
                Logger.log("impossibile completare l'encoder agreement, era atteso \"ns\" o \"ok\" ma è stato ricevuto: " + server_reply, true);
                yield null;
            }
        };
    }

    /*
     * Durante l encoder agreement se il server risponde con "ns" invia anche la lista con tutti gli encoders
     * installati, cerca fra di essi tutti quelli che sono installati anche in questo client e chiede all'utente se
     * vuole cambiare encoder specificato nelle server info
     */
    private static String propose_encoder_change(ConnectorWrapper connector, Cipher[] session_cipher, ServerInfo server_info) {
        byte[] server_encoder_list_byte = connector.read();
        if (server_encoder_list_byte == null) {
            Logger.log("errore nella letture della lista di encoders installati nel server", true);
            return null;
        }
        String[] server_encoder_list = new String(server_encoder_list_byte).split(";");

        Vector<Object> user_response = TempPanel.show(new TempPanel_info(
                TempPanel_info.INPUT_MSG,
                true,
                "impossibile utilizzare l encoder predefinito, vuoi cambiarlo?"
        ), Thread.currentThread());

        if (user_response != null) { //user_response = null quando viene premuto "annulla"
            Logger.log("l'utente ha accettato di modificare l encoder legato a questo server");

            String[] usable_encoders = get_common_encoder(server_encoder_list);

            StringBuilder ue_string = new StringBuilder();
            for (String encoder : usable_encoders) {
                ue_string.append(encoder).append(", ");
            }
            Logger.log("trovati i seguenti encoder utilizzabili per connettersi al server\n\t\t\t\t" + ue_string);

            return change_encoder(connector, usable_encoders, session_cipher, server_info);
        }
        else {
            Logger.log("l'utente non ha accettato di modificare l encoder legato a questo server, chiudo la connessione");
            return null;
        }
    }

    /*
     * Riceve una lista di encoders supportati dal server e ritorna la lista di encoders che sono supportati anche da
     * questo client, perchè un encoder sia considerato come compatibile con uno installato sul server deve essere
     * registrato in registered_encoders con il suo stesso nome
     */
    private static String[] get_common_encoder(String[] server_encoder) {
        Set<String> supported_encoders = registered_encoders.keySet();
        Vector<String> common = new Vector<>();

        for (String encoder : server_encoder) {
            if (supported_encoders.contains(encoder)) {
                common.add(encoder);
            }
        }

        return common.toArray(new String[0]);
    }

    /*
     * durante l encoder agreement se non si può utilizzare l encoder presente nelle ServerInfo e l'utente accetta, si
     * può cambiare automaticamente in un encoder supportato dal server, questo metodo riceve la lista di encoders
     * utilizzabili e richiede all'utente di sceglierne uno da utilizzare anche per le prossime connessioni con il server
     */
    private static String change_encoder(ConnectorWrapper connector, String[] usable_encoders, Cipher[] session_cipher, ServerInfo server_info) {
        Vector<Object> user_response = TempPanel.show(new TempPanel_info(
                TempPanel_info.INPUT_MSG,
                true,
                "scegliere l'encoder da utilizzare:"
        ).set_combo_box(
                new int[] {0},
                usable_encoders
        ), Thread.currentThread());

        if (user_response != null) {
            String selected_encoder = (String) user_response.getFirst();
            Logger.log("l'utente ha selezionato l'encoder: (" + selected_encoder + ") dalla lista");

            byte[] server_reply;
            try {
                connector.send(session_cipher[1].doFinal(selected_encoder.getBytes()));
                server_reply = connector.read();
            }
            catch (Exception e) {
                Logger.log("errore nell'attesa di una risposta dal server, chiudo la connessione", true);
                Logger.log(e.getMessage(), true);

                return null;
            }

            if (server_reply == null) {
                Logger.log("errore durante l'attesa della conferma del cambio di encoder da parte del server, chiudo la connessione", true);
                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "errore nell'attesa di conferma parte del server"
                ), null);

                return null;
            }

            String decoded_server_reply;
            try {
                byte[] decoded_reply = session_cipher[1].doFinal(server_reply);
                decoded_server_reply = new String(decoded_reply);
            }
            catch (Exception e) {
                Logger.log("impossibile decifrare la risposta del server, chiudo la connessione", true);
                Logger.log(e.getMessage(), true);

                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "errore nell'attesa di conferma parte del server"
                ), null);

                return null;
            }

            if (decoded_server_reply.equals("ack")) {
                Logger.log("il server ha accettato il nuovo encoder");

                //cambia l'encoder registrato per questo server
                registered_servers.put(server_info.SERVER_NAME, new ServerInfo(
                        server_info.SERVER_NAME,
                        server_info.SERVER_LINK,
                        server_info.SERVER_IP,
                        server_info.SERVER_PORT,
                        server_info.DNS_NAME,
                        selected_encoder,
                        server_info.CONNECTOR
                ));
                return selected_encoder;
            }
            else {
                Logger.log("il server non ha accettato il nuovo encoder, response: (" + decoded_server_reply + "), chiudo la connessione");
                return null;
            }
        }
        else { //è premuto annulla quando chiede che encoder utilizzare
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "annullato cambio di encoder, connessione terminata"
            ), null);
        }

        return null;
    }

    /*
     * una volta scelto che encoder utilizzare con il server deve condividere un numero di byte random specificato da
     * agreed_encoder_name e inizializzare l'encoder usando questo array
     */
    private static EncodersWrapper init_encoder(ConnectorWrapper connector, Cipher[] session_cipher, String agreed_encoder_name) {
        EncodersWrapper encoder = registered_encoders.get(agreed_encoder_name);
        if (encoder == null) {
            Logger.log("impossibile trovare l encoder (" + agreed_encoder_name + ") per inizializzarlo", true);
            return null;
        }

        //genera i bytes da condividere con il server per inizializzare l'encoder
        byte[] shared_bytes = new byte[encoder.array_size];
        SecureRandom random = new SecureRandom();
        random.nextBytes(shared_bytes);

        //encoder.initialize() ritorna false se ha riscontrato un errore, true se è stato inizializzato con successo
        if (!encoder.initialize(shared_bytes)) {
            Logger.log("errore durante l'inizializzazione dell encoder", true);
            return null;
        }

        //tenta di inviare i byte al server cifrati con la session key
        try {
            shared_bytes = session_cipher[0].doFinal(shared_bytes);
            connector.send(shared_bytes);
        }
        catch (Exception e) {
            Logger.log("impossibile condividere con il server i bytes necessari a inizializzare l encoder", true);
            Logger.log(e.getMessage(), true);
            return null;
        }

        //controlla che il server sia in grado di utilizzare l'encoder
        if (test_encoder(encoder, connector, session_cipher)) {
            Logger.log("encoder inizializzato con successo");
            return encoder;
        }
        else {
            Logger.log("impossibile inizializzare l encoder", true);
            return null;
        }
    }

    /*
     * Una volta inizializzato l encoder e inviati i byte al server per inizializzarlo, si vuole controllare che entrambi
     * siano effettivamente in grado di utilizzare l encoder per cifrare e decifrare messaggi.
     * Il server e il client generano 32bytes random e li inviano cifrati con la session key all'altro che dovrà
     * cifrarli con l encoder e mandarli in dietro.
     * Infine se entrambi hanno ricevuto i propri bytes cifrati con l encoder in modo corretto inviano "ack", altrimenti
     * inviano "abt" cifrando in ogni caso con la session key.
     */
    private static boolean test_encoder(EncodersWrapper encoder, ConnectorWrapper connector, Cipher[] session_cipher) {
        byte[] my_test_bytes = new byte[32];
        new SecureRandom().nextBytes(my_test_bytes);

        //invia i propri bytes cifrati con la session key
        try {
            byte[] encoded_mtb = session_cipher[0].doFinal(my_test_bytes);
            connector.send(encoded_mtb);
        }
        catch (Exception e) {
            Logger.log("impossibile inviare al server i bytes per il test dell encoder", true);
            return false;
        }

        //riceve i bytes del server cifrati con la session key, li decifra e li rinvia al server cifrati usando l encoder
        try {
            byte[] server_test_bytes = connector.read();
            byte[] decoded_stb = session_cipher[1].doFinal(server_test_bytes);
            byte[] encoded_stb = encoder.encode(decoded_stb);
            connector.send(encoded_stb);
        }
        catch (Exception e) {
            Logger.log("impossibile decifrare e cifrare usando l encoder i bytes ricevuti dal server per il test dell encoder", true);
            return false;
        }

        //riceve i bytes dal server cifrati con l encoder
        byte[] received_bytes = connector.read();
        if (received_bytes == null) {
            Logger.log("errore nella ricezione dei bytes per testare l encoder con il server", true);
            return false;
        }

        received_bytes = encoder.decode(received_bytes);
        if (received_bytes == null) {
            Logger.log("impossibile decifrare i bytes ricevuti dal server con l encoder, durante il test dell encoder", true);
            return false;
        }

        if (Arrays.equals(received_bytes, my_test_bytes)) {
            Logger.log("ricevuti i bytes corretti dal server");

            try {
                connector.send(session_cipher[0].doFinal("ack".getBytes()));
            }
            catch (Exception e) {
                Logger.log("impossibile cifrare il messaggio di riuscita del test per inviarlo al server", true);
                Logger.log(e.getMessage(), true);
                return false;
            }

            try {
                byte[] server_state = connector.read();
                if (server_state != null && Arrays.equals(session_cipher[1].doFinal(server_state), "ack".getBytes())) {
                    Logger.log("test dell encoder passato con successo");
                    return true;
                }
                else {
                    Logger.log("il server non ha accettato il test dell encoder, test fallito", true);
                    return false;
                }
            }
            catch (Exception e) {
                Logger.log("errore nell'attesa del messaggio di conferma per il test dell encoder, test fallito", true);
                Logger.log(e.getMessage(), true);
                return false;
            }
        }
        else {
            Logger.log("il server non ha cifrato correttamente i bytes con l encoder, test fallito", true);

            try {
                connector.send(session_cipher[0].doFinal("abt".getBytes()));
            }
            catch (Exception e) {
                Logger.log("impossibile cifrare il messaggio per il fallimento del test al server", true);
                Logger.log(e.getMessage(), true);
            }

            return false;
        }
    }

    /*
     * esegue il login o cerca di registrare un nuovo utente nel server, per il login è necessario nome utente è password,
     * per registrare nuovi utenti il server potrà decidere le informazioni richieste e chiaramente dovrà validarle lui stesso.
     * ci si aspetta che il server ignori ogni altro messaggio finchè non si ha eseguito il login in un utente.
     */
    private static void log() {
        TempPanel.show(new TempPanel_info(
                TempPanel_info.INPUT_MSG,
                true,
                "vuoi eseguire il login o registrare un nuovo utente?"
        ).set_combo_box(new int[] {0}, new String[] {"login", "registrazione"}), login_or_register);
    }

    /*
     * riceve la riposta a "vuoi eseguire il login o registrare un nuovo account", in caso di fail() si scollega dal server.
     * Per eseguire il login richiede al server la lista di informazioni da richiedere all'utente e le formatta in un
     * TempPanel_info, se non lo ha già fatto precedentemente per questo server.
     */
    private static final TempPanel_action login_or_register = new TempPanel_action() {
        @Override
        public void success() {
            if (input.elementAt(0).equals("login")) {
                request_login();
            }
            else if (input.elementAt(0).equals("registrazione")) {
                request_register();
            }
        }

        @Override
        public void annulla() {
            close(true);
        }
    };

    /*
     * se non le ha già richieste invia al server "login" richiedendo la lista di informazioni necessarie per eseguire il
     * login, una volta creato l'oggetto TempPanel_info viene salvato finche non si esegue un login con successo o ci si discollega
     */
    private static void request_login() {
        if (login_requests == null) {
            byte cc = send("login".getBytes(), null);

            byte[] server_return = Connection.wait_for_reply(cc);
            if (server_return == null) {
                Logger.log("impossibile eseguire un login, errore nell'attesa delle informazioni da richiedere all'utente dal server", true);
                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "errore nella comunicazione con il server, chiudo la connessione"
                ), null);

                close(true);
                return;
            }

            String requests_str = new String(server_return);
            Connection.unlock_cc(cc);

            login_requests = build_temppanel_info(requests_str);
        }

        sender_prefix = "login:".getBytes();
        TempPanel.show(login_requests, info_sender);
    }

    /*
     * se non le ha ancora richieste chiede al server di inviare le informazioni necessarie per registrare un nuovo utente,
     * crea il TempPanel_info() richiedendo tutte le informazioni e le invia al TempPanel_action registerer
     */
    private static void request_register() {
        if (register_requests == null) {
            byte cc = send("register".getBytes(), null);

            byte[] server_return = Connection.wait_for_reply(cc);
            if (server_return == null) {
                Logger.log("impossibile registrari, errore nell'attesa delle informazioni da richiedere all'utente dal server", true);
                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "errore nella comunicazione con il server, chiudo la connessione"
                ), null);

                close(true);
                return;
            }
            String requests_str = new String(Connection.wait_for_reply(cc));
            Connection.unlock_cc(cc);

            register_requests = build_temppanel_info(requests_str);
        }

        sender_prefix = "register:".getBytes();
        TempPanel.show(register_requests, info_sender);
    }

    /*
     * Riceve in argomento una stringa che specifica le richieste del server per eseguire il login/registrazione e deve
     * convertirla in un TempPanel_info.
     * La stringa ricevuta dal server dovrà seguire il format: "<request1>;<request2>;..." dove
     * <request> possono essere di vari tipi:
     * 1) "<msg>" viene richiesto di inserire un testo normale
     * 2) "#<msg>" viene richiesto di inserire una password
     * 3) "<msg>:<opt1>,<opt2>,..." viene richiesto di scegliere fra varie opzioni
     */
    private static TempPanel_info build_temppanel_info(String request_spec) {
        String[] request_args = request_spec.split(":");

        //per generare il TempPanel_info ho bisogno di array, non posso utilizzare i vettori senza poi doverli riconvertire
        int password_num = 0, menu_num = 0;
        int[] password_indexes = new int[request_args.length],
              menu_indexes = new int[request_args.length];
        String[] texts = new String[request_args.length];
        Vector<String[]> menu_options_vector = new Vector<>();

        for (int i = 0; i < request_args.length; i++) {
            String request = request_args[i];

            if (request.startsWith("#")) { //richiede una password
                texts[i] = request.substring(1);

                password_indexes[password_num] = i;
                password_num++;
            }
            else if (request.contains(":")) { //richiede di scegliere fra vari elementi
                int sep_index = request.indexOf(':');

                texts[i] = request.substring(0, sep_index);

                menu_options_vector.add(request.substring(sep_index + 1).split(":"));
                menu_indexes[menu_num] = i;
                menu_num++;
            }
            else { //richiesta di testo normale
                texts[i] = request;
            }
        }

        String[][] menu_options = menu_options_vector.toArray(new String[0][0]);
        password_indexes = Arrays.copyOf(password_indexes, password_num);
        menu_indexes = Arrays.copyOf(menu_indexes, menu_num);

        return new TempPanel_info(
                TempPanel_info.INPUT_MSG,
                true,
                texts
        ).set_psw_indices(password_indexes).set_combo_box(menu_indexes, menu_options);
    }

    /*
     * Una volta inserite le informazioni per eseguire il login o la registrazione dovrà inviare al server tutte le informazioni
     * per la validazione, in caso di login/registrazione avvenuta con successo si riceve "log" altrimenti "fail:<msg>"
     * e si torna al punto 0 chiedendo se si vuole eseguire il login o registrare un nuovo account.
     * Per distinguere fra login e registrazione prima di utilizzare info_sender è necessario specificare il prefisso da
     * utilizzare in sender_prefix
     */
    private static final TempPanel_action info_sender = new TempPanel_action() {
        @Override
        public void success() {
            //per unire tutte le informazioni in un unico vettore byte[] calcola prima la sua dimensione
            int msg_size = 0;
            for (Object obj : input) {
                //in input sono tutti oggetti String o char[]
                if (obj instanceof String str) {
                    msg_size += str.length();
                }
                else if (obj instanceof char[] chrs) {
                    msg_size += chrs.length;
                }
            }

            int filled_to = sender_prefix.length;
            byte[] msg = new byte[msg_size + input.size() + sender_prefix.length];
            System.arraycopy(sender_prefix, 0, msg, 0, sender_prefix.length);

            for (Object obj : input) {
                if (obj instanceof String str) {
                    System.arraycopy(str.getBytes(), 0, msg, filled_to, str.length());

                    filled_to += str.length() + 1;
                    msg[filled_to - 1] = ';';
                }
                else if (obj instanceof char[] chrs) {
                    for (int i = 0; i < chrs.length; i++) {
                        msg[filled_to + i] = (byte) chrs[i];
                    }

                    filled_to += chrs.length + 1;
                    msg[filled_to - 1] = ';';
                }
            }

            msg = Arrays.copyOfRange(msg, 0, msg.length - 1); //rimuove l'ultimo ';'
            send(msg, log_result);
        }

        @Override
        public void annulla() {
            log();
        }
    };

    /*
     * Inviati tutti i dati per eseguire il login o registrare un nuovo utente ora riceve la risposta del server, se il
     * login è stato effettuato con successo "log:<id>" altrimenti "fail:<msg>", dove <msg> può anche essere null. In
     * ogni caso questa conversazione è finita quindi libera il cc
     */
    private static final OnArrival log_result = new OnArrival() {
        @Override
        public void on_arrival(byte conv_code, byte[] msg) {
            Connection.unlock_cc(conv_code);
            String msg_str = new String(msg);

            if (msg_str.startsWith("log")) { //login con successo
                client_id = msg_str.substring(4); //taglia "log:"

                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "login effettuato con successo in: " + client_id
                ), null);
                Logger.log("login effettuato con successo all'utente: " + client_id);

                //dimentica i dati necessari ad eseguire il login o registrare utenti con questo server
                register_requests = null;
                login_requests = null;
            }
            else if (msg_str.startsWith("fail")) { //login fallito e il server ha dato un errore
                String server_msg = msg_str.substring(5); //taglia "fail:"
                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "errore nel login al server: " + server_msg
                ), null);
                Logger.log("login fallito, messaggio dal server: " + server_msg);

                log();
            }
            else { //login fallito senza errore dal server
                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "errore nel login al server"
                ), null);
                Logger.log("login fallito, nessun messaggio dal server");

                log();
            }
        }
    };

    /**
     * invia un messaggio a cui non è attesa nessuna risposta dal server, per queste conversazioni viene sempre utilizzato
     * il cc = 0x00 che chiaramente è l'unico cc non bloccabile e a cui non è possibile registrare OnArrival o Thread in attesa
     */
    public static void send(byte[] msg) {
        Connection.send(msg, (byte) 0x00, null);
    }

    /**
     * Invia un messaggio al server iniziando una nuova conversazione e aspettandosi una continuazione registrando un
     * OnArrival da chiamare una volta ricevuta una risposta dal server, il cc viene bloccato.
     * Il cc viene generato casualmente fra quelli non bloccati e viene ritornato dalla funzione.
     * Se action è null non viene registrata in pending_action e si presuppone si voglia poi registrare il current Thread
     * per attendere la risposta del server.
     */
    public static byte send(byte[] msg, OnArrival action) {
        //gen_cc() blocca i cc appena creati
        byte cc = Connection.gen_cc();
        if (cc == 0x00) { //se ha fallito a generare un cc casuale gen_cc() ritorna 0x00
            Logger.log("impossibile iniziare una nuova conversazione con il server inviando: " + new String(msg) + ", troppe conversazioni attive", true);
            return 0x00;
        }

        Connection.send(msg, cc, action);

        return cc;
    }

    /**
     * invia un messaggio specificando cc, è inteso da utilizzzare per rispondere a un messaggio del server a cui non è
     * più attesa una risposta, non viene bloccato il cc e non viene registrato nessun OnArrival o Thread in attesa
     */
    public static void send(byte[] msg, byte cc) {
        Connection.send(msg, cc, null);
    }

    /**
     * invia un messaggio al server specificando che cc utilizzare e in che modo attendere una risposta da esso:
     * cc: cc da utilizzare per il messaggio, deve essere sempre specificato e se null da errore
     * notifier: oggetto da utilizzare per specificare come reagire una volta ricevuta una risposta dal sever, può essere
     * un OnArrival, e in tal caso viene registrato in pending_action, e se non lo è già il cc viene bloccato, se invece
     * è null non è attesa nessuna risposta da parte del server e il cc non viene bloccato.
     */
    public static void send(byte[] msg, byte cc, OnArrival action) {
        Connection.send(msg, cc, action);
    }

    /**
     * se cc è bloccato registra il current thread a waiting_threads e attende che venga notificato quando viene ricevuta
     * una risposta dal server.
     * Viene ritornato null se cc non è bloccato o se fallisce a registrare il current thread come notifier, altrimenti
     * il messaggio ricevuto in risposta dal server.
     */
    public static byte[] wait_for_reply(byte cc) {
        return Connection.wait_for_reply(cc);
    }

    ///se il cc specificato è bloccato lo libera
    public static void unlock_cc(byte cc) {
        Connection.unlock_cc(cc);
    }

    //TODO gestisci le classi, le classi non sono ancora state create e verranno aggiunte una volta finito il server
    public static void create_class() {
    }

    public static void join_class(String class_name) {
    }

    public static void exit_class() {
    }

    /**
     * chiude la connessione con il server, se è questo client che vuole scollegarsi dovrà avvisare il server, se invece
     * il server si è disconnesso una notifica produrrebbe errore.
     */
    public static void close(boolean notify_server) {
        if (!connected) {
            Logger.log("non si è connessi a nessun server, impossibile disconnettersi", true);
            return;
        }

        if (notify_server) {
            send("EOC".getBytes()); //End Of Connection
        }
        Connection.close();

        //dimentica i dati necessari a eseguire il login o registrare utenti con questo server
        register_requests = null;
        login_requests = null;
    }

    // metodi per interagire con le mappe registered_encoders, registered_connectors, registered_dns, registered_servers

    public static void add_connector(String name, ConnectorWrapper wrapper) {
        if (wrapper.type == ConnectorWrapper.DNS_CONNECTOR) {
            registered_dns_connector.put(name, wrapper);
        }
        else {
            registered_server_connector.put(name, wrapper);
        }

        Logger.log("aggiunto il connector: " + name + " alla lista dei disponibili");
    }

    public static void add_encoder(String name, EncodersWrapper wrapper) {
        if (registered_encoders.putIfAbsent(name, wrapper) == null) {
            Logger.log("aggiunto l'encoder: " + name + " alla lista dei disponibili");
        }
        else {
            Logger.log("impossibile registrare un altro encoder: " + name + ", se ne è già registrato uno con questo nome", true);
        }
    }

    public static void add_dns(String name, DnsInfo info) {
        registered_dns.put(name, info);
        Logger.log("aggiunto il dns: " + name + " alla lista dei disponibili");
    }

    public static void add_server(String name, ServerInfo info) {
        registered_servers.put(name, info);
        Logger.log("aggiunto il server: " + name + " alla lista dei disponibili");
    }

    public static Set<String> get_server_connectors_list() {
        return registered_server_connector.keySet();
    }

    public static Set<String> get_dns_connectors_list() {
        return registered_dns_connector.keySet();
    }

    public static Set<String> get_encoder_list() {
        return registered_encoders.keySet();
    }

    public static EncodersWrapper get_encoder(String name) {
        return registered_encoders.get(name);
    }

    public static Set<String> get_dns_list() {
        return registered_dns.keySet();
    }

    public static DnsInfo get_dns_info(String name) {
        return registered_dns.get(name);
    }

    public static void reset_dns_list() {
        registered_dns.clear();
    }

    /**
     * Aggiorna i dati relativi a uno dei dns registrati o ne crea di nuovi.
     * Per prima cosa controlla se deve eliminare il dns, in tal caso new_data = null, altrimenti aggiorna i dati
     * memorizzati nella mappa registered_dns. Per i dns non è necessario controllare che l'oggetto DnsInfo ricevuto
     * sia valido poiché non è possibile assegnare null in nessun campo
     */
    public static void update_dns(String dns_name, DnsInfo new_data) {
        Logger.log("ServerInterface: inizio modifica dei dati del dns (" + dns_name + ") in:\n\t\t\t\t - " + ((new_data == null)? "null" : new_data.toString()));
        if (new_data == null) { //deve eliminare il dns
            Vector<Object> result = TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    true,
                    "vuoi eliminare il dns: " + dns_name + "?"
            ), Thread.currentThread());

            if (result != null) { //l'utente ha confermato di voler eliminare il dns
                //se trova un server che utilizza questo dns abortisce l'operazione e mostra un messaggio di errore
                for (ServerInfo server_info : registered_servers.values()) {
                    if (server_info.DNS_NAME.equals(dns_name)) {
                        Logger.log("impossibile eliminare il dns (" + dns_name + ") essendo utilizzato dal server (" + server_info.SERVER_NAME + ")");
                        TempPanel.show(new TempPanel_info(
                                TempPanel_info.SINGLE_MSG,
                                false,
                                "impossibile eliminare il dns: " + dns_name + " è utilizzato da: " + server_info.SERVER_NAME
                        ), null);

                        return;
                    }
                }

                //se arriva qui non ha trovato nessun server che utilizza questo dns e lo elimina
                registered_dns.remove(dns_name);
                Logger.log("eliminato il dns (" + dns_name + ") con successo");
            }
            else {
                Logger.log("eliminazione del dns (" + dns_name + ") bloccata dall'utente");
            }
        }
        else {
            update_dns_info(dns_name, new_data);
        }
    }

    /*
     * Se deve modificare il nome del dns o aggiungere un nuovo dns dovrà modificare anche il key set in
     * registered_dns, eliminando vecchi collegamenti e creandone di nuovi, altrimenti dovrà solo aggiornare
     * il valore legato a dns_name.
     * Controlla anche che rinominando un dns non si creino doppioni.
     */
    private static void update_dns_info(String dns_name, DnsInfo new_data) {
        if (new_data.NAME.equals(dns_name) && registered_dns.containsKey(dns_name)) {
            registered_dns.put(dns_name, new_data);

            Logger.log("modificati i dati del dns (" + dns_name + ") con successo");
        }
        else if (registered_dns.containsKey(dns_name) && !registered_dns.containsKey(new_data.NAME)) {
            registered_dns.remove(dns_name);
            registered_dns.put(new_data.NAME, new_data);

            //cambia il nome del dns legato a tutti i server che utilizzavano questo
            for (ServerInfo server_info : registered_servers.values()) {
                if (server_info.serverDNS().equals(dns_name)) {
                    registered_servers.put(
                            server_info.SERVER_NAME,
                            new ServerInfo(
                                    server_info.SERVER_NAME,
                                    server_info.SERVER_LINK,
                                    server_info.SERVER_IP,
                                    server_info.SERVER_PORT,
                                    new_data.NAME,
                                    server_info.ENCODER,
                                    server_info.CONNECTOR
                            )
                    );
                }
            }

            Logger.log("modificati i dati del dns (" + dns_name + ") con successo");
        }
        else if (!registered_dns.containsKey(new_data.NAME)){
            registered_dns.put(new_data.NAME, new_data);

            Logger.log("aggiunto il dns (" + new_data.NAME + ") con successo");
        }
        else {
            Logger.log("impossibile modificare il dns (" + dns_name + "), esiste già un dns con questo nome", true);
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile rinominare il dns (" + dns_name + ") in (" + new_data.NAME +")"
            ), null);
        }
    }

    public static Set<String> get_server_list() {
        return registered_servers.keySet();
    }

    public static ServerInfo get_server_info(String server_name) { return registered_servers.get(server_name); }

    /**
     * Aggiorna i dati relativi a uno dei server registrati.
     * Per prima cosa controlla se deve eliminare il server, in tal caso new_data = null, altrimenti testa che i dati
     * ricevuti siano sufficienti a definire un server correttamente, guarda is_valid_server_data(), e se vanno bene
     * aggiorna i dati memorizzati nella mappa
     */
    public static void update_server(String server_name, ServerInfo new_data) {
        Logger.log("ServerInterface: inizio modifica dei dati del server (" + server_name + ") in:\n\t\t\t\t - " + ((new_data == null)? "null" : new_data.toString()));
        if (new_data == null) { //deve eliminare il server
            Vector<Object> result = TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    true,
                    "vuoi eliminare il server: " + server_name + "?"
            ), Thread.currentThread());

            if (result != null) { //ritorna null se viene premuto annulla
                registered_servers.remove(server_name);

                Logger.log("eliminato il server (" + server_name + ") con successo");
            }
            else {
                Logger.log("eliminazione del server (" + server_name + ") bloccata dall'utente");
            }
        }
        //se deve aggiornare i dati di un server controlla che quelli ricevuti siano validi
        else if (is_valid_server_info(new_data)) {
            update_server_info(server_name, new_data);
        }
        //si voleva aggiornare i dati di un server ma quelli ricevuti non sono dei dati validi
        else {
            Logger.log("impossibile aggiornare i dati del server (" + server_name + "), non validi", true);
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile aggiornare il server: " + server_name
            ), null);
        }
    }

    /*
     * Se deve modificare il nome del server o aggiungere un nuovo server dovrà modificare anche il key set in
     * registered_server, eliminando vecchi collegamenti e creandone di nuovi, altrimenti dovrà solo aggiornare
     * il valore legato a server_name.
     * Controlla anche che rinominando un server non si creino doppioni, ma da per scontato che new_data sia valido.
     */
    private static void update_server_info(String server_name, ServerInfo new_data) {
        if (new_data.SERVER_NAME.equals(server_name) && registered_servers.containsKey(server_name)) {
            registered_servers.put(server_name, new_data);

            Logger.log("modificati i dati del server (" + server_name + ") con successo");
        }
        else if (registered_servers.containsKey(server_name) && !registered_servers.containsKey(new_data.SERVER_NAME)) {
            registered_servers.remove(server_name);
            registered_servers.put(new_data.SERVER_NAME, new_data);

            Logger.log("modificati i dati del server (" + server_name + ") con successo");
        }
        else if (!registered_servers.containsKey(new_data.SERVER_NAME)){
            registered_servers.put(new_data.SERVER_NAME, new_data);

            Logger.log("aggiunto il server (" + server_name + ") con successo");
        }
        else {
            Logger.log("impossibile modificare il server (" + server_name + "), esiste già un server con questo nome", true);
            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile rinominare il server (" + server_name + ") in (" + new_data.SERVER_NAME +")"
            ), null);
        }
    }

    /*
     * controlla che l'oggetto ricevuto contenga abbastanza informazioni per definire un server, l'unico caso in cui
     * questo test fallisce se non si specificano contemporaneamente (link e ip) o (ip e dns) o se non si specifica
     * una porta.
     */
    private static boolean is_valid_server_info(ServerInfo data) {
        if (data.SERVER_LINK == null && data.SERVER_IP == null) {
            return false;
        }

        if (data.SERVER_IP == null && data.DNS_NAME == null) {
            return false;
        }

        return data.SERVER_PORT != 0;
    }

    public static void remove_server(String name) {
        registered_servers.remove(name);
    }

    public static void reset_server_list() {
        registered_servers.clear();
    }

    public static void rename_server(String old_name, String new_name) {
        ServerInfo server_info = registered_servers.get(old_name);

        if (server_info == null) {
            Logger.log("impossibile rinominare il server: " + old_name + " in: " + new_name + ", server non esistente",true);
            return;
        }

        registered_servers.remove(old_name);
        registered_servers.put(new_name, server_info);
    }

    public static String get_id() {
        return client_id;
    }

    public static boolean is_connected() {
        return connected;
    }

    public static boolean is_in_class() {
        return !class_id.isEmpty();
    }
}

/*
 * standard per cifrare le conversazioni, richiede un byte[] da 40 ed è compatibile solo con il metodo standard del server
 * init: dei 40 byte utilizza i primi 32 come chiave AES, gli ultimi 8 li combina in un long da utilizzare come seed per
 *       gli iv durante le conversazioni. Ogni volta che cifra o decifra un messaggio genera un iv random nuovo, gli iv
 *       vengono generati usando lo stesso seed con il server quindi rimangono sempre sincronizzati
 * encoder e decoder semplicemente cifrano e cambiano l'iv ogni volta
 */
@EncoderDefinition(name = "standard encoder", array_size = 40)
abstract class StdProtocol {
    private static Cipher encoder;
    private static Cipher decoder;

    private static final Random random = new Random();
    private static SecretKey session_key;

    @EncoderDefinition.Initializer
    public static void init(byte[] check_code) {
        try {
            encoder = Cipher.getInstance("AES/CBC/PKCS5Padding");
            decoder = Cipher.getInstance("AES/CBC/PKCS5Padding");
        }
        catch (Exception _) {
            throw new RuntimeException("impossibile definire i cipher");
        }

        byte[] aes_key = Arrays.copyOf(check_code, 32);
        long random_seed = (long) check_code[39] | ((long) check_code[38]) << 8 | ((long) check_code[37]) << 16 | ((long) check_code[36]) << 24 | ((long) check_code[35]) << 32 | ((long) check_code[34]) << 40 | ((long) check_code[33]) << 48 | ((long) check_code[32]) << 56;

        random.setSeed(random_seed);

        session_key = new SecretKeySpec(aes_key, "AES");
        IvParameterSpec iv = next_iv();

        try {
            encoder.init(Cipher.ENCRYPT_MODE, session_key, iv);
            decoder.init(Cipher.DECRYPT_MODE, session_key, iv);
        } catch (Exception _) {
            throw new RuntimeException("impossibile inizializzare i due encoder");
        }
    }

    @EncoderDefinition.Encoder
    public static byte[] encode(byte[] msg) {
        try {
            byte[] encoded_msg = encoder.doFinal(msg); //decodifica il messaggio
            regen_iv(); //rigenera gli iv

            return encoded_msg;
        } catch (Exception _) {
            throw new RuntimeException("impossibile cifrare: " + new String(msg));
        }
    }

    @EncoderDefinition.Decoder
    public static byte[] decode(byte[] msg) {
        try {
            byte[] plain_msg = decoder.doFinal(msg);
            regen_iv();

            return plain_msg;
        } catch (Exception _) {
            throw new RuntimeException("impossibile decifrare: " + new String(msg));
        }
    }

    private static void regen_iv() {
        try {
            IvParameterSpec iv = next_iv();
            encoder.init(Cipher.ENCRYPT_MODE, session_key, iv);
            decoder.init(Cipher.DECRYPT_MODE, session_key, iv);
        }
        catch (Exception _) {} //impossibile rientrare
    }

    private static IvParameterSpec next_iv() { //genera un iv casuale
        byte[] iv_bytes = new byte[encoder.getBlockSize()];
        random.nextBytes(iv_bytes);

        return new IvParameterSpec(iv_bytes);
    }
}

@Connector(name = "standard connector", type = ConnectorWrapper.SERVER_CONNECTOR)
abstract class STDConnector {
    private static Socket sck;
    private static BufferedInputStream iStream;
    private static BufferedOutputStream oStream;

    private static Cipher session_encoder;
    private static Cipher session_decoder;

    private static SecureRandom random;

    @Connector.handshake
    public static Cipher[] handshake(String server_ip, ServerInfo info) {
        //prima di iniziare una nuova connessione bisogna chiudere quella in corso
        if (sck != null && !sck.isClosed()) {
            Logger.log("impossibile iniziare una nuova connessione con il server: " + server_ip + " mentre si è connessi ad un altro server", true);

            return null;
        }

        try {
            sck = new Socket(server_ip, info.SERVER_PORT);
            iStream = new BufferedInputStream(sck.getInputStream());
            oStream = new BufferedOutputStream(sck.getOutputStream());
        }
        catch (IOException e) {
            Logger.log("impossibile connettersi con il server, errore nell'apertura del socket:", true);
            Logger.log(e.getMessage(), true);
        }
        Logger.log("connessione con il server instaurata con successo");

        //controlla che il certificato del server sia corretto, se lo è ritorna la chiave pubblica del server, altrimenti null
        byte[] server_pkey = check_server_certificate(info);
        if (server_pkey == null) {
            Logger.log("impossibile assicurare una connessione sicura con il server, è fallito il controllo del certificato", true);

            return null;
        }
        Logger.log("controllo del certificato superato con successo");

        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey key = kf.generatePublic(new X509EncodedKeySpec(server_pkey));

            Cipher pkey_cipher = Cipher.getInstance("RSA");
            pkey_cipher.init(Cipher.ENCRYPT_MODE, key);

            Logger.log("generato il cipher con la chiave pubblica del server");

            return session_cipher(pkey_cipher);
        }
        catch (Exception e) {
            Logger.log("impossibile cifrare messaggi con la chiave pubblica del server contenuta nel suo certificato", true);
            Logger.log(e.getMessage(), true);

            return null;
        }
    }

    /*
     * Appena collegati a un server si riceve una stringa con le informazioni del server (nome, ip, link, base64{pkey}, mail)
     * e il certificato rilasciato dal dns a cui si dovrebbe essere registrato, il certificato non è altro che l'hash SHA3-256
     * della stringa "nome;ip;link;base64{pkey};mail" cifrata con la chiave privata del dns. Per controllare la validità di
     * un server si calcola l'hash della stringa e si decifra il certificato, se i due coincidono ritorna la chiave pubblica
     * del server trovata nelle info.
     */
    private static byte[] check_server_certificate(ServerInfo server_info) {
        byte[] server_info_bytes = read();
        byte[] server_certificate = read();

        if (server_info_bytes == null || server_certificate == null) {
            Logger.log("errore durante l'attesa delle informazioni del server o del suo certificato, impossible assicurare una connessione sicura", true);

            return null;
        }

        Cipher dns_pkey_decoder = get_dns_cipher(server_info.DNS_NAME);
        byte[] decoded_certificate;
        try {
            decoded_certificate = dns_pkey_decoder.doFinal(server_certificate);
        }
        catch (Exception e) {
            Logger.log("impossiblie decifrare il certificato ricevuto dal server con la chiave pubblica del dns", true);
            Logger.log(e.getMessage(), true);

            return null;
        }

        MessageDigest sha3_digest;
        try {
             sha3_digest = MessageDigest.getInstance("SHA3-256");
        }
        catch (Exception _) {
            Logger.log("impossibile inizializzare il message digest con sha3-256 nello standard connector", true);
            return null;
        }

        byte[] calculated_hash = sha3_digest.digest(server_info_bytes);

        if (Arrays.equals(calculated_hash, decoded_certificate)) {
            Pattern info_patter = Pattern.compile("([^;]+);([^;]+);([^;]+);([^;]+);([^;]+)");
            Matcher info_matcher = info_patter.matcher(new String(server_info_bytes));

            if (info_matcher.matches() && info_matcher.group(3).equals(server_info.SERVER_LINK)) {
                String server_pkey_base64 = info_matcher.group(4);
                return Base64.getDecoder().decode(server_pkey_base64);
            }
            else {
                Logger.log("il certificato coincide con le informazioni ricevute ma queste non rispettano il pattern", true);
                return null;
            }
        }
        else {
            Logger.log("il certificato ricevuto dal server non coincide con le informazioni: " + new String(server_info_bytes), true);

            return null;
        }
    }

    /*
     * ritorna un cipher che decifra utilizzando la chiave pubblica del dns specificato
     */
    private static Cipher get_dns_cipher(String dns_name) {
        try {
            DnsInfo dns_info = ServerInterface.get_dns_info(dns_name);
            byte[] dns_pkey_bytes = dns_info.get_pkey();

            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey dns_pkey = kf.generatePublic(new X509EncodedKeySpec(dns_pkey_bytes));

            Cipher dns_pkey_decoder = Cipher.getInstance("RSA");
            dns_pkey_decoder.init(Cipher.DECRYPT_MODE, dns_pkey);

            return dns_pkey_decoder;
        }
        catch (Exception e) {
            Logger.log("impossibile inizializzare il decoder con la chiave pubblica del dns: " + dns_name, true);
            Logger.log(e.getMessage(), true);

            return null;
        }
    }

    /*
     * Concorda con il server una chiave per la sessione e invia 2 byte cifrati con la nuova chiave, il server dovrà
     * mandarli in dietro cifrati scambiati di posto, questo serve per assicurarsi che il server conosca la chiave privata
     * e non so se davvero sia utile, ma penso sia sensato. Ritorna il cipher encoder per questa sessione
     */
    private static Cipher[] session_cipher(Cipher server_pkey_encoder) {
        try {
            session_encoder = Cipher.getInstance("AES/CBC/PKCS5Padding");
            session_decoder = Cipher.getInstance("AES/CBC/PKCS5Padding");

            if (random == null) {
                random = new SecureRandom();
                random.setSeed(System.currentTimeMillis());
            }

            byte[] session_key = get_aes_key(server_pkey_encoder, random);
            byte[] session_iv = get_iv(server_pkey_encoder, random, session_decoder.getBlockSize());

            if (session_iv == null || session_key == null) {
                Logger.log("impossibile concordare con il server una chiave di sessione", true);

                return null;
            }
            Logger.log("inviata al server la chiave per la sessione");

            SecretKey key = new SecretKeySpec(session_key, "AES");
            IvParameterSpec iv = new IvParameterSpec(session_iv);

            session_encoder.init(Cipher.ENCRYPT_MODE, key, iv);
            session_decoder.init(Cipher.DECRYPT_MODE, key, iv);

            byte[] test_bytes = new byte[2];
            random.nextBytes(test_bytes);

            send(session_encoder.doFinal(test_bytes));
            Logger.log("inviati i test bytes al server");
            byte[] received_test_bytes = read();
            if (received_test_bytes == null) {
                Logger.log("errore nell'attesa dei test bytes dal server", true);
                return null;
            }

            byte[] decoded_test_bytes = session_decoder.doFinal(received_test_bytes);
            if (decoded_test_bytes.length != 2 || decoded_test_bytes[0] != test_bytes[1] || decoded_test_bytes[1] != test_bytes[0]) {
                Logger.log("il server non ha decifrato correttamente i test bytes", true);
                return null;
            }
            Logger.log("test bytes ricevuti correttamente, session key stabilita");

            return new Cipher[] {session_encoder, session_decoder};
        }
        catch (Exception e) {
            Logger.log("errore durante la definizione della session key con il server, impossibile instaurare una connessione sicura", true);
            Logger.log(e.getMessage(), true);

            return null;
        }
    }

    //genera una chiave per la sessione e la invia al server
    private static byte[] get_aes_key(Cipher server_pkey_encoder, SecureRandom random) {
        byte[] random_key_bytes = new byte[32];
        random.nextBytes(random_key_bytes);

        try {
            byte[] encoded_key = server_pkey_encoder.doFinal(random_key_bytes);
            send(encoded_key);
        }
        catch (Exception _) {
            Logger.log("impossibile cifrare la chiave di sessione con la chiave pubblica del server", true);
            return null;
        }

        return random_key_bytes;
    }

    private static byte[] get_iv(Cipher server_pkey_encoder, SecureRandom random, int blocksize) {
        byte[] iv_bytes = new byte[blocksize];
        random.nextBytes(iv_bytes);

        try {
            byte[] encoded_iv = server_pkey_encoder.doFinal(iv_bytes);
            send(encoded_iv);
        }
        catch (Exception _) {
            Logger.log("impossibile cifrare l'iv con la chiave pubblica del server", true);
            return null;
        }

        return iv_bytes;
    }

    @Connector.sender
    public static void send(byte[] msg) {
        try {
            oStream.write(new byte[]{(byte) (msg.length & 0xff), (byte) ((msg.length >> 8) & 0xff)}); //invia 2 byte che indicano la dimensione del messaggio
            oStream.write(msg);

            oStream.flush();
        }
        catch (Exception e) {
            Logger.log("errore durante l'invio al server di un messaggio", true);
            Logger.log(e.getMessage());

            if (Connection.DEBUGGING) {
                Logger.log("contenuto del messaggio:");

                //scrive tutti i byte del messaggio in una tabella con 4 per riga
                int line_size = 4;
                for (int i = 0; i < msg.length / 4 + 1; i++) {
                    try {
                        for (int j = 0; j < 4; j++) {
                            Logger.log(String.format("%8s", Integer.toBinaryString(msg[4 * i + j] & 0xff)).replace(' ', '0'), false, ' ', false);
                        }
                    }
                    catch (Exception _) { //si trova nell'ultimo blocco da 4 e non ci sono 4 byte
                        line_size = msg.length % 4;
                        for (int j = 0 ; j < 4 - line_size; j++) {
                            Logger.log("--------", false, ' ', false);
                        }
                    }

                    Logger.log(" | ", false, ' ', false);

                    Logger.log(new String(Arrays.copyOfRange(msg, 4*i, 4*i + line_size)), false, '\n', false);
                }
            }
        }
    }

    @Connector.reader
    public static byte[] read() {
        try {
            byte[] msg_size_byte = iStream.readNBytes(2);
            int msg_size = (msg_size_byte[0] & 0Xff) | (msg_size_byte[1] << 8);

            return iStream.readNBytes(msg_size);
        }
        catch (Exception e) {
            Logger.log("errore durate l'attesa di un messaggio dal server", true);
            Logger.log(e.getMessage(), true);

            return null;
        }
    }

    @Connector.closer
    public static void close() {
        if (sck != null) {
            try {
                sck.close();
            }
            catch (IOException _) {}
        }
    }
}

@Connector(name = "standard connector dns", type = ConnectorWrapper.DNS_CONNECTOR)
abstract class STDConnector_DNS {
    @Connector.handshake
    public static String handshake(String dns_name, String server_link) {
        DnsInfo info = ServerInterface.get_dns_info(dns_name);

        Socket dns_socket;
        try {
            dns_socket = new Socket(info.IP, info.PORT);
        }
        catch (Exception e) {
            Logger.log("impossibile aprire la connessione con il dns: " + dns_name, true);
            return null;
        }

        //crea dei cipher per cifrare e decifrare utilizzando la chiave pubblica del dns
        Cipher decoder, encoder;
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey key = kf.generatePublic(new X509EncodedKeySpec(info.get_pkey())); //sep_key_list[i+1] conitiene la chiave pubblica in base64

            decoder = Cipher.getInstance("RSA");
            encoder = Cipher.getInstance("RSA");
            decoder.init(Cipher.DECRYPT_MODE, key);
            encoder.init(Cipher.ENCRYPT_MODE, key);
        }
        catch (Exception e) {
            Logger.log("impossibile inizializzare i cipher con la chiave pubblica del dns: " + info.NAME, true);
            Logger.log(e.getMessage(), true);

            try {
                dns_socket.close();
            }
            catch (IOException _) {}

            return null;
        }

        byte[] msg;
        try {
            msg = encoder.doFinal(server_link.getBytes());
        } catch (Exception e) {
            Logger.log("impossibile cifrare il link: " + server_link + " con la chiave pubblica del dns: " + info.NAME, true);
            Logger.log(e.getMessage(), true);

            try {
                dns_socket.close();
            }
            catch (IOException _) {}

            return null;
        }

        try {
            dns_socket.getOutputStream().write(msg);
            dns_socket.getOutputStream().flush();

            try {
                dns_socket.close();
            }
            catch (IOException _) {}

            return new String(dns_socket.getInputStream().readAllBytes());
        } catch (Exception e) {
            Logger.log("errore durante la comunicazione con il dns: " + info.NAME, true);
            Logger.log(e.getMessage(), true);

            try {
                dns_socket.close();
            }
            catch (IOException _) {}

            return null;
        }
    }
}