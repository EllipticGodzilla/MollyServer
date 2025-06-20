package files.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RunAtStart {
    int START = 0;
    int INIT = 1;
    int DECODE = 2;
    int END = 3;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Fire {
        /*
         * si può decidere quando eseguire questo metodo:
         * 0) viene eseguito subito dopo aver cercato tutti i file, ma nulla è stato inizializzato, default
         * 1) viene eseguito subito prima richiedere la password per decifrare i file
         * 2) viene eseguito subito dopo aver sbloccato tutti i file cifrati
         */
        int run_at() default RunAtStart.START;
    }
}
