package top.sob.platform.test;

import org.apache.log4j.BasicConfigurator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import top.sob.platform.api.PlatformBuilder;

import java.net.URISyntaxException;
import java.util.Set;

public class MainTest {

    static {
        BasicConfigurator.configure();
    }

    static final String HW_TEST_JAR = "assets/jars/hello-world-~.jar";
    static final String UI_TEST_JAR = "assets/jars/ui-~.jar";

    @Test
    public void testHW() throws URISyntaxException {
        testByJar(HW_TEST_JAR);
    }

    @Test
    public void testUI() throws URISyntaxException {
        testByJar(UI_TEST_JAR);
    }

    void testByJar(String jarPath) throws URISyntaxException {
        var jar = MainTest.class.getClassLoader().getResource(jarPath);

        Assertions.assertNotNull(jar);

        new PlatformBuilder(Set.of(jar.toURI()), null, new String[0]).build().exec();
    }
}
