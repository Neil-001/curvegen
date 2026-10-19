package dev.curvegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TranslationResourcesTest {
    private static final Path LANG = Path.of("../src/main/resources/assets/curvegen/lang");
    // Matches Chroma Studio's languages, including the shared English source.
    private static final Set<String> LOCALES = Set.of("de_de", "en_us", "es_es", "fr_fr", "ja_jp",
            "ko_kr", "pl_pl", "pt_br", "ru_ru", "zh_cn");
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    private static Map<String, String> read(Path file) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (var reader = new JsonReader(Files.newBufferedReader(file))) {
            reader.setLenient(false);   // 1.21.1's Gson has no Strictness
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                assertEquals(JsonToken.STRING, reader.peek(), file + ": " + key);
                String value = reader.nextString();
                assertFalse(value.isBlank(), file + ": " + key);
                assertNull(entries.put(key, value), file + ": duplicate " + key);
            }
            reader.endObject();
            assertEquals(JsonToken.END_DOCUMENT, reader.peek(), file.toString());
        }
        return entries;
    }

    private static Map<String, Integer> arguments(String text) {
        Map<String, Integer> counts = new HashMap<>();
        var matcher = FORMAT.matcher(text);
        int implicit = 1;
        while (matcher.find()) {
            String argument = matcher.group().equals("%%") ? "%"
                    : matcher.group(1) == null ? Integer.toString(implicit++) : matcher.group(1);
            counts.merge(argument, 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void allLanguagesHaveEveryEntryAndPreserveFormattingArguments() throws Exception {
        Map<String, String> english = read(LANG.resolve("en_us.json"));
        try (var files = Files.list(LANG)) {
            assertEquals(LOCALES.stream().map(locale -> locale + ".json").collect(java.util.stream.Collectors.toSet()),
                    files.map(file -> file.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
        for (String locale : LOCALES) {
            Map<String, String> translated = read(LANG.resolve(locale + ".json"));
            assertEquals(english.keySet(), translated.keySet(), locale);
            for (var entry : english.entrySet()) {
                String context = locale + ": " + entry.getKey();
                assertEquals(arguments(entry.getValue()), arguments(translated.get(entry.getKey())), context);
            }
        }
    }
}
