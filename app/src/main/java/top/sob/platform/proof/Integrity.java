package top.sob.platform.proof;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Integrity {

    int PERFECT = Integer.MAX_VALUE;

    int RECOMMENDED = 10000;
    int LEGACY = 9000;
    int HEALTHY = 8000;
    int STABLE = 7000;
    int MAINTAINED = 6000;
    int EXPERIMENTAL = 5000;
    int BLOATED = 4000;
    int UNRECOMMENDED = 0;

    int DEPRECATED = Integer.MIN_VALUE;

    int integrity();

}
