package com.dev.julian09.financeserver.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "google_subject")
    private String googleSubject;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    // ADR-0014: the +code of the forwarding address, and Gmail's code to accept the forward once
    @Column(name = "mail_code", nullable = false, length = 12)
    private String mailCode;

    @Column(name = "forwarding_confirmation", length = 30)
    private String forwardingConfirmation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String username, String email, String displayName, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.currency = "COP";
        this.mailCode = newMailCode();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A user who signs up with Google: no password (ck_users_credential accepts either). */
    public static User forGoogle(String username, String email, String displayName, String googleSubject, Instant now) {
        User user = new User(username, email, displayName, null, now);
        user.googleSubject = googleSubject;
        return user;
    }

    /** REQ-AUT-03: an existing account gets its Google sign-in, by its verified email. */
    public void linkGoogle(String subject, Instant now) {
        this.googleSubject = subject;
        this.updatedAt = now;
    }

    /** REQ-AUT-09: also gives a Google-only user a password. */
    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    // 8 of a-z and 2-9 without l, o, 0 and 1, which read alike; the unique index refuses the rare repeat
    private static String newMailCode() {
        String alphabet = "abcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder code = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 8; i++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    public String getMailCode() { return mailCode; }

    public String getForwardingConfirmation() { return forwardingConfirmation; }

    public UUID getId() { return id; }

    public String getUsername() { return username; }

    public String getEmail() { return email; }

    public String getDisplayName() { return displayName; }

    public String getPasswordHash() { return passwordHash; }

    public String getGoogleSubject() { return googleSubject; }

    public String getCurrency() { return currency; }
}
