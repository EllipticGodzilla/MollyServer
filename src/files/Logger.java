package files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public abstract class Logger {
    private static final FileOutputStream ostream;
    private static final SimpleDateFormat date_format = new SimpleDateFormat("[HH:mm:ss.SSS] - ");

    static {
        FileOutputStream temp_ostream;

        //prova a creare l'output stream per il file, se non esiste ancora cerca di crearlo e riprova a inizializzare l'output stream
        try {
            temp_ostream = new FileOutputStream(FileInterface.jar_path + "/database/TerminalLog.dat");
        }
        catch (FileNotFoundException _) {
            try {
                new File(FileInterface.jar_path + "/database/TerminalLog.dat").createNewFile();
                temp_ostream = new FileOutputStream(FileInterface.jar_path + "/database/TerminalLog.dat");
            }
            catch (IOException e) {
                System.out.println("impossibile creare il file per i log a: " + FileInterface.jar_path + "/database/TerminalLog.dat");
                temp_ostream = null;

                System.exit(0);
            }
        }

        //se è stato creato FileOutputStream con successo ha resettato il file, come prima cosa scrive "clear" per indicare che non è cifrato
        try {
            temp_ostream.write("clear\n".getBytes());
        }
        catch (IOException _) {}

        ostream = temp_ostream;
    }

    public static void log(String txt) {
        log(txt, false, '\n', true);
    }
    public static void log(String txt, boolean error) {
        log(txt, error, '\n', true);
    }

    public static synchronized void log(String txt, boolean error, char end, boolean print_date) {
        byte[] new_line = (((error)? "! " : "  ") + (print_date? current_time() : "") + txt + end).getBytes();

        try {
            ostream.write(new_line);
        } catch (IOException _) {
            System.out.println("impossibile per il logger scrivere nel FileOutputStream: " + ostream);
            System.exit(0);
        }
    }

    public static void close() {
        try {
            ostream.close();
        }
        catch (IOException _) {
            System.out.println("errore nel chiudere il FileOutputStream: " + ostream);
            System.exit(0);
        }
    }

    private static String current_time() {
        Calendar c_time = Calendar.getInstance();
        return date_format.format(c_time.getTime());
    }
}
