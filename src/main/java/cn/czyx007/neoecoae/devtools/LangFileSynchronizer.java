package cn.czyx007.neoecoae.devtools;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Language File Synchronizer
 * Synchronizes translation files with en_us.json as the reference source.
 * - Adds new keys from en_us.json to other language files in the same order
 * - Removes keys from other language files that no longer exist in en_us.json
 */
public class LangFileSynchronizer {

    // File paths
    private static final String LANG_PATH_GENERATED = "src/generated/resources/assets/neoecoae/lang/";
    private static final String LANG_PATH_MAIN = "src/main/resources/assets/neoecoae/lang/";

    private static final String EN_US_PATH = LANG_PATH_GENERATED + "en_us.json";
    private static final String[] TARGET_LANG_FILES = {
            LANG_PATH_MAIN + "zh_cn.json",
            LANG_PATH_MAIN + "zh_hk.json",
            LANG_PATH_MAIN + "zh_tw.json",
            LANG_PATH_MAIN + "lzh.json"
    };

    public static void main(String[] args) {
        try {
            System.out.println("=== Language File Synchronizer ===\n");

            // Load en_us.json as reference
            LinkedHashMap<String, String> enUsMap = loadJsonFile(EN_US_PATH);
            System.out.println("Loaded reference file: " + EN_US_PATH);
            System.out.println("Total keys in en_us.json: " + enUsMap.size() + "\n");

            // Process each target language file
            for (String targetFile : TARGET_LANG_FILES) {
                processLanguageFile(targetFile, enUsMap);
            }

            System.out.println("\n=== Synchronization Complete ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process a single language file - sync with en_us.json
     */
    private static void processLanguageFile(String filePath, LinkedHashMap<String, String> referenceMap) throws IOException {
        System.out.println("Processing: " + filePath);

        // Load existing translations
        LinkedHashMap<String, String> targetMap = loadJsonFile(filePath);
        int originalSize = targetMap.size();

        // Create new map with keys in the same order as en_us.json
        LinkedHashMap<String, String> synchronizedMap = new LinkedHashMap<>();

        int addedCount = 0;
        int keptCount = 0;

        for (Map.Entry<String, String> entry : referenceMap.entrySet()) {
            String key = entry.getKey();

            if (targetMap.containsKey(key)) {
                // Keep existing translation
                synchronizedMap.put(key, targetMap.get(key));
                keptCount++;
            } else {
                // Add new key with English value as placeholder
                synchronizedMap.put(key, entry.getValue());
                addedCount++;
            }
        }

        int removedCount = originalSize - keptCount;

        // Save synchronized file
        saveJsonFile(filePath, synchronizedMap);

        // Print statistics
        System.out.println("  Original keys: " + originalSize);
        System.out.println("  Keys kept: " + keptCount);
        System.out.println("  Keys added: " + addedCount);
        System.out.println("  Keys removed: " + removedCount);
        System.out.println("  Final keys: " + synchronizedMap.size());
        System.out.println();
    }

    /**
     * Load JSON file into LinkedHashMap to preserve order
     * Simple JSON parser for key-value pairs
     */
    private static LinkedHashMap<String, String> loadJsonFile(String filePath) throws IOException {
        String content = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        // Remove outer braces and split by lines
        content = content.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

        // Parse each line
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equals("{") || line.equals("}")) continue;

            // Remove trailing comma
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1);
            }

            // Parse "key": "value" format
            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                // Remove quotes
                key = unquote(key);
                value = unquote(value);

                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
        }

        return map;
    }

    /**
     * Save LinkedHashMap to JSON file with proper formatting
     */
    private static void saveJsonFile(String filePath, LinkedHashMap<String, String> map) throws IOException {
        StringBuilder json = new StringBuilder("{\n");

        int count = 0;
        int total = map.size();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            count++;
            json.append("  \"")
                    .append(escapeJson(entry.getKey()))
                    .append("\": \"")
                    .append(escapeJson(entry.getValue()))
                    .append("\"");

            if (count < total) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("}");

        Files.writeString(Paths.get(filePath), json.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Remove surrounding quotes from a string
     */
    private static String unquote(String str) {
        str = str.trim();
        if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
            str = str.substring(1, str.length() - 1);
        }
        // Unescape common escape sequences
        str = str.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
        return str;
    }

    /**
     * Escape special characters for JSON
     */
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

