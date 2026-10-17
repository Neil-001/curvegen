package dev.curvegen.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Every production text literal must be a translation key or reviewed technical syntax. */
class TranslationSourceTest {
    // Match comments and character literals first, so quoted examples in them aren't UI text.
    private static final Pattern TOKENS = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/|'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");

    @Test
    void productionStringsHaveTranslationKeys() throws Exception {
        Path root = Path.of("..");
        Set<String> technical = new HashSet<>();
        try (var reader = Files.newBufferedReader(root.resolve("src/test/resources/translation-technical-literals.json"))) {
            JsonParser.parseReader(reader).getAsJsonArray().forEach(e -> technical.add(e.getAsString()));
        }
        Set<String> keys;
        try (var reader = Files.newBufferedReader(root.resolve("src/main/resources/assets/curvegen/lang/en_us.json"))) {
            keys = Set.copyOf(JsonParser.parseReader(reader).getAsJsonObject().keySet());
        }
        for (String dir : List.of("src/main/java", "src/client/java", "fabric/src", "neoforge/src")) {
            try (var paths = Files.walk(root.resolve(dir))) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    var matcher = TOKENS.matcher(source);
                    while (matcher.find()) {
                        String token = matcher.group();
                        if (!token.startsWith("\"")) continue;
                        String value = token.substring(1, token.length() - 1);
                        if (!LETTER.matcher(value).find() || technical.contains(value)) continue;
                        // Prefixes are used to construct state-specific keys. LangTest checks the generated names.
                        assertTrue(keys.contains(value) || keys.stream().anyMatch(k -> k.startsWith(value)),
                                file + ": unregistered text literal " + token);
                    }
                }
            }
        }
    }
}
