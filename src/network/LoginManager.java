package network;

import files.Logger;
import files.Pair;

import java.util.Arrays;

/**
 * Specifica le informazioni richieste, e come verificarle, per login / registrazione di utenti nel server.
 * Esegue il protocollo per eseguire login / registrazioni a utenti nel server
 */
public abstract class LoginManager {
    /**
     * Stringa con le informazioni che ogni client dovrà inviare per eseguire un login nel server, dovrà essere
     * formattata come: {@code request1;request2;...} e ogni {@code request} potrà essere:
     * <ul>
     *     <li>
     *         "{@code txt}" viene richiesto un testo in chiaro con etichetta {@code txt}
     *     </li>
     *     <li>
     *         "{@code #txt}" viene richiesto un testo nascosto (password) con etichetta {@code txt}
     *     </li>
     *     <li>
     *         "{@code txt:opt1,opt2,...}" viene richiesto di scegliere fra {@code opt1}, {@code opt2}, {@code ...} con
     *         etichetta {@code txt}
     *     </li>
     * </ul>
     * Ex: "{@code nome utente;#password;admin:true,false}" formatta una richiesta chiedendo nome utente, password
     * e se l'utente è admin o meno
     */
    private final byte[] login_request;

    /**
     * Stringa con le informazioni che ogni client dovrà inviare per registrare un nuovo utente nel server, per la
     * formattazione della richiesta guarda login_request
     */
    private final byte[] register_request;

    /// Nome con cui ci si riferisce al login manager
    private final String name;

    /**
     * Specifica come controllare la validità delle informazioni ricevute per login. Nel caso in cui il login sia stato
     * eseguito con successo dovrà ritornare una coppia {@code (true, uname)} dove uname è il nome utente, altrimenti
     * {@code (false, emessage)} dove emessage è un messaggio di errore da mostrare al client.
     * @param data dati per il login
     * @return coppia {@code (true, uname)} se il login è avvenuto con successo, altrimenti {@code (false, emessage)}
     */
    public abstract Pair<Boolean, String> login_tester(byte[] data);

    /**
     * Specifica come controllare la validità delle informazioni ricevute per la registrazione. Nel caso in cui sia
     * stata eseguita con successo dovrà ritornare una coppia {@code (psw, uname)} dove:
     * <ul>
     *     <li>
     *         {@code uname : String} è il nome utente con cui si è registrato l'utente, o un messaggio di errore nel
     *         caso non sia andata a buon fine
     *     </li>
     *     <li>
     *         {@code psw : byte[]} è un codice segreto che verrà memorizzato e utilizzato per verificare successivi
     *         login a questo utente. O {@code null} se l'operazione non è andata a buon fine
     *     </li>
     * </ul>
     *
     * @param data dati per la registrazione
     * @return coppia {@code (psw, uname)} se è avvenuta con successo, altrimenti {@code (null, emessage)}
     */
    public abstract Pair<byte[], String> register_tester(byte[] data);

    public LoginManager(String manager_name, String login_request, String register_request) {
        this.login_request = login_request.getBytes();
        this.register_request = register_request.getBytes();
        this.name = manager_name;
    }

    /**
     * Ritorna i bytes della stringa che codifica le richieste da inviare a un client per effettuare il login
     * @return Bytes che codificano la richiesta per il login
     */
    public byte[] get_login_request() {
        return login_request.clone();
    }

    /**
     * Ritorna i bytes della stringa che codifica le richieste da inviare a un client per effettuare la registrazione
     * @return Bytes che codificano la richiesta per la registrazione
     */
    public byte[] get_register_request() {
        return register_request.clone();
    }

    /**
     * Attende che il client esegua il login o si registri in un utente, per specificare a che cc inviare le richieste
     * di login gli invia "{@code log_here}" e attende a quel cc le richieste.
     * <ul>
     *     <li>
     *         Se la richiesta inizia con "{@code login}" sarà una richiesta di login, formattata: "{@code login:dati}",
     *         invoca il login_tester con i dati ricevuti
     *     </li>
     *     <li>
     *         Se la richiesta inizia con "{@code register}" sarà una richiesta di registrarsi, formattata:
     *         "{@code register:dati}", invoca il register_tester con i dati ricevuti
     *     </li>
     * </ul>
     * @param client client che deve eseguire il login / registrazione
     * @return {@code true} se è stato eseguito con successo, {@code false} se ha riscontrato un errore
     */
    public boolean login_client(Client client) {
        String uname;

        //indica al client che cc utilizzare per eseguire il login
        byte cc = client.send("log_here".getBytes(), null);
        client.lock_cc(cc); //avendo action null non viene bloccato da send

        //continua ad attendere tentativi di login / registrazioni finché non entra in un account
        while (true) {
            byte[] request = client.wait_for_reply(cc);
            if (request == null) {
                Logger.log("errore nell'attesa del messaggio di login dal client: (" + client.get_name() + ")", true);
                return false;
            }

            if (Arrays.mismatch("login".getBytes(), request) == 5) {
                uname = try_login(client, request, cc);
            }
            else if (Arrays.mismatch("register".getBytes(), request) == 8) {
                uname = try_register(client, request, cc);
            }
            else {
                Logger.log("il client: (" + client.get_name() + ") ha inviato una richiesta per login non valida: (" + new String(request) + ")", true);
                return false;
            }

            if (uname == null) { //login / registrazione fallita
                Logger.log("il client: (" + client.get_name() + ") ha fallito un login / registrazione", true);
                if (Client.DEBUGGING) { Logger.log(new String(request), true, '\n', false); }
            }
            else { //login / registrazione riuscita
                Logger.log("il client: (" + client.get_name() + ") è entrato nell'account: (" + uname + ")");

                client.send(("log:" + uname).getBytes(), cc);
                client.set_uname(uname);

                return true;
            }
        }
    }

    /**
     * Prova a eseguire il login per il client con la richiesta ricevuto, le specifiche su come valutare i dati nella
     * richiesta dipendono da come è implementato il login manager. Una volta controllata la loro validità con il
     * metodo {@code login_tester()}, se è una richiesta valida, controlla se c'è già un client online registrato con
     * quell utente, e in tal caso fallisce, altrimenti il login è valido
     * @param client  client che tenta il login
     * @param request informazioni per il login
     * @return nome utente se il login è riuscito, {@code null} se è fallito
     */
    private String try_login(Client client, byte[] request, byte cc) {
        byte[] request_data = Arrays.copyOfRange(request, 6, request.length);

        Pair<Boolean, String> result = login_tester(request_data);

        if (result.first() && !ClientsInterface.is_online(result.second())) { //login con successo
            return result.second();
        }
        else if (result.first()) { //richiesta valida ma un client è già online con quell'utente
            Logger.log("il client: (" + client.get_name() + ") ha tentato di eseguire il login in un utente già online", true);
            client.send("fail:utente già online".getBytes(), cc);

            return null;
        }
        else { //login fallito
            Logger.log("il client: (" + client.get_name() + ") ha fallito il login in un utente", true);
            client.send(("fail:" + result.second()).getBytes(), cc);

            return null;
        }
    }

    /**
     * Prova a registrare un nuovo utente per un client, le specifiche su come valutare una richiesta dipendono da come
     * è implementato il login manager. Una volta controllata la validità della richiesta con il metodo
     * {@code register_tester()}, se è una richista valida, controlla non ci sia già un utente con questo nome, e in
     * tal caso fallisce, altrimenti la registrazione è valida
     * @param client  client che vuole registrare un nuovo utente
     * @param request richiesta per il nuovo utente
     * @return nome utente se la registrazione è andata a buon fine, {@code null} se è fallita
     */
    private String try_register(Client client, byte[] request, byte cc) {
        byte[] request_data = Arrays.copyOfRange(request, 9, request.length);

        Pair<byte[], String> result = register_tester(request_data);
        if (result.first() != null && !ClientsInterface.exist_user(result.second())) { //registrazione con successo
            ClientsInterface.register_user(result.second(), result.first());
            return result.second();
        }
        else if (result.first() != null) { //registrazione corretta ma esiste già un utente con questo nome
            Logger.log("il client: (" + client.get_name() + ") ha tentato di creare un utente con nome già esistente: (" + result.second() + ")", true);
            client.send("fail:nome utente già in uso".getBytes(), cc);

            return null;
        }
        else { //registrazione fallita
            Logger.log("il client: (" + client.get_name() + ") ha fallito la registrazione di un account", true);
            client.send(("fail:" + result.second()).getBytes(), cc);

            return null;
        }
    }

    /**
     * Ritorna il nome assegnato a questo login manager
     * @return nome del manager
     */
    public String get_name() {
        return name;
    }
}
