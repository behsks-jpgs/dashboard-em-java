package br.umc.thindesk;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ThindeskApplication {

    public static void main(String[] args) {
        // datas gravadas e calculadas no fuso do negócio
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(ThindeskApplication.class, args);
    }
}
