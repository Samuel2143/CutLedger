CREATE TABLE model_dispatch_gate (
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
 next_dispatch_at timestamptz NOT NULL
);
INSERT INTO model_dispatch_gate VALUES (true, now());
