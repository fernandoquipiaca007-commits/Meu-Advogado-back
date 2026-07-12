package com.activecourses.upwork.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.beans.factory.annotation.Value;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${SPRING_MAIL_HOST:smtp.gmail.com}")
    private String host;

    @Value("${SPRING_MAIL_PORT:587}")
    private int port;

    @Value("${SPRING_MAIL_USERNAME:}")
    private String username;

    @Value("${SPRING_MAIL_PASSWORD:}")
    private String password;

    @Value("${SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH:true}")
    private String smtpAuth;

    @Value("${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE:true}")
    private String starttlsEnable;
    
    @Bean
    public JavaMailSender getJavaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.starttls.enable", starttlsEnable);
        props.put("mail.debug", "${DEBUG:false}");

        return mailSender;
    }
}
