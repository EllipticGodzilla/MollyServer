package files;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class SecureFile {
    private boolean is_encoded;

    private final FileOutputStream FOS;
    private final File FILE;

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

    protected SecureFile(String pathname, boolean encoded) { //crea un nuovo SecureFile da un file non esistente
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

    public void set_encoded(boolean encode) {
        this.is_encoded = encode;
    }

    public boolean is_protected() {
        return is_encoded;
    }

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

    protected void delete() {
        close();
        FILE.delete();
    }

    protected void close() {
        try {
            FOS.close();
        } catch (IOException _) {}
    }
}
