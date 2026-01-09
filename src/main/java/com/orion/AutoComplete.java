package com.orion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;

public class AutoComplete {
    private Map<String, Map<String, String>> languageCompletions = new HashMap<>();

    public AutoComplete() {
        loadCompletions();
    }

    private void loadCompletions() {
        try {
            InputStream is = getClass().getResourceAsStream("/com/orion/autocomplete.json");
            if (is == null) {
                System.err.println("Could not find autocomplete.json");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);

            for (String language : List.of("java", "python", "cpp", "javascript")) {
                Map<String, String> completions = new HashMap<>();
                JsonNode langNode = root.get(language);
                
                if (langNode != null && langNode.isArray()) {
                    for (JsonNode item : langNode) {
                        String trigger = item.get("trigger").asText();
                        String completion = item.get("completion").asText();
                        completions.put(trigger, completion);
                    }
                }
                languageCompletions.put(language, completions);
            }

            System.out.println("Loaded autocomplete for " + languageCompletions.size() + " languages");
        } catch (Exception e) {
            System.err.println("Error loading autocomplete: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> getSuggestions(String prefix, String fileExtension) {
        String language = getLanguageFromExtension(fileExtension);
        Map<String, String> completions = languageCompletions.get(language);
        
        if (completions == null) return Collections.emptyList();

        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<String, String> entry : completions.entrySet()) {
            if (entry.getKey().startsWith(prefix.toLowerCase())) {
                suggestions.add(entry.getValue());
            }
        }
        return suggestions;
    }

    private String getLanguageFromExtension(String fileExtension) {
        if (fileExtension == null) return "java";
        if (fileExtension.endsWith(".py")) return "python";
        if (fileExtension.endsWith(".cpp") || fileExtension.endsWith(".c")) return "cpp";
        if (fileExtension.endsWith(".js")) return "javascript";
        return "java";
    }
}
