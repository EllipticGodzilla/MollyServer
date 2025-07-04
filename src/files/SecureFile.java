package files;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Rappresenta un file esterno al progetto dando la possibilità di cifrarne il contenuto.
 * Per interagire con i secure file non si deve passare per questa classe ma dai vari metodi in FileInterface, nelle
 * prossime versioni probabilmente questa dinamica cambierà
 */
public class SecureFile {
    private boolean is_encoded;

    private final FileOutputStream FOS;
    private final File FILE;

    /// Wrapper che rappresenta un file già esistente
    protected SecureFile(String pathname) {
        FileOutputStream temp_fos = null;
        File temp_file = null;

        try {
            temp_file = new File(pathname);
            temp_fos = new FileOutputStream(temp_file, true);

            FileInputStream fis = new FileInputStream(temp_file);
            is_encoded = !Arrays.equals(fis.readNBytes(6), "clear\n".getBytes());
            fis.close();
        } catch (IOException _) {
            Logger.log("impossibile aprire il file " + pathname + ", il file non esiste", true);
        }

        FOS = temp_fos;
        FILE = temp_file;
    }

    /**
     * Crea un nuovo file specificando se cifrarne il contenuto o meno
     * @param pathname posizione del nuovo file
     * @param encoded true se si vuole cifrare il contenuto, false altrimenti
     */
    protected SecureFile(String pathname, boolean encoded) {
        this.is_encoded = encoded;
        this.FILE = new File(pathname);

        FileOutputStream temp_fos = null;

        try {
            if (!FILE.createNewFile())
                throw new IOException();

            temp_fos = new FileOutputStream(FILE);

            if (!encoded) {
                temp_fos.write("clear\n".getBytes());
            }
        }
        catch (IOException _) {
            Logger.log("impossibile creare il file: " + pathname, true);
        }

        FOS = temp_fos;
    }

    /**
     * Modifica la sicurezza del file
     * @param encode true per iniziare a cifrare il contenuto del file, false per smettere di cifrare
     */
    protected void set_encoded(boolean encode) {
        this.is_encoded = encode;
    }

    /**
     * Test sulla sicurezza del file
     * @return true se il suo contenuto è cifrato, false altrimenti
     */
    protected boolean is_protected() {
        return is_encoded;
    }

    /**
     * Legge il contenuto del file, nel caso sia cifrato viene decifrato prima di ritornarlo.
     * @return testo in chiaro contenuto nel file
     */
    protected byte[] read() {
        byte[] txt;
        try {
            FileInputStream fis = new FileInputStream(FILE);
            if (!is_encoded) {
                fis.readNBytes(6);
            } //salta i primi 6 byte (clear\n) poiché non sono contenuto del file ma indicano che il file non è cifrato

            txt = fis.readAllBytes();
            fis.close();
        }
        catch (IOException _) {
            Logger.log("impossibile leggere il contenuto del file: " + FILE.getAbsolutePath(), true);
            return null;
        }

        if (is_encoded) {
            try {
                txt = FileCipher.decode(txt);
            } catch (IllegalBlockSizeException | BadPaddingException _) {
                Logger.log("impossibile decifrare il contenuto del file: " + FILE.getAbsolutePath() + ", file corrotto", true);
                return null;
            }
        }

        return txt;
    }

    /**
     * Aggiunge dei dati a quelli scritti già sul file
     * @param data dati da aggiungere al file
     */
    protected void append(byte[] data) {
        if (is_encoded) {
            byte[] written_data = read();
            byte[] new_data = Arrays.copyOf(written_data, written_data.length + data.length);
            System.arraycopy(data, 0, new_data, written_data.length, data.length);

            replace(new_data);
        }
        else {
            try {
                FOS.write(data);
            }
            catch (IOException _) {
                Logger.log("impossibile scrivere nel file: " + FILE.getAbsolutePath(), true);
            }
        }
    }

    /**
     * Cancella ciò che era scritto sul file e scrive dei dati
     * @param data dati da scrivere nel file
     */
    protected void replace(byte[] data) {
        try { //elimina il contenuto del file
            new FileOutputStream(FILE, false).close();
        }
        catch (IOException _) {
            Logger.log("errore nell'eliminare il contenuto del file: " + FILE.getAbsolutePath(), true);
            return;
        }

        //calcola i bytes da scrivere nel file
        byte[] new_data;
        if (is_encoded) { //se deve cifrare il testo
            try {
                new_data = FileCipher.encode(data);

                if (new_data == null) //non è ancora stato inizializzato File_cipher
                    return;
            }
            catch (IllegalBlockSizeException | BadPaddingException _) {
                Logger.log("impossibile decifrare il contenuto del file: " + FILE.getAbsolutePath(), true);
                new_data = new byte[0];
            }
        }
        else { //se non deve cifrare il testo, indica che il file non è cifrato
            new_data = Arrays.copyOf("clear\n".getBytes(), data.length + 6);
            System.arraycopy(data, 0, new_data, 6, data.length);
        }

        //scrive il contenuto nel file
        try {
            FOS.write(new_data);
        }
        catch (IOException _) {
            Logger.log("impossibile scrivere nel file: " + FILE.getAbsolutePath(), true);
        }
    }

    /**
     * Elimina il file
     */
    protected void delete() {
        close();
        FILE.delete();
    }

    /**
     * Chiude gli stream aperti
     */
    protected void close() {
        try {
            FOS.close();
        } catch (IOException _) {}
    }
}
