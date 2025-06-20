package files;

import files.annotations.*;
import gui.ButtonTopBar_panel;
import gui.custom.ButtonIcons;
import gui.custom.ButtonInfo;
import network.*;

import javax.swing.*;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModLoader extends ClassLoader {
    //mappa fra il nome di ogni risorsa in una mod e il suo contenuto, per nome di una risorsa viene utilizzato <mod_name>.<file_path>
    //e viene considerata risorsa ogni file nella cartella resource in un jar caricato come mod
    private static final Map<String, byte[]> mod_resources = new LinkedHashMap<>();

    /*
     * prima di eseguire tutti i moduli nelle classi trovate in un jar vengono smistate in questi vettori durante la ricerca
     * di tutte le risorse, una volta registrate tutte nella mappa mod_resources vengono registrate
     */
    private static final Vector<Class<?>> button_definer = new Vector<>(),
                                          encoder_definer = new Vector<>(),
                                          connector_definer = new Vector<>(),
                                          prefix_definer = new Vector<>();
    //classifica i metodi in classi con annotation RunAtStart in base a quando dovranno essere chiamati
    private static final Vector<Method> instant_running = new Vector<>(),
                                        encoded_running = new Vector<>(),
                                        decoded_running = new Vector<>(),
                                        end_running = new Vector<>();

    /*
     * Cerca mod in tutti i file <jarpath>/database/mods/*.jar. Per ognuna di queste decomprime i file, carica tutte le risorse
     * in mod_resource (viene considerata risorsa ogni file <mod>/resources/*), e cercando in ogni file *.class se contiene
     * classi con annotation definite in questo software le aggiunge a liste in altre classi per essere caricate nei momenti giusti:
     * 1) ButtonSpec: vengono aggiunge a ButtonTopBar_panel.button_classes e verranno caricate una volta inizializzato il pannello
     * 2) Connector: il loro caricamento non dipende da nessuna componente di questo software, vengono caricati subito
     * 3) EncoderDefinition: come Connector, vengono caricati subito
     * 4) Prefix: vengono caricati subito
     * 5) RunAtStart: questi metodi possono essere eseguiti in tre momenti diversi, subito dopo essere trovati, subito dopo
     *                aver inizializzato tutto il software, prima di richiedere la password per decifrare i file, e subito
     *                dopo aver decifrato tutti i file.
     * Viene ritornato un Vector<Method>[3] contenente tutti i metodi RunAtStart in ordine di quando bisogna chiamarli.
     */
    public Vector<Method>[] load_mods() {
        Logger.log("inizio la ricerca di mod in database/mod/.");

        for (String file : FileInterface.file_list()) {
            if (file.endsWith(".jar") && file.startsWith("mods/")) {
                Logger.log("trovata una mod in: " + file);

                load_file(FileInterface.jar_path + "/" + file);
            }
        }

        Logger.log("caricate tutte le mods");

        return new Vector[] { instant_running, encoded_running, decoded_running, end_running };
    }

    public static byte[] get_resource(String name) {
        return mod_resources.get(name);
    }

    private void load_file(String file_path) {
        String current_mod = file_path.substring(file_path.lastIndexOf('/') + 1, file_path.length() - 4);

        ZipFile zip_file;
        try {
            zip_file = new ZipFile(file_path);
        }
        catch (Exception e) {
            Logger.log("impossibile costruire lo ZipFile con la mod al file: " + file_path, true);
            Logger.log(e.getMessage(), true);

            return;
        }

        Enumeration<? extends ZipEntry> entries = zip_file.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();

            if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                try {
                    store_classes(entry.getName(), zip_file.getInputStream(entry));
                }
                catch (Exception e) {
                    Logger.log("impossibile trovare l'input stream per l'entrata: " + entry.getName(), true);
                    Logger.log(e.getMessage(), true);
                }
            }
            else if (!entry.isDirectory() && entry.getName().startsWith("resources/")) {
                try {
                    load_resource(entry.getName(), zip_file.getInputStream(entry), current_mod);
                }
                catch (Exception e) {
                    Logger.log("impossibile trovare l'input stream per l'entrata: " + entry.getName(), true);
                    Logger.log(e.getMessage(), true);
                }
            }
        }

        load_classes(current_mod);
    }

    private void store_classes(String name, InputStream iStream) {
        //rimuove il ".class" dalla fine del nome e rimpiazza tutti i '/' nella path con '.'
        String class_name = name.substring(0, name.length() - 6).replaceAll("/", ".");

        byte[] entry_data;
        try {
            entry_data = iStream.readAllBytes();
            iStream.close();
        }
        catch (Exception e) {
            Logger.log("errore nella lettura dell'entrata: " + name, true);
            Logger.log(e.getMessage(), true);

            return;
        }

        Class<?> entry_class = defineClass(class_name, entry_data, 0, entry_data.length);
        if (entry_class.isAnnotationPresent(ButtonSpec.class)) {
            button_definer.add(entry_class);
        }
        else if (entry_class.isAnnotationPresent(Connector.class)) {
            connector_definer.add(entry_class);
        }
        else if (entry_class.isAnnotationPresent(EncoderDefinition.class)) {
            encoder_definer.add(entry_class);
        }
        else if (entry_class.isAnnotationPresent(Prefix.class)) {
            prefix_definer.add(entry_class);
        }
        else if (entry_class.isAnnotationPresent(RunAtStart.class)) {
            add_runnuble_methods(entry_class);
        }
    }

    private void add_runnuble_methods(Class<?> runnable_class) {
        for (Method method : runnable_class.getMethods()) {
            if (method.isAnnotationPresent(RunAtStart.Fire.class)) {
                RunAtStart.Fire method_annotation = method.getAnnotation(RunAtStart.Fire.class);

                switch (method_annotation.run_at()) {
                    case RunAtStart.START:
                        instant_running.add(method);
                        break;

                    case RunAtStart.INIT:
                        encoded_running.add(method);
                        break;

                    case RunAtStart.DECODE:
                        decoded_running.add(method);
                        break;

                    case RunAtStart.END:
                        end_running.add(method);
                        break;

                    default:
                        Logger.log("impossibile aggiungere il metodo: " + method.getName() + " con quelli da chiamare a startup, run_at = " + method_annotation.run_at(), true);
                }
            }
        }
    }

    private void load_resource(String name, InputStream iStream, String mod_name) {
        //rimuove "resources/" dal nome dell'entrata
        String entry_name = name.substring(10);

        try {
            mod_resources.put(mod_name + "." + entry_name, iStream.readAllBytes());
            iStream.close();
        }
        catch (Exception e) {
            Logger.log("impossibile leggere il contenuto dell'entrata: " + name, true);
            Logger.log(e.getMessage(), true);
        }
    }

    private static void load_classes(String mod_name) {
        for (Class<?> entry_class : button_definer) {
            load_button(entry_class, mod_name);
        }
        for (Class<?> entry_class : encoder_definer) {
            load_encoder(entry_class);
        }
        for (Class<?> entry_class : connector_definer) {
            load_encoder(entry_class);
        }
        for (Class<?> entry_class : prefix_definer) {
            load_prefix(entry_class, mod_name);
        }
    }

    public static void load_prefix(Class<?> conversation_class, String mod_name) {
        for (Method m : conversation_class.getMethods()) {
            if (m.isAnnotationPresent(Prefix.RegisterToPrefix.class)) {
                String prefix_name = m.getAnnotation(Prefix.RegisterToPrefix.class).prefix_name();

                OnArrival wrapper = (conv_code, msg) -> {
                    try {
                        m.invoke(null, conv_code, msg);
                    }
                    catch (Exception e) {
                        Logger.log("errore nella chiamata al metodo registrato al prefisso: " + prefix_name + " dalla mod: " + mod_name, true);
                        Logger.log(e.getMessage(), true);
                    }
                };

                Connection.register_prefix_action(prefix_name, wrapper);
            }
        }
    }

    public static void load_button(Class<?> button_class, String mod_name) {
        Logger.log("aggiungo un nuovo pulsante dalla classe: " + button_class.getName());

        ButtonSpec specification = button_class.getAnnotation(ButtonSpec.class);

        int active_when = specification.when_active();
        String name = specification.name();
        ButtonIcons icons;
        try {
            icons = new ButtonIcons(
                    new ImageIcon(get_resource(mod_name + "." + specification.default_icon())),
                    new ImageIcon(get_resource(mod_name + "." + specification.rollover_icon())),
                    new ImageIcon(get_resource(mod_name + "." + specification.pressed_icon())),
                    new ImageIcon(get_resource(mod_name + "." + specification.disabled_icon()))
            );
        }
        catch (Exception _) { //se non è stata trovata una resource dalla mod
            Logger.log("impossibile caricare le immagini per il pulsante: " + name, true);
            return;
        }

        //cerca i metodi con annotation @OnPress e @OnStop nella classe
        Method onPress = null, stop = null;

        for (Method method : button_class.getMethods()) {
            //cerca dei metodi con @ButtonSpec.OnPress o @ButtonSpec.OnStop o @ButtonSpec.IconsSpec e che non richiedano nessun parametro
            if (onPress == null && method.isAnnotationPresent(ButtonSpec.OnPress.class) && Arrays.equals(method.getParameterTypes(), new Class<?>[0])) {
                onPress = method;
            }
            else if (stop == null && method.isAnnotationPresent(ButtonSpec.OnStop.class) && Arrays.equals(method.getParameterTypes(), new Class<?>[0])) {
                stop = method;
            }

            //una volta trovati entrambi i metodi non ha senso continuare a cercarne altri
            if (onPress != null && stop != null) {
                break;
            }
        }

        if (onPress == null || stop == null) {
            Logger.log("non è stato trovato il modulo OnPress o OnStop o IconsSpec per il pulsante: " + name, true);
            return;
        }

        ButtonInfo info = new ButtonInfo(name, onPress, stop, active_when);
        ButtonTopBar_panel.add_button(icons, info);

        Logger.log("aggiunto con successo il pulsante: " + name + " alla top bar");
    }

    public static void load_encoder(Class<?> encoder_class) {
        Logger.log("registro un nuovo encoder dalla classe: " + encoder_class.getName());

        String name = encoder_class.getAnnotation(EncoderDefinition.class).name();
        Method initializer = null, encoder = null, decoder = null;

        for (Method method : encoder_class.getMethods()) {
            if (method.isAnnotationPresent(EncoderDefinition.Initializer.class) && initializer == null) {
                initializer = method;
            }
            else if (method.isAnnotationPresent(EncoderDefinition.Encoder.class) && encoder == null) {
                encoder = method;
            }
            else if (method.isAnnotationPresent(EncoderDefinition.Decoder.class) && decoder == null) {
                decoder = method;
            }

            //quando ha inizializzato tutti i metodi smette di cercarne altri
            if (initializer != null && encoder != null && decoder != null) {
                break;
            }
        }

        //non è riuscito a inizializzare tutti i metodi
        if (initializer == null || encoder == null || decoder == null) {
            Logger.log("nella classe: " + encoder_class.getName() + " non sono specificati tutti i metodi necessari", true);

            if (initializer == null) {
                Logger.log("initializer non specificato", true);
            }
            if (encoder == null) {
                Logger.log("encoder non specificato", true);
            }
            if (decoder == null) {
                Logger.log("decoder non specificato", true);
            }

            return;
        }

        int array_size = encoder_class.getAnnotation(EncoderDefinition.class).array_size();

        EncodersWrapper info = new EncodersWrapper(name, initializer, array_size, encoder, decoder);
        ServerInterface.add_encoder(name, info);
    }

    public static void load_connector(Class<?> connector_class) {
        Logger.log("aggiungo un nuovo connector dalla classe: " + connector_class.getName());
        Connector annotation = connector_class.getAnnotation(Connector.class);
        String connector_name = annotation.name();

        if (annotation.type() == ConnectorWrapper.DNS_CONNECTOR) {
            Method handshake_method = null;

            for (Method method : connector_class.getMethods()) {
                if (method.isAnnotationPresent(Connector.handshake.class)) {
                    handshake_method = method;
                    break;
                }
            }

            if (handshake_method == null) {
                Logger.log("per il connector: (" + connector_name + ") non è stato registrato nessun metodo per gestire l'handshake", true);
                return;
            }

            ConnectorWrapper wrapper = new ConnectorWrapper(connector_name, handshake_method);
            ServerInterface.add_connector(connector_name, wrapper);
        }
        else {
            Method  handshake = null,
                    sender = null,
                    reader = null,
                    closer = null;

            for (Method method : connector_class.getMethods()) {
                if (method.isAnnotationPresent(Connector.handshake.class) && handshake == null) {
                    handshake = method;
                }
                else if (method.isAnnotationPresent(Connector.reader.class) && reader == null) {
                    reader = method;
                }
                else if (method.isAnnotationPresent(Connector.sender.class) && sender == null) {
                    sender = method;
                }
                else if (method.isAnnotationPresent(Connector.closer.class) && closer == null) {
                    closer = method;
                }

                if (handshake != null && reader != null && sender != null && closer != null) {
                    break;
                }
            }

            if (handshake == null || sender == null || reader == null || closer == null) {
                Logger.log("per il connector: (" + connector_name + ") non sono stati specificati tutti i metodi necessari", true);

                if (handshake == null) {
                    Logger.log("handshake non specificato", true);
                }
                if (sender == null) {
                    Logger.log("sender non specificato", true);
                }
                if (closer == null) {
                    Logger.log("closer non specificato", true);
                }

                return;
            }

            ConnectorWrapper wrapper = new ConnectorWrapper(connector_name, handshake, sender, reader, closer);
            ServerInterface.add_connector(connector_name, wrapper);
        }
    }
}
