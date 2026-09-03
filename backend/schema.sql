-- 建立資料庫（若不存在）
CREATE DATABASE IF NOT EXISTS `kitchen_detection`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `kitchen_detection`;

-- 建立感測器紀錄資料表
CREATE TABLE IF NOT EXISTS `sensor_logs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `device_id` VARCHAR(64) NOT NULL,
  `gas_level` DECIMAL(10,2) NULL,
  `temperature` DECIMAL(7,2) NOT NULL,
  `humidity` DECIMAL(7,2) NOT NULL,
  `lpg` DECIMAL(10,2) NOT NULL,
  `co` DECIMAL(10,2) NOT NULL,
  `smoke` DECIMAL(10,2) NOT NULL,
  `fire` TINYINT(1) NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_sensor_logs_device_id` (`device_id`),
  INDEX `idx_sensor_logs_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 建立使用者/裝置註冊資料表
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `phone_number` VARCHAR(20) NOT NULL COMMENT '註冊手機號碼',
  `fcm_token` TEXT NULL COMMENT 'FCM推播Token',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_users_phone_number` (`phone_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;