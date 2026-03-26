package io.jaiclaw.subscription;

/**
 * Result of verifying a payment with a provider.
 *
 * @param verified      whether the payment was verified successfully
 * @param paymentId     provider-specific payment ID
 * @param status        payment status from the provider
 * @param errorMessage  error message if verification failed
 */
public record PaymentVerification(
        boolean verified,
        String paymentId,
        String status,
        String errorMessage
) {
    public static PaymentVerification success(String paymentId, String status) {
        return new PaymentVerification(true, paymentId, status, null);
    }

    public static PaymentVerification failure(String errorMessage) {
        return new PaymentVerification(false, null, null, errorMessage);
    }
}
