CREATE TABLE merchant (
 id uuid PRIMARY KEY, name varchar(100) NOT NULL, expected_paypal_merchant_id varchar(100), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE asset (
 id uuid PRIMARY KEY, merchant_id uuid NOT NULL REFERENCES merchant, slug varchar(80) NOT NULL UNIQUE,
 name varchar(100) NOT NULL, local_preview_path text NOT NULL, provenance text NOT NULL, demo_license_text text NOT NULL
);
CREATE TABLE policy_version (
 id uuid PRIMARY KEY, merchant_id uuid NOT NULL REFERENCES merchant, family varchar(80) NOT NULL,
 version_number integer NOT NULL CHECK(version_number>0), schema_version integer NOT NULL CHECK(schema_version=1),
 terms_hash varchar(80) NOT NULL, clauses jsonb NOT NULL, effective_at timestamptz NOT NULL,
 UNIQUE(merchant_id,family,version_number)
);
CREATE TABLE asset_tier (
 id uuid PRIMARY KEY, asset_id uuid NOT NULL REFERENCES asset, policy_version_id uuid NOT NULL REFERENCES policy_version,
 tier_code varchar(20) NOT NULL CHECK(tier_code IN ('ORGANIC30','PAID90','GLOBAL365')),
 rank integer NOT NULL CHECK(rank BETWEEN 0 AND 2), cumulative_minor bigint NOT NULL CHECK(cumulative_minor>=0),
 currency char(3) NOT NULL CHECK(currency='USD'), coverage jsonb NOT NULL, UNIQUE(asset_id,policy_version_id,tier_code)
);
CREATE TABLE workspace (id uuid PRIMARY KEY, created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL);
CREATE TABLE browser_session (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspace, token_hash char(64) NOT NULL UNIQUE,
 csrf_token_hash char(64) NOT NULL, expires_at timestamptz NOT NULL, last_seen_at timestamptz NOT NULL
);
CREATE TABLE campaign (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspace, merchant_id uuid NOT NULL REFERENCES merchant,
 title varchar(120) NOT NULL, anchor_date date NOT NULL, usage_timezone varchar(20) NOT NULL CHECK(usage_timezone='UTC'),
 latest_revision_id uuid, entitlement_version bigint NOT NULL DEFAULT 1 CHECK(entitlement_version>0),
 version bigint NOT NULL DEFAULT 1 CHECK(version>0), active_payment_id uuid
);
CREATE INDEX campaign_ownership ON campaign(workspace_id,id);
CREATE TABLE campaign_asset (
 campaign_id uuid NOT NULL REFERENCES campaign, asset_id uuid NOT NULL REFERENCES asset,
 position smallint NOT NULL CHECK(position BETWEEN 0 AND 2), PRIMARY KEY(campaign_id,asset_id), UNIQUE(campaign_id,position)
);
CREATE TABLE interpretation (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspace, campaign_id uuid NOT NULL REFERENCES campaign,
 base_revision_id uuid NOT NULL, brief_text varchar(4000) NOT NULL, brief_hash char(64) NOT NULL,
 reference_date date NOT NULL, request_key uuid NOT NULL, request_hash char(64) NOT NULL,
 status varchar(30) NOT NULL CHECK(status IN ('PENDING','RUNNING','READY','NEEDS_CLARIFICATION','UNSUPPORTED','FAILED')),
 model_id varchar(100) NOT NULL, prompt_version varchar(40) NOT NULL, schema_version integer NOT NULL CHECK(schema_version=1),
 proposed_edits jsonb, normalized_candidate jsonb, issues jsonb, evidence jsonb, provider_reference varchar(200),
 latency_ms bigint, attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), run_after timestamptz NOT NULL,
 lease_owner varchar(100), lease_until timestamptz, completed_at timestamptz, created_at timestamptz NOT NULL,
 UNIQUE(workspace_id,request_key)
);
CREATE INDEX interpretation_ownership ON interpretation(workspace_id,id);
CREATE INDEX interpretation_work ON interpretation(run_after) WHERE completed_at IS NULL;
CREATE TABLE campaign_revision (
 id uuid PRIMARY KEY, campaign_id uuid NOT NULL REFERENCES campaign, sequence bigint NOT NULL CHECK(sequence>0),
 based_on_revision_id uuid REFERENCES campaign_revision, interpretation_id uuid REFERENCES interpretation,
 origin varchar(20) NOT NULL CHECK(origin IN ('AI_CONFIRMED','MANUAL','SEEDED')), usage jsonb NOT NULL,
 usage_hash char(64) NOT NULL, confirmation_evidence jsonb NOT NULL, created_at timestamptz NOT NULL,
 UNIQUE(campaign_id,sequence), UNIQUE(campaign_id,id)
);
ALTER TABLE interpretation ADD FOREIGN KEY(campaign_id,base_revision_id) REFERENCES campaign_revision(campaign_id,id);
ALTER TABLE campaign ADD FOREIGN KEY(id,latest_revision_id) REFERENCES campaign_revision(campaign_id,id);
CREATE TABLE quote (
 id uuid PRIMARY KEY, campaign_id uuid NOT NULL REFERENCES campaign, revision_id uuid NOT NULL,
 campaign_version bigint NOT NULL, entitlement_version bigint NOT NULL,
 policy_version_id uuid NOT NULL REFERENCES policy_version, snapshot_hash char(64) NOT NULL,
 total_minor bigint NOT NULL CHECK(total_minor>0), currency char(3) NOT NULL CHECK(currency='USD'),
 status varchar(20) NOT NULL CHECK(status IN ('OPEN','ACCEPTED','EXPIRED','INVALIDATED','FULFILLED','CLOSED')),
 created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL CHECK(expires_at>created_at),
 UNIQUE(campaign_id,id), FOREIGN KEY(campaign_id,revision_id) REFERENCES campaign_revision(campaign_id,id)
);
CREATE TABLE payment (
 id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspace, campaign_id uuid NOT NULL REFERENCES campaign,
 quote_id uuid NOT NULL UNIQUE, state varchar(30) NOT NULL CHECK(state IN ('CREATING_ORDER','AWAITING_APPROVAL','CAPTURE_COMMITTED','CAPTURE_PENDING','RECONCILING','SETTLED','FAILED','CANCELLED','REVIEW_REQUIRED')),
 resolution varchar(60), create_request_id uuid NOT NULL UNIQUE, capture_request_id uuid NOT NULL UNIQUE,
 create_payload jsonb NOT NULL, create_payload_hash char(64) NOT NULL, capture_payload jsonb NOT NULL, capture_payload_hash char(64) NOT NULL,
 provider_order_id varchar(100) UNIQUE, provider_capture_id varchar(100) UNIQUE, approval_url text,
 expected_merchant_id varchar(100) NOT NULL, expected_amount_minor bigint NOT NULL CHECK(expected_amount_minor>0),
 currency char(3) NOT NULL CHECK(currency='USD'), capture_committed_at timestamptz, first_create_sent_at timestamptz,
 first_capture_sent_at timestamptz, last_verified_at timestamptz, verification_snapshot jsonb,
 last_error_code varchar(80), next_check_at timestamptz, version bigint NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 CHECK(create_request_id<>capture_request_id), UNIQUE(campaign_id,id),
 FOREIGN KEY(campaign_id,quote_id) REFERENCES quote(campaign_id,id)
);
ALTER TABLE campaign ADD FOREIGN KEY(id,active_payment_id) REFERENCES payment(campaign_id,id);
CREATE INDEX payment_ownership ON payment(workspace_id,id);
CREATE INDEX payment_unresolved ON payment(next_check_at) WHERE state IN ('CREATING_ORDER','CAPTURE_COMMITTED','CAPTURE_PENDING','RECONCILING');
CREATE TABLE entitlement (
 id uuid PRIMARY KEY, campaign_id uuid NOT NULL REFERENCES campaign, asset_id uuid NOT NULL REFERENCES asset,
 policy_version_id uuid NOT NULL REFERENCES policy_version, tier_code varchar(20) NOT NULL CHECK(tier_code IN ('ORGANIC30','PAID90','GLOBAL365')),
 scope_snapshot jsonb NOT NULL, credited_minor bigint NOT NULL CHECK(credited_minor>=0), paid_delta_minor bigint NOT NULL CHECK(paid_delta_minor>=0),
 currency char(3) NOT NULL CHECK(currency='USD'), source varchar(20) NOT NULL CHECK(source IN ('SEEDED','PAYPAL_CAPTURE')),
 source_payment_id uuid REFERENCES payment, accepted_revision_id uuid NOT NULL, predecessor_id uuid REFERENCES entitlement,
 terms_hash varchar(80) NOT NULL, created_at timestamptz NOT NULL,
 CHECK((source='SEEDED' AND source_payment_id IS NULL AND paid_delta_minor=0) OR (source='PAYPAL_CAPTURE' AND source_payment_id IS NOT NULL AND paid_delta_minor>0)),
 UNIQUE(source_payment_id,asset_id), FOREIGN KEY(campaign_id,accepted_revision_id) REFERENCES campaign_revision(campaign_id,id),
 FOREIGN KEY(campaign_id,asset_id) REFERENCES campaign_asset(campaign_id,asset_id)
);
CREATE INDEX entitlement_history ON entitlement(campaign_id,asset_id);
CREATE TABLE entitlement_state (
 entitlement_id uuid PRIMARY KEY REFERENCES entitlement, status varchar(30) NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED_REVIEW')),
 reason varchar(100), changed_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 1
);
CREATE TABLE quote_line (
 id uuid PRIMARY KEY, quote_id uuid NOT NULL REFERENCES quote, asset_id uuid NOT NULL REFERENCES asset, prior_grant_id uuid REFERENCES entitlement,
 target_tier varchar(20) NOT NULL CHECK(target_tier IN ('ORGANIC30','PAID90','GLOBAL365')),
 prior_credit_minor bigint NOT NULL CHECK(prior_credit_minor>=0), target_cumulative_minor bigint NOT NULL,
 delta_minor bigint NOT NULL CHECK(delta_minor>0), purchased_scope jsonb NOT NULL, reason_codes jsonb NOT NULL, clause_ids jsonb NOT NULL,
 CHECK(target_cumulative_minor-prior_credit_minor=delta_minor), UNIQUE(quote_id,asset_id)
);
CREATE TABLE payment_operation (
 id uuid PRIMARY KEY, payment_id uuid NOT NULL REFERENCES payment, kind varchar(20) NOT NULL CHECK(kind IN ('CREATE_ORDER','CAPTURE','VERIFY')),
 request_id uuid NOT NULL, attempt_no integer NOT NULL CHECK(attempt_no>0), started_at timestamptz NOT NULL, completed_at timestamptz,
 result_class varchar(60), http_status integer, provider_debug_id varchar(100), sanitized_error varchar(100), UNIQUE(payment_id,kind,attempt_no)
);
CREATE TABLE payment_job (
 id uuid PRIMARY KEY, payment_id uuid NOT NULL REFERENCES payment, kind varchar(20) NOT NULL CHECK(kind IN ('CREATE_ORDER','CAPTURE','VERIFY')),
 run_after timestamptz NOT NULL, attempts integer NOT NULL DEFAULT 0, lease_owner varchar(100), lease_until timestamptz, done_at timestamptz
);
CREATE UNIQUE INDEX one_unfinished_payment_job ON payment_job(payment_id,kind) WHERE done_at IS NULL;
CREATE INDEX payment_job_work ON payment_job(run_after) WHERE done_at IS NULL;
CREATE TABLE webhook_event (
 provider_event_id varchar(100) PRIMARY KEY, event_type varchar(100) NOT NULL, provider_resource_id varchar(100),
 received_at timestamptz NOT NULL, verified_at timestamptz NOT NULL, payload_hash char(64) NOT NULL,
 sanitized_resource jsonb NOT NULL, processing_state varchar(30) NOT NULL, error_code varchar(80)
);
CREATE TABLE audit_event (
 id uuid PRIMARY KEY, workspace_id uuid REFERENCES workspace, campaign_id uuid REFERENCES campaign, payment_id uuid REFERENCES payment,
 event_type varchar(80) NOT NULL, actor_type varchar(30) NOT NULL, safe_details jsonb NOT NULL, created_at timestamptz NOT NULL
);
CREATE INDEX audit_history ON audit_event(campaign_id,created_at DESC);
CREATE TABLE request_dedup (
 workspace_id uuid NOT NULL REFERENCES workspace, operation varchar(60) NOT NULL, request_key uuid NOT NULL,
 payload_hash char(64) NOT NULL, resource_id uuid, response_code integer, created_at timestamptz NOT NULL,
 PRIMARY KEY(workspace_id,operation,request_key)
);
CREATE TABLE usage_counter (
 scope_key varchar(150) NOT NULL, window_start timestamptz NOT NULL, operation varchar(40) NOT NULL,
 count integer NOT NULL CHECK(count>=0), PRIMARY KEY(scope_key,window_start,operation)
);
CREATE FUNCTION reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Immutable historical record'; END; $$;
CREATE TRIGGER revision_immutable BEFORE UPDATE OR DELETE ON campaign_revision FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER grant_immutable BEFORE UPDATE OR DELETE ON entitlement FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER policy_immutable BEFORE UPDATE OR DELETE ON policy_version FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER tier_immutable BEFORE UPDATE OR DELETE ON asset_tier FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER quote_line_immutable BEFORE UPDATE OR DELETE ON quote_line FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON audit_event FOR EACH ROW EXECUTE FUNCTION reject_history_mutation();
CREATE FUNCTION preserve_quote_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (to_jsonb(NEW)-'status') IS DISTINCT FROM (to_jsonb(OLD)-'status') THEN RAISE EXCEPTION 'Immutable quote snapshot'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER quote_snapshot_immutable BEFORE UPDATE ON quote FOR EACH ROW EXECUTE FUNCTION preserve_quote_snapshot();
