package com.keybridge.entity;

import com.keybridge.enums.DscApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDateTime;

/** One entry of a DSC application's status timeline. */
@Embeddable
public class DscStatusEvent {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DscApplicationStatus status;

    @Column(length = 500)
    private String note;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime at;

    public DscStatusEvent() {
    }

    public DscStatusEvent(DscApplicationStatus status, String note, String actorName, LocalDateTime at) {
        this.status = status;
        this.note = note;
        this.actorName = actorName;
        this.at = at;
    }

    public DscApplicationStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public String getActorName() {
        return actorName;
    }

    public LocalDateTime getAt() {
        return at;
    }
}
