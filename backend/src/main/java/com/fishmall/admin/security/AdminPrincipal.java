package com.fishmall.admin.security;

public record AdminPrincipal(Long adminId, String username, Long tokenVersion) {
}
