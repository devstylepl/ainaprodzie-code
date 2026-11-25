package app.retrofit;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

/// The dev seed (`db/seed/demo-data.sql`, then its CREATED backfill `db/seed/demo-data-created-audit.sql`, its LINK
/// audit alignment `db/seed/demo-data-link-audit.sql` and its PESEL comment `db/seed/demo-data-pesel.sql`) applied to
/// the current schema inside a rolled-back transaction, so a schema change that the hand-written demo data no longer
/// satisfies fails here instead of on the next `dev` start.
@Transactional
class DemoDataTests extends RandomPortContext {

	private static final String SEED_ORGANIZATIONS = """
			('00000000-0000-0000-0000-0000000b0001', '00000000-0000-0000-0000-0000000b0002',
			 '00000000-0000-0000-0000-0000000b0003')""";

	@Autowired
	DataSource dataSource;

	@BeforeEach
	void applySeed() {
		new ResourceDatabasePopulator(new ClassPathResource("db/seed/demo-data.sql"),
				new ClassPathResource("db/seed/demo-data-created-audit.sql"),
				new ClassPathResource("db/seed/demo-data-link-audit.sql"),
				new ClassPathResource("db/seed/demo-data-pesel.sql")).execute(dataSource);
	}

	@Test
	void everyTicketHasOneCreatedEntryByItsAuthorAtItsCreation() {
		assertThat(count("""
				SELECT count(*) FROM ticket t
				WHERE t.organization_id IN %s
				  AND (SELECT count(*) FROM ticket_audit_entry a
				       WHERE a.ticket_id = t.id AND a.field = 'CREATED' AND a.actor_id = t.author_id
				         AND a.changed_at = t.created_at AND a.old_value IS NULL AND a.new_value = t.title) = 1
				  AND (SELECT count(*) FROM ticket_audit_entry a WHERE a.ticket_id = t.id AND a.field = 'CREATED') = 1
				""".formatted(SEED_ORGANIZATIONS))).isEqualTo(50);
	}

	/// Each side's value mirrors the relation `TicketQueries.links()` reads from that ticket, followed by the other
	/// ticket's reference.
	@Test
	void everySeededLinkIsAuditedOnBothTicketsWithSideAwareValues() {
		assertThat(count("""
				SELECT count(*) FROM ticket_audit_entry a JOIN ticket t ON t.id = a.ticket_id
				WHERE t.organization_id IN %s AND a.field = 'LINK'
				""".formatted(SEED_ORGANIZATIONS))).isEqualTo(10);
		assertThat(count("""
				SELECT count(*) FROM ticket_audit_entry a JOIN ticket t ON t.id = a.ticket_id
				WHERE t.organization_id IN %s AND a.field = 'LINK'
				  AND (a.old_value ~ '^POTENTIAL_DUPLICATE RET-' OR a.new_value ~ '^POTENTIAL_DUPLICATE RET-')
				""".formatted(SEED_ORGANIZATIONS))).isZero();
		assertThat(count("""
				SELECT count(*) FROM ticket_link l
				JOIN ticket t ON t.id = l.ticket_id
				JOIN ticket linked ON linked.id = l.linked_ticket_id
				WHERE t.organization_id IN %s
				  AND (SELECT count(*) FROM ticket_audit_entry a
				       WHERE a.ticket_id = l.ticket_id AND a.field = 'LINK' AND a.actor_id = l.created_by
				         AND a.changed_at = l.created_at AND a.old_value IS NULL
				         AND a.new_value = CASE WHEN l.type = 'RELATED' THEN 'RELATED' ELSE 'POTENTIAL_DUPLICATE_OF' END
				                           || ' RET-' || linked.number) = 1
				  AND (SELECT count(*) FROM ticket_audit_entry a
				       WHERE a.ticket_id = l.linked_ticket_id AND a.field = 'LINK' AND a.actor_id = l.created_by
				         AND a.changed_at = l.created_at AND a.old_value IS NULL
				         AND a.new_value = CASE WHEN l.type = 'RELATED' THEN 'RELATED' ELSE 'HAS_POTENTIAL_DUPLICATE' END
				                           || ' RET-' || t.number) = 1
				""".formatted(SEED_ORGANIZATIONS))).isEqualTo(5);
	}

