import files.FileInterface;
import gui.MollyFrame;
import gui.settingsFrame.SettingsFrame;
import gui.themes.GraphicsSettings;

public class Main {
    private static byte[] file_key_test;

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
        //load mods
        GraphicsSettings.load_from_files();
        MollyFrame.init();
        SettingsFrame.init();
    }

    private static final Thread shut_down = new Thread(() -> {

    });
}