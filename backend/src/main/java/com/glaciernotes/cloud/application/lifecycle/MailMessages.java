package com.glaciernotes.cloud.application.lifecycle;

/**
 * Every outbound lifecycle message in both supported languages. German is formal (<em>Sie</em>), to
 * match the frontend dictionary. No template takes a secret, a code, a provisioning URI, or a
 * remaining-code count — security notices say what happened and when, never what the value was.
 */
public enum MailMessages {
    INVITATION(
        "Your Glacier Notes invitation",
        """
        You have been invited to Glacier Notes.

        Open this link to create your account:
        %s

        If you did not expect this invitation, you can ignore this message.
        """,
        "Ihre Einladung zu Glacier Notes",
        """
        Sie wurden zu Glacier Notes eingeladen.

        Öffnen Sie diesen Link, um Ihr Konto anzulegen:
        %s

        Falls Sie diese Einladung nicht erwartet haben, können Sie diese Nachricht ignorieren.
        """
    ),
    PASSWORD_RESET(
        "Reset your Glacier Notes password",
        """
        A password reset was requested for your Glacier Notes account.

        Open this link to choose a new password:
        %s

        If you did not request this reset, you can ignore this message.
        """,
        "Setzen Sie Ihr Glacier-Notes-Passwort zurück",
        """
        Für Ihr Glacier-Notes-Konto wurde ein Zurücksetzen des Passworts angefordert.

        Öffnen Sie diesen Link, um ein neues Passwort zu wählen:
        %s

        Falls Sie das nicht angefordert haben, können Sie diese Nachricht ignorieren.
        """
    ),
    EMAIL_CHANGE_VERIFICATION(
        "Verify your new Glacier Notes email address",
        """
        A change to this email address was requested for your Glacier Notes account.

        Open this link to verify the new address:
        %s

        If you did not request this change, you can ignore this message.
        """,
        "Bestätigen Sie Ihre neue Glacier-Notes-E-Mail-Adresse",
        """
        Für Ihr Glacier-Notes-Konto wurde eine Änderung auf diese E-Mail-Adresse angefordert.

        Öffnen Sie diesen Link, um die neue Adresse zu bestätigen:
        %s

        Falls Sie diese Änderung nicht angefordert haben, können Sie diese Nachricht ignorieren.
        """
    ),
    EMAIL_CHANGED_NOTICE(
        "Your Glacier Notes email address changed",
        """
        The email address for your Glacier Notes account was changed successfully.

        If you did not make this change, contact your Glacier Notes administrator immediately.
        """,
        "Ihre Glacier-Notes-E-Mail-Adresse wurde geändert",
        """
        Die E-Mail-Adresse Ihres Glacier-Notes-Kontos wurde erfolgreich geändert.

        Falls Sie diese Änderung nicht vorgenommen haben, wenden Sie sich umgehend an Ihre
        Glacier-Notes-Administration.
        """
    ),
    SECOND_FACTOR_ENROLLMENT_STARTED(
        "Two-factor setup started for your Glacier Notes account",
        """
        Setting up a second factor was started for your Glacier Notes account.

        When: %s
        Device: %s

        It is not active until you confirm it. If this was not you, change your password
        immediately and contact your Glacier Notes administrator.
        """,
        "Einrichtung der Zwei-Faktor-Anmeldung für Ihr Glacier-Notes-Konto begonnen",
        """
        Für Ihr Glacier-Notes-Konto wurde die Einrichtung eines zweiten Faktors begonnen.

        Zeitpunkt: %s
        Gerät: %s

        Der zweite Faktor ist erst nach Ihrer Bestätigung aktiv. Falls Sie das nicht waren, ändern
        Sie umgehend Ihr Passwort und wenden Sie sich an Ihre Glacier-Notes-Administration.
        """
    ),
    SECOND_FACTOR_ENABLED(
        "Two-factor authentication is active on your Glacier Notes account",
        """
        A second factor is now active on your Glacier Notes account. Every other session was signed
        out.

        When: %s
        Device: %s

        Keep your recovery codes somewhere safe — they are the only way back in if you lose your
        authenticator. If this was not you, contact your Glacier Notes administrator immediately.
        """,
        "Zwei-Faktor-Anmeldung für Ihr Glacier-Notes-Konto aktiv",
        """
        Für Ihr Glacier-Notes-Konto ist jetzt ein zweiter Faktor aktiv. Alle anderen Sitzungen
        wurden abgemeldet.

        Zeitpunkt: %s
        Gerät: %s

        Bewahren Sie Ihre Wiederherstellungscodes sicher auf — nur damit kommen Sie ohne Ihre
        Authenticator-App wieder hinein. Falls Sie das nicht waren, wenden Sie sich umgehend an Ihre
        Glacier-Notes-Administration.
        """
    ),
    SECOND_FACTOR_DISABLED(
        "Two-factor authentication was turned off on your Glacier Notes account",
        """
        The second factor on your Glacier Notes account was turned off. Every other session was
        signed out.

        When: %s
        Device: %s

        If this was not you, change your password immediately and contact your Glacier Notes
        administrator.
        """,
        "Zwei-Faktor-Anmeldung für Ihr Glacier-Notes-Konto deaktiviert",
        """
        Der zweite Faktor Ihres Glacier-Notes-Kontos wurde deaktiviert. Alle anderen Sitzungen
        wurden abgemeldet.

        Zeitpunkt: %s
        Gerät: %s

        Falls Sie das nicht waren, ändern Sie umgehend Ihr Passwort und wenden Sie sich an Ihre
        Glacier-Notes-Administration.
        """
    ),
    RECOVERY_CODE_USED(
        "A recovery code was used to sign in to Glacier Notes",
        """
        A recovery code was used instead of your authenticator to sign in to Glacier Notes. That
        code is now spent.

        When: %s
        Device: %s

        If this was not you, change your password immediately and contact your Glacier Notes
        administrator.
        """,
        "Ein Wiederherstellungscode wurde zur Anmeldung bei Glacier Notes verwendet",
        """
        Zur Anmeldung bei Glacier Notes wurde statt Ihrer Authenticator-App ein
        Wiederherstellungscode verwendet. Dieser Code ist nun verbraucht.

        Zeitpunkt: %s
        Gerät: %s

        Falls Sie das nicht waren, ändern Sie umgehend Ihr Passwort und wenden Sie sich an Ihre
        Glacier-Notes-Administration.
        """
    ),
    RECOVERY_CODES_REGENERATED(
        "Your Glacier Notes recovery codes were replaced",
        """
        A new set of recovery codes was generated for your Glacier Notes account. Every earlier code
        stopped working, and every other session was signed out.

        When: %s
        Device: %s

        If this was not you, change your password immediately and contact your Glacier Notes
        administrator.
        """,
        "Ihre Glacier-Notes-Wiederherstellungscodes wurden ersetzt",
        """
        Für Ihr Glacier-Notes-Konto wurde ein neuer Satz Wiederherstellungscodes erzeugt. Alle
        bisherigen Codes sind ungültig, und alle anderen Sitzungen wurden abgemeldet.

        Zeitpunkt: %s
        Gerät: %s

        Falls Sie das nicht waren, ändern Sie umgehend Ihr Passwort und wenden Sie sich an Ihre
        Glacier-Notes-Administration.
        """
    ),
    SECOND_FACTOR_CLEARED_BY_OPERATOR(
        "An operator removed the second factor from your Glacier Notes account",
        """
        The second factor on your Glacier Notes account was removed by an operator of this instance.
        Signing in now needs your password alone.

        When: %s

        If you did not ask for this, contact your Glacier Notes administrator immediately.
        """,
        "Der zweite Faktor Ihres Glacier-Notes-Kontos wurde durch den Betrieb entfernt",
        """
        Der zweite Faktor Ihres Glacier-Notes-Kontos wurde durch den Betrieb dieser Instanz
        entfernt. Zur Anmeldung genügt nun Ihr Passwort.

        Zeitpunkt: %s

        Falls Sie das nicht veranlasst haben, wenden Sie sich umgehend an Ihre
        Glacier-Notes-Administration.
        """
    ),
    SECOND_FACTOR_CLEARED_BY_ADMINISTRATOR(
        "An administrator removed the second factor from your Glacier Notes account",
        """
        An administrator removed the second factor from your Glacier Notes account. Signing in now
        needs your password alone, and all your sessions were signed out.

        When: %s

        If you did not ask for this, contact your Glacier Notes administrator immediately.
        """,
        "Ihre Administration hat den zweiten Faktor Ihres Glacier-Notes-Kontos entfernt",
        """
        Eine Administration hat den zweiten Faktor Ihres Glacier-Notes-Kontos entfernt. Zur
        Anmeldung genügt nun Ihr Passwort, und alle Ihre Sitzungen wurden beendet.

        Zeitpunkt: %s

        Falls Sie das nicht veranlasst haben, wenden Sie sich umgehend an Ihre
        Glacier-Notes-Administration.
        """
    );

    private final String subjectEn;
    private final String bodyEn;
    private final String subjectDe;
    private final String bodyDe;

    MailMessages(String subjectEn, String bodyEn, String subjectDe, String bodyDe) {
        this.subjectEn = subjectEn;
        this.bodyEn = bodyEn;
        this.subjectDe = subjectDe;
        this.bodyDe = bodyDe;
    }

    public String subject(String language) {
        return german(language) ? subjectDe : subjectEn;
    }

    public String body(String language, Object... arguments) {
        return (german(language) ? bodyDe : bodyEn).formatted(arguments);
    }

    private boolean german(String language) {
        return "de".equals(language);
    }
}
