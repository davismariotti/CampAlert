-- Add new schema named "public"
CREATE SCHEMA IF NOT EXISTS "public";
-- Set comment to schema: "public"
COMMENT ON SCHEMA "public" IS 'standard public schema';
-- Create "users" table
CREATE TABLE "public"."users" (
  "id" bigserial NOT NULL,
  "email" character varying(255) NOT NULL,
  "password_hash" character varying(255) NOT NULL,
  "pushover_user_key" character varying(255) NULL,
  "pushover_api_token" character varying(255) NULL,
  "pushover_override_enabled" boolean NOT NULL DEFAULT false,
  "timezone" character varying(64) NOT NULL DEFAULT 'America/Los_Angeles',
  "email_verified_at" timestamptz NULL,
  PRIMARY KEY ("id"),
  UNIQUE ("email")
);
-- Create "search_requests" table
-- atlas:renamed_from search_requests_v2
CREATE TABLE "public"."search_requests" (
  "id" bigserial NOT NULL,
  "start_day" date NOT NULL,
  "nights" integer NOT NULL,
  "group_size" integer NOT NULL,
  "campsite_id" integer NOT NULL,
  "loops" json NULL,
  "name" character varying(255) NOT NULL,
  "completed" boolean NOT NULL,
  "user_id" bigint NULL,
  "pause_reason" character varying(64) NULL,
  "campground_name" character varying(255) NOT NULL DEFAULT '',
  "last_availability_state" character varying(16) NULL,
  "user_paused" boolean NOT NULL DEFAULT false,
  "last_notified_at" timestamptz NULL,
  "reminder_sent_at" timestamptz NULL,
  "campground_timezone" character varying(64) NULL,
  PRIMARY KEY ("id"),
  -- atlas:renamed_from fk_search_requests_v2_user
  CONSTRAINT "fk_search_requests_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id"),
  CONSTRAINT "chk_search_requests_last_availability_state" CHECK (last_availability_state IN ('AVAILABLE', 'UNAVAILABLE'))
);
CREATE INDEX ON "public"."search_requests" ("user_id");
-- Create "search_request_checks" table
CREATE TABLE "public"."search_request_checks" (
  "id" bigserial NOT NULL,
  "search_request_id" bigint NOT NULL,
  "checked_at" timestamptz NOT NULL,
  "available" boolean NOT NULL,
  "available_site_count" integer NOT NULL,
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_src_search_request" FOREIGN KEY ("search_request_id") REFERENCES "public"."search_requests" ("id") ON DELETE CASCADE
);
CREATE INDEX ON "public"."search_request_checks" ("search_request_id");
-- Create "notification_outbox" table
CREATE TABLE "public"."notification_outbox" (
  "id" bigserial NOT NULL,
  "user_id" bigint NOT NULL,
  "request_id" bigint NOT NULL,
  "type" character varying(16) NOT NULL,
  "send_after" timestamptz NOT NULL,
  "sent_at" timestamptz NULL,
  "missed_at" timestamptz NULL,
  "claimed_at" timestamptz NULL,
  "attempt_count" integer NOT NULL DEFAULT 0,
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_outbox_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id"),
  CONSTRAINT "fk_outbox_request" FOREIGN KEY ("request_id") REFERENCES "public"."search_requests" ("id") ON DELETE CASCADE,
  CONSTRAINT "chk_outbox_type" CHECK (type IN ('AVAILABLE', 'UNAVAILABLE', 'REMINDER'))
);
CREATE INDEX ON "public"."notification_outbox" ("send_after") WHERE sent_at IS NULL AND missed_at IS NULL;
CREATE INDEX ON "public"."notification_outbox" ("request_id");
CREATE INDEX ON "public"."notification_outbox" ("user_id");
-- Create "search_request_state" table
CREATE TABLE "public"."search_request_state" (
  "search_request_id" bigint NOT NULL,
  "completed" boolean NOT NULL DEFAULT false,
  "user_paused" boolean NOT NULL DEFAULT false,
  "pause_reason" character varying(64) NULL,
  "last_availability_state" character varying(16) NULL,
  "last_notified_at" timestamptz NULL,
  "reminder_sent_at" timestamptz NULL,
  "total_checks" integer NOT NULL DEFAULT 0,
  "available_checks" integer NOT NULL DEFAULT 0,
  "window_count" integer NOT NULL DEFAULT 0,
  "total_window_seconds" integer NOT NULL DEFAULT 0,
  "became_available_at" timestamptz NULL,
  PRIMARY KEY ("search_request_id"),
  CONSTRAINT "fk_search_request_state_request" FOREIGN KEY ("search_request_id") REFERENCES "public"."search_requests" ("id") ON DELETE CASCADE,
  CONSTRAINT "chk_search_request_state_last_availability_state" CHECK (last_availability_state IN ('AVAILABLE', 'UNAVAILABLE'))
);
-- Create "shedlock" table
CREATE TABLE "public"."shedlock" (
  "name" character varying(64) NOT NULL,
  "lock_until" timestamp NOT NULL,
  "locked_at" timestamp NOT NULL,
  "locked_by" character varying(255) NOT NULL,
  PRIMARY KEY ("name")
);
-- Create "persistent_logins" table
CREATE TABLE "public"."persistent_logins" (
  "username" character varying(64) NOT NULL,
  "series" character varying(64) NOT NULL,
  "token" character varying(64) NOT NULL,
  "last_used" timestamp NOT NULL,
  PRIMARY KEY ("series")
);
-- Create "phone_numbers" table
CREATE TABLE "public"."phone_numbers" (
  "id" bigserial NOT NULL,
  "user_id" bigint NOT NULL,
  "phone" character varying(20) NOT NULL,
  "status" character varying(32) NOT NULL,
  "first_message_sent" boolean NOT NULL DEFAULT false,
  "sms_consent_at" timestamptz NOT NULL,
  "created_at" timestamptz NOT NULL DEFAULT now(),
  "verified_at" timestamptz NULL,
  PRIMARY KEY ("id"),
  UNIQUE ("phone"),
  CONSTRAINT "fk_phone_numbers_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id")
);
CREATE INDEX ON "public"."phone_numbers" ("user_id");
-- PRODUCTION MIGRATION NOTE: Before deploying login enforcement (section 3 of the
-- email-verification change), backfill all pre-existing users as verified:
--   UPDATE users SET email_verified_at = now() WHERE email_verified_at IS NULL;
-- This must run while the old application code is still live (before the new code
-- that blocks unverified login is deployed). Do NOT roll this back after it runs.
-- Create "email_verifications" table
CREATE TABLE "public"."email_verifications" (
  "id" uuid NOT NULL,
  "user_id" bigint NOT NULL,
  "code_hash" text NOT NULL,
  "attempts" smallint NOT NULL DEFAULT 0,
  "created_at" timestamptz NOT NULL,
  "expires_at" timestamptz NOT NULL,
  "consumed_at" timestamptz NULL,
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_email_verifications_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id")
);
CREATE INDEX ON "public"."email_verifications" ("user_id", "consumed_at", "expires_at");
-- Create "password_resets" table
CREATE TABLE "public"."password_resets" (
  "id" uuid NOT NULL,
  "user_id" bigint NOT NULL,
  "token_hash" text NOT NULL,
  "created_at" timestamptz NOT NULL,
  "expires_at" timestamptz NOT NULL,
  "consumed_at" timestamptz NULL,
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_password_resets_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id")
);
CREATE INDEX ON "public"."password_resets" ("user_id", "consumed_at", "expires_at");
