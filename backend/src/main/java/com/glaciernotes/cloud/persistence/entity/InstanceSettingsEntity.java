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
    @Column(name = "allowed_upload_types", columnDefinition = "text[]")
    private String[] allowedUploadTypes;
    @Column(name = "maximum_image_bytes")
    private long maximumImageBytes;
    @Column(name = "per_user_storage_quota_bytes")
    private long perUserStorageQuotaBytes;
    @Column(name = "image_orphan_grace_hours")
    private int imageOrphanGraceHours;
    @Column(name = "note_version_maximum_count")
    private int noteVersionMaximumCount;
    @Column(name = "note_version_retention_days")
    private int noteVersionRetentionDays;

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
    public List<String> allowedUploadTypes() { return List.of(allowedUploadTypes); }
    public long maximumImageBytes() { return maximumImageBytes; }
    public long perUserStorageQuotaBytes() { return perUserStorageQuotaBytes; }
    public int imageOrphanGraceHours() { return imageOrphanGraceHours; }
    public int noteVersionMaximumCount() { return noteVersionMaximumCount; }
    public int noteVersionRetentionDays() { return noteVersionRetentionDays; }

    public void updateLifecycle(List<String> domains, Integer invitationHours, Integer resetMinutes) {
        if (domains != null) allowedEmailDomains = domains.toArray(String[]::new);
        if (invitationHours != null) invitationExpirationHours = invitationHours;
        if (resetMinutes != null) passwordResetExpirationMinutes = resetMinutes;
    }

    public void updateImages(List<String> types, Long maximumBytes, Long quotaBytes, Integer graceHours) {
        if (types != null) allowedUploadTypes = types.toArray(String[]::new);
        if (maximumBytes != null) maximumImageBytes = maximumBytes;
        if (quotaBytes != null) perUserStorageQuotaBytes = quotaBytes;
        if (graceHours != null) imageOrphanGraceHours = graceHours;
    }

    public void updateHistory(Integer maximumCount, Integer retentionDays) {
        if (maximumCount != null) noteVersionMaximumCount = maximumCount;
        if (retentionDays != null) noteVersionRetentionDays = retentionDays;
    }
}
