-- ============================================================
-- StackLens Demo MySQL Databases
-- Simulates a real microservices data landscape with:
--   ecommerce   – orders, payments, users, sessions, cache
--   auth_db     – OAuth2, JWT tokens, roles, MFA, API keys
--   analytics_db– metrics, event tracking, performance data
--
-- Keywords that will be found by StackLens search:
--   authentication, payment, session, cache, user, order,
--   notification, token, role, permission, api
-- ============================================================

-- ─── Global setup ─────────────────────────────────────────────────────────────
CREATE USER IF NOT EXISTS 'stacklens'@'%' IDENTIFIED BY 'stacklens';

CREATE DATABASE IF NOT EXISTS ecommerce CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS auth_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS analytics_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON ecommerce.*    TO 'stacklens'@'%';
GRANT ALL PRIVILEGES ON auth_db.*      TO 'stacklens'@'%';
GRANT ALL PRIVILEGES ON analytics_db.* TO 'stacklens'@'%';
FLUSH PRIVILEGES;


-- ============================================================
-- DATABASE: ecommerce
-- ============================================================
USE ecommerce;

-- ── Tables ────────────────────────────────────────────────────────────────────

CREATE TABLE users (
    user_id            BIGINT        NOT NULL AUTO_INCREMENT,
    username           VARCHAR(80)   NOT NULL UNIQUE,
    email              VARCHAR(255)  NOT NULL UNIQUE,
    password_hash      VARCHAR(255)  NOT NULL,
    authentication_token VARCHAR(512),           -- current session bearer token hash
    display_name       VARCHAR(150),
    role               ENUM('ADMIN','MANAGER','USER','READ_ONLY') NOT NULL DEFAULT 'USER',
    tenant_id          VARCHAR(64)   NOT NULL DEFAULT 'default',
    email_verified     TINYINT(1)    NOT NULL DEFAULT 0,
    last_login_at      DATETIME,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at         DATETIME      DEFAULT NULL,
    PRIMARY KEY (user_id),
    INDEX idx_users_authentication_token (authentication_token(64)),
    INDEX idx_users_email (email),
    INDEX idx_users_tenant_role (tenant_id, role),
    INDEX idx_users_deleted_at (deleted_at)
) ENGINE=InnoDB;

