package io.jaiclaw.channel.telegram;

import tools.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * {@link TelegramHttpClient} implementation using Spring's {@link RestTemplate}
 * with {@link SimpleClientHttpRequestFactory} configured with explicit timeouts.
 *
 * <p>Note: {@code HttpURLConnection} (used by {@code SimpleClientHttpRequestFactory})
 * has synchronized methods that can cause virtual thread pinning. Prefer
 * {@link JdkHttpClientTelegramHttpClient} for virtual-thread-heavy deployments.
 */
public class RestTemplateTelegramHttpClient implements TelegramHttpClient {

    private final RestTemplate restTemplate;

    /**
     * @param pollingTimeoutSeconds Telegram long-poll timeout; the HTTP read timeout
     *                              is set to this value plus a 10-second buffer
     */
    public RestTemplateTelegramHttpClient(int pollingTimeoutSeconds) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout((pollingTimeoutSeconds + 10) * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Constructor that accepts a pre-configured {@code RestTemplate} (for testing).
     */
    RestTemplateTelegramHttpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public JsonNode get(String url) {
        try {
            var response = restTemplate.getForEntity(url, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TelegramHttpException(response.getStatusCode().value(),
                        "GET returned HTTP " + response.getStatusCode());
            }
            return response.getBody();
        } catch (TelegramHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramHttpException("GET " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public JsonNode post(String url, Object body) {
        try {
            var response = restTemplate.postForEntity(url, body, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TelegramHttpException(response.getStatusCode().value(),
                        "POST returned HTTP " + response.getStatusCode());
            }
            return response.getBody();
        } catch (TelegramHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramHttpException("POST " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public JsonNode postMultipart(String url, Map<String, Object> parts) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (var entry : parts.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof MultipartFile mp) {
                    ByteArrayResource resource = new ByteArrayResource(mp.data()) {
                        @Override
                        public String getFilename() {
                            return mp.filename();
                        }
                    };
                    body.add(entry.getKey(), resource);
                } else if (value instanceof byte[] bytes) {
                    ByteArrayResource resource = new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return "file";
                        }
                    };
                    body.add(entry.getKey(), resource);
                } else {
                    body.add(entry.getKey(), value);
                }
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            var response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TelegramHttpException(response.getStatusCode().value(),
                        "POST multipart returned HTTP " + response.getStatusCode());
            }
            return response.getBody();
        } catch (TelegramHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramHttpException("POST multipart " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public byte[] getBytes(String url) {
        try {
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
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
