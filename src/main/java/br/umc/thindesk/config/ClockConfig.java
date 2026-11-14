package br.umc.thindesk.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Relógio injetável: nos testes usamos um Clock fixo para resultados determinísticos. */
    @Bean
    public Clock clock(@Value("${thindesk.zona:America/Sao_Paulo}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
