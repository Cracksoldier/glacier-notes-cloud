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
    @Column(name = "instance_name")
    private String instanceName;
    @Column(name = "default_language")
    private String defaultLanguage;
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
    @Column(name = "email_change_expiration_minutes")
    private int emailChangeExpirationMinutes;
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
    @Column(name = "user_exports_enabled")
    private boolean userExportsEnabled;
    @Column(name = "default_trash_retention_days")
    private int defaultTrashRetentionDays;
    @Column(name = "users_may_disable_auto_purge")
    private boolean usersMayDisableAutoPurge;
    @Column(name = "admin_deletion_retention_days")
    private int adminDeletionRetentionDays;
    @Column(name = "self_deletion_enabled")
    private boolean selfDeletionEnabled;
    @Column(name = "common_password_check_enabled")
    private boolean commonPasswordCheckEnabled;
    @Column(name = "password_history_enabled")
    private boolean passwordHistoryEnabled;
    @Column(name = "smtp_sender_name")
    private String smtpSenderName;
    @Column(name = "smtp_sender_address")
    private String smtpSenderAddress;
    @Column(name = "audit_retention_days")
    private int auditRetentionDays;
    @Column(name = "operational_log_retention_days")
    private int operationalLogRetentionDays;

    protected InstanceSettingsEntity() {
    }

    public int sessionDurationMinutes(boolean rememberMe) {
        return rememberMe ? rememberSessionDurationMinutes : normalSessionDurationMinutes;
    }

    public String instanceName() { return instanceName; }
    public String defaultLanguage() { return defaultLanguage; }
    public int normalSessionDurationMinutes() { return normalSessionDurationMinutes; }
    public int rememberSessionDurationMinutes() { return rememberSessionDurationMinutes; }

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
    public int emailChangeExpirationMinutes() { return emailChangeExpirationMinutes; }
    public List<String> allowedEmailDomains() { return List.of(allowedEmailDomains); }
    public List<String> allowedUploadTypes() { return List.of(allowedUploadTypes); }
    public long maximumImageBytes() { return maximumImageBytes; }
    public long perUserStorageQuotaBytes() { return perUserStorageQuotaBytes; }
    public int imageOrphanGraceHours() { return imageOrphanGraceHours; }
    public int noteVersionMaximumCount() { return noteVersionMaximumCount; }
    public int noteVersionRetentionDays() { return noteVersionRetentionDays; }
    public boolean userExportsEnabled() { return userExportsEnabled; }
    public int defaultTrashRetentionDays() { return defaultTrashRetentionDays; }
    public boolean usersMayDisableAutoPurge() { return usersMayDisableAutoPurge; }
    public int adminDeletionRetentionDays() { return adminDeletionRetentionDays; }
    public boolean selfDeletionEnabled() { return selfDeletionEnabled; }
    public boolean commonPasswordCheckEnabled() { return commonPasswordCheckEnabled; }
    public boolean passwordHistoryEnabled() { return passwordHistoryEnabled; }
    public String smtpSenderName() { return smtpSenderName; }
    public String smtpSenderAddress() { return smtpSenderAddress; }
    public int auditRetentionDays() { return auditRetentionDays; }
    public int operationalLogRetentionDays() { return operationalLogRetentionDays; }

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

    public void updateTransfers(Boolean exportsEnabled) {
        if (exportsEnabled != null) userExportsEnabled = exportsEnabled;
    }

    public void updateAccount(Integer emailChangeMinutes, Integer trashDays, Boolean mayDisablePurge,
                              Integer deletionDays, Boolean selfDeletion, Boolean commonPasswords,
                              Boolean passwordHistory) {
        if (emailChangeMinutes != null) emailChangeExpirationMinutes = emailChangeMinutes;
        if (trashDays != null) defaultTrashRetentionDays = trashDays;
        if (mayDisablePurge != null) usersMayDisableAutoPurge = mayDisablePurge;
        if (deletionDays != null) adminDeletionRetentionDays = deletionDays;
        if (selfDeletion != null) selfDeletionEnabled = selfDeletion;
        if (commonPasswords != null) commonPasswordCheckEnabled = commonPasswords;
        if (passwordHistory != null) passwordHistoryEnabled = passwordHistory;
    }

    public void updateOperations(String name, String language, Integer normalSessionMinutes,
                                 Integer rememberSessionMinutes, String baseUrl, String senderName,
                                 String senderAddress, Integer auditDays, Integer logDays,
                                 Integer delayThreshold, Integer lockThreshold, Integer lockMinutes) {
        if (name != null) instanceName = name.strip();
        if (language != null) defaultLanguage = language;
        if (normalSessionMinutes != null) normalSessionDurationMinutes = normalSessionMinutes;
        if (rememberSessionMinutes != null) rememberSessionDurationMinutes = rememberSessionMinutes;
        if (baseUrl != null) publicBaseUrl = baseUrl;
        if (senderName != null) smtpSenderName = senderName.strip();
        if (senderAddress != null) smtpSenderAddress = senderAddress.strip();
        if (auditDays != null) auditRetentionDays = auditDays;
        if (logDays != null) operationalLogRetentionDays = logDays;
        if (delayThreshold != null) loginDelayThreshold = delayThreshold;
        if (lockThreshold != null) loginLockThreshold = lockThreshold;
        if (lockMinutes != null) loginLockMinutes = lockMinutes;
    }
}
