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

    public UUID getId() { return id; }

    public String getUsername() { return username; }

    public String getEmail() { return email; }

    public String getDisplayName() { return displayName; }

    public String getPasswordHash() { return passwordHash; }

    public String getGoogleSubject() { return googleSubject; }

    public String getCurrency() { return currency; }
}
