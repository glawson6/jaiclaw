package io.jaiclaw.web.errors.webflux;

import io.jaiclaw.web.errors.core.WebErrorLogger;
import io.jaiclaw.web.errors.core.WebErrorMapper;
import io.jaiclaw.web.errors.core.WebErrorProperties;
import io.jaiclaw.web.errors.core.WebErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Lowest-precedence {@link ErrorWebExceptionHandler} for the reactive
 * stack. Registered at {@link Ordered#HIGHEST_PRECEDENCE} + 1 so it
 * runs <b>before</b> Spring Boot's built-in
 * {@code DefaultErrorWebExceptionHandler} (which is
 * {@link Ordered#LOWEST_PRECEDENCE} - 1). Adopter-registered
 * {@link WebExceptionHandler} beans placed higher in the chain (i.e.
 * with a numerically lower {@code @Order}) continue to win.
 *
 * <p>Why WebFlux is the harder half of this fix: WebFlux resolves
 * static-resource 404s inside the request pipeline <b>before</b>
 * {@code @ControllerAdvice} runs, so an adopter's
 * {@code @RestControllerAdvice(NoResourceFoundException.class)} never
 * fires. Only a properly-ordered {@link ErrorWebExceptionHandler} sees
 * these. This handler is the fix for that pattern.
 *
 * <p>Body is written via {@link DataBufferFactory#wrap(byte[])} — no
 * {@code ResponseEntity} because the reactive stack composes buffers
 * directly, not entities.
 */
public class JaiclawWebFluxErrorHandler implements ErrorWebExceptionHandler, Ordered {

    private final WebErrorMapper mapper;
    private final WebErrorProperties properties;
    private final WebErrorLogger errorLogger;

    public JaiclawWebFluxErrorHandler(WebErrorMapper mapper,
                                     WebErrorProperties properties,
                                     WebErrorLogger errorLogger) {
        this.mapper = mapper;
        this.properties = properties;
        this.errorLogger = errorLogger;
    }

    @Override
    public int getOrder() {
        // Higher precedence than Spring Boot's DefaultErrorWebExceptionHandler
        // (which is LOWEST_PRECEDENCE - 1) but leave room for adopters who
        // want their own handler in front by using Ordered.HIGHEST_PRECEDENCE.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            // Nothing we can do — bytes already on the wire.
            return Mono.error(throwable);
        }

        WebErrorResponse mapped = mapper.map(throwable, properties);
        String requestSummary = describe(exchange.getRequest());

        if (mapped.isServerError()) {
            errorLogger.log5xx(throwable, requestSummary);
        } else if (mapped.isClientError()) {
            errorLogger.log4xx(throwable, requestSummary);
        }

        HttpStatusCode status;
        try {
            status = HttpStatus.valueOf(mapped.status());
        } catch (RuntimeException e) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        response.setStatusCode(status);

        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(mapped.contentType());
        } catch (RuntimeException e) {
            contentType = MediaType.TEXT_PLAIN;
        }
        response.getHeaders().setContentType(contentType);

        String bodyText = mapped.body() == null ? "" : mapped.body();
        byte[] bytes = bodyText.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static String describe(ServerHttpRequest request) {
        if (request == null) return "<unknown>";
        String method = request.getMethod() == null ? "?" : request.getMethod().name();
        String path = request.getPath() == null || request.getPath().value() == null
                ? "?" : request.getPath().value();
        return method + " " + path;
    }
}
