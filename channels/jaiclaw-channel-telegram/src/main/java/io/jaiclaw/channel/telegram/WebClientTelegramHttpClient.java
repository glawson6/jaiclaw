package io.jaiclaw.channel.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

/**
 * {@link TelegramHttpClient} implementation using Spring WebFlux's reactive
 * {@link WebClient}. Requires {@code spring-webflux} and {@code reactor-netty-http}
 * on the classpath.
 *
 * <p>All calls are blocking ({@code .block()}) since {@link TelegramAdapter}
 * uses a synchronous call model. The benefit over {@link RestTemplateTelegramHttpClient}
 * is Reactor Netty's non-blocking I/O layer, which avoids virtual thread pinning
 * from {@code HttpURLConnection}'s synchronized methods.
 */
public class WebClientTelegramHttpClient implements TelegramHttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final Duration requestTimeout;

    /**
     * @param pollingTimeoutSeconds Telegram long-poll timeout; the HTTP read timeout
     *                              is set to this value plus a 10-second buffer
     */
    public WebClientTelegramHttpClient(int pollingTimeoutSeconds) {
        this.requestTimeout = Duration.ofSeconds(pollingTimeoutSeconds + 10);
        HttpClient nettyClient = HttpClient.create()
                .responseTimeout(requestTimeout);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(nettyClient))
                .build();
    }

    /**
     * Constructor that accepts a pre-configured {@code WebClient} (for testing).
     */
    WebClientTelegramHttpClient(WebClient webClient, int pollingTimeoutSeconds) {
        this.webClient = webClient;
        this.requestTimeout = Duration.ofSeconds(pollingTimeoutSeconds + 10);
    }

    @Override
    public JsonNode get(String url) {
        try {
            String body = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(requestTimeout);
            return MAPPER.readTree(body);
        } catch (TelegramHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramHttpException("GET " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public JsonNode post(String url, Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            String responseBody = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(json)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(requestTimeout);
            return MAPPER.readTree(responseBody);
        } catch (TelegramHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramHttpException("POST " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public JsonNode postMultipart(String url, Map<String, Object> parts) {
        try {
            MultiValueMap<String, Object> multipartData = new LinkedMultiValueMap<>();
            for (var entry : parts.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof MultipartFile mp) {
                    ByteArrayResource resource = new ByteArrayResource(mp.data()) {
                        @Override
                        public String getFilename() {
                            return mp.filename();
                        }
                    };
                    multipartData.add(entry.getKey(), resource);
                } else if (value instanceof byte[] bytes) {
                    ByteArrayResource resource = new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return "file";
                        }
                    };
                    multipartData.add(entry.getKey(), resource);
                } else {
                    multipartData.add(entry.getKey(), value);
                }
            }

            String responseBody = webClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipartData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(requestTimeout);
            return MAPPER.readTree(responseBody);
        } catch (TelegramHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramHttpException("POST multipart " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public byte[] getBytes(String url) {
        try {
            byte[] bytes = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(requestTimeout);
            if (bytes == null) {
                throw new TelegramHttpException("GET bytes " + redactUrl(url) + " returned null");
            }
            return bytes;
        } catch (TelegramHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramHttpException("GET bytes " + redactUrl(url) + " failed", e);
        }
    }

    private static String redactUrl(String url) {
        return url.replaceAll("bot[^/]+/", "bot<redacted>/");
    }
}
