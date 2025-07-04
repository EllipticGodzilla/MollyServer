package network.connection;

/**
 * Permette di cifrare le connessioni fra server e client, per inizializzare l encoder vengono concordati con il client
 * un numero specificato nella mod di bytes segreti, e passati in init()
 */
public abstract class Encoder {
    /**
     * Cifra i bytes ricevuti come specificato nella mod da cui proviene l encoder
     * @param msg bytes da cifrare
     * @return bytes cifrati
     */
    public abstract byte[] encode(byte[] msg);

    /**
     * Decifra i bytes ricevuti come specificato nella mod da cui proviene l encoder
     * @param msg bytes da decifrare
     * @return bytes decifrati
     */
    public abstract byte[] decode(byte[] msg);

    /**
     * Inizializza l encoder utilizzando dei bytes segreti fra il server e il client
     * @param shared_bytes bytes conosciuti solo dal server e client
     * @return {@code true} se riesce a inizializzarlo, {@code false} se fallisce
     */
    public abstract boolean init(byte[] shared_bytes);
}
