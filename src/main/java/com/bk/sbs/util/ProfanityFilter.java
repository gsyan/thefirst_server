//--------------------------------------------------------------------------------------------------
// 이름 비속어 필터: DataTableForbiddenWords.json 로드, containsProfanity()로 이름 포함 여부 확인
package com.bk.sbs.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class ProfanityFilter {

    private static final Set<String> BANNED_WORDS;

    static {
        Set<String> words = new HashSet<>();
        try {
            ClassPathResource resource = new ClassPathResource("data/DataTableForbiddenWords.json");
            try (InputStream is = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(is);
                JsonNode bannedWordsNode = root.get("bannedWords");
                if (bannedWordsNode != null && bannedWordsNode.isArray()) {
                    for (JsonNode node : bannedWordsNode) {
                        words.add(node.asText().toLowerCase());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[ProfanityFilter] DataTableForbiddenWords.json 로드 실패: " + e.getMessage());
        }
        BANNED_WORDS = words;
    }

    public static boolean containsProfanity(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase();
        for (String word : BANNED_WORDS) {
            if (lower.contains(word)) return true;
        }
        return false;
    }
}
