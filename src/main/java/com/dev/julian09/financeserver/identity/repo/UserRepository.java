package com.dev.julian09.financeserver.identity.repo;

import java.util.Optional;
import java.util.UUID;

import com.dev.julian09.financeserver.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Same lower() expressions as the unique indexes of V3
    @Query("select u from User u where lower(u.username) = lower(:identifier) or lower(u.email) = lower(:identifier)")
    Optional<User> findByIdentifier(@Param("identifier") String identifier);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean usernameTaken(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean emailTaken(@Param("email") String email);
}
