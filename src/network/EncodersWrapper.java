package network;

import files.Logger;

import java.lang.reflect.Method;

/*
 * Gestisce la sicurezza delle conversazioni, per essere inizializzato viene fornito un byte[] di dimensione specificata
 * nelle annotation, questo array dovrebbe essere condiviso solo con il server.
 * Si basa su 4 metodi:
 * 1) initializer: si aspetta un byte[], inizializza l encoder. Come utilizzare questo byte[] dipende molto da encoder a encoder,
 *    la diffusione dell array dipenderà invece dal connector. Ritorna un boolean come feedback, true se tutto è andato a
 *    buon fine, false se c'è stato un errore, per cogliere la differenza ci si aspetta che il metodo lanci un eccezione in
 *    caso di fallita inizializzazione.
 * 2) encoder: indovina? Richiede un byte[] e lo codifica ritornando un byte[]
 * 3) decoder: uguale a sopra
 */
public class EncodersWrapper {
    public final String name;
    public final int array_size;
    private final Method initializer, encoder, decoder;

    public EncodersWrapper(String name, Method initializer, int array_size, Method encoder, Method decoder) {
        this.name = name;
        this.array_size = array_size;
        this.initializer = initializer;
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public boolean initialize(byte[] check_code) {
        try {
            initializer.invoke(null, (Object) check_code);
            return true;
        }
        catch (Exception e) {
            Logger.log("impossibile inizializzare l encoder: " + name, true);
            Logger.log(e.getMessage(), true);

            return false;
        }
    }

    public byte[] encode(byte[] msg) {
        try {
            return (byte[]) encoder.invoke(null, (Object) msg);
        }
        catch (Exception e) {
            Logger.log("impossibile invocare il metodo encoder per l encoder: " + name, true);
            Logger.log(e.getMessage(), true);

            return null;
        }
    }

    public byte[] decode(byte[] msg) {
        try {
            return (byte[]) decoder.invoke(null, (Object) msg);
        }
        catch (Exception e) {
            Logger.log("impossibile invocare il metodo decoder per l encoder: " + name, true);
            Logger.log(e.getMessage(), true);

            return null;
        }
    }
}