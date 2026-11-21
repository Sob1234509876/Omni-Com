package io.github.sob1234509876.osa.server.service;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OsaServerMailService {

    public static final String EMAIL_SUBJECT = "OSA verification code";

    @NonNull
    private ObjectProvider<JavaMailSender> javaMailSender;

    @Value("${spring.mail.username:osa@localhost}")
    private String from;

    public void sendVerificationCode(@NonNull String email, @NonNull String code) {
        var sender = javaMailSender.getIfAvailable();

        if (sender == null) {
            log.warn("Dev mode without SMTP: verification code for " + email + " is " + code);
            return;
        }

        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(EMAIL_SUBJECT);
        message.setText("Your verification code is: " + code + " (valid for 10 minutes)");

        sender.send(message);
    }

}
