package gui.temppanel;

import java.util.Vector;

public abstract class TempPanel_action {
    public Vector<Object> input = new Vector<>();

    public abstract void success();
    public void annulla() {} //di default non fa nulla
}
