package network;

import files.Logger;

import javax.crypto.Cipher;
import java.lang.reflect.Method;

/*
 *  Gestisce i vari moduli specificati nei connector, i connector controllano il layer più basso della connessione, implementando
 *  il canale di comunicazione fra client e server, per identificare server vengono utilizzati indirizzi ip o link, per
 *  nessuno dei due è imposto un format e l'unica differenza è che ci si aspetta che il connector utilizzi ip per riconoscere
 *  il server a cui ci si vuole collegare e il link sia utilizzato per renderlo comprensibile all'utente. La conversione
 *  da link a ip ci si aspetta sia fatta da DNS su server esterni ma è possibile scrivere un connector per dns con database
 *  interno. A seconda del server connector indirizzi ip potranno ovviamente avere format e significati molto diversi, ma ci si
 *  riferirà sempre a loro come indirizzo ip. I Connector si dividono in due tipi:
 *  1) dns_connector, sono i piu semplici e gestiscono il canale di connessione con i dns, non è necessario inizializzare
 *     encoders e attivare le connessioni poiché non si devono mandare molti messaggi, l'unico metodo necessario per inizializzare
 *     un dns_connector è dns_handshake che si aspetterà due String contenenti l'indirizzo ip del dns e il link del server
 *     dovrà ritornare String contenente l'indirizzo ip del server o null se l'operazione non è andata a buon fine
 *
 *  2) server_connector, sono più complicati dovendo gestire una connessione più lunga, devono gestire il canale di
 *     comunicazione con il server specificando:
 *     1) handshake: riceve una stringa con l'indirizzo ip del server e l'oggetto ServerInfo legato a questo server,
 *                   dovrà gestire la prima parte della connessione, una volta assicurata la sicurezza della connessione dovrà
 *                   ritornare un array con due Cipher, il primo encoder e il secondo decoder che verranno utilizzati
 *                   per cifrare la connessione prima di aver inizializzato un EncoderWrapper
 *     2) sender: poca fantasia, deve inviare byte[] attraverso il canale specificato, non si deve occupare di sicurezza
 *                poiché il messaggio è già stato cifrato dall encoder prescelto. Non ritorna nulla
 *     3) reader: non riceve nulla e ritorna un singolo messaggio appena ricevuto dal server o null in caso di errore
 *                nel caso in cui il connector sia chiuso mentre si sta aspettando un messaggio dovrà ritornare null
 *     4) closer: viene chiamato quando si deve chiudere la connessione con il server
 */
public class ConnectorWrapper {
    public static final int DNS_CONNECTOR = 0,
                            SERVER_CONNECTOR = 1;
    public final int type;

    private final Method handshake, sender, reader, dns_handshake, closer;
    public final String name;

    public ConnectorWrapper(String name, Method handshake, Method sender, Method reader, Method closer) {
        type = SERVER_CONNECTOR;
        this.name = name;

        this.handshake = handshake;
        this.sender = sender;
        this.reader = reader;
        this.closer = closer;
        this.dns_handshake = null;
    }

    public ConnectorWrapper(String name, Method dns_handshake) {
        type = DNS_CONNECTOR;
        this.name = name;

        this.closer = null;
        this.handshake = null;
        this.sender = null;
        this.reader = null;
        this.dns_handshake = dns_handshake;
    }

    public String dns_handshake(String dns_name, String server_link) {
        if (type == DNS_CONNECTOR) {
            try {
                return (String) dns_handshake.invoke(null, dns_name);
            } catch (Exception e) {
                Logger.log("impossibile connettersi al dns: " + dns_name + " per trovare l'indirizzo ip del server: " + server_link + " con il connector: " + name, true);
                Logger.log(e.getMessage());

                return null;
            }
        }
        else {
            Logger.log("impossibile utilizzare il connector: " + name + " per handshake con dns, è registrato come server connector", true);
            return null;
        }
    }

    public Cipher[] server_handshake(String server_ip, ServerInfo info) {
        if (type == SERVER_CONNECTOR) {
            try {
                return (Cipher[]) handshake.invoke(null, server_ip, info);
            }
            catch (Exception e) {
                Logger.log("errore durante handshake con il server: " + server_ip + " e connector: " + name, true);
                Logger.log(e.getMessage(), true);

                return null;
            }
        }
        else {
            Logger.log("impossibile utilizzare il connector: " + name + " per handshake con server, è registrato come dns connector", true);
            return null;
        }
    }

    public void send(byte[] data) {
        if (type == SERVER_CONNECTOR) {
            try {
                sender.invoke(null, (Object) data);
            }
            catch (Exception e) {
                Logger.log("errore nell'invio di dati attraverso il connector: " + name, true);
                Logger.log(e.getMessage(), true);
            }
        }
        else {
            Logger.log("impossibile utilizzare il connector: " + name + " per inviare dati, è registrato come dns connector", true);
        }
    }

    public byte[] read() {
        if (type == SERVER_CONNECTOR) {
            try {
                return (byte[]) reader.invoke(null);
            }
            catch (Exception e) {
                Logger.log("errore nell'attesa di dati dal connector: " + name, true);
                Logger.log(e.getMessage());

                return null;
            }
        }
        else {
            Logger.log("impossibile utilizzare il connector: " + name + " per ricevere dati, è registrato come dns connector", true);
            return null;
        }
    }

    public void close() {
        try {
            closer.invoke(null);
        }
        catch (Exception e) {
            Logger.log("errore nella chiusura della connessione utilizzando il connector: " + name, true);
            Logger.log(e.getMessage(), true);
        }
    }
}
