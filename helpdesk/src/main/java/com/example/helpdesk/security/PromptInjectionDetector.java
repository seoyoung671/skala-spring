package com.example.helpdesk.security;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Day 3와 같은 규칙으로 직접·간접 프롬프트 인젝션 문구를 탐지한다. */
@Component
public class PromptInjectionDetector {

    private static final List<Pattern> PATTERNS = List.of(
            pattern("이전\\s*(지시|명령).*(무시|잊어)"),
            pattern("앞(선|의)\\s*(지시|명령).*(무시|잊어)"),
            pattern("(시스템|system)\\s*(프롬프트|prompt).*(출력|보여|공개)"),
            pattern("(규칙|정책|지침).*(무시|우회)"),
            pattern("ignore\\s+(all\\s+)?previous\\s+instructions?"),
            pattern("developer\\s+message.*(show|reveal|print)"),
            pattern("(도구|tool).*(반복|무한|계속).*(호출|실행)"));

    public boolean containsInjection(String input) {
        if (input == null) {
            return false;
        }
        return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(input).find());
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    }
}
