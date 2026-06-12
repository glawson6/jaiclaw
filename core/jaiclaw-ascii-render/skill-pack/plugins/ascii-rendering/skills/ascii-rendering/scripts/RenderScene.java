///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS io.jaiclaw:jaiclaw-ascii-render:0.8.1-SNAPSHOT
//REPOS taptech=https://tooling.taptech.net/repository/maven-public/

import io.jaiclaw.asciirender.factory.AsciiSceneFactory;
import io.jaiclaw.asciirender.factory.SceneSpecException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Render an ASCII scene from JSON input.
 *
 * <p>Reads a JSON scene document from either {@code stdin} or the file
 * named by {@code --file <path>}, then prints the rendered ASCII to
 * {@code stdout}. Designed to be invoked from an Anthropic Claude Skill
 * sandbox where the model writes JSON to a temp file then calls this
 * script.
 *
 * <p>Scene JSON shape:
 * <pre>{@code
 * {
 *   "width":  40,
 *   "height": 5,
 *   "elements": [
 *     {"type": "rectangle"},
 *     {"type": "label", "params": {"text": "Hi", "x": 18, "y": 2}}
 *   ]
 * }
 * }</pre>
 *
 * <p>Usage:
 * <pre>
 *   echo '{...}' | jbang RenderScene.java
 *   jbang RenderScene.java --file scene.json
 *   jbang RenderScene.java &lt; scene.json
 * </pre>
 *
 * <p>Exit codes:
 * <ul>
 *   <li>0 — rendered successfully</li>
 *   <li>1 — input was malformed or rendering failed; details on stderr</li>
 *   <li>2 — usage error (e.g. unknown flag)</li>
 * </ul>
 */
public class RenderScene {

    public static void main(String[] args) throws IOException {
        String json = readInput(args);
        if (json == null || json.isBlank()) {
            System.err.println("ascii-render: no JSON input — pipe a scene to stdin or pass --file <path>");
            System.exit(2);
        }
        try {
            String ascii = AsciiSceneFactory.renderJson(json);
            System.out.print(ascii);
            if (!ascii.endsWith("\n")) {
                System.out.println();
            }
        } catch (SceneSpecException e) {
            String prefix = e.elementIndex() < 0 ? "" : "[element " + e.elementIndex() + "]: ";
            System.err.println("ascii-render: " + prefix + e.getMessage());
            System.exit(1);
        }
    }

    private static String readInput(String[] args) throws IOException {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("ascii-render: --file requires a path argument");
                        System.exit(2);
                    }
                    return Files.readString(Path.of(args[i + 1]), StandardCharsets.UTF_8);
                }
                case "-h", "--help" -> {
                    System.out.println("""
                            Usage: RenderScene.java [--file <path>]
                              Reads a scene JSON from stdin (or --file) and prints rendered ASCII.
                              See https://github.com/jaiclaw/jaiclaw/tree/main/core/jaiclaw-ascii-render
                              for the element catalogue.""");
                    System.exit(0);
                }
                default -> {
                    System.err.println("ascii-render: unknown argument '" + args[i] + "'");
                    System.exit(2);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
