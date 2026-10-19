package com.equigcc.system;

import com.equigcc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** A planned pause announced to clients. While {@code readOnly} and active, writes are refused. */
@Entity
@Table(name = "maintenance_window")
public class MaintenanceWindow extends BaseEntity {

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "message_en", nullable = false, length = 500)
    private String messageEn;

    @Column(name = "message_ar", nullable = false, length = 500)
    private String messageAr;

    @Column(name = "read_only", nullable = false)
    private boolean readOnly = true;

    protected MaintenanceWindow() { }

    public MaintenanceWindow(Instant startsAt, Instant endsAt, String messageEn, String messageAr, boolean readOnly) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.messageEn = messageEn;
        this.messageAr = messageAr;
        this.readOnly = readOnly;
    }

    public boolean isActiveAt(Instant now) { return !now.isBefore(startsAt) && now.isBefore(endsAt); }

    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getMessageEn() { return messageEn; }
    public String getMessageAr() { return messageAr; }
    public boolean isReadOnly() { return readOnly; }
}
