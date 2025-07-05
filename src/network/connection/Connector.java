package network.connection;

/**
 * Istanze di questa classe vengono utilizzate per permettere al server di essere visibile ai clients. Viene attivato e
 * disattivato da {@code ServerManager}, quando attivo dovrà rimanere in attesa di connessioni con nuovi clients,
 * creare istanze di {@code Client} che permettano di comunicare con essi e iniziare l handshake fino a definire una
 * session key {@code Cipher[2]}, poi passera i due oggetti a {@code ClientsInterface.new_client()} assieme al proprio
 * nome.
 * <p>Nuove istanze di questa classe verranno create chiamando il constructor {@code Connector(name : String)},
 * ricevendo come unico argomento il nome con cui si sta registrando. Appena creata l'istanza questa dovrà poter essere
 * fatta partire da subito.
 */
public abstract class Connector {
    /// Nome con cui si registra il connector
    private final String name;

    /**
     * Permette ai client di vedere e collegarsi al server attraverso questo Connector, a ogni connessione dovrà creare
     * una nuova istanza di {@code Client} permettendo di comunicare con esso e portare avanti l handshake fino a
     * definire una chiave di sessione {@code Cipher[2]}. A quel punto potrà passare i due oggetti, assieme al proprio
     * nome, a {@code ClientsInterface.new_client()}.
     * <p>Una volta attivato dovrà impostare il proprio status a {@code true}, e rimanere in attesa di nuovi clients
     * finché non verrà chiamato {@code stop()}
     */
    public abstract void start();

    /**
     * Stoppa il connector rifiutando tutti i futuri tentativi di connessione, e dovrebbe non essere più visibile e
     * impostare il proprio status a {@code false}.
     * <p>Benché la specifica di come i clients collegati al server tramite questo connector dipenda proprio da esso,
     * questi dovranno poter comunicare anche una volta che questo è stoppato. Infatti per chiudere un connector aperto
     * viene prima stoppato per evitare che nuovi clients si colleghino, e dopo scollegati tutti i clients notificandoli
     * con {@code EOC}
     */
    public abstract void stop();

    /**
     * @return {@code true} se il connector è attivo e in attesa di nuove connessioni di clients, {@code false} se è
     * stoppato e il server non è visibile a clients attraverso il canale definito da questo connector
     */
    public abstract boolean get_status();

    public Connector(String name) {
        this.name = name;
    }

    /**
     * Ogni connector è registrato in {@code ServerManager} con un nome che lo identifica
     * @return nome con cui è registrato questo connector
     */
    public String get_name() {
        return name;
    }
}