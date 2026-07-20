package com.glaciernotes.cloud.application.setup;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class PasswordPolicy {
    private Set<String> commonPasswords;

    @PostConstruct
    void loadCommonPasswords() {
        try (var input = PasswordPolicy.class.getResourceAsStream("/security/common-passwords.txt")) {
            if (input == null) {
                throw new IllegalStateException("The common-password denylist is missing");
            }
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                commonPasswords = reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(this::comparisonForm)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the common-password denylist", exception);
        }
    }

    public void validate(char[] password) {
        var value = new String(password);
        var length = value.codePointCount(0, value.length());
        var violations = new HashSet<SetupFailure.FieldViolation>();
        if (length < 12 || length > 128) {
            violations.add(new SetupFailure.FieldViolation(
                "password",
                "Password must contain between 12 and 128 characters"
            ));
        }
        if (value.codePoints().anyMatch(Character::isWhitespace)) {
            violations.add(new SetupFailure.FieldViolation("password", "Password must not contain whitespace"));
        }
        if (commonPasswords.contains(comparisonForm(value))) {
            violations.add(new SetupFailure.FieldViolation("password", "Choose a less common password"));
        }
        if (!violations.isEmpty()) {
            throw SetupFailure.invalid(List.copyOf(violations));
        }
    }

    private String comparisonForm(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
