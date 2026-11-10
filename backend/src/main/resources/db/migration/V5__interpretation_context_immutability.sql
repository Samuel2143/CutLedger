CREATE FUNCTION preserve_interpretation_context() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(OLD.workspace_id,OLD.campaign_id,OLD.base_revision_id,OLD.brief_text,OLD.brief_hash,OLD.reference_date,OLD.request_key,OLD.request_hash,OLD.model_id,OLD.prompt_version,OLD.schema_version,OLD.base_usage_hash,OLD.cache_key)
 IS DISTINCT FROM ROW(NEW.workspace_id,NEW.campaign_id,NEW.base_revision_id,NEW.brief_text,NEW.brief_hash,NEW.reference_date,NEW.request_key,NEW.request_hash,NEW.model_id,NEW.prompt_version,NEW.schema_version,NEW.base_usage_hash,NEW.cache_key)
 THEN RAISE EXCEPTION 'Immutable interpretation context'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER interpretation_context_immutable BEFORE UPDATE ON interpretation FOR EACH ROW EXECUTE FUNCTION preserve_interpretation_context();