CREATE TABLE user_sessions (
    session_id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    session_token      VARCHAR(512)  NOT NULL UNIQUE,  -- hashed JWT refresh token
    ip_address         VARCHAR(45),
    user_agent         VARCHAR(500),
    authentication_method ENUM('PASSWORD','OAUTH2','BIOMETRIC','SSO') NOT NULL DEFAULT 'PASSWORD',
    is_active          TINYINT(1)    NOT NULL DEFAULT 1,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at         DATETIME      NOT NULL,
    last_accessed_at   DATETIME,
    PRIMARY KEY (session_id),
    INDEX idx_user_sessions_token (session_token(64)),
    INDEX idx_user_sessions_user_id (user_id),
    INDEX idx_user_sessions_expires (expires_at),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE user_authentication_logs (
    log_id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT,                         -- NULL for failed attempts with unknown user
    username_attempt   VARCHAR(80),
    action             ENUM('LOGIN_SUCCESS','LOGIN_FAILED','LOGOUT','TOKEN_REFRESH','PASSWORD_RESET','MFA_CHALLENGE','ACCOUNT_LOCKED') NOT NULL,
    ip_address         VARCHAR(45)   NOT NULL,
    user_agent         VARCHAR(500),
    authentication_method VARCHAR(50),
    failure_reason     VARCHAR(255),
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    INDEX idx_auth_log_user_id (user_id),
    INDEX idx_auth_log_ip (ip_address),
    INDEX idx_auth_log_action_created (action, created_at)
) ENGINE=InnoDB;

CREATE TABLE payment_methods (
    payment_method_id  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    provider           ENUM('STRIPE','PAYPAL','SEPA','INVOICE') NOT NULL,
    masked_card_number VARCHAR(19),                    -- e.g. **** **** **** 4242
    card_type          VARCHAR(20),
    expiry_month       TINYINT,
    expiry_year        SMALLINT,
    billing_address    JSON,
    is_default         TINYINT(1)    NOT NULL DEFAULT 0,
    is_verified        TINYINT(1)    NOT NULL DEFAULT 0,
    stripe_payment_method_id VARCHAR(255),
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_method_id),
    INDEX idx_payment_methods_user (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE orders (
    order_id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    order_number       VARCHAR(32)   NOT NULL UNIQUE,
    status             ENUM('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    subtotal           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    tax_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    shipping_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    currency_code      CHAR(3)       NOT NULL DEFAULT 'EUR',
    shipping_address   JSON,
    notes              TEXT,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    INDEX idx_orders_user_status (user_id, status),
    INDEX idx_orders_number (order_number),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

CREATE TABLE order_items (
    item_id            BIGINT        NOT NULL AUTO_INCREMENT,
    order_id           BIGINT        NOT NULL,
    product_id         BIGINT        NOT NULL,
    product_name       VARCHAR(255)  NOT NULL,
    quantity           INT           NOT NULL DEFAULT 1,
    unit_price         DECIMAL(12,2) NOT NULL,
    discount_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    line_total         DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (item_id),
    INDEX idx_order_items_order (order_id),
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE payment_transactions (
    transaction_id     BIGINT        NOT NULL AUTO_INCREMENT,
    order_id           BIGINT        NOT NULL,
    payment_method_id  BIGINT,
    amount             DECIMAL(12,2) NOT NULL,
    currency_code      CHAR(3)       NOT NULL DEFAULT 'EUR',
    payment_status     ENUM('PENDING','PROCESSING','COMPLETED','FAILED','REFUNDED','CHARGEBACK') NOT NULL DEFAULT 'PENDING',
    payment_gateway    VARCHAR(50),
    payment_gateway_reference VARCHAR(255),           -- Stripe charge ID / PayPal transaction ID
    payment_gateway_response   JSON,                  -- full gateway response for debugging
    error_code         VARCHAR(50),
    error_message      VARCHAR(500),
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (transaction_id),
    INDEX idx_payment_status_created (payment_status, created_at),
    INDEX idx_payment_order (order_id),
    INDEX idx_payment_gateway_ref (payment_gateway_reference),
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB;

CREATE TABLE products (
    product_id         BIGINT        NOT NULL AUTO_INCREMENT,
    sku                VARCHAR(64)   NOT NULL UNIQUE,
    name               VARCHAR(255)  NOT NULL,
    description        TEXT,
    price              DECIMAL(12,2) NOT NULL,
    sale_price         DECIMAL(12,2),
    stock_quantity     INT           NOT NULL DEFAULT 0,
    category_id        INT,
    is_active          TINYINT(1)    NOT NULL DEFAULT 1,
    weight_kg          DECIMAL(8,3),
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    INDEX idx_products_sku (sku),
    INDEX idx_products_category (category_id),
    INDEX idx_products_active_price (is_active, price)
) ENGINE=InnoDB;

CREATE TABLE session_cache (
    cache_id           BIGINT        NOT NULL AUTO_INCREMENT,
    cache_key          VARCHAR(512)  NOT NULL UNIQUE,  -- namespaced: user:{id}:profile, etc.
    cache_value        MEDIUMTEXT,
    cache_type         VARCHAR(64),                    -- e.g. USER_PROFILE, PRODUCT_LIST, ORDER_SUMMARY
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at         DATETIME      NOT NULL,
    hit_count          INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (cache_id),
    INDEX idx_session_cache_key (cache_key(128)),
    INDEX idx_session_cache_expires (expires_at),
    INDEX idx_session_cache_type (cache_type)
) ENGINE=InnoDB;

CREATE TABLE cache_eviction_log (
    eviction_id        BIGINT        NOT NULL AUTO_INCREMENT,
    cache_key          VARCHAR(512)  NOT NULL,
    cache_type         VARCHAR(64),
    eviction_reason    ENUM('EXPIRED','MANUAL','CAPACITY','UPDATE') NOT NULL,
    evicted_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (eviction_id),
    INDEX idx_cache_eviction_type_reason (cache_type, eviction_reason)
) ENGINE=InnoDB;

CREATE TABLE notification_queue (
    notification_id    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    channel            ENUM('EMAIL','PUSH','SMS','WEBHOOK') NOT NULL,
    subject            VARCHAR(255),
    message_body       TEXT          NOT NULL,
    template_id        VARCHAR(64),
    template_variables JSON,
    status             ENUM('PENDING','PROCESSING','SENT','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    retry_count        TINYINT       NOT NULL DEFAULT 0,
    max_retries        TINYINT       NOT NULL DEFAULT 3,
    scheduled_at       DATETIME,
    sent_at            DATETIME,
    error_message      VARCHAR(500),
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    INDEX idx_notification_status_channel (status, channel),
    INDEX idx_notification_user (user_id),
    INDEX idx_notification_scheduled (scheduled_at),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE notification_templates (
    template_id        VARCHAR(64)   NOT NULL,
    name               VARCHAR(150)  NOT NULL,
    channel            ENUM('EMAIL','PUSH','SMS','WEBHOOK') NOT NULL,
    subject_template   VARCHAR(255),
    body_template      TEXT          NOT NULL,
    is_active          TINYINT(1)    NOT NULL DEFAULT 1,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (template_id)
) ENGINE=InnoDB;

-- ── Views ─────────────────────────────────────────────────────────────────────

CREATE OR REPLACE VIEW active_user_sessions AS
    SELECT
        s.session_id,
        s.user_id,
        u.username,
        u.email,
        s.authentication_method,
        s.ip_address,
        s.created_at  AS session_created_at,
        s.expires_at,
        s.last_accessed_at
    FROM user_sessions s
    JOIN users u ON s.user_id = u.user_id
    WHERE s.is_active = 1
      AND s.expires_at > NOW()
      AND u.deleted_at IS NULL;

CREATE OR REPLACE VIEW pending_payment_orders AS
    SELECT
        o.order_id,
        o.order_number,
        o.user_id,
        u.email,
        o.total_amount,
        o.currency_code,
        pt.payment_status,
        pt.payment_gateway,
        pt.payment_gateway_reference,
        pt.created_at AS payment_created_at
    FROM orders o
    JOIN payment_transactions pt ON o.order_id = pt.order_id
    JOIN users u ON o.user_id = u.user_id
    WHERE pt.payment_status IN ('PENDING','PROCESSING');

CREATE OR REPLACE VIEW order_payment_summary AS
    SELECT
        o.order_id,
        o.order_number,
        o.status AS order_status,
        o.total_amount,
        SUM(CASE WHEN pt.payment_status = 'COMPLETED' THEN pt.amount ELSE 0 END) AS total_paid,
        COUNT(pt.transaction_id) AS payment_attempt_count,
        MAX(pt.payment_status) AS latest_payment_status
    FROM orders o
    LEFT JOIN payment_transactions pt ON o.order_id = pt.order_id
    GROUP BY o.order_id, o.order_number, o.status, o.total_amount;

CREATE OR REPLACE VIEW session_cache_stats AS
    SELECT
        cache_type,
        COUNT(*)                                    AS total_entries,
        SUM(CASE WHEN expires_at > NOW() THEN 1 ELSE 0 END) AS active_entries,
        AVG(hit_count)                              AS avg_hit_count,
        MAX(hit_count)                              AS max_hit_count
    FROM session_cache
    GROUP BY cache_type;

-- ── Stored Procedures & Functions ─────────────────────────────────────────────

DELIMITER $$

CREATE PROCEDURE sp_authenticate_user(
    IN  p_username         VARCHAR(80),
    IN  p_password_hash    VARCHAR(255),
    IN  p_ip_address       VARCHAR(45),
    IN  p_user_agent       VARCHAR(500),
    OUT p_user_id          BIGINT,
    OUT p_authentication_token VARCHAR(512),
    OUT p_result_code      TINYINT  -- 0=OK, 1=invalid credentials, 2=account locked
)
proc_block: BEGIN
    DECLARE v_user_id       BIGINT  DEFAULT NULL;
    DECLARE v_stored_hash   VARCHAR(255);
    DECLARE v_recent_fails  INT;

    -- Check recent authentication failures for rate limiting
    SELECT COUNT(*) INTO v_recent_fails
    FROM user_authentication_logs
    WHERE username_attempt = p_username
      AND action = 'LOGIN_FAILED'
      AND created_at > DATE_SUB(NOW(), INTERVAL 15 MINUTE);

    IF v_recent_fails >= 5 THEN
        SET p_result_code = 2;
        SET p_user_id = NULL;
        SET p_authentication_token = NULL;
        -- Log the blocked authentication attempt
        INSERT INTO user_authentication_logs (user_id, username_attempt, action, ip_address, user_agent, failure_reason)
        VALUES (NULL, p_username, 'ACCOUNT_LOCKED', p_ip_address, p_user_agent, 'Too many failed authentication attempts');
        LEAVE proc_block;
    END IF;

    -- Validate credentials
    SELECT user_id, password_hash INTO v_user_id, v_stored_hash
    FROM users
    WHERE username = p_username AND deleted_at IS NULL;

    IF v_user_id IS NULL OR v_stored_hash != p_password_hash THEN
        SET p_result_code = 1;
        SET p_user_id = NULL;
        SET p_authentication_token = NULL;
        INSERT INTO user_authentication_logs (user_id, username_attempt, action, ip_address, user_agent, authentication_method, failure_reason)
        VALUES (v_user_id, p_username, 'LOGIN_FAILED', p_ip_address, p_user_agent, 'PASSWORD', 'Invalid credentials');
    ELSE
        -- Successful authentication: generate token reference
        SET p_user_id = v_user_id;
        SET p_authentication_token = SHA2(CONCAT(v_user_id, NOW(), RAND()), 256);
        SET p_result_code = 0;

        -- Update last_login and authentication_token
        UPDATE users
        SET last_login_at = NOW(), authentication_token = p_authentication_token
        WHERE user_id = v_user_id;

        -- Log successful authentication
        INSERT INTO user_authentication_logs (user_id, username_attempt, action, ip_address, user_agent, authentication_method)
        VALUES (v_user_id, p_username, 'LOGIN_SUCCESS', p_ip_address, p_user_agent, 'PASSWORD');
    END IF;
END proc_block$$

CREATE PROCEDURE sp_process_payment(
    IN  p_order_id            BIGINT,
    IN  p_payment_method_id   BIGINT,
    IN  p_amount              DECIMAL(12,2),
    IN  p_payment_gateway     VARCHAR(50),
    IN  p_gateway_reference   VARCHAR(255),
    OUT p_transaction_id      BIGINT,
    OUT p_payment_status      VARCHAR(20)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_transaction_id = -1;
        SET p_payment_status = 'FAILED';
    END;

    START TRANSACTION;

    -- Create payment transaction record
    INSERT INTO payment_transactions
        (order_id, payment_method_id, amount, payment_status, payment_gateway, payment_gateway_reference)
    VALUES
        (p_order_id, p_payment_method_id, p_amount, 'PROCESSING', p_payment_gateway, p_gateway_reference);

    SET p_transaction_id = LAST_INSERT_ID();

    -- Simulate payment gateway success (in demo: always succeeds)
    UPDATE payment_transactions
    SET payment_status = 'COMPLETED',
        updated_at = NOW()
    WHERE transaction_id = p_transaction_id;

    -- Update order status
    UPDATE orders
    SET status = 'CONFIRMED', updated_at = NOW()
    WHERE order_id = p_order_id;

    SET p_payment_status = 'COMPLETED';
    COMMIT;
END$$

CREATE PROCEDURE sp_invalidate_session(
    IN p_user_id   BIGINT,
    IN p_reason    VARCHAR(100)
)
BEGIN
    -- Deactivate all active sessions for the user
    UPDATE user_sessions
    SET is_active = 0
    WHERE user_id = p_user_id AND is_active = 1;

    -- Clear authentication token
    UPDATE users
    SET authentication_token = NULL
    WHERE user_id = p_user_id;

    -- Log the logout / session invalidation
    INSERT INTO user_authentication_logs (user_id, action, ip_address, failure_reason)
    VALUES (p_user_id, 'LOGOUT', '0.0.0.0', p_reason);

    -- Evict all user cache entries
    INSERT INTO cache_eviction_log (cache_key, cache_type, eviction_reason)
    SELECT cache_key, cache_type, 'MANUAL'
    FROM session_cache
    WHERE cache_key LIKE CONCAT('user:', p_user_id, ':%');

    DELETE FROM session_cache
    WHERE cache_key LIKE CONCAT('user:', p_user_id, ':%');
END$$

CREATE FUNCTION fn_get_session_cache_value(
    p_cache_key VARCHAR(512)
)
RETURNS MEDIUMTEXT
READS SQL DATA
BEGIN
    DECLARE v_value MEDIUMTEXT DEFAULT NULL;
    DECLARE v_expires_at DATETIME;

    SELECT cache_value, expires_at INTO v_value, v_expires_at
    FROM session_cache
    WHERE cache_key = p_cache_key;

    IF v_expires_at IS NULL OR v_expires_at < NOW() THEN
        RETURN NULL;
    END IF;

    -- Increment cache hit counter
    UPDATE session_cache SET hit_count = hit_count + 1 WHERE cache_key = p_cache_key;
    RETURN v_value;
END$$

CREATE FUNCTION fn_calculate_order_total(
    p_order_id BIGINT
)
RETURNS DECIMAL(12,2)
READS SQL DATA
BEGIN
    DECLARE v_total DECIMAL(12,2);
    SELECT COALESCE(SUM(line_total), 0) INTO v_total
    FROM order_items WHERE order_id = p_order_id;
    RETURN v_total;
END$$

-- ── Triggers ──────────────────────────────────────────────────────────────────

CREATE TRIGGER trg_after_payment_completed
AFTER UPDATE ON payment_transactions
FOR EACH ROW
BEGIN
    -- When payment status changes to COMPLETED, update the associated order
    IF NEW.payment_status = 'COMPLETED' AND OLD.payment_status != 'COMPLETED' THEN
        UPDATE orders SET status = 'CONFIRMED', updated_at = NOW()
        WHERE order_id = NEW.order_id AND status = 'PENDING';

        -- Queue order confirmation notification
        INSERT INTO notification_queue (user_id, channel, subject, message_body, template_id, template_variables, scheduled_at)
        SELECT o.user_id, 'EMAIL',
               CONCAT('Order ', o.order_number, ' Confirmed'),
               CONCAT('Your payment of ', NEW.amount, ' ', NEW.currency_code, ' was processed successfully.'),
               'ORDER_CONFIRMATION',
               JSON_OBJECT('order_id', o.order_id, 'amount', NEW.amount, 'payment_gateway_reference', NEW.payment_gateway_reference),
               NOW()
        FROM orders o WHERE o.order_id = NEW.order_id;
    END IF;

    -- When payment fails: log and send notification
    IF NEW.payment_status = 'FAILED' AND OLD.payment_status = 'PROCESSING' THEN
        INSERT INTO notification_queue (user_id, channel, subject, message_body, template_id, scheduled_at)
        SELECT o.user_id, 'EMAIL', 'Payment Failed',
               CONCAT('Your payment could not be processed. Error: ', COALESCE(NEW.error_message, 'Unknown error')),
               'PAYMENT_FAILED', NOW()
        FROM orders o WHERE o.order_id = NEW.order_id;
    END IF;
END$$

CREATE TRIGGER trg_before_user_authentication
BEFORE UPDATE ON users
FOR EACH ROW
BEGIN
    -- Prevent authentication_token from being set to empty string (must be NULL or valid hash)
    IF NEW.authentication_token = '' THEN
        SET NEW.authentication_token = NULL;
    END IF;
    SET NEW.updated_at = NOW();
END$$

CREATE TRIGGER trg_after_session_cache_expire
BEFORE INSERT ON session_cache
FOR EACH ROW
BEGIN
    -- Auto-clean expired entries for the same cache_key before inserting
    DELETE FROM session_cache WHERE cache_key = NEW.cache_key AND expires_at < NOW();
END$$

DELIMITER ;

-- ── Sample data ───────────────────────────────────────────────────────────────

INSERT INTO notification_templates (template_id, name, channel, subject_template, body_template) VALUES
('ORDER_CONFIRMATION',   'Order Confirmation Email', 'EMAIL', 'Order {{order_number}} Confirmed', 'Dear {{display_name}}, your order {{order_number}} has been confirmed. Total: {{amount}} {{currency}}.'),
('PAYMENT_FAILED',       'Payment Failed Notification', 'EMAIL', 'Payment Failed for Order {{order_number}}', 'We could not process your payment. Please update your payment method.'),
('PASSWORD_RESET',       'Password Reset Email', 'EMAIL', 'Reset your password', 'Click the link to reset your password: {{reset_link}}. Expires in 15 minutes.'),
('SESSION_EXPIRED',      'Session Expired Push Notification', 'PUSH', NULL, 'Your session has expired. Please log in again to continue.'),
('ORDER_SHIPPED',        'Order Shipped Notification', 'EMAIL', 'Your order is on its way!', 'Your order {{order_number}} has been shipped. Tracking: {{tracking_number}}');

INSERT INTO products (sku, name, description, price, stock_quantity, category_id) VALUES
('SOFT-ENT-001', 'Enterprise License - Backend Platform', 'Annual license for the backend API platform including authentication module', 2999.00, 999, 1),
('SOFT-ENT-002', 'Enterprise License - Payment Gateway Integration', 'Payment processing module with Stripe, PayPal, and SEPA support', 1499.00, 999, 1),
('SOFT-SRV-001', 'Professional Support (12 months)', 'Priority support with 4-hour SLA for production incidents', 4999.00, 100, 2),
('SOFT-TRN-001', 'Developer Training - Authentication & Security', '2-day hands-on workshop: OAuth2, JWT, and secure API development', 899.00, 20, 3),
('SOFT-TRN-002', 'Developer Training - Database Performance', '1-day workshop: query optimisation, indexing strategies, caching patterns', 599.00, 20, 3);


-- ============================================================
-- DATABASE: auth_db
-- ============================================================
USE auth_db;

-- ── Tables ────────────────────────────────────────────────────────────────────

CREATE TABLE roles (
    role_id     INT           NOT NULL AUTO_INCREMENT,
    role_name   VARCHAR(64)   NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id)
) ENGINE=InnoDB;

CREATE TABLE permissions (
    permission_id   INT           NOT NULL AUTO_INCREMENT,
    permission_name VARCHAR(100)  NOT NULL UNIQUE,  -- e.g. 'users:read', 'payment:process'
    resource        VARCHAR(64),
    action          VARCHAR(32),
    description     VARCHAR(255),
    PRIMARY KEY (permission_id),
    INDEX idx_permissions_resource (resource)
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
    role_id         INT NOT NULL,
    permission_id   INT NOT NULL,
    granted_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id)       REFERENCES roles(role_id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_role_id    BIGINT  NOT NULL AUTO_INCREMENT,
    user_id         BIGINT  NOT NULL,
    role_id         INT     NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    assigned_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by     BIGINT,
    PRIMARY KEY (user_role_id),
    UNIQUE KEY uq_user_role_tenant (user_id, role_id, tenant_id),
    INDEX idx_user_roles_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE oauth2_clients (
    client_id           VARCHAR(128)  NOT NULL,
    client_secret_hash  VARCHAR(255),
    client_name         VARCHAR(150)  NOT NULL,
    redirect_uris       TEXT          NOT NULL,
    scopes              TEXT          NOT NULL,          -- space-separated
    grant_types         VARCHAR(255)  NOT NULL,          -- authorization_code, client_credentials, refresh_token
    is_public           TINYINT(1)    NOT NULL DEFAULT 0,
    is_active           TINYINT(1)    NOT NULL DEFAULT 1,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id),
    INDEX idx_oauth2_clients_active (is_active)
) ENGINE=InnoDB;

CREATE TABLE oauth2_authorization_codes (
    code_id             BIGINT        NOT NULL AUTO_INCREMENT,
    code_hash           VARCHAR(255)  NOT NULL UNIQUE,
    client_id           VARCHAR(128)  NOT NULL,
    user_id             BIGINT        NOT NULL,
    redirect_uri        VARCHAR(500),
    scopes              TEXT,
    code_challenge      VARCHAR(255),                   -- PKCE S256
    code_challenge_method VARCHAR(10),
    expires_at          DATETIME      NOT NULL,
    used_at             DATETIME,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (code_id),
    INDEX idx_auth_codes_hash (code_hash(64)),
    FOREIGN KEY (client_id) REFERENCES oauth2_clients(client_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE oauth2_access_tokens (
    token_id            BIGINT        NOT NULL AUTO_INCREMENT,
    token_hash          VARCHAR(255)  NOT NULL UNIQUE,
    client_id           VARCHAR(128)  NOT NULL,
    user_id             BIGINT,
    scopes              TEXT,
    token_type          VARCHAR(20)   NOT NULL DEFAULT 'Bearer',
    expires_at          DATETIME      NOT NULL,
    revoked_at          DATETIME,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token_id),
    INDEX idx_oauth2_access_token_hash (token_hash(64)),
    INDEX idx_oauth2_access_token_user (user_id),
    INDEX idx_oauth2_access_token_expires (expires_at)
) ENGINE=InnoDB;

CREATE TABLE oauth2_refresh_tokens (
    refresh_token_id    BIGINT        NOT NULL AUTO_INCREMENT,
    token_hash          VARCHAR(255)  NOT NULL UNIQUE,
    access_token_id     BIGINT,
    client_id           VARCHAR(128)  NOT NULL,
    user_id             BIGINT        NOT NULL,
    scopes              TEXT,
    expires_at          DATETIME      NOT NULL,
    revoked_at          DATETIME,
    replaced_by         BIGINT,                         -- Refresh Token Rotation: new token ID
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (refresh_token_id),
    INDEX idx_oauth2_refresh_token_hash (token_hash(64)),
    INDEX idx_oauth2_refresh_token_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE jwt_blacklist (
    blacklist_id        BIGINT        NOT NULL AUTO_INCREMENT,
    token_jti           VARCHAR(255)  NOT NULL UNIQUE,  -- JWT jti claim (unique token ID)
    user_id             BIGINT        NOT NULL,
    token_type          ENUM('ACCESS','REFRESH') NOT NULL,
    revoked_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revocation_reason   VARCHAR(100),
    expires_at          DATETIME      NOT NULL,         -- kept until expired for cleanup
    PRIMARY KEY (blacklist_id),
    INDEX idx_jwt_blacklist_jti (token_jti(64)),
    INDEX idx_jwt_blacklist_user (user_id),
    INDEX idx_jwt_blacklist_expires (expires_at)
) ENGINE=InnoDB;

CREATE TABLE mfa_configurations (
    mfa_config_id       BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL UNIQUE,
    mfa_type            ENUM('TOTP','SMS','EMAIL','HARDWARE_KEY') NOT NULL,
    secret_encrypted    VARBINARY(500),                 -- AES-encrypted TOTP secret
    phone_number        VARCHAR(20),
    is_enabled          TINYINT(1)    NOT NULL DEFAULT 0,
    backup_codes_hash   JSON,                           -- hashed backup codes array
    last_used_at        DATETIME,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (mfa_config_id),
    INDEX idx_mfa_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE api_keys (
    api_key_id          BIGINT        NOT NULL AUTO_INCREMENT,
    key_prefix          VARCHAR(12)   NOT NULL,         -- first 8 chars for display: sk_live_abcd
    key_hash            VARCHAR(255)  NOT NULL UNIQUE,  -- SHA-256 hash of full key
    user_id             BIGINT        NOT NULL,
    name                VARCHAR(150)  NOT NULL,
    scopes              TEXT,
    expires_at          DATETIME,
    last_used_at        DATETIME,
    is_active           TINYINT(1)    NOT NULL DEFAULT 1,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (api_key_id),
    INDEX idx_api_keys_hash (key_hash(64)),
    INDEX idx_api_keys_user (user_id),
    INDEX idx_api_keys_active (is_active)
) ENGINE=InnoDB;

CREATE TABLE authentication_attempts (
    attempt_id          BIGINT        NOT NULL AUTO_INCREMENT,
    identifier          VARCHAR(255)  NOT NULL,         -- username or email
    ip_address          VARCHAR(45)   NOT NULL,
    attempt_type        ENUM('PASSWORD','MFA','API_KEY','OAUTH2') NOT NULL,
    success             TINYINT(1)    NOT NULL DEFAULT 0,
    failure_reason      VARCHAR(255),
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attempt_id),
    INDEX idx_auth_attempts_identifier (identifier(64)),
    INDEX idx_auth_attempts_ip (ip_address),
    INDEX idx_auth_attempts_created (created_at)
) ENGINE=InnoDB;

-- ── Views ─────────────────────────────────────────────────────────────────────

CREATE OR REPLACE VIEW active_api_keys AS
    SELECT
        ak.api_key_id,
        ak.user_id,
        ak.key_prefix,
        ak.name,
        ak.scopes,
        ak.expires_at,
        ak.last_used_at,
        ak.created_at
    FROM api_keys ak
    WHERE ak.is_active = 1
      AND (ak.expires_at IS NULL OR ak.expires_at > NOW());

CREATE OR REPLACE VIEW user_authentication_summary AS
    SELECT
        aa.identifier,
        COUNT(*) AS total_attempts,
        SUM(aa.success) AS successful_authentications,
        SUM(1 - aa.success) AS failed_authentication_attempts,
        MAX(aa.created_at) AS last_attempt_at,
        GROUP_CONCAT(DISTINCT aa.attempt_type) AS authentication_methods_used
    FROM authentication_attempts aa
    WHERE aa.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)
    GROUP BY aa.identifier;

CREATE OR REPLACE VIEW oauth2_token_statistics AS
    SELECT
        c.client_name,
        COUNT(DISTINCT at2.user_id) AS active_users,
        SUM(CASE WHEN at2.expires_at > NOW() AND at2.revoked_at IS NULL THEN 1 ELSE 0 END) AS valid_access_tokens,
        SUM(CASE WHEN rt.expires_at > NOW() AND rt.revoked_at IS NULL THEN 1 ELSE 0 END) AS valid_refresh_tokens
    FROM oauth2_clients c
    LEFT JOIN oauth2_access_tokens at2 ON c.client_id = at2.client_id
    LEFT JOIN oauth2_refresh_tokens rt ON c.client_id = rt.client_id
    GROUP BY c.client_id, c.client_name;

-- ── Stored Procedures ─────────────────────────────────────────────────────────

DELIMITER $$

CREATE PROCEDURE sp_validate_oauth2_token(
    IN  p_token_hash     VARCHAR(255),
    OUT p_user_id        BIGINT,
    OUT p_scopes         TEXT,
    OUT p_is_valid       TINYINT  -- 1 = valid, 0 = invalid/expired/revoked
)
BEGIN
    SELECT user_id, scopes, 1
    INTO p_user_id, p_scopes, p_is_valid
    FROM oauth2_access_tokens
    WHERE token_hash = p_token_hash
      AND expires_at > NOW()
      AND revoked_at IS NULL;

    IF p_user_id IS NULL THEN
        SET p_is_valid = 0;
    END IF;
END$$

CREATE PROCEDURE sp_revoke_all_user_tokens(
    IN p_user_id    BIGINT,
    IN p_reason     VARCHAR(100)
)
BEGIN
    -- Revoke all OAuth2 access tokens
    UPDATE oauth2_access_tokens
    SET revoked_at = NOW()
    WHERE user_id = p_user_id AND revoked_at IS NULL;

    -- Revoke all OAuth2 refresh tokens
    UPDATE oauth2_refresh_tokens
    SET revoked_at = NOW()
    WHERE user_id = p_user_id AND revoked_at IS NULL;

    -- Add active JWT access tokens to blacklist
    INSERT INTO jwt_blacklist (token_jti, user_id, token_type, revocation_reason, expires_at)
    SELECT CONCAT('revoked-', user_id, '-', UNIX_TIMESTAMP()), p_user_id, 'ACCESS', p_reason, DATE_ADD(NOW(), INTERVAL 1 DAY);
END$$

CREATE PROCEDURE sp_check_authentication_rate_limit(
    IN  p_identifier    VARCHAR(255),
    IN  p_ip_address    VARCHAR(45),
    OUT p_is_blocked    TINYINT,
    OUT p_retry_after   INT
)
BEGIN
    DECLARE v_recent_failures INT;

    -- Check recent authentication failures
    SELECT COUNT(*) INTO v_recent_failures
    FROM authentication_attempts
    WHERE (identifier = p_identifier OR ip_address = p_ip_address)
      AND success = 0
      AND created_at > DATE_SUB(NOW(), INTERVAL 15 MINUTE);

    IF v_recent_failures >= 10 THEN
        SET p_is_blocked = 1;
        SET p_retry_after = 900; -- 15 minutes in seconds
    ELSE
        SET p_is_blocked = 0;
        SET p_retry_after = 0;
    END IF;
END$$

DELIMITER ;

-- ── Sample data ───────────────────────────────────────────────────────────────

INSERT INTO roles (role_name, description) VALUES
('ADMIN',     'Full system administration access'),
('MANAGER',   'Team management and reporting access'),
('USER',      'Standard user access'),
('READ_ONLY', 'Read-only access to own resources'),
('API_CLIENT','Programmatic API client access');

INSERT INTO permissions (permission_name, resource, action, description) VALUES
('users:read',           'users',        'read',   'Read user profiles'),
('users:write',          'users',        'write',  'Create and update users'),
('users:delete',         'users',        'delete', 'Delete users'),
('payment:read',         'payment',      'read',   'View payment transactions'),
('payment:process',      'payment',      'write',  'Process payments'),
('orders:read',          'orders',       'read',   'View orders'),
('orders:manage',        'orders',       'write',  'Create and manage orders'),
('authentication:admin', 'authentication','admin', 'Manage authentication configuration'),
('api_keys:manage',      'api_keys',     'write',  'Create and revoke API keys');

INSERT INTO oauth2_clients (client_id, client_name, redirect_uris, scopes, grant_types, is_public) VALUES
('stacklens-web',    'StackLens Web App',      'http://localhost:8080/callback', 'openid profile email roles', 'authorization_code refresh_token', 1),
('demo-mobile',      'Demo Mobile App',         'demo://callback',                'openid profile', 'authorization_code refresh_token', 1),
('demo-service-acct','Demo Service Account',    '',                               'orders:read payment:read', 'client_credentials', 0);


-- ============================================================
-- DATABASE: analytics_db
-- ============================================================
USE analytics_db;

-- ── Tables ────────────────────────────────────────────────────────────────────

CREATE TABLE event_log (
    event_id            BIGINT        NOT NULL AUTO_INCREMENT,
    event_type          VARCHAR(100)  NOT NULL,   -- e.g. user_authentication, payment_initiated, order_placed
    user_id             BIGINT,
    session_id          BIGINT,
    tenant_id           VARCHAR(64),
    event_source        VARCHAR(64),              -- auth-service, order-service, payment-service
    event_data          JSON,
    ip_address          VARCHAR(45),
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    INDEX idx_event_type_created (event_type, created_at),
    INDEX idx_event_user (user_id),
    INDEX idx_event_tenant (tenant_id)
) ENGINE=InnoDB;

CREATE TABLE authentication_metrics (
    metric_id           BIGINT        NOT NULL AUTO_INCREMENT,
    metric_date         DATE          NOT NULL,
    total_authentication_attempts INT NOT NULL DEFAULT 0,
    successful_authentications INT    NOT NULL DEFAULT 0,
    failed_authentication_attempts INT NOT NULL DEFAULT 0,
    oauth2_authentications INT        NOT NULL DEFAULT 0,
    password_authentications INT      NOT NULL DEFAULT 0,
    mfa_challenges_issued INT         NOT NULL DEFAULT 0,
    mfa_challenges_passed INT         NOT NULL DEFAULT 0,
    unique_users_authenticated INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (metric_id),
    UNIQUE KEY uq_metric_date (metric_date),
    INDEX idx_auth_metrics_date (metric_date)
) ENGINE=InnoDB;

CREATE TABLE payment_analytics (
    analytics_id        BIGINT        NOT NULL AUTO_INCREMENT,
    analytics_date      DATE          NOT NULL,
    total_payment_transactions INT    NOT NULL DEFAULT 0,
    successful_payments INT           NOT NULL DEFAULT 0,
    failed_payments INT               NOT NULL DEFAULT 0,
    total_revenue       DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    avg_transaction_value DECIMAL(12,2),
    payment_method_breakdown JSON,    -- {"STRIPE": 120, "PAYPAL": 45, "SEPA": 23}
    currency_breakdown  JSON,
    PRIMARY KEY (analytics_id),
    UNIQUE KEY uq_payment_analytics_date (analytics_date)
) ENGINE=InnoDB;

CREATE TABLE cache_hit_statistics (
    stat_id             BIGINT        NOT NULL AUTO_INCREMENT,
    recorded_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cache_type          VARCHAR(64)   NOT NULL,
    total_requests      BIGINT        NOT NULL DEFAULT 0,
    cache_hits          BIGINT        NOT NULL DEFAULT 0,
    cache_misses        BIGINT        NOT NULL DEFAULT 0,
    cache_hit_ratio     DECIMAL(5,4)  NOT NULL DEFAULT 0.0000,  -- 0.8523 = 85.23%
    avg_response_time_ms DECIMAL(8,2),
    p99_response_time_ms DECIMAL(8,2),
    PRIMARY KEY (stat_id),
    INDEX idx_cache_stats_type_recorded (cache_type, recorded_at)
) ENGINE=InnoDB;

CREATE TABLE api_usage_statistics (
    stat_id             BIGINT        NOT NULL AUTO_INCREMENT,
    endpoint            VARCHAR(200)  NOT NULL,
    http_method         CHAR(6)       NOT NULL,
    status_code         SMALLINT,
    total_requests      BIGINT        NOT NULL DEFAULT 0,
    avg_response_ms     DECIMAL(8,2),
    p95_response_ms     DECIMAL(8,2),
    p99_response_ms     DECIMAL(8,2),
    error_rate          DECIMAL(5,4),
    recorded_date       DATE          NOT NULL,
    PRIMARY KEY (stat_id),
    INDEX idx_api_stats_endpoint_date (endpoint(100), recorded_date),
    INDEX idx_api_stats_status (status_code)
) ENGINE=InnoDB;

CREATE TABLE database_query_performance (
    query_id            BIGINT        NOT NULL AUTO_INCREMENT,
    query_digest        VARCHAR(255)  NOT NULL,
    query_sample        TEXT,
    database_name       VARCHAR(64)   NOT NULL,
    table_names         VARCHAR(500),
    execution_count     BIGINT        NOT NULL DEFAULT 0,
    avg_execution_ms    DECIMAL(10,3),
    max_execution_ms    DECIMAL(10,3),
    rows_examined_avg   BIGINT,
    rows_returned_avg   BIGINT,
    uses_index          TINYINT(1),
    recorded_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (query_id),
    INDEX idx_db_perf_database (database_name),
    INDEX idx_db_perf_avg_ms (avg_execution_ms)
) ENGINE=InnoDB;

CREATE TABLE user_activity_metrics (
    activity_id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    activity_date       DATE          NOT NULL,
    page_views          INT           NOT NULL DEFAULT 0,
    api_calls           INT           NOT NULL DEFAULT 0,
    orders_placed       INT           NOT NULL DEFAULT 0,
    payment_attempts    INT           NOT NULL DEFAULT 0,
    session_count       INT           NOT NULL DEFAULT 0,
    total_session_duration_sec INT,
    PRIMARY KEY (activity_id),
    UNIQUE KEY uq_user_activity_date (user_id, activity_date),
    INDEX idx_user_activity_date (activity_date)
) ENGINE=InnoDB;

CREATE TABLE search_query_log (
    log_id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT,
    query_text          VARCHAR(500)  NOT NULL,
    result_count        INT,
    response_time_ms    INT,
    search_source       VARCHAR(50),  -- 'elasticsearch', 'database', 'cache'
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    INDEX idx_search_log_created (created_at),
    INDEX idx_search_log_user (user_id)
) ENGINE=InnoDB;

-- ── Views ─────────────────────────────────────────────────────────────────────

CREATE OR REPLACE VIEW daily_authentication_report AS
    SELECT
        metric_date,
        total_authentication_attempts,
        successful_authentications,
        failed_authentication_attempts,
        ROUND(successful_authentications * 100.0 / NULLIF(total_authentication_attempts, 0), 2) AS success_rate_pct,
        mfa_challenges_issued,
        unique_users_authenticated
    FROM authentication_metrics
    ORDER BY metric_date DESC;

CREATE OR REPLACE VIEW payment_conversion_funnel AS
    SELECT
        analytics_date,
        total_payment_transactions,
        successful_payments,
        failed_payments,
        total_revenue,
        ROUND(successful_payments * 100.0 / NULLIF(total_payment_transactions, 0), 2) AS payment_success_rate,
        avg_transaction_value
    FROM payment_analytics
    ORDER BY analytics_date DESC;

CREATE OR REPLACE VIEW cache_performance_summary AS
    SELECT
        cache_type,
        AVG(cache_hit_ratio)                         AS avg_hit_ratio,
        SUM(total_requests)                          AS total_requests_all_time,
        AVG(avg_response_time_ms)                    AS avg_response_ms,
        MAX(recorded_at)                             AS last_recorded
    FROM cache_hit_statistics
    WHERE recorded_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
    GROUP BY cache_type;

-- ── Stored Procedures ─────────────────────────────────────────────────────────

DELIMITER $$

CREATE PROCEDURE sp_record_authentication_event(
    IN p_user_id     BIGINT,
    IN p_success     TINYINT,
    IN p_method      VARCHAR(50),
    IN p_ip_address  VARCHAR(45)
)
BEGIN
    -- Insert into event log
    INSERT INTO event_log (event_type, user_id, event_source, event_data, ip_address)
    VALUES (
        IF(p_success, 'user_authentication_success', 'user_authentication_failed'),
        p_user_id, 'auth-service',
        JSON_OBJECT('method', p_method, 'ip', p_ip_address, 'success', p_success),
        p_ip_address
    );

    -- Update daily authentication metrics (upsert)
    INSERT INTO authentication_metrics
        (metric_date, total_authentication_attempts, successful_authentications,
         failed_authentication_attempts,
         password_authentications, oauth2_authentications)
    VALUES
        (CURDATE(), 1,
         IF(p_success, 1, 0),
         IF(p_success, 0, 1),
         IF(p_method = 'PASSWORD', 1, 0),
         IF(p_method = 'OAUTH2', 1, 0))
    ON DUPLICATE KEY UPDATE
        total_authentication_attempts    = total_authentication_attempts + 1,
        successful_authentications       = successful_authentications + IF(p_success, 1, 0),
        failed_authentication_attempts   = failed_authentication_attempts + IF(p_success, 0, 1),
        password_authentications         = password_authentications + IF(p_method = 'PASSWORD', 1, 0),
        oauth2_authentications           = oauth2_authentications + IF(p_method = 'OAUTH2', 1, 0);
END$$

CREATE PROCEDURE sp_aggregate_payment_analytics(
    IN p_date DATE
)
BEGIN
    -- Aggregate payment data for the given date from the ecommerce database
    -- (Uses event_log as the source since cross-DB JOINs are not reliable in replicated setups)
    INSERT INTO payment_analytics
        (analytics_date, total_payment_transactions, successful_payments, failed_payments)
    SELECT
        p_date,
        COUNT(*),
        SUM(CASE WHEN JSON_VALUE(event_data, '$.status') = 'COMPLETED' THEN 1 ELSE 0 END),
        SUM(CASE WHEN JSON_VALUE(event_data, '$.status') = 'FAILED' THEN 1 ELSE 0 END)
    FROM event_log
    WHERE event_type = 'payment_transaction'
      AND DATE(created_at) = p_date
    ON DUPLICATE KEY UPDATE
        total_payment_transactions = VALUES(total_payment_transactions),
        successful_payments = VALUES(successful_payments),
        failed_payments = VALUES(failed_payments);
END$$

DELIMITER ;

-- ── Sample data ───────────────────────────────────────────────────────────────

INSERT INTO authentication_metrics
    (metric_date, total_authentication_attempts, successful_authentications, failed_authentication_attempts,
     oauth2_authentications, password_authentications, mfa_challenges_issued, mfa_challenges_passed, unique_users_authenticated)
VALUES
    ('2026-03-21', 1240, 1185, 55,  320, 920, 180, 175, 840),
    ('2026-03-22', 1380, 1320, 60,  380, 1000, 210, 205, 920),
    ('2026-03-23', 1510, 1445, 65,  420, 1090, 235, 228, 1010),
    ('2026-03-24', 820,  790,  30,  240, 580,  130, 127, 580);

INSERT INTO payment_analytics
    (analytics_date, total_payment_transactions, successful_payments, failed_payments, total_revenue, avg_transaction_value)
VALUES
    ('2026-03-21', 342, 328, 14, 87420.50,  255.61),
    ('2026-03-22', 398, 381, 17, 102340.00, 257.09),
    ('2026-03-23', 451, 435, 16, 118900.75, 263.74),
    ('2026-03-24', 221, 213, 8,  57320.25,  259.09);

INSERT INTO cache_hit_statistics (cache_type, total_requests, cache_hits, cache_misses, cache_hit_ratio, avg_response_time_ms, p99_response_time_ms)
VALUES
    ('USER_PROFILE',    45000, 39150, 5850,  0.8700, 2.4,  18.5),
    ('PRODUCT_LIST',    28000, 25760, 2240,  0.9200, 1.8,  12.3),
    ('ORDER_SUMMARY',   12000, 9840,  2160,  0.8200, 3.1,  24.7),
    ('SESSION_CACHE',   96000, 90240, 5760,  0.9400, 0.9,   7.2),
    ('SEARCH_RESULTS',  18000, 12600, 5400,  0.7000, 8.2,  65.0);

INSERT INTO event_log (event_type, user_id, session_id, event_source, event_data, ip_address)
VALUES
    ('user_authentication_success', 1001, 5001, 'auth-service', '{"method":"PASSWORD","mfa":false}', '10.0.1.42'),
    ('user_authentication_failed',  NULL, NULL,  'auth-service', '{"method":"PASSWORD","reason":"invalid_credentials"}', '185.220.101.32'),
    ('payment_initiated',           1001, 5001, 'payment-service', '{"amount":299.00,"currency":"EUR","gateway":"STRIPE"}', '10.0.1.42'),
    ('payment_transaction',         1001, 5001, 'payment-service', '{"amount":299.00,"status":"COMPLETED","gateway_ref":"ch_demo_1234"}', '10.0.1.42'),
    ('order_placed',                1001, 5001, 'order-service', '{"order_id":9001,"total":299.00,"items":1}', '10.0.1.42'),
    ('user_authentication_success', 1002, 5002, 'auth-service', '{"method":"OAUTH2","mfa":true}', '10.0.2.15'),
    ('session_cache_hit',           1002, 5002, 'api-gateway', '{"cache_type":"USER_PROFILE","cache_key":"user:1002:profile"}', '10.0.2.15'),
    ('user_authentication_success', 1003, 5003, 'auth-service', '{"method":"PASSWORD","mfa":false}', '192.168.1.100');
