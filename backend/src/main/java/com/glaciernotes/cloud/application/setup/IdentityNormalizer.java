package com.glaciernotes.cloud.application.setup;

import jakarta.enterprise.context.ApplicationScoped;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

@ApplicationScoped
public class IdentityNormalizer {
    private static final Pattern USERNAME = Pattern.compile("[\\p{L}\\p{N}._-]+");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public NormalizedIdentity normalize(String usernameInput, String emailInput, String displayNameInput) {
        var violations = new ArrayList<SetupFailure.FieldViolation>();
        var username = usernameInput == null ? "" : usernameInput.strip();
        var usernameNormalized = Normalizer.normalize(username, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
        var usernameLength = usernameNormalized.codePointCount(0, usernameNormalized.length());
        if (usernameLength < 3 || usernameLength > 64 || !USERNAME.matcher(usernameNormalized).matches()) {
            violations.add(new SetupFailure.FieldViolation(
                "username",
                "Use 3–64 letters, numbers, dots, underscores, or hyphens"
            ));
        }

        var email = emailInput == null ? "" : emailInput.strip();
        var emailNormalized = email.toLowerCase(Locale.ROOT);
        if (email.length() > 320 || !EMAIL.matcher(email).matches()) {
            violations.add(new SetupFailure.FieldViolation("email", "Enter a valid email address"));
        }

        String displayName = displayNameInput == null ? null : displayNameInput.strip();
        if (displayName != null && displayName.isEmpty()) {
            displayName = null;
        }
        if (displayName != null && displayName.codePointCount(0, displayName.length()) > 128) {
            violations.add(new SetupFailure.FieldViolation(
                "displayName",
                "Display name must contain at most 128 characters"
            ));
        }

        if (!violations.isEmpty()) {
            throw SetupFailure.invalid(violations);
        }
        return new NormalizedIdentity(username, usernameNormalized, email, emailNormalized, displayName);
    }

    public record NormalizedIdentity(
        String username,
        String usernameNormalized,
        String email,
        String emailNormalized,
        String displayName
    ) {
    }
}
