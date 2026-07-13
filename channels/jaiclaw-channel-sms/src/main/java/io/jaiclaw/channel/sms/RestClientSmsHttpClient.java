package io.jaiclaw.channel.sms;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * {@link RestClient}-backed implementation of {@link SmsHttpClient}.
 *
 * <p>Introduced at 1.0.0 with the Boot 4 / Spring Framework 7 upgrade
 * (RestTemplate → RestClient).
 */
public final class RestClientSmsHttpClient implements SmsHttpClient {

    private final RestClient restClient;

    public RestClientSmsHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public RestClientSmsHttpClient() {
        this(RestClient.create());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> postForm(String url, String accountSid, String authToken, String formBody) {
        String basic = java.util.Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return restClient.post()
                .uri(url)
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .body(Map.class);
    }
}
