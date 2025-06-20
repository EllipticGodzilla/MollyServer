package network;

import java.util.Arrays;
import java.util.Base64;

//contiene tutte le informazioni su un dns registrato
public class DnsInfo {
    private final byte[] public_key;
    public final String NAME, IP, CONNECTOR;
    public final int PORT;

    public DnsInfo(String name, String ip, int port, String connector, byte[] pkey){
        this.public_key = pkey;
        this.NAME = name;
        this.IP = ip;
        this.PORT = port;
        this.CONNECTOR = connector;
    }

    //ritornare direttamente l array darebbe la possibilità di modificare le singole cifre
    public byte[] get_pkey() {
        if (public_key != null) {
            return Arrays.copyOf(public_key, public_key.length);
        }
        return null;
    }

    @Override
    public String toString() {
        return "name: " + NAME + ", ip: " + IP + ", port: " + PORT + ", connector: " + CONNECTOR + ", pKey: " + Base64.getEncoder().encodeToString(public_key);
    }
}
