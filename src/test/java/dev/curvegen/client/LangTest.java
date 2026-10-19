package dev.curvegen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.curvegen.core.Count3;
import dev.curvegen.core.Expr;
import dev.curvegen.core.Message;
import dev.curvegen.core.Pieces;
import dev.curvegen.core.PresetData;
import dev.curvegen.core.ShapeSettings;
import dev.curvegen.core.edit.Edit2D;
import dev.curvegen.core.edit.Edit3D;
import dev.curvegen.core.edit.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

class LangTest {
    private static Map<String, String> entries(Path file) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (var reader = Files.newBufferedReader(file)) {
            JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e -> entries.put(e.getKey(), e.getValue().getAsString()));
        }
        return entries;
    }

    private static Language language(Map<String, String> entries) {
        return new Language() {
            @Override public String getOrDefault(String key, String fallback) { return entries.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return entries.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return FormattedCharSequence.forward(text.getString(), net.minecraft.network.chat.Style.EMPTY); }
        };
    }

    @Test
    void startupLoggingHasEnglishBeforeResourceLanguagesLoad() {
        Language original = Language.getInstance();
        // 1.21.1 keeps its default language private. Like it, this one has none of the mod's keys.
        Language.inject(language(Map.of()));
        try {
            assertEquals("Couldn't understand settings.json, so the settings are back to their defaults",
                    Lang.tr("curvegen.log.settings_invalid", "settings.json"));
            assertEquals("Couldn't save the settings", Lang.tr("curvegen.log.settings_save"));
        } finally { Language.inject(original); }
    }

    @Test
    void nestedMessagesKeepTheirArgumentsAndUseTheCurrentLanguage() throws Exception {
        Language original = Language.getInstance();
        Map<String, String> entries = entries(Path.of("../src/main/resources/assets/curvegen/lang/en_us.json"));
        Language.inject(language(entries));
        try {
            var text = Lang.component(Message.of("curvegen.editor.fix_shape", Message.of("curvegen.error.unexpected_token", "@")));
            assertEquals("Fix the shape first: \"@\" is in an unexpected place.", text.getString());
            entries.put("curvegen.editor.fix_shape", "[%s]");
            Language.inject(language(entries));
            assertEquals("[\"@\" is in an unexpected place.]", text.getString());
        } finally { Language.inject(original); }
    }

    @Test
    void existingComponentsFollowLanguageChangesAndFormatTranslatedArguments() throws Exception {
        Language original = Language.getInstance();
        String key = "curvegen.placement.fallback.place";
        var component = Lang.component(key, 12, 3);
        try (var files = Files.list(Path.of("../src/main/resources/assets/curvegen/lang"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                Map<String, String> entries = entries(file);
                Language.inject(language(entries));
                assertEquals(entries.get(key).replace("%1$s", "12").replace("%2$s", "3"), component.getString(), file.toString());
            }
        } finally { Language.inject(original); }
    }

    @Test
    void allCorePieceNamesAndOptionsHaveEnglishEntries() throws Exception {
        Map<String, String> entries = entries(Path.of("../src/main/resources/assets/curvegen/lang/en_us.json"));
        Language original = Language.getInstance();
        Language.inject(language(entries));
        try {
            for (String key : Pieces.NAME) assertTrue(entries.containsKey(key), key);
            for (var group : Count3.GROUPS) {
                assertTrue(entries.containsKey(group.name().key()), group.toString());
                assertNotEquals(group.name().key(), Lang.text(group.name()));
                assertTrue(Lang.text(group.name()).length() <= 26, group.name().toString());
            }
            for (var example : PresetData.examples()) assertTrue(entries.containsKey(example.name()), example.name());
            for (ShapeSettings.Gen gen : ShapeSettings.Gen.values()) {
                ShapeSettings settings = new ShapeSettings();
                settings.gen = gen;
                var options = settings.is3d() ? Edit3D.options(settings) : Edit2D.options(settings);
                for (Option option : options) {
                    assertTrue(entries.containsKey(option.label()), option.label());
                    assertTrue(Lang.tr(option.label()).length() <= 13, option.label());
                    if (option.off() != null) assertTrue(entries.containsKey(option.off()), option.off());
                    if (option instanceof Option.Cycler cycler)
                        for (String key : cycler.names()) assertTrue(entries.containsKey(key), key);
                }
                Message description = PresetData.defaultName(settings, gen);
                assertTrue(entries.containsKey(description.key()), description.key());
            }
            try { Expr.parseEquation("sin("); }
            catch (Expr.ParseException e) { assertTrue(entries.containsKey(e.text().key()), e.text().key()); }
        } finally { Language.inject(original); }
    }
}
