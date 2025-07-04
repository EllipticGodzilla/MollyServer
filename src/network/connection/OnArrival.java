package network.connection;

/**
 * Utilizzato nelle conversazioni con il server per registrare delle azioni da eseguire una volta ricevuto una
 * risposta a una certo conv code
 */
public interface OnArrival {
    void on_arrival(Client client, byte conv_code, byte[] msg);
}