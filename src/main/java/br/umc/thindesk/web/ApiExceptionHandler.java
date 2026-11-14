package br.umc.thindesk.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.umc.thindesk.exception.ApiException;
import br.umc.thindesk.web.dto.ErroResponse;

/** Converte qualquer exceção no formato padrão de erro da API. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErroResponse> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ErroResponse.de(e.getCodigo(), e.getMessage(), e.getDetalhes()));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErroResponse> naoEncontrado(Exception e) {
        return ResponseEntity.status(404).body(ErroResponse.de("NAO_ENCONTRADO", "Rota não encontrada.", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> metodo(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405)
                .body(ErroResponse.de("METODO_NAO_PERMITIDO", "Método " + e.getMethod() + " não permitido nesta rota.", null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResponse> midia(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415)
                .body(ErroResponse.de("TIPO_NAO_SUPORTADO", "Envie o corpo como application/json.", null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.status(400)
                .body(ErroResponse.de("JSON_INVALIDO", "Corpo da requisição ausente ou com JSON malformado.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> interno(Exception e) {
        log.error("Erro não tratado", e);
        return ResponseEntity.status(500).body(ErroResponse.de("ERRO_INTERNO", "Erro interno no servidor.", null));
    }
}
