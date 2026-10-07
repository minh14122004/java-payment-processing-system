package com.example.payment.security;

import java.io.Serializable;
import java.util.UUID;

public record AuthPrincipal(UUID userId, int credentialVersion) implements Serializable {}
