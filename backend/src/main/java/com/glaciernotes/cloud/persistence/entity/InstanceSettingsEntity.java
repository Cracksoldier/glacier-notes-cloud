package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "instance_settings")
public class InstanceSettingsEntity {
    @Id
    @Column(name = "singleton_key")
    private short singletonKey;
    @Column(name = "normal_session_duration_minutes")
    private int normalSessionDurationMinutes;
    @Column(name = "remember_session_duration_minutes")
    private int rememberSessionDurationMinutes;
    @Column(name = "login_delay_threshold")
    private int loginDelayThreshold;
    @Column(name = "login_lock_threshold")
    private int loginLockThreshold;
    @Column(name = "login_lock_minutes")
    private int loginLockMinutes;
    @Column(name = "public_base_url")
    private String publicBaseUrl;
    @Column(name = "invitation_expiration_hours")
    private int invitationExpirationHours;
    @Column(name = "password_reset_expiration_minutes")
    private int passwordResetExpirationMinutes;
    @Column(name = "allowed_email_domains", columnDefinition = "text[]")
    private String[] allowedEmailDomains;

    protected InstanceSettingsEntity() {
    }

    public int sessionDurationMinutes(boolean rememberMe) {
        return rememberMe ? rememberSessionDurationMinutes : normalSessionDurationMinutes;
    }

    public int loginDelayThreshold() {
        return loginDelayThreshold;
    }

    public int loginLockThreshold() {
        return loginLockThreshold;
    }

    public int loginLockMinutes() {
        return loginLockMinutes;
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public int invitationExpirationHours() { return invitationExpirationHours; }
    public int passwordResetExpirationMinutes() { return passwordResetExpirationMinutes; }
    public List<String> allowedEmailDomains() { return List.of(allowedEmailDomains); }

    public void updateLifecycle(List<String> domains, Integer invitationHours, Integer resetMinutes) {
        if (domains != null) allowedEmailDomains = domains.toArray(String[]::new);
        if (invitationHours != null) invitationExpirationHours = invitationHours;
        if (resetMinutes != null) passwordResetExpirationMinutes = resetMinutes;
    }
}
