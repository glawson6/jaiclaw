package io.jaiclaw.identity.link;

import io.jaiclaw.core.model.IdentityLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Receives the identity provider's redirect at the end of a channel-link flow.
 *
 * <p>The endpoint is reached by the <strong>user's browser</strong>, not by an
 * API client, so it is unauthenticated — the caller has, by definition, not yet
 * proven who they are. The OAuth {@code state} is what makes that safe: it is a
 * single-use, unguessable nonce this gateway minted, and every decision about
 * <em>which</em> channel user is being linked comes from the server-side record
 * it keys, never from the request.
 *
 * <p>Responses are plain HTML because a person is reading them.
 *
 * <p>1.4.0.
 */
@RestController
public class ChannelLinkController {

    private static final Logger log = LoggerFactory.getLogger(ChannelLinkController.class);

    private final ChannelLinkService linkService;

    public ChannelLinkController(ChannelLinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping(path = "/api/identity/link/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {

        if (error != null) {
            // The user declined, or the provider refused. Not an error on our side.
            log.debug("Identity provider returned error '{}' on link callback", error);
            return html(400, "Sign-in was not completed",
                    "You can close this window and try again from the chat.");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return html(400, "Something went wrong",
                    "The sign-in link was incomplete. Please request a new one.");
        }

        Optional<IdentityLink> link = linkService.complete(state, code);
        if (link.isEmpty()) {
            // Covers expired, replayed, unknown, and a failed exchange alike.
            // Distinguishing them here would help someone probing for live
            // nonces and help a genuine user not at all.
            return html(400, "This sign-in link is no longer valid",
                    "Links expire after a few minutes and can only be used once. "
                            + "Please request a new one from the chat.");
        }

        return html(200, "You're signed in",
                "Your account is now linked. You can close this window and return to the chat.");
    }

    private static ResponseEntity<String> html(int status, String heading, String message) {
        String body = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title>
                <style>
                  body{font-family:system-ui,-apple-system,sans-serif;max-width:32rem;
                       margin:4rem auto;padding:0 1.5rem;line-height:1.6;color:#1a1a1a}
                  h1{font-size:1.4rem;margin-bottom:.5rem}
                  p{color:#555}
                  @media(prefers-color-scheme:dark){
                    body{background:#111;color:#eee} p{color:#aaa}}
                </style></head>
                <body><h1>%s</h1><p>%s</p></body></html>
                """.formatted(escape(heading), escape(heading), escape(message));
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    /** Defence in depth — today's strings are all constants, but that can change. */
    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
