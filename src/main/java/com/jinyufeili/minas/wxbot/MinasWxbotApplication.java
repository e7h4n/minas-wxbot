package com.jinyufeili.minas.wxbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MinasWxbotApplication {

    public static void main(String[] args) {
        System.setProperty("jsse.enableSNIExtension", "false");

        SpringApplication.run(MinasWxbotApplication.class, args);
    }
}
