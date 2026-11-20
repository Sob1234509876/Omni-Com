package io.github.sob1234509876.osftp;

import io.github.sob1234509876.osftp.dao.ftp.FtpTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.charset.StandardCharsets;

@Slf4j
@SpringBootApplication
public class Main {
    public static void main(String[] args) {

        System.setProperty("javax.net.debug", "ssl:handshake:session");

        var context = SpringApplication.run(Main.class, args);
        var ftp = context.getBean(FtpTemplate.class);

        ftp.makeDirectories("/test");
        ftp.writeFile("/test/hello world.txt", "Hello world!".getBytes(StandardCharsets.UTF_8));
        log.info(new String(ftp.readFile("/test/hello world.txt"), StandardCharsets.UTF_8));
        ftp.deleteFile("/test/hello world.txt");
        ftp.deleteFile("/test");
    }
}
