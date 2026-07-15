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
  "site_ids" json NULL,
  "name" character varying(255) NOT NULL,
  "user_id" bigint NULL,
  "campground_name" character varying(255) NOT NULL DEFAULT '',
  "campground_timezone" character varying(64) NULL,
  "provider" character varying(32) NOT NULL DEFAULT 'RECREATION_GOV',
  "latest_start_day" date NULL,
  PRIMARY KEY ("id"),
  -- atlas:renamed_from fk_search_requests_v2_user
  CONSTRAINT "fk_search_requests_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id"),
  CONSTRAINT "chk_search_requests_provider" CHECK (provider IN ('RECREATION_GOV', 'CAMPLIFE'))
);
CREATE INDEX ON "public"."search_requests" ("user_id");
-- Create "recreation_gov_search_request_details" table
-- Step 1 of a two-step migration: additive only. search_requests.loops stays in place (unmapped by
-- the app after this step) until a follow-up PR drops it once this table is confirmed populated.
CREATE TABLE "public"."recreation_gov_search_request_details" (
  "search_request_id" bigint NOT NULL,
  "loops" json NOT NULL,
  PRIMARY KEY ("search_request_id"),
  CONSTRAINT "fk_recreation_gov_search_request_details_request" FOREIGN KEY ("search_request_id") REFERENCES "public"."search_requests" ("id") ON DELETE CASCADE
);
-- Create "camplife_search_request_details" table
CREATE TABLE "public"."camplife_search_request_details" (
  "search_request_id" bigint NOT NULL,
  "site_type_id" integer NULL,
  "amenity_ids" json NULL,
  PRIMARY KEY ("search_request_id"),
  CONSTRAINT "fk_camplife_search_request_details_request" FOREIGN KEY ("search_request_id") REFERENCES "public"."search_requests" ("id") ON DELETE CASCADE
);
-- Create "notification_outbox" table
CREATE TABLE "public"."notification_outbox" (
  "id" bigserial NOT NULL,
  "user_id" bigint NOT NULL,
  "request_id" bigint NOT NULL,
  "request_type" character varying(16) NOT NULL DEFAULT 'CAMPGROUND',
  "type" character varying(16) NOT NULL,
  "send_after" timestamptz NOT NULL,
  "sent_at" timestamptz NULL,
  "missed_at" timestamptz NULL,
  "claimed_at" timestamptz NULL,
  "attempt_count" integer NOT NULL DEFAULT 0,
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_outbox_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id"),
  CONSTRAINT "chk_outbox_type" CHECK (type IN ('AVAILABLE', 'UNAVAILABLE', 'REMINDER')),
  CONSTRAINT "chk_outbox_request_type" CHECK (request_type IN ('CAMPGROUND', 'PERMIT'))
);
CREATE INDEX ON "public"."notification_outbox" ("send_after") WHERE sent_at IS NULL AND missed_at IS NULL;
CREATE INDEX ON "public"."notification_outbox" ("request_type", "request_id");
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
  "matched_start_day" date NULL,
  "matched_end_day" date NULL,
  PRIMARY KEY ("search_request_id"),
  CONSTRAINT "fk_search_request_state_request" FOREIGN KEY ("search_request_id") REFERENCES "public"."search_requests" ("id") ON DELETE CASCADE,
  CONSTRAINT "chk_search_request_state_last_availability_state" CHECK (last_availability_state IN ('AVAILABLE', 'UNAVAILABLE'))
);
-- Create "permit_search_requests" table
CREATE TABLE "public"."permit_search_requests" (
  "id" bigserial NOT NULL,
  "permit_id" character varying(32) NOT NULL,
  "permit_name" character varying(255) NOT NULL,
  "permit_timezone" character varying(64) NULL,
  "group_size" integer NOT NULL,
  "name" character varying(255) NOT NULL,
  "user_id" bigint NULL,
  "search_type" character varying(16) NOT NULL,
  "provider" character varying(32) NOT NULL DEFAULT 'RECREATION_GOV',
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_permit_search_requests_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id"),
  CONSTRAINT "chk_permit_search_requests_search_type" CHECK (search_type IN ('ZONE', 'ITINERARY')),
  CONSTRAINT "chk_permit_search_requests_provider" CHECK (provider IN ('RECREATION_GOV'))
);
CREATE INDEX ON "public"."permit_search_requests" ("user_id");
-- Create "permit_zone_target" table
CREATE TABLE "public"."permit_zone_target" (
  "permit_search_request_id" bigint NOT NULL,
  "division_ids" json NOT NULL,
  "start_day" date NOT NULL,
  "end_day" date NOT NULL,
  PRIMARY KEY ("permit_search_request_id"),
  CONSTRAINT "fk_permit_zone_target_request" FOREIGN KEY ("permit_search_request_id") REFERENCES "public"."permit_search_requests" ("id") ON DELETE CASCADE
);
-- Create "permit_itinerary_target" table
CREATE TABLE "public"."permit_itinerary_target" (
  "permit_search_request_id" bigint NOT NULL,
  "legs" json NOT NULL,
  PRIMARY KEY ("permit_search_request_id"),
  CONSTRAINT "fk_permit_itinerary_target_request" FOREIGN KEY ("permit_search_request_id") REFERENCES "public"."permit_search_requests" ("id") ON DELETE CASCADE
);
-- Create "permit_search_request_state" table
CREATE TABLE "public"."permit_search_request_state" (
  "permit_search_request_id" bigint NOT NULL,
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
  "matched_division_id" character varying(32) NULL,
  "matched_date" date NULL,
  "blocking_division_id" character varying(32) NULL,
  "blocking_date" date NULL,
  PRIMARY KEY ("permit_search_request_id"),
  CONSTRAINT "fk_permit_search_request_state_request" FOREIGN KEY ("permit_search_request_id") REFERENCES "public"."permit_search_requests" ("id") ON DELETE CASCADE,
  CONSTRAINT "chk_permit_search_request_state_last_availability_state" CHECK (last_availability_state IN ('AVAILABLE', 'UNAVAILABLE'))
);
-- Create "poll_target_state" table
CREATE TABLE "public"."poll_target_state" (
  "target_type" character varying(16) NOT NULL,
  "target_id" character varying(64) NOT NULL,
  "provider" character varying(32) NOT NULL DEFAULT 'RECREATION_GOV',
  "phase_offset_ms" integer NOT NULL,
  "next_due_at" timestamptz NOT NULL,
  "locked_until" timestamptz NULL,
  "last_started_at" timestamptz NULL,
  "last_finished_at" timestamptz NULL,
  "last_status" character varying(16) NULL,
  "last_error" text NULL,
  PRIMARY KEY ("target_type", "provider", "target_id"),
  CONSTRAINT "chk_poll_target_state_target_type" CHECK (target_type IN ('CAMPGROUND', 'PERMIT')),
  CONSTRAINT "chk_poll_target_state_provider" CHECK (provider IN ('RECREATION_GOV', 'CAMPLIFE'))
);
CREATE INDEX ON "public"."poll_target_state" ("next_due_at", "locked_until");
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
