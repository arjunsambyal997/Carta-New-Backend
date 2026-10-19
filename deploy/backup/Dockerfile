# Postgres 16 client tools plus rclone (off-site copies to R2 or B2) and age (encryption). Built on the server by
# docker-compose.prod.yml; nothing secret is baked in.
FROM postgres:16-alpine
RUN apk add --no-cache rclone age tzdata
COPY backup.sh restore.sh restore-drill.sh scheduler.sh /usr/local/bin/
RUN chmod 755 /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/restore-drill.sh /usr/local/bin/scheduler.sh
ENTRYPOINT ["/usr/local/bin/scheduler.sh"]