	@Test
	void everySeededLinkIsStoredFromTheLaterTicketToTheEarlierOne() {
		assertThat(count("""
				SELECT count(*) FROM ticket_link l
				JOIN ticket t ON t.id = l.ticket_id
				JOIN ticket linked ON linked.id = l.linked_ticket_id
				WHERE t.organization_id IN %s AND t.number < linked.number
				""".formatted(SEED_ORGANIZATIONS))).isZero();
		assertThat(count("""
				SELECT count(*) FROM ticket_link l JOIN ticket t ON t.id = l.ticket_id
				WHERE t.organization_id IN %s
				""".formatted(SEED_ORGANIZATIONS))).isEqualTo(5);
	}

	@Test
	void linkedTicketsWereUpdatedNoEarlierThanTheirLinks() {
		assertThat(count("""
				SELECT count(*) FROM ticket_link l
				JOIN ticket t ON t.id = l.ticket_id
				JOIN ticket linked ON linked.id = l.linked_ticket_id
				WHERE t.organization_id IN %s AND (t.updated_at < l.created_at OR linked.updated_at < l.created_at)
				""".formatted(SEED_ORGANIZATIONS))).isZero();
	}

	@Test
	void seedsThreeOrganizationsThreeTeamsTenAccountsAndFiftyTickets() {
		assertThat(count("SELECT count(*) FROM organization WHERE id IN " + SEED_ORGANIZATIONS)).isEqualTo(3);
		assertThat(count("SELECT count(*) FROM team WHERE name IN ('Identity', 'Projects', 'Billing')")).isEqualTo(3);
		assertThat(count("SELECT count(*) FROM app_user WHERE id::text LIKE '00000000-0000-0000-0000-00000000a0%'"))
				.isEqualTo(10);
		assertThat(count("SELECT count(*) FROM ticket WHERE organization_id IN " + SEED_ORGANIZATIONS)).isEqualTo(50);
	}

	@Test
	void untriagedTicketsAreUnansweredAndTicketsInSupportsHandsAreAnswered() {
		assertThat(count("""
				SELECT count(*) FROM ticket
				WHERE organization_id IN %s
				  AND (status = 'NEW' AND first_response_at IS NOT NULL
				       OR status IN ('IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED') AND first_response_at IS NULL)
				""".formatted(SEED_ORGANIZATIONS))).isZero();
	}

	@Test
	void closedTicketsWereClosedByTheirLastStatusChange() {
		assertThat(count("""
				SELECT count(*) FROM ticket t
				WHERE t.organization_id IN %s AND t.status = 'CLOSED'
				  AND t.closed_at IS DISTINCT FROM (SELECT max(a.changed_at) FROM ticket_audit_entry a
				                                    WHERE a.ticket_id = t.id AND a.field = 'STATUS')
				""".formatted(SEED_ORGANIZATIONS))).isZero();
	}

	@Test
	void nothingHappensInTheFutureOrBeforeItsTicketWasCreated() {
		assertThat(count("""
				SELECT count(*) FROM ticket t
				WHERE t.organization_id IN %s AND (t.updated_at > now() OR t.updated_at < t.created_at
				    OR EXISTS (SELECT 1 FROM ticket_comment c WHERE c.ticket_id = t.id
				               AND (c.created_at > now() OR c.created_at < t.created_at))
				    OR EXISTS (SELECT 1 FROM ticket_audit_entry a WHERE a.ticket_id = t.id
				               AND (a.changed_at > now() OR a.changed_at < t.created_at)))
				""".formatted(SEED_ORGANIZATIONS))).isZero();
	}

	@Test
	void everyStatusChangeEndsInTheTicketsCurrentStatus() {
		assertThat(count("""
				SELECT count(*) FROM ticket t
				WHERE t.organization_id IN %s
				  AND t.status <> coalesce((SELECT a.new_value FROM ticket_audit_entry a
				                            WHERE a.ticket_id = t.id AND a.field = 'STATUS'
				                            ORDER BY a.changed_at DESC LIMIT 1), 'NEW')
				""".formatted(SEED_ORGANIZATIONS))).isZero();
	}

	private long count(String sql) {
		return jdbc.sql(sql).query(Long.class).single();
	}

}
