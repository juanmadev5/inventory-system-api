package com.jm.inventory.domain.user;

import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public class User {
    UUID id;
    String email;
    String passwordHash;
    Role role;
    boolean active;
    Instant createdAt;
}
