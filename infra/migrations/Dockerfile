# Flyway with the SQL baked in: the migrate Job in Minikube runs this image (ADR-005).
# docker-compose mounts the same folder as a volume instead.
FROM flyway/flyway:11

COPY *.sql /flyway/sql/
