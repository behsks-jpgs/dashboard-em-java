package br.umc.thindesk.web;

import java.time.Clock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import br.umc.thindesk.repository.CatalogoGateway;
import br.umc.thindesk.repository.ChamadoGateway;
import br.umc.thindesk.support.Fixtures;

/** Substitui o MongoDB por gateways em memória e fixa o relógio nos testes de API. */
@TestConfiguration
public class ApiTestConfig {
    @Bean
    ChamadoGateway chamadoGateway() { return Fixtures.gatewayChamados(); }

    @Bean
    CatalogoGateway catalogoGateway() { return Fixtures.gatewayCatalogo(); }

    @Bean
    Clock clock() { return Fixtures.clock(); }
}
