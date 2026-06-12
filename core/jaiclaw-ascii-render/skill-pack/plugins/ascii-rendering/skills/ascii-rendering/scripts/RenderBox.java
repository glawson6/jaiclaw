///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS io.jaiclaw:jaiclaw-ascii-render:0.8.1-SNAPSHOT
//REPOS taptech=https://tooling.taptech.net/repository/maven-public/

import io.jaiclaw.asciirender.factory.AsciiBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wrap text in a Unicode-bordered ASCII box.
 *
 * <p>Companion to {@code RenderScene.java}; mirrors the {@code ascii_box}
 * built-in tool. Content can come from stdin, a file, or the {@code --content}
 * flag. Title and border style are independent flags.
 *
 * <p>Usage:
 * <pre>
 *   echo "Build green" | jbang RenderBox.java --title=STATUS --border=double
 *   jbang RenderBox.java --content "hello" --width 30
 *   jbang RenderBox.java --file message.txt --border=rounded
 * </pre>
 *
 * <p>Flags:
 * <ul>
 *   <li>{@code --content TEXT} — content (alternative to stdin / --file)</li>
 *   <li>{@code --file PATH} — read content from a file</li>
 *   <li>{@code --width N} — max inner content width (default 60, clamped [4, 500])</li>
 *   <li>{@code --border STYLE} — single | double | bold | rounded (default single)</li>
 *   <li>{@code --title TEXT} — optional banner rendered on the top edge</li>
 *   <li>{@code -h, --help} — show usage</li>
 * </ul>
 *
 * <p>Exit codes:
 * <ul>
 *   <li>0 — rendered successfully</li>
 *   <li>1 — content was missing or rendering failed</li>
 *   <li>2 — usage error (unknown flag, missing value)</li>
 * </ul>
 */
public class RenderBox {

    public static void main(String[] args) throws IOException {
        String content = null;
        Path file = null;
        int width = AsciiBox.DEFAULT_WIDTH;
        AsciiBox.Style style = AsciiBox.Style.SINGLE;
        String title = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--content" -> content = requireValue(args, ++i, "--content");
                case "--file"    -> file = Path.of(requireValue(args, ++i, "--file"));
                case "--width"   -> {
                    try { width = Integer.parseInt(requireValue(args, ++i, "--width")); }
                    catch (NumberFormatException e) {
                        System.err.println("ascii-box: --width must be an integer");
                        System.exit(2);
                    }
                }
                case "--border"  -> {
                    String key = requireValue(args, ++i, "--border");
                    style = AsciiBox.Style.resolve(key);
                    if (style == null) {
                        System.err.println("ascii-box: unknown --border '" + key
                                + "'; using 'single'. Valid: single, double, bold, rounded.");
                        style = AsciiBox.Style.SINGLE;
                    }
                }
                case "--title"   -> title = requireValue(args, ++i, "--title");
                case "-h", "--help" -> {
                    printHelp();
                    System.exit(0);
                }
                default -> {
                    // Support --key=value form too
                    int eq = arg.indexOf('=');
                    if (arg.startsWith("--") && eq > 0) {
                        String[] split = {arg.substring(0, eq), arg.substring(eq + 1)};
                        // re-process via tiny recursion-by-splice
                        String[] expanded = new String[args.length + 1];
                        System.arraycopy(args, 0, expanded, 0, i);
                        expanded[i] = split[0];
                        expanded[i + 1] = split[1];
                        System.arraycopy(args, i + 1, expanded, i + 2, args.length - i - 1);
                        main(expanded);
                        return;
                    }
                    System.err.println("ascii-box: unknown argument '" + arg + "'");
                    System.exit(2);
                }
            }
        }

        if (content == null && file != null) {
            content = Files.readString(file, StandardCharsets.UTF_8);
        }
        if (content == null) {
            content = readStdin();
        }
        if (content == null || content.isEmpty()) {
            System.err.println("ascii-box: no content — pass --content, --file, or pipe to stdin");
            System.exit(1);
        }

        try {
            String boxed = AsciiBox.render(content, width, style, title);
            System.out.print(boxed);
            if (!boxed.endsWith("\n")) {
                System.out.println();
            }
        } catch (IllegalArgumentException e) {
            System.err.println("ascii-box: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String requireValue(String[] args, int i, String flag) {
        if (i >= args.length) {
            System.err.println("ascii-box: " + flag + " requires a value");
            System.exit(2);
        }
        return args[i];
    }

    private static String readStdin() throws IOException {
        if (System.console() != null) {
            // running interactively; don't block waiting for piped input
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void printHelp() {
        System.out.println("""
                Usage: RenderBox.java [--content TEXT | --file PATH] [--width N]
                                      [--border STYLE] [--title TEXT]

                  Wrap text in a Unicode-bordered ASCII box.

                  --content TEXT       Text to put in the box.
                  --file PATH          Read content from a file.
                                       (Alternatively, pipe to stdin.)
                  --width N            Max inner width (default 60; clamped [4, 500]).
                  --border STYLE       single | double | bold | rounded (default single).
                  --title TEXT         Banner rendered on the top edge as "[ TEXT ]".

                Examples:
                  echo "Build green" | jbang RenderBox.java --title=STATUS --border=double
                  jbang RenderBox.java --content "hello" --width 30
                  jbang RenderBox.java --file message.txt --border=rounded""");
    }
}
