package com.controlers;

import com.dtos.ApiErrorDto;
import com.exceptions.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.PropertyValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(org.springframework.security.core.AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header("WWW-Authenticate", "Bearer")
                .body(new ApiErrorDto(401, "Usuário, senha ou token inválidos. Entre novamente."));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return response(HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação.");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(EntityNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex) {
        return response(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleInvalidArgument(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Não devolver mensagens do driver: podem conter SQL e dados do cadastro.
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PropertyValueException) {
                return response(HttpStatus.BAD_REQUEST, "Preencha os campos obrigatórios corretamente.");
            }
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if ("23502".equals(state) || "23514".equals(state) || (state != null && state.startsWith("22"))) {
                    return response(HttpStatus.BAD_REQUEST, "Preencha os campos obrigatórios com valores válidos.");
                }
                if ("23505".equals(state)) {
                    return response(HttpStatus.CONFLICT, "Já existe um registro com os dados informados.");
                }
                // PostgreSQL e H2 usam códigos diferentes para vínculos existentes/ausentes.
                if ("23503".equals(state) || "23506".equals(state)) {
                    return response(HttpStatus.CONFLICT, "A operação conflita com os vínculos entre os registros.");
                }
            }
        }
        return handleUnexpected(ex);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Object> handleConcurrency(ConcurrencyFailureException ex) {
        return response(HttpStatus.CONFLICT, "Outra operação alterou ou está utilizando estes dados. Tente novamente.");
    }

    @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
    public ResponseEntity<Object> handleAmbiguousResult(IncorrectResultSizeDataAccessException ex) {
        return response(HttpStatus.CONFLICT, "A consulta encontrou mais de um registro para os dados informados.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Falha inesperada ao processar requisição da API", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação. Tente novamente mais tarde.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                              HttpStatusCode status, WebRequest request) {
        String message = switch (status.value()) {
            case 400 -> "Requisição inválida. Confira o corpo JSON, os campos e seus formatos.";
            case 404 -> "Recurso não encontrado.";
            case 405 -> "Método HTTP não permitido para este endereço.";
            case 406 -> "Formato de resposta solicitado não suportado.";
            case 413 -> "O conteúdo enviado excede o tamanho permitido.";
            case 415 -> "Tipo de conteúdo não suportado. Envie JSON com Content-Type: application/json.";
            case 503 -> "Serviço temporariamente indisponível. Tente novamente mais tarde.";
            default -> status.is5xxServerError()
                    ? "Não foi possível concluir a operação. Tente novamente mais tarde."
                    : "Não foi possível processar a requisição.";
        };
        if (status.is5xxServerError()) {
            log.error("Falha ao processar requisição da API", ex);
        }
        return super.handleExceptionInternal(ex, new ApiErrorDto(status.value(), message), headers, status, request);
    }

    private ResponseEntity<Object> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiErrorDto(status.value(), message));
    }
}
