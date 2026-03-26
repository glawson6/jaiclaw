package io.jaiclaw.channel.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * JSON-RPC 2.0 client that communicates over TCP or stdio streams.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>TCP</b>: connects to {@code localhost:port} via {@link Socket}</li>
 *   <li><b>Stdio</b>: reads/writes on provided {@link InputStream}/{@link OutputStream}</li>
 * </ul>
 *
 * <p>Newline-delimited JSON — each message is a single line terminated by {@code \n}.
 *
 * <p>Supports push notifications via a listener callback. When a
 * notification (JSON-RPC message without "id") is received during
 * response reading, it is dispatched to the listener.
 */
public class JsonRpcClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcClient.class);

    private final ObjectMapper mapper;
    private final AtomicInteger requestId = new AtomicInteger(1);

    private BufferedWriter writer;
    private BufferedReader reader;
    private Socket socket;
    private Consumer<JsonNode> notificationListener;

    public JsonRpcClient() {
        this(new ObjectMapper());
    }

    public JsonRpcClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Connect to a TCP endpoint.
     */
    public void connectTcp(String host, int port) throws IOException {
        socket = new Socket(host, port);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        log.debug("JSON-RPC client connected to {}:{}", host, port);
    }

    /**
     * Connect via stdio streams (e.g. from a {@link Process}).
     */
    public void connectStdio(InputStream inputStream, OutputStream outputStream) {
        writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        log.debug("JSON-RPC client connected via stdio");
    }

    /**
     * Set a listener for JSON-RPC notifications (messages without "id").
     */
    public void setNotificationListener(Consumer<JsonNode> listener) {
        this.notificationListener = listener;
    }

    /**
     * Send a JSON-RPC request and wait for the matching response.
     *
     * @return the "result" field of the response, or throws on error
     */
    public JsonNode sendRequest(String method, Map<String, Object> params) throws IOException {
        int id = requestId.getAndIncrement();
        var request = mapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method);
        request.set("params", mapper.valueToTree(params));

        String json = mapper.writeValueAsString(request);
        writeLine(json);

        // Read lines until we get a response with matching id
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("JSON-RPC connection closed unexpectedly");
            }

            JsonNode response = mapper.readTree(line);

            // Check if this is a notification (no "id" field)
            if (!response.has("id") || response.get("id").isNull()) {
                if (notificationListener != null) {
                    notificationListener.accept(response);
                }
                continue;
            }

            // Check if id matches our request
            if (response.get("id").asInt() != id) {
                log.warn("Received response with unexpected id={}, expected={}", response.get("id").asInt(), id);
                continue;
            }

            if (response.has("error")) {
                JsonNode error = response.get("error");
                throw new IOException("JSON-RPC error: " + error.path("message").asText());
            }

            return response.get("result");
        }
    }

    /**
     * Send a JSON-RPC notification (no response expected).
     */
    public void sendNotification(String method, Map<String, Object> params) throws IOException {
        var notification = mapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("method", method);
        notification.set("params", mapper.valueToTree(params));

        writeLine(mapper.writeValueAsString(notification));
    }

    /**
     * Read a single line from the input stream.
     * Returns null if the stream is closed.
     */
    public String readLine() throws IOException {
        return reader.readLine();
    }

    /**
     * Start a background thread that reads notifications from the stream
     * and dispatches them to the notification listener.
     *
     * @return the reader thread
     */
    public Thread startNotificationReader() {
        return Thread.ofVirtual().name("jsonrpc-notification-reader").start(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        JsonNode message = mapper.readTree(line);
                        if (notificationListener != null) {
                            notificationListener.accept(message);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse JSON-RPC notification: {}", e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    log.debug("JSON-RPC notification reader stopped: {}", e.getMessage());
                }
            }
        });
    }

    private void writeLine(String json) throws IOException {
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (reader != null) {
            reader.close();
        }
    }
}
