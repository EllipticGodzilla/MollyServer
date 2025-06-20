package files.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Connector {
    String name();
    int type();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface handshake {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface sender {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface reader {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface closer {}
}
