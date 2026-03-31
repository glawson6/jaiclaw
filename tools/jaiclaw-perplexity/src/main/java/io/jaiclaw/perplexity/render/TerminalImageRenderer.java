package io.jaiclaw.perplexity.render;

import io.jaiclaw.tools.exec.SsrfGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

public class TerminalImageRenderer {

    private static final Logger log = LoggerFactory.getLogger(TerminalImageRenderer.class);

    public enum Protocol { ITERM2, CHAFA, TEXT_ONLY }

    private volatile Protocol cachedProtocol;

    public Protocol detect() {
        if (cachedProtocol != null) return cachedProtocol;

        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null && (termProgram.contains("iTerm") || termProgram.contains("WezTerm"))) {
            cachedProtocol = Protocol.ITERM2;
            return cachedProtocol;
        }

        if (isOnPath("chafa")) {
            cachedProtocol = Protocol.CHAFA;
            return cachedProtocol;
        }

        cachedProtocol = Protocol.TEXT_ONLY;
        return cachedProtocol;
    }

    public void renderImage(byte[] imageData, String altText) {
        renderImage(imageData, altText, null);
    }

    public void renderImageUrl(String url, String altText) {
        Protocol protocol = detect();
        if (protocol == Protocol.TEXT_ONLY) {
            System.out.printf("[Image: %s] %s%n", altText != null ? altText : "image", url);
            return;
        }

        try {
            byte[] data = fetchUrl(url);
            renderImage(data, altText, url);
        } catch (Exception e) {
            log.warn("Failed to fetch image from {}: {}", url, e.getMessage());
            System.out.printf("[Image: %s] %s%n", altText != null ? altText : "image", url);
        }
    }

    private void renderImage(byte[] imageData, String altText, String url) {
        Protocol protocol = detect();

        switch (protocol) {
            case ITERM2 -> renderIterm2(imageData);
            case CHAFA -> renderChafa(imageData);
            case TEXT_ONLY -> {
                String display = altText != null ? altText : "image";
                if (url != null) {
                    System.out.printf("[Image: %s] %s%n", display, url);
                } else {
                    System.out.printf("[Image: %s]%n", display);
                }
            }
        }
    }

    private void renderIterm2(byte[] imageData) {
        String b64 = Base64.getEncoder().encodeToString(imageData);
        try {
            OutputStream out = System.out;
            out.write(("\033]1337;File=inline=1;width=auto;height=auto:" + b64 + "\007\n").getBytes());
            out.flush();
        } catch (IOException e) {
            log.warn("Failed to write iTerm2 image escape", e);
        }
    }

    private void renderChafa(byte[] imageData) {
        try {
            Path tempFile = Files.createTempFile("pplx-img-", ".png");
            Files.write(tempFile, imageData);

            ProcessBuilder pb = new ProcessBuilder("chafa", "--size=80x20", tempFile.toString());
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();

            Files.deleteIfExists(tempFile);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Failed to render with chafa: {}", e.getMessage());
        }
    }

    private byte[] fetchUrl(String url) throws IOException, InterruptedException {
        // SSRF protection: always-on for standalone CLI tools
        Optional<String> ssrfError = SsrfGuard.validate(url);
        if (ssrfError.isPresent()) {
            throw new IOException("SSRF protection: " + ssrfError.get());
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " fetching image");
        }
        return response.body();
    }

    static boolean isOnPath(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }
}
