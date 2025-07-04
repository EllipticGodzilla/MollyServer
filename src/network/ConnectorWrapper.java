package network;


import files.Logger;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_info;

import java.lang.reflect.Method;

/**
 * Permette agli utenti di collegarsi al server aprendo un canale e rimanendo in attesa di clients che si collegano,
 * una volta collegato un client dovrà creare un istanza di Client che lo rappresenti e iniziare l handshake fino alla
 * decisione della chiave di sessione, a quel punto dovrà passare Client e chiave di sessione a ClientInterface.
 * Istanze di questa classe sono create dal ModLoader e vanno create utilizzando l'annotazione @Connector su una classe.
 */
public class ConnectorWrapper {
    /**
     * Metodi specificati dalla classe nella mod che specificano come iniziare l'attesa di nuovi client per questo
     * connector e come stopparla
     */
    private final Method starter, stopper;

    /// Nome con cui viene identificato il connector, viene utilizzato per mostrae messaggi di errore
    private final String NAME;

    /// True se il connector è attivo, false se è disattivo
    private boolean status = false;

    public ConnectorWrapper(Method starter, Method stopper, String name) {
        this.starter = starter;
        this.stopper = stopper;
        this.NAME = name;
    }

    /// Inizia a ricevere nuovi client
    public void start() {
        try {
            starter.invoke(null);
            status = true;
        }
        catch (Exception e) {
            Logger.log("impossibile fare partire il connector: (" + NAME + ")", true);
            Logger.log(e.getMessage(), true);

            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile fare partire il connector: " + NAME
            ), null);
        }
    }

    /// Smette di ricevere nuovi client
    public void stop() {
        try {
            stopper.invoke(null);
            status = false;
        }
        catch (Exception e) {
            Logger.log("impossibile stoppare il connector: (" + NAME + ")", true);
            Logger.log(e.getMessage(), true);

            TempPanel.show(new TempPanel_info(
                    TempPanel_info.SINGLE_MSG,
                    false,
                    "impossibile stoppare il connector: " + NAME
            ), null);
        }
    }

    /// Ritorna true se è in attesa di nuovi client, false se è spento
    public boolean is_active() {
        return status;
    }
}
