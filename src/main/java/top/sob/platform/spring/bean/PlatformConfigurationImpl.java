package top.sob.platform.spring.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformConfigurationImpl implements PlatformConfiguration {

    public static final String MAIN_BEAN_CLASS_PROPERTY = "launch.main-bean-class";
    public static final String MAIN_BEAN_NAME_PROPERTY = "launch.main-bean-name";
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    Properties properties;

    public PlatformConfigurationImpl(@NonNull InputStream is) throws IOException {
        Assert.notNull(is, "Argument \"is\" is null");

        var p = new Properties();
        p.load(new InputStreamReader(is, DEFAULT_CHARSET));
        setProperties(p);
    }

    @Override
    public String getMainBeanClass() {
        return getProperties().getProperty(MAIN_BEAN_CLASS_PROPERTY);
    }

    @Override
    public String getMainBeanName() {
        return getProperties().getProperty(MAIN_BEAN_NAME_PROPERTY);
    }
}
