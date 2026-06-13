-- Add new schema named "public"
CREATE SCHEMA IF NOT EXISTS "public";
-- Set comment to schema: "public"
COMMENT ON SCHEMA "public" IS 'standard public schema';
-- Create "search_requests" table
CREATE TABLE "public"."search_requests" (
  "id" serial NOT NULL,
  "campgrounds" jsonb NULL,
  "start_night" character varying(12) NOT NULL,
  "end_night" character varying(12) NOT NULL,
  "nights" integer NOT NULL,
  "persons" integer NOT NULL,
  PRIMARY KEY ("id")
);
-- Create "users" table
CREATE TABLE "public"."users" (
  "id" bigserial NOT NULL,
  "email" character varying(255) NOT NULL,
  "password_hash" character varying(255) NOT NULL,
  "pushover_user_key" character varying(255) NULL,
  "pushover_api_token" character varying(255) NULL,
  "pushover_override_enabled" boolean NOT NULL DEFAULT false,
  "timezone" character varying(64) NOT NULL DEFAULT 'America/Los_Angeles',
  PRIMARY KEY ("id"),
  UNIQUE ("email")
);
-- Create "search_requests_v2" table
CREATE TABLE "public"."search_requests_v2" (
  "id" serial NOT NULL,
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
  CONSTRAINT "fk_search_requests_v2_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id")
);
-- Create "search_request_checks" table
CREATE TABLE "public"."search_request_checks" (
  "id" bigserial NOT NULL,
  "search_request_id" integer NOT NULL,
  "checked_at" timestamptz NOT NULL,
  "available" boolean NOT NULL,
  "available_site_count" integer NOT NULL,
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_src_search_request" FOREIGN KEY ("search_request_id") REFERENCES "public"."search_requests_v2" ("id") ON DELETE CASCADE
);
-- Create "notification_outbox" table
CREATE TABLE "public"."notification_outbox" (
  "id" bigserial NOT NULL,
  "user_id" bigint NOT NULL,
  "request_id" integer NOT NULL,
  "type" character varying(16) NOT NULL,
  "send_after" timestamptz NOT NULL,
  "sent_at" timestamptz NULL,
  "missed_at" timestamptz NULL,
  "claimed_at" timestamptz NULL,
  "attempt_count" integer NOT NULL DEFAULT 0,
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_outbox_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id"),
  CONSTRAINT "fk_outbox_request" FOREIGN KEY ("request_id") REFERENCES "public"."search_requests_v2" ("id") ON DELETE CASCADE
);
CREATE INDEX ON "public"."notification_outbox" ("send_after") WHERE sent_at IS NULL AND missed_at IS NULL;
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
