import files.FileInterface;
import gui.MollyFrame;
import gui.themes.GraphicsSettings;

public class Main {
    public static void main(String[] args) {
        FileInterface.search_files();
        GraphicsSettings.load_from_files();
        MollyFrame.init();
    }
}