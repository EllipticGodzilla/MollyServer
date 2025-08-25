package files;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public abstract class FileCipher {
    private static Cipher encoder = null;
    private static Cipher decoder = null;

    public static void init_ciphers(byte[] key_hash) {
        if (encoder == null && decoder == null) {
            //utilizza i primi 32byte dell hash come key e iv per inizializzare AES
            byte[] key_bytes = Arrays.copyOf(key_hash, 16);
            byte[] iv_bytes = Arrays.copyOfRange(key_hash, 16, 32);

            SecretKey key = new SecretKeySpec(key_bytes, "AES");
            IvParameterSpec iv = new IvParameterSpec(iv_bytes);

            //inzializza encoder e decoder con key e iv appena calcolati
            try {
                encoder = Cipher.getInstance("AES/CBC/PKCS5Padding");
                decoder = Cipher.getInstance("AES/CBC/PKCS5Padding");

                encoder.init(Cipher.ENCRYPT_MODE, key, iv);
                decoder.init(Cipher.DECRYPT_MODE, key, iv);
            }
            catch (Exception e) {
                Logger.log("impossibile inizializzare encoder e decoder per i files", true);
                Logger.log(e.getMessage(), true, '\n', false);

                System.out.println("impossibile inizializzare encoder e decoder per i files");
                System.exit(0);
            }
            Logger.log("definiti i cipher per decifrare e cifrare i file correttamente");
        }
        else {
            Logger.log("impossibile inizializzare File_cipher più di una volta", true);
        }
    }

    protected static byte[] decode(byte[] txt) throws IllegalBlockSizeException, BadPaddingException {
        if (decoder != null) {
            return decoder.doFinal(txt);
        }
        else {
            Logger.log("il decoder non è ancora stato definito, impossibile decifrare il testo", true);
            return null;
        }
    }

    protected static byte[] encode(byte[] txt) throws IllegalBlockSizeException, BadPaddingException {
        if (encoder != null) {
            return encoder.doFinal(txt);
        }
        else {
            Logger.log("l'encoder non è ancora stato definito, impossibile cifrare il testo", true);
            return null;
        }
    }
}
