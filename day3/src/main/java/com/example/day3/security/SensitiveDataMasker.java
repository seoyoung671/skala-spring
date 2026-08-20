package com.example.day3.security;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("\\d{6}-?\\d{7}"),
            Pattern.compile("\\d{4}-\\d{4}-\\d{4}-\\d{4}"),
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+"),
            Pattern.compile("01[016789]-?\\d{3,4}-?\\d{4}"));

    public boolean containsSensitiveData(String value) {
        if (value == null) {
            return false;
        }
        return SENSITIVE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }

    public String mask(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("\\d{6}-?\\d{7}", "******-*******")
                .replaceAll("\\d{4}-\\d{4}-\\d{4}-\\d{4}", "****-****-****-****")
                .replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "***@***")
                .replaceAll("01[016789]-?\\d{3,4}-?\\d{4}", "010-****-****");
    }
}
