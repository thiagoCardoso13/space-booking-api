CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(100)             NOT NULL,
    email         VARCHAR(255)             NOT NULL UNIQUE,
    password_hash VARCHAR(100)             NOT NULL,
    role          VARCHAR(20)              NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE locations (
    id        UUID PRIMARY KEY,
    name      VARCHAR(150) NOT NULL,
    address   VARCHAR(255) NOT NULL,
    city      VARCHAR(100) NOT NULL,
    opens_at  TIME         NOT NULL,
    closes_at TIME         NOT NULL,
    CONSTRAINT locations_hours_check CHECK (closes_at > opens_at)
);

CREATE TABLE spaces (
    id          UUID PRIMARY KEY,
    location_id UUID         NOT NULL REFERENCES locations (id),
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(30)  NOT NULL CHECK (type IN ('MEETING_ROOM', 'DESK', 'SPORTS_COURT')),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT spaces_name_unique_per_location UNIQUE (location_id, name)
);

CREATE INDEX idx_spaces_location ON spaces (location_id);

CREATE TABLE bookings (
    id         UUID PRIMARY KEY,
    space_id   UUID                     NOT NULL REFERENCES spaces (id),
    user_id    UUID                     NOT NULL REFERENCES users (id),
    starts_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    status     VARCHAR(20)              NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT bookings_time_check CHECK (ends_at > starts_at)
);

CREATE INDEX idx_bookings_user_starts ON bookings (user_id, starts_at);
CREATE INDEX idx_bookings_space_starts ON bookings (space_id, starts_at);
