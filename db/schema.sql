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
  PRIMARY KEY ("id")
);
