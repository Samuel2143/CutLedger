CREATE FUNCTION preserve_payment_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.id,NEW.workspace_id,NEW.campaign_id,NEW.quote_id,NEW.create_request_id,NEW.capture_request_id,
        NEW.create_payload,NEW.create_payload_hash,NEW.capture_payload,NEW.capture_payload_hash,NEW.expected_merchant_id,
        NEW.expected_amount_minor,NEW.currency,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.id,OLD.workspace_id,OLD.campaign_id,OLD.quote_id,OLD.create_request_id,OLD.capture_request_id,
        OLD.create_payload,OLD.create_payload_hash,OLD.capture_payload,OLD.capture_payload_hash,OLD.expected_merchant_id,
        OLD.expected_amount_minor,OLD.currency,OLD.created_at) THEN RAISE EXCEPTION 'Immutable payment identity'; END IF;
 IF OLD.capture_committed_at IS NOT NULL AND NEW.capture_committed_at IS DISTINCT FROM OLD.capture_committed_at THEN RAISE EXCEPTION 'Immutable capture commitment'; END IF;
 IF OLD.provider_order_id IS NOT NULL AND NEW.provider_order_id IS DISTINCT FROM OLD.provider_order_id THEN RAISE EXCEPTION 'Immutable provider order'; END IF;
 IF OLD.provider_capture_id IS NOT NULL AND NEW.provider_capture_id IS DISTINCT FROM OLD.provider_capture_id THEN RAISE EXCEPTION 'Immutable provider capture'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER payment_identity_immutable BEFORE UPDATE ON payment FOR EACH ROW EXECUTE FUNCTION preserve_payment_identity();
CREATE INDEX revision_history ON campaign_revision(campaign_id,sequence DESC);
