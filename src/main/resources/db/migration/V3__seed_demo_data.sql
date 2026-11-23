-- Demo data. The admin password hash is generated with bcrypt (pgcrypto), compatible with Spring Security.
-- DEMO ONLY: the password is "ChangeMe123!" and must be rotated in any real deployment.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, name, email, password_hash, role)
VALUES (gen_random_uuid(), 'Demo Admin', 'admin@example.com', crypt('ChangeMe123!', gen_salt('bf', 10)), 'ADMIN');

WITH coworking AS (
    INSERT INTO locations (id, name, address, city, opens_at, closes_at)
    VALUES (gen_random_uuid(), 'Downtown Coworking', 'Calle Mayor 1', 'Elche', '08:00', '20:00')
    RETURNING id
), sports AS (
    INSERT INTO locations (id, name, address, city, opens_at, closes_at)
    VALUES (gen_random_uuid(), 'Sports Center South', 'Avenida del Sur 25', 'Elche', '09:00', '22:00')
    RETURNING id
)
INSERT INTO spaces (id, location_id, name, type)
SELECT gen_random_uuid(), id, name, type FROM (
    SELECT id, 'Meeting Room A' AS name, 'MEETING_ROOM' AS type FROM coworking
    UNION ALL SELECT id, 'Meeting Room B', 'MEETING_ROOM' FROM coworking
    UNION ALL SELECT id, 'Hot Desk 1', 'DESK' FROM coworking
    UNION ALL SELECT id, 'Padel Court 1', 'SPORTS_COURT' FROM sports
    UNION ALL SELECT id, 'Padel Court 2', 'SPORTS_COURT' FROM sports
    UNION ALL SELECT id, 'Futsal Court', 'SPORTS_COURT' FROM sports
) s;
