package files;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public abstract class Logger {
    private static FileOutputStream ostream;
    private static final SimpleDateFormat date_format = new SimpleDateFormat("[HH:mm:ss.SSS] - ");

    protected static void init() {
        try {
            ostream = new FileOutputStream(FileInterface.jar_path + "/database/TerminalLog.dat");
            ostream.write("clear\n".getBytes());

            SimpleDateFormat start_format = new SimpleDateFormat("[yy/LL/dd]");
            ostream.write(("================================== Server Started " + start_format.format(Calendar.getInstance()) + " ==================================").getBytes());
        }
        catch (Exception e) {
            System.out.println("impossibile inizializzare il logger, " + e.getMessage());
            System.exit(0);
        }
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
