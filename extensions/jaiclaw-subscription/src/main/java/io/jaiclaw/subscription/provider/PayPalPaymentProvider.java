package io.jaiclaw.subscription.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.subscription.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * PayPal payment provider using the Orders API v2 for checkout creation
 * and webhook processing for payment events.
 */
public class PayPalPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PayPalPaymentProvider.class);

    private final String clientId;
    private final String clientSecret;
    private final String baseUrl; // https://api-m.sandbox.paypal.com or https://api-m.paypal.com
    private final String webhookId;
    private final String returnUrl;
    private final String cancelUrl;
    private final boolean verifyWebhook;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PayPalPaymentProvider(String clientId, String clientSecret, boolean sandbox,
                                  String webhookId, String returnUrl, String cancelUrl) {
        this(clientId, clientSecret, sandbox, webhookId, returnUrl, cancelUrl, false);
    }

    public PayPalPaymentProvider(String clientId, String clientSecret, boolean sandbox,
                                  String webhookId, String returnUrl, String cancelUrl,
                                  boolean verifyWebhook) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = sandbox
                ? "https://api-m.sandbox.paypal.com"
                : "https://api-m.paypal.com";
        this.webhookId = webhookId;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
        this.verifyWebhook = verifyWebhook;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "paypal";
    }

    @Override
    public CheckoutResult createCheckout(String userId, SubscriptionPlan plan,
                                          Map<String, String> metadata) {
        try {
            String accessToken = getAccessToken();
            String orderJson = createOrderJson(plan, metadata);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v2/checkout/orders"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(orderJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                log.error("PayPal order creation failed: {} {}", response.statusCode(), response.body());
                throw new RuntimeException("PayPal order creation failed: " + response.statusCode());
            }

            Map<String, Object> body = mapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});

            String orderId = (String) body.get("id");
            String approvalUrl = extractApprovalUrl(body);

            return new CheckoutResult(approvalUrl, orderId, "paypal",
                    Map.of("paypal_order_id", orderId));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PayPal checkout creation failed", e);
        }
    }

    @Override
    public PaymentVerification verifyPayment(String paymentId) {
        try {
            String accessToken = getAccessToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v2/checkout/orders/" + paymentId))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return PaymentVerification.failure("PayPal verification failed: " + response.statusCode());
            }

            Map<String, Object> body = mapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
            String status = (String) body.get("status");
            boolean completed = "COMPLETED".equals(status) || "APPROVED".equals(status);

            return completed
                    ? PaymentVerification.success(paymentId, status)
                    : PaymentVerification.failure("Order status: " + status);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return PaymentVerification.failure("Verification error: " + e.getMessage());
        }
    }

    @Override
    public void cancelSubscription(String externalSubscriptionId) {
        try {
            String accessToken = getAccessToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/billing/subscriptions/" + externalSubscriptionId + "/cancel"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"reason\":\"User cancelled\"}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204 && response.statusCode() != 200) {
                log.warn("PayPal subscription cancellation returned {}: {}",
                        response.statusCode(), response.body());
            } else {
                log.info("Cancelled PayPal subscription: {}", externalSubscriptionId);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to cancel PayPal subscription: {}", e.getMessage());
        }
    }

    @Override
    public Optional<PaymentEvent> handleWebhook(String payload, Map<String, String> headers) {
        try {
            // Verify webhook signature when enabled
            if (verifyWebhook && webhookId != null && !webhookId.isBlank()) {
                if (!verifyWebhookSignature(payload, headers)) {
                    log.warn("PayPal webhook signature verification failed");
                    return Optional.empty();
                }
            }

            Map<String, Object> event = mapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() {});

            String eventType = (String) event.get("event_type");
            if (eventType == null) return Optional.empty();

            PaymentEventType type = switch (eventType) {
                case "PAYMENT.CAPTURE.COMPLETED" -> PaymentEventType.CHECKOUT_COMPLETED;
                case "BILLING.SUBSCRIPTION.CANCELLED" -> PaymentEventType.SUBSCRIPTION_CANCELLED;
                case "BILLING.SUBSCRIPTION.PAYMENT.FAILED" -> PaymentEventType.PAYMENT_FAILED;
                default -> null;
            };

            if (type == null) {
                log.debug("Ignoring PayPal event type: {}", eventType);
                return Optional.empty();
            }

            String eventId = (String) event.get("id");

            // Extract subscription ID from custom_id in resource
            String subscriptionId = null;
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) event.get("resource");
            if (resource != null) {
                subscriptionId = (String) resource.get("custom_id");
            }

            return Optional.of(new PaymentEvent(
                    eventId,
                    subscriptionId,
                    "paypal",
                    type,
                    null,
                    null,
                    Instant.now(),
                    Map.of("paypal_event_type", eventType)
            ));
        } catch (IOException e) {
            log.error("Failed to parse PayPal webhook: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String getAccessToken() throws IOException, InterruptedException {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + credentials)
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("PayPal auth failed: " + response.statusCode());
        }

        Map<String, Object> body = mapper.readValue(response.body(),
                new TypeReference<Map<String, Object>>() {});
        return (String) body.get("access_token");
    }

    private String createOrderJson(SubscriptionPlan plan, Map<String, String> metadata)
            throws IOException {
        String subscriptionId = metadata.getOrDefault("subscription_id", "");
        Map<String, Object> order = Map.of(
                "intent", "CAPTURE",
                "purchase_units", java.util.List.of(Map.of(
                        "amount", Map.of(
                                "currency_code", plan.currency(),
                                "value", plan.price().toPlainString()
                        ),
                        "description", plan.name(),
                        "custom_id", subscriptionId
                )),
                "application_context", Map.of(
                        "return_url", returnUrl,
                        "cancel_url", cancelUrl
                )
        );
        return mapper.writeValueAsString(order);
    }

    /**
     * Verify webhook signature by calling PayPal's verification API.
     *
     * @see <a href="https://developer.paypal.com/docs/api/webhooks/v1/#verify-webhook-signature_post">PayPal docs</a>
     */
    boolean verifyWebhookSignature(String payload, Map<String, String> headers) {
        try {
            String accessToken = getAccessToken();

            Map<String, Object> verifyBody = new java.util.LinkedHashMap<>();
            verifyBody.put("auth_algo", headers.getOrDefault("paypal-auth-algo", ""));
            verifyBody.put("cert_url", headers.getOrDefault("paypal-cert-url", ""));
            verifyBody.put("transmission_id", headers.getOrDefault("paypal-transmission-id", ""));
            verifyBody.put("transmission_sig", headers.getOrDefault("paypal-transmission-sig", ""));
            verifyBody.put("transmission_time", headers.getOrDefault("paypal-transmission-time", ""));
            verifyBody.put("webhook_id", webhookId);
            verifyBody.put("webhook_event", mapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() {}));

            String verifyJson = mapper.writeValueAsString(verifyBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/notifications/verify-webhook-signature"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(verifyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = mapper.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {});
                return "SUCCESS".equals(result.get("verification_status"));
            }
            log.warn("PayPal webhook verification returned HTTP {}", response.statusCode());
            return false;
        } catch (Exception e) {
            log.error("PayPal webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractApprovalUrl(Map<String, Object> body) {
        var links = (java.util.List<Map<String, Object>>) body.get("links");
        if (links != null) {
            for (var link : links) {
                if ("approve".equals(link.get("rel"))) {
                    return (String) link.get("href");
                }
            }
        }
        return "";
    }
}
