-- Two CONFIRMED bookings for the same space can never overlap, even under concurrent requests.
-- Cancelled bookings do not block the slot. Ranges are half-open [start, end), so back-to-back bookings are allowed.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_bookings
    EXCLUDE USING gist (
        space_id WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status = 'CONFIRMED');
