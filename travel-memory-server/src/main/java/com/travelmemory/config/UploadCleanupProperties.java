package com.travelmemory.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.upload.cleanup")
public class UploadCleanupProperties {

    private boolean enabled = false;
    private boolean dryRun = true;

    @Min(1)
    private long retentionHours = 24;

    @NotBlank
    private String cron = "0 30 3 * * ?";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public long getRetentionHours() {
        return retentionHours;
    }

    public void setRetentionHours(long retentionHours) {
        this.retentionHours = retentionHours;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }
}
