-- Used by init-db.sh (psql variables :mb_pw and :ro_pw). Idempotent: passwords are reset to the .env values.
SELECT format('CREATE ROLE metabase LOGIN PASSWORD %L', :'mb_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'metabase') \gexec
SELECT format('ALTER ROLE metabase LOGIN PASSWORD %L', :'mb_pw') \gexec
SELECT 'CREATE DATABASE metabase OWNER metabase'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'metabase') \gexec

SELECT format('CREATE ROLE sijill_analytics LOGIN PASSWORD %L', :'ro_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sijill_analytics') \gexec
SELECT format('ALTER ROLE sijill_analytics LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE', :'ro_pw') \gexec
ALTER ROLE sijill_analytics SET default_transaction_read_only = on;
ALTER ROLE sijill_analytics SET statement_timeout = '30s';

-- Nothing in public: the reporting role reads the analytics views and no table directly.
REVOKE ALL ON SCHEMA public FROM sijill_analytics;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM sijill_analytics;
GRANT CONNECT ON DATABASE equigcc TO sijill_analytics;
GRANT USAGE ON SCHEMA analytics TO sijill_analytics;
GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO sijill_analytics;
-- Views added by later migrations (created by equigcc) become readable too.
ALTER DEFAULT PRIVILEGES FOR ROLE equigcc IN SCHEMA analytics GRANT SELECT ON TABLES TO sijill_analytics;
