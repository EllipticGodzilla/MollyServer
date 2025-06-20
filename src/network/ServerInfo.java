package network;

/*
 * contiene tutte le informazioni su un server registrato
 */
public class ServerInfo {
    public final String SERVER_NAME;
    public final String SERVER_IP;
    public final int SERVER_PORT;
    public final String SERVER_LINK;
    public final String DNS_NAME;
    public final String ENCODER;
    public final String CONNECTOR;

    public ServerInfo(String name, String link, String ip, int port, String dns_name, String encoder, String connector) {
        this.SERVER_NAME = name;
        this.SERVER_IP = ip;
        this.SERVER_PORT = port;
        this.SERVER_LINK = link;
        this.DNS_NAME = dns_name;
        this.ENCODER = encoder;
        this.CONNECTOR = connector;
    }

    @Override
    public String toString() {
        return "name: " + SERVER_NAME + ", ip: " + SERVER_IP + ", link: " + SERVER_LINK + ", port: " + SERVER_PORT + ", dns: " + DNS_NAME + ", encoder: " + ENCODER + ", connector: " + CONNECTOR;
    }

    public String serverIp() {
        return (SERVER_IP == null)? ">not defined<" : SERVER_IP;
    }

    public String serverLink() {
        return (SERVER_LINK == null)? ">not defined<" : SERVER_LINK;
    }

    public String serverDNS() {
        return (DNS_NAME == null)? ">not defined<" : DNS_NAME;
    }
}