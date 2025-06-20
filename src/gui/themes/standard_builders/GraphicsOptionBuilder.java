package gui.themes.standard_builders;

import javax.swing.*;

public interface GraphicsOptionBuilder<T> {
    T cast(String value_str); //data una stringa con le informazioni volute deve generare un oggetto
    String revert_cast(Object value); //dato un oggetto deve generare la stringa che lo rappresenta
    void display(JPanel panel, Object value); //dato un panello deve aggiungere tutte le componenti necessarie per modificare gli aspetti di questi oggetti
    void update(JPanel panel, Object value); //aggiorna i valori nel pannello con quelli dell'oggetto ricevuto
    void update_colors(JPanel panel); //aggiorna i colori delle componenti nel pannello ricevuto
    T new_value(JPanel panel); //dal pannello con i valori deve ritornare il nuovo oggetto con quelle caratteristiche
    default boolean equals(Object obj1, Object obj2) {
        return obj1.equals(obj2);
    } //ritorna true se i due oggetti sono da considerare uguali
}
