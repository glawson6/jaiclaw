package io.jaiclaw.channel.sms;

import java.util.Map;

/**
 * Abstraction over HTTP transport for Twilio Messages API calls.
 *
 * <p>Introduced at 1.0.0 alongside the RestTemplate → RestClient migration
 * (Boot 4 / Framework 7): {@link SmsAdapter} depends on this interface so
 * unit specs can mock at this layer instead of at the underlying HTTP
 * client's fluent surface. Reference impl: {@link RestClientSmsHttpClient}.
 */
public interface SmsHttpClient {

    /**
     * POST an application/x-www-form-urlencoded body with HTTP Basic auth,
     * returning the parsed JSON response as a {@code Map}.
     *
     * @param url         fully-qualified Twilio API URL
     * @param accountSid  Twilio account SID (basic-auth username)
     * @param authToken   Twilio auth token (basic-auth password)
     * @param formBody    the form-urlencoded body (e.g. {@code "From=...&To=..."})
     * @return parsed JSON response body, or null on empty response
     */
    Map<String, Object> postForm(String url, String accountSid, String authToken, String formBody);
}
