package com.keybridge.entity;

import com.keybridge.enums.KeyStorageType;
import com.keybridge.enums.TokenStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "tokens")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_no", nullable = false, unique = true, length = 60)
    private String serialNo;

    @Column(length = 100)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenStatus status = TokenStatus.AVAILABLE;

    @Column(name = "assigned_date")
    private LocalDate assignedDate;

    /** Where this token's private keys live. Null (rows created before this column) means SOFT_P12. */
    @Enumerated(EnumType.STRING)
    @Column(name = "key_storage", length = 20)
    private KeyStorageType keyStorage;

    /** PKCS#11 slot ID of the device; only set when keyStorage is PKCS11. */
    @Column(name = "pkcs11_slot")
    private Long pkcs11Slot;

    public Token() {
    }

    public Token(String serialNo, String label) {
        this.serialNo = serialNo;
        this.label = label;
        this.status = TokenStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public KeyStorageType getKeyStorage() {
        return keyStorage;
    }

    /** Storage type with the pre-column default applied. */
    public KeyStorageType effectiveKeyStorage() {
        return keyStorage != null ? keyStorage : KeyStorageType.SOFT_P12;
    }

    public void setKeyStorage(KeyStorageType keyStorage) {
        this.keyStorage = keyStorage;
    }

    public Long getPkcs11Slot() {
        return pkcs11Slot;
    }

    public void setPkcs11Slot(Long pkcs11Slot) {
        this.pkcs11Slot = pkcs11Slot;
    }
}
