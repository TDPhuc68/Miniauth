-- Users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150),
    email VARCHAR(150),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    failed_attempts INT DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Registered OAuth2 / OIDC Clients
CREATE TABLE IF NOT EXISTS client_applications (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(255) NOT NULL,
    client_name VARCHAR(150) NOT NULL,
    redirect_uris VARCHAR(500) NOT NULL,
    authorized_grant_types VARCHAR(200) NOT NULL,
    scopes VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Audit logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor VARCHAR(100),
    target VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    details VARCHAR(500),
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial test user: username 'john_doe', password 'password123'
-- BCrypt hash for 'password123': $2a$10$wK1k6O1l7B6oO/4O9R4x2.a1uPsqjLpC6JqJtH9t1f1C/Rk8Oa8fW
INSERT INTO users (id, username, password_hash, full_name, email, status)
VALUES ('u-101', 'john_doe', '$2a$10$7Z418Yk8uX6q3gZ1g8hNueQ7tD5yZ0s5.R5W9B9mF9N6Q5vK6mC6S', 'John Doe', 'john.doe@example.com', 'ACTIVE');

-- Seed initial test client application:
-- client_id: 'demo-client', client_secret: 'demo-secret'
INSERT INTO client_applications (id, client_id, client_secret_hash, client_name, redirect_uris, authorized_grant_types, scopes)
VALUES ('c-101', 'demo-client', '$2a$10$7Z418Yk8uX6q3gZ1g8hNueQ7tD5yZ0s5.R5W9B9mF9N6Q5vK6mC6S', 'Demo Web Application', 'http://localhost:3000/callback.html,http://localhost:8080/callback.html', 'authorization_code,refresh_token', 'openid,profile,email');
