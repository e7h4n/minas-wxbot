package com.jinyufeili.minas.wxbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MinasWxbotApplication {

    public static void main(String[] args) {
        System.setProperty("jsse.enableSNIExtension", "false");

        SpringApplication.run(MinasWxbotApplication.class, args);
    }
}
