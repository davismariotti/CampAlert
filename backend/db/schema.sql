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
  PRIMARY KEY ("id"),
  CONSTRAINT "fk_search_requests_v2_user" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id")
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
