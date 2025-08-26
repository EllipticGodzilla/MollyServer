import files.FileCipher;
import files.FileInterface;
import files.Logger;
import files.ModLoader;
import gui.MollyFrame;
import gui.settingsFrame.SettingsFrame;
import gui.temppanel.TempPanel;
import gui.temppanel.TempPanel_action;
import gui.temppanel.TempPanel_info;
import gui.themes.GraphicsSettings;
import network.ServerManager;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Vector;

public class Main {
    private static byte[] file_key_test;
    private static Vector<Method> end_method;

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(shut_down);

        try {
            file_key_test = Main.class.getClassLoader().getResourceAsStream("files/FileCipherKey.dat").readAllBytes();
        }
        catch (Exception e) {
            System.out.println("impossibile trovare il file contenente la password per decifrare i file all'interno dell'eseguibile\n" + e.getMessage());
            System.exit(0);
        }

        FileInterface.search_files();

        Vector<Method>[] run_method = new ModLoader().load_mods();
        end_method = run_method[3];
        Logger.log("chiamo tutti i metodi da eseguire prima dell'inizializzazione delle altre componenti");
        exec_methods(run_method[0]);

        GraphicsSettings.load_from_files();
        MollyFrame.init();
        SettingsFrame.init();
        ServerManager.init_status_file();

        Logger.log("chiamo tutti i metodi da eseguire prima dopo l'inizializzazione delle altre componenti");
        exec_methods(run_method[1]);

        request_file_psw();

        Logger.log("chiamo tutti i metodi da eseguire dopo aver sbloccato i file cifrati");
        exec_methods(run_method[2]);
    }

    private static void exec_methods(Vector<Method> methods) {
        for (Method m : methods) {
            try {
                m.invoke(null);
            }
            catch (Exception e) {
                Logger.log("impossibile eseguire il metodo: (" + m.getName() + ")", true);
                Logger.log(e.getMessage(), true, '\n', false);
            }
        }
    }

    private static void request_file_psw() {
        while (true) { //continua finché non viene raggiunto il return
            //chiede la password per decifrare i file cifrati
            Vector<Object> input = TempPanel.show(new TempPanel_info(
                    TempPanel_info.INPUT_MSG,
                    false,
                    "inserisci la chiave per i database:"
            ).set_psw_indices(0), Thread.currentThread());

            byte[] psw_hash = test_psw((char[]) input.elementAt(0));
            if (psw_hash == null) {
                Logger.log("è stata inserita una password per decifrare i file errata");

                TempPanel.show(new TempPanel_info(
                        TempPanel_info.SINGLE_MSG,
                        false,
                        "password non corretta, riprovare"
                ), null);
            }
            else {
                Logger.log("inserita la password corretta per decifrare i file");

                FileCipher.init_ciphers(psw_hash); //inizializza File_cipher

                //decifra tutte le informazioni contenute dei file cifrati e aggiorna tutte le variabili interne con i dati
                FileInterface.load_from_disk();
                GraphicsSettings.add_file_managers();

//                fire_methods(runnable_methods);
                Logger.log("eseguiti tutti i metodi da chiamare dopo aver decifrato i file");

                return;
            }
        }
    }

    private static byte[] test_psw(char[] psw) {
        //ricava un array di byte[] da password[] prendendo il secondo byte per ogni char in esso
        byte[] psw_bytes = new byte[psw.length];
        for (int i = 0; i < psw.length; i++) {
            psw_bytes[i] = (byte) psw[i];
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA3-512");
            byte[] hash = md.digest(psw_bytes);

            //la seconda metà dell hash viene utilizzata per controllare che la password sia corretta, confrontandola con una copia che ha in un file dell hash corretto
            byte[] comp_hash = Arrays.copyOfRange(hash, 32, 64);

            if (Arrays.equals(comp_hash, file_key_test)) {
                return hash;
            }

            return null;
        } catch (Exception e) {
            Logger.log("errore nel calcolare l'hash della password", true);
            Logger.log(e.getMessage(), true, '\n', false);

            return null;
        }
    }

    private static final Thread shut_down = new Thread(() -> {
        //salva tutte le informazioni in files
        FileInterface.update_files();

        Logger.log("chiamo tutti i metodi da eseguire alla chiusura del software");
        exec_methods(end_method);
    });
}