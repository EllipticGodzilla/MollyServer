package files;

import network.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class FileInterface {
    public static final String jar_path;
    private static boolean initialized = false;

    private static final Map<String, SecureFile> loaded_files = new LinkedHashMap<>();
    //quando si vogliono aggiornare le informazioni nei file per salvare il progresso vengono eseguiti tutti i runnable in questo array
    private static Runnable[] file_updaters = new Runnable[0];
    //per caricare dai file tutte le informazioni vengono eseguiti i runnable in questo array, vengono eseguiti anche per dei reload
    private static Runnable[] file_loaders = new Runnable[0];

    //costanti che vengono ritornate da is_encoded()
    public static final int FILE_NOT_FOUND = -1,
                            FILE_CLEAR = 0,
                            FILE_ENCODED = 1;

    static {
        String tmp_jar_path = FileInterface.class.getProtectionDomain().getCodeSource().getLocation().getPath(); //calcola l'abs path del jar
        tmp_jar_path = tmp_jar_path.substring(0, tmp_jar_path.length() - 1); //rimuove l'ultimo /
        jar_path = tmp_jar_path.substring(0, tmp_jar_path.lastIndexOf('/')); //rimuove Molly.jar dalla fine della path

        add_file_updater(FileInterface::standard_updater);
        add_file_loader(FileInterface::standard_loader);

        check_essential_files();
    }

    //controlla che tutti i file e cartelle essenziali esistano
    private static void check_essential_files() {
        try {
            new File(jar_path + "/mods").mkdir();
            new File(jar_path + "/database").mkdir();
            new File(jar_path + "/database/graphics").mkdir();
            new File(jar_path + "/database/graphics/default.dat").createNewFile();
            new File(jar_path + "/database/ServerList.dat").createNewFile();
            new File(jar_path + "/database/TerminalLog.dat").createNewFile();
            new File(jar_path + "/database/DNS_CA_list.dat").createNewFile();
        }
        catch (IOException _) {
            System.out.println("impossibile creare cartelle o file in: " + jar_path);
            System.exit(0);
        }
    }

    private static void standard_updater() {
        Logger.log("salvo tutti i server in database/ServerList.dat");
        update_server_list();
        Logger.log("salvo tutti i dns in database/DNS_CA_list.dat");
        update_dns_list();
    }

    private static void update_server_list() {
        StringBuilder file_content = new StringBuilder();

        //per ogni server aggiunge una riga con <nome>;<link>;<ip>;<porta>;<nome dns>;<encoder>;<connector>
        for (String server_name : ServerInterface.get_server_list()) {
            ServerInfo server_info = ServerInterface.get_server_info(server_name);

            file_content.append(server_name).append(";")
                        .append((server_info.SERVER_LINK== null)? "" : server_info.SERVER_LINK).append(";")
                        .append((server_info.SERVER_IP == null)? "" : server_info.SERVER_IP).append(";")
                        .append(server_info.SERVER_PORT).append(";")
                        .append((server_info.DNS_NAME == null)? "" : server_info.DNS_NAME).append(";")
                        .append(server_info.ENCODER).append(";")
                        .append(server_info.CONNECTOR).append("\n");
        }

        overwrite_file("database/ServerList.dat", file_content.toString().getBytes());
    }

    private static void update_dns_list() {
        StringBuilder file_content = new StringBuilder();

        for (String dns_name : ServerInterface.get_dns_list()) {
            DnsInfo dns_info = ServerInterface.get_dns_info(dns_name);

            //le linee sono formattate come "<nome>;<ip>;<porta>;<connector>;<base64 pKey>"
            file_content.append(dns_name).append(";")
                    .append(dns_info.IP).append(";")
                    .append(dns_info.PORT).append(";")
                    .append(dns_info.CONNECTOR).append(";")
                    .append(Base64.getEncoder().encodeToString(dns_info.get_pkey())).append("\n");
        }

        overwrite_file("database/DNS_CA_list.dat", file_content.toString().getBytes());
    }

    private static void standard_loader() {
        Logger.log("carico tutti i server e dns memorizzati nei file");

        //se ha già dei dns o server memorizzati li dimentica
        if (!ServerInterface.get_dns_list().isEmpty()) {
            Logger.log("resetto la lista di dns memorizzati in ServerInterface");
            ServerInterface.reset_dns_list();
        }
        if (!ServerInterface.get_server_list().isEmpty()) {
            ServerInterface.reset_server_list();
        }

        load_server_list();
        load_dns_list();
    }

    private static void load_server_list() {
        Logger.log("leggo tutti i dati sui server memorizzati in database/ServerList.dat");
        /*
         * il testo è formattato come:
         * <nome1>;<indirizzo1>;<ip1>;<porta1>;<dns_name1>;<encoder1>;<connector1>
         * <nome2>;<indirizzo2>;<ip2>;<porta2>;<dns_name2>;<encoder2>;<connector2>
         * ...
         * ...
         */
        byte[] file_content_bytes = read_file("database/ServerList.dat");
        if (file_content_bytes == null) {
            Logger.log("impossibile leggere il contenuto del file database/ServerList.dat", true);
            return;
        }
        String file_content = new String(file_content_bytes);

        if (file_content.isEmpty()) {
            Logger.log("nessun server è memorizzato nel file");
            return;
        }

        String[] lines_array = file_content.split("\n");
        for (String line : lines_array) {
            String[] line_info = line.split(";");

            if (line_info.length != 7) {
                Logger.log("in ServerList.dat la linea: " + line + " contiene un numero sbagliato di elementi", true);
                return;
            }

            int port;
            try {
                port = Integer.parseInt(line_info[3]);
            }
            catch (Exception _) {
                Logger.log("in ServerList.dat per il server: " + line_info[0] + " è specificata una porta non numerica", true);
                return;
            }

            ServerInfo info = new ServerInfo(
                    line_info[0],
                    line_info[1].isEmpty()? null : line_info[1], //link
                    line_info[2].isEmpty()? null : line_info[2], //ip
                    port,                                        //porta
                    line_info[4].isEmpty()? null : line_info[4], //nome dns
                    line_info[5],                                //encoder
                    line_info[6]                                 //connector
            );

            ServerInterface.add_server(line_info[0], info);
            Logger.log("aggiunto un nuovo server alla lista: " + line_info[0]);
        }

        Logger.log("lista dei server inizializzata");
    }

    private static void load_dns_list() {
        Logger.log("carico tutte le informazioni sui dns memorizzate");

        byte[] file_content_bytes = read_file("database/DNS_CA_list.dat");
        if (file_content_bytes == null) {
            Logger.log("impossibile leggere il contenuto di database/DNS_CA_list.dat", true);
            return;
        }
        String file_content = new String(file_content_bytes);
        String[] file_lines = file_content.split("\n");

        //le linee sono formattate come "<nome>;<ip>;<porta>;<connector>;<base64 pKey>"
        for (int i = 0; i < file_lines.length; i++) {
            String[] line_info = file_lines[i].split(";");

            //per inizializzare un nuovo dns devono essere specificati: nome, ip, porta, pkey, connector, ignorando le linee vuote
            if (line_info.length != 5 && line_info.length != 1) {
                Logger.log("impossibile comprendere la linea (" + (i + 1) + "): " + file_lines[i] + " in DNS_CA_list.dat, sono presenti un numero di paramentri diverso da 5", true);
            }
            else if (line_info.length != 1) {
                try {
                    byte[] pkey = Base64.getDecoder().decode(line_info[4]);
                    int port = Integer.parseInt(line_info[2]);

                    DnsInfo info = new DnsInfo(line_info[0], line_info[1], port, line_info[3], pkey);
                    ServerInterface.add_dns(line_info[0], info);
                    Logger.log("trovato il dns: " + line_info[0]);
                }
                catch (Exception e) {
                    Logger.log("impossibile decodificare la pkey o porta del dns: " + line_info[0] + " da DNS_CA_list.dat", true);
                    Logger.log(e.getMessage(), true);
                }
            }
        }

        Logger.log("memorizzati tutti i dns");
    }

    public static void load_from_disk() {
        Logger.log("inizio a caricare tutti i dati dai file");
        for (Runnable loader : file_loaders) {
            loader.run();
        }
        Logger.log("dati aggiornati");

        initialized = true;
    }

    public static void update_files() {
        /*
         * se il programma viene chiuso prima di inserire la password non vengono mai chiamati i file_loaders, quindi non
         * ha nessuna informazione da salvare e chiamare gli updaters eliminerebbe ogni dati nei files
         */
        if (!initialized) {
            return;
        }

        Logger.log("aggiorno tutti i dati contenuti nei file");
        for (Runnable updater : file_updaters) {
            updater.run();
        }
        Logger.log("file aggiornati");
    }

    public static void add_file_updater(Runnable updater) {
        file_updaters = Arrays.copyOf(file_updaters, file_updaters.length + 1);
        file_updaters[file_updaters.length - 1] = updater;
    }

    public static void add_file_loader(Runnable loader) {
        file_loaders = Arrays.copyOf(file_loaders, file_loaders.length + 1);
        file_loaders[file_loaders.length - 1] = loader;
    }

    //cerca tutti i file nella cartella jar_path e li aggiunge alla mappa loaded_files
    public static void search_files() {
        File[] files = new File(jar_path).listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                search_folder(file);
            }
            else if (!file.getName().equals("Molly.jar")) {
                add_file(file);
            }
        }
    }

    private static void search_folder(File folder) {
        if (folder.listFiles() != null) { //se questa cartella non è vuota
            for (File file : folder.listFiles()) {
                if (file.isDirectory()) {
                    search_folder(file);
                } else {
                    add_file(file);
                }
            }
        }
    }

    private static void add_file(File file) {
        String file_name = file.getAbsolutePath().replaceAll(jar_path + "/", "");
        loaded_files.put(file_name, new SecureFile(file.getPath()));
    }

    public static byte[] read_file(String file_name) {
        SecureFile file = loaded_files.get(file_name);

        if (file == null) {
            Logger.log("impossibile leggere il contenuto del file: " + file_name + ", file non esistente", true);
            return null;
        }
        else {
            return file.read();
        }
    }

    public static void append_to(String file_name, byte[] data) {
        SecureFile file = loaded_files.get(file_name);
        if (file == null) {
            Logger.log("impossibile aggiungere il testo: " + new String(data) + " al file: " + file_name + ", file non esistente", true);
        }
        else {
            file.append(data);
        }
    }

    public static void overwrite_file(String file_name, byte[] data) {
        SecureFile file = loaded_files.get(file_name);

        if (file == null) {
            Logger.log("impossibile scrivere nel file: " + file_name + " file non esistente", true);
        }
        else {
            file.replace(data);
        }
    }

    public static boolean create_file(String file_name, boolean encoded) {
        if (loaded_files.containsKey(file_name)) { //se esiste già un file con questo nome
            Logger.log("impossibile creare il file: " + file_name + ", esiste già un file con questo nome", true);
            return false;
        }

        loaded_files.put(file_name, new SecureFile(jar_path + "/" + file_name, encoded));
        return true;
    }

    public static void delete_file(String file_name) {
        if (!loaded_files.containsKey(file_name)) {
            Logger.log("impossibile eliminare il file: " + file_name + ", file non esistente", true);
            return;
        }

        loaded_files.get(file_name).delete();
        loaded_files.remove(file_name);
    }

    public static void set_encoded(String file_name, boolean encoded) {
        SecureFile file = loaded_files.get(file_name);

        if (file == null) {
            Logger.log("impossibile cambiare la sicurezza del file: " + file_name + ", file non esistente", true);
            return;
        }

        file.set_encoded(encoded);
    }

    public static boolean exist(String file_name) {
        return loaded_files.containsKey(file_name);
    }

    //ritorna -1 = file non esistente, 0 = file non cifrato, 1 = file cifrato
    public static int is_encoded(String file_name) {
        SecureFile file = loaded_files.get(file_name);

        if (file == null) {
            return FILE_NOT_FOUND;
        }
        else {
            return file.is_protected()? FILE_ENCODED : FILE_CLEAR;
        }
    }

    public static String[] file_list() {
        return loaded_files.keySet().toArray(new String[0]);
    }

    public static void close() {
        for (SecureFile file : loaded_files.values()) {
            file.close();
        }
    }
}