package network.connection;

/**
 * L'azione da eseguire come risposta per ogni messaggio ricevuto dai client viene passata ai thread worker in
 * ClientsInterface rappresentati da istanze di questa classe:
 * <ul>
 *     <li>
 *         {@code action == null}: il messaggio è ricevuto senza che nessuno fosse in attesa di una risposta a quel
 *         {@code cc}, verrà ricavato il prefisso da {@code msg} e processato di conseguenza
 *     </li>
 *     <li>
 *         {@code action != null}: dovrà eseguire l'azione specificata rispondendo al messaggio ricevuto
 *     </li>
 * </ul>
 */
public class WorkData {
    /// Client che invia il messaggio
    private final Client client;

    /// Messaggio ricevuto
    private final byte[] msg;

    /// cc utilizzato per inviare il messaggio
    private final byte cc;

    /// Se != {@code null} specifica l'azione da eseguire per rispondere al messaggio
    private final OnArrival action;

    public WorkData(Client client, byte[] msg, byte cc, OnArrival action) {
        this.client = client;
        this.msg = msg;
        this.cc = cc;
        this.action = action;
    }

    public WorkData(Client client, byte[] msg, byte cc) {
        this(client, msg, cc, null);
    }

    /// Ritorna l'istanza di {@code Client} da cui è ricevuto il messaggio
    public Client get_client() {
        return client;
    }

    /// Ritorna il messaggio ricevuto dal client
    public byte[] get_message() {
        return msg.clone();
    }

    /// Ritorna il cc utilizzato dal client per inviare il messaggio
    public byte get_cc() {
        return cc;
    }

    /// Ritorna l'azione da eseguire per rispondere al messaggio o {@code null} se non specificata
    public OnArrival get_action() {
        return action;
    }
}
