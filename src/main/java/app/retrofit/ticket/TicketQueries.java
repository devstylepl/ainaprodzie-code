package app.retrofit.ticket;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.retrofit.organization.Caller;
import app.retrofit.organization.CallerLookup;

/// The read side of tickets for every caller, the REST API included (ADR 0001). Visibility is
/// enforced here and nowhere else.
///
/// Public because the chat's tools call [#list(Caller, TicketFilter, TicketPageRequest)] and
/// [#find(Caller, String)] from outside this package, always for the signed-in user its question looked up.
@Service
@Transactional(readOnly = true)
public class TicketQueries {

	/// Tickets with their organization, the two instants the SLA compares, named `sla.response_deadline` and
	/// `sla.waiting_until`, and the time of an unanswered customer comment, `unanswered.commented_at` (null when
	/// there is none), for every query that reads, filters, counts or sorts by them. An unanswered customer comment
	/// is the latest public comment on a ticket in progress after its first response, when a customer wrote it:
	/// support staff owe the next public comment. Before the first response the SLA measures that wait instead, and
	/// a ticket support set to waiting for customer, resolved or closed is not theirs to answer.
	private static final String TICKETS_WITH_SLA = """
			FROM ticket t
			JOIN organization o ON o.id = t.organization_id
			CROSS JOIN LATERAL (
			    SELECT t.created_at + o.first_response_sla_minutes * interval '1 minute' AS response_deadline,
			           coalesce(t.first_response_at, t.closed_at, CAST(:now AS timestamptz)) AS waiting_until
			) sla
			LEFT JOIN LATERAL (
			    SELECT latest.created_at AS commented_at
			    FROM (SELECT c.created_at, c.author_id
			          FROM ticket_comment c
			          WHERE c.ticket_id = t.id AND NOT c.internal
			          ORDER BY c.created_at DESC, c.id DESC
			          LIMIT 1) latest
			    -- Only customers belong to an organization.
			    JOIN app_user commenter ON commenter.id = latest.author_id AND commenter.organization_id IS NOT NULL
			    WHERE t.status = 'IN_PROGRESS' AND t.first_response_at IS NOT NULL
			) unanswered ON true
			""";

	/// The columns [#summary] reads; a query that needs more appends them after these.
	private static final String SUMMARY_COLUMNS = """
			SELECT sla.response_deadline, sla.waiting_until > sla.response_deadline AS sla_breached,
			       CAST(extract(epoch FROM sla.waiting_until - t.created_at) * 1000000 AS bigint) AS waiting_micros,
			       t.number, t.title, t.category, t.priority, t.status,
			       t.created_at, t.updated_at, t.first_response_at, t.closed_at, unanswered.commented_at,
			       o.id AS organization_id, o.name AS organization_name,
			       author.id AS author_id, author.display_name AS author_name,
			       team.id AS team_id, team.name AS team_name,
			       assignee.id AS assignee_id, assignee.display_name AS assignee_name""";

	private static final String SUMMARY_FROM = TICKETS_WITH_SLA + """
			JOIN app_user author ON author.id = t.author_id
			LEFT JOIN team ON team.id = t.team_id
			LEFT JOIN app_user assignee ON assignee.id = t.assignee_id
			""";

	/// A response deadline closer than this is due soon: still within the SLA, but next in line.
	private static final Duration DUE_SOON = Duration.ofHours(1);

	private final JdbcClient jdbc;

	private final CallerLookup callers;

	private final Clock clock;

	TicketQueries(JdbcClient jdbc, CallerLookup callers, Clock clock) {
		this.jdbc = jdbc;
		this.callers = callers;
		this.clock = clock;
	}

	TicketPage list(UUID callerId, TicketFilter filter, TicketPageRequest pageRequest) {
		return list(callers.current(callerId), filter, pageRequest);
	}

	/// As [#list(UUID, TicketFilter, TicketPageRequest)] for a caller the operation has already looked up.
	public TicketPage list(Caller caller, TicketFilter filter, TicketPageRequest pageRequest) {
		var query = new Conditions(caller, clock.instant());
		if (filter.text() != null) {
			var reference = TicketReference.parse(filter.text());
			if (reference.isPresent()) {
				query.add("t.number = :number", "number", reference.get().number());
			}
			else {
				query.add("(t.title ~* :text OR t.description ~* :text)", "text", fromWordStart(filter.text()));
			}
		}
		if (filter.organizationId() != null) {
			query.add("t.organization_id = :organizationId", "organizationId", filter.organizationId());
		}
		if (!filter.statuses().isEmpty()) {
			query.add("t.status IN (:statuses)", "statuses", names(filter.statuses()));
		}
		if (!filter.categories().isEmpty()) {
			query.add("t.category IN (:categories)", "categories", names(filter.categories()));
		}
		if (!filter.priorities().isEmpty()) {
			query.add("t.priority IN (:priorities)", "priorities", names(filter.priorities()));
		}
		if (filter.teamId() != null) {
			query.add("t.team_id = :teamId", "teamId", filter.teamId());
		}
		if (filter.assigneeId() != null) {
			query.add("t.assignee_id = :assigneeId", "assigneeId", filter.assigneeId());
		}
		if (filter.slaBreached() != null) {
			query.add("(sla.waiting_until > sla.response_deadline) = :slaBreached", "slaBreached",
					filter.slaBreached());
		}
		if (filter.authorId() != null) {
			query.add("t.author_id = :authorId", "authorId", filter.authorId());
		}
		if (filter.createdFrom() != null) {
			query.add("t.created_at >= :createdFrom", "createdFrom", utc(filter.createdFrom()));
		}
		if (filter.createdTo() != null) {
			query.add("t.created_at < :createdTo", "createdTo", utc(filter.createdTo()));
		}
		if (filter.deadlineBefore() != null) {
			query.add("t.first_response_at IS NULL AND t.closed_at IS NULL AND sla.response_deadline < :deadlineBefore",
					"deadlineBefore", utc(filter.deadlineBefore()));
		}
		if (filter.attention() != null) {
			query.add(condition(filter.attention()));
		}
		var total = jdbc.sql("SELECT count(*)\n" + TICKETS_WITH_SLA + query.where())
				.params(query.params)
				.query(Long.class)
				.single();
		var order = switch (pageRequest.sort()) {
			case RECENTLY_UPDATED -> "t.updated_at DESC";
			case LONGEST_WAITING -> "(sla.waiting_until - t.created_at) DESC";
			case RESPONSE_DEADLINE -> """
					CASE WHEN t.closed_at IS NOT NULL THEN 2 WHEN t.first_response_at IS NOT NULL THEN 1 ELSE 0 END,
					CASE WHEN t.first_response_at IS NULL AND t.closed_at IS NULL THEN sla.response_deadline END,
					t.updated_at DESC""";
		};
		var items = jdbc.sql(SUMMARY_COLUMNS + "\n" + SUMMARY_FROM + query.where() + """
				ORDER BY %s, t.number DESC
				LIMIT :limit OFFSET :offset
				""".formatted(order))
				.params(query.params)
				.param("limit", pageRequest.size())
				.param("offset", (long) pageRequest.page() * pageRequest.size())
				.query((rs, rowNum) -> summary(rs))
				.list();
		return new TicketPage(items, total);
	}

	/// How many tickets each of support staff's views holds and how many need a person now, in one pass over the
	/// tickets the caller may see. The desk is support staff's, so a customer is refused.
	TicketCounts counts(UUID callerId) {
		var caller = callers.current(callerId);
		if (!(caller instanceof Caller.Support)) {
			throw TicketProblems.forbidden();
		}
		var query = new Conditions(caller, clock.instant());
		return jdbc.sql("""
				SELECT count(*) AS all_tickets,
				       count(*) FILTER (WHERE t.assignee_id = :callerId) AS assigned_to_me,
				       count(*) FILTER (WHERE t.team_id = (SELECT u.team_id FROM app_user u WHERE u.id = :callerId))
				           AS my_team,
				       count(*) FILTER (WHERE %s) AS past_deadline,
				       count(*) FILTER (WHERE %s) AS due_within_hour,
				       count(*) FILTER (WHERE %s) AS unanswered_customer_comment,
				       count(*) FILTER (WHERE %s) AS unassigned
				""".formatted(condition(Attention.PAST_DEADLINE), condition(Attention.DUE_WITHIN_HOUR),
				condition(Attention.UNANSWERED_CUSTOMER_COMMENT), condition(Attention.UNASSIGNED))
				+ TICKETS_WITH_SLA + query.where())
				.params(query.params)
				.param("callerId", caller.id())
				.query((rs, rowNum) -> new TicketCounts(rs.getLong("all_tickets"), rs.getLong("assigned_to_me"),
						rs.getLong("my_team"), rs.getLong("past_deadline"),
						rs.getLong("due_within_hour"), rs.getLong("unassigned"),
						rs.getLong("unanswered_customer_comment")))
				.single();
	}

	/// How many tickets each of a customer's views holds, over their organization's tickets only. Support staff have
	/// the desk's [#counts] instead, so they are refused.
	CustomerTicketCounts customerCounts(UUID callerId) {
		var caller = callers.current(callerId);
		if (!(caller instanceof Caller.Customer)) {
			throw TicketProblems.forbidden();
		}
		var query = new Conditions(caller, clock.instant());
		return jdbc.sql("""
				SELECT count(*) AS all_tickets,
				       count(*) FILTER (WHERE t.status = 'WAITING_FOR_CUSTOMER') AS waiting_for_customer,
				       count(*) FILTER (WHERE t.author_id = :callerId) AS raised_by_me
				FROM ticket t
				""" + query.where())
				.params(query.params)
				.param("callerId", caller.id())
				.query((rs, rowNum) -> new CustomerTicketCounts(rs.getLong("all_tickets"),
						rs.getLong("waiting_for_customer"), rs.getLong("raised_by_me")))
				.single();
	}

	/// The ticket with this reference, or empty when there is none or the caller may not see it.
	Optional<TicketDetails> find(UUID callerId, String reference) {
		return find(callers.current(callerId), reference);
	}

	/// As [#find(UUID, String)] for a caller the operation has already looked up.
	public Optional<TicketDetails> find(Caller caller, String reference) {
		var parsed = TicketReference.parse(reference);
		if (parsed.isEmpty()) {
			return Optional.empty();
		}
		var query = new Conditions(caller, clock.instant());
		query.add("t.number = :number", "number", parsed.get().number());
		return jdbc.sql(SUMMARY_COLUMNS + ", t.id, t.version, t.description, t.resolution\n" + SUMMARY_FROM + query.where())
				.params(query.params)
				.query((rs, rowNum) -> new Row(rs.getObject("id", UUID.class), summary(rs), rs.getLong("version"),
						rs.getString("description"), rs.getString("resolution")))
				.optional()
				.map(row -> new TicketDetails(row.summary(), row.version(), row.description(), row.resolution(),
						comments(row.id(), caller), history(row.id(), caller), links(row.id(), caller)));
	}

	/// What a write decides on, or empty when there is no such ticket or the caller may not see it. `lock` holds the
	/// ticket row until the enclosing transaction ends.
	Optional<Ticket> findState(Caller caller, String reference, boolean lock) {
		var parsed = TicketReference.parse(reference);
		if (parsed.isEmpty()) {
			return Optional.empty();
		}
		var query = new Conditions(caller, clock.instant());
		query.add("t.number = :number", "number", parsed.get().number());
		return jdbc.sql("""
				SELECT t.id, t.number, t.version, t.author_id, t.title, t.description, t.status, t.priority, t.category,
				       t.resolution, t.first_response_at, t.closed_at, team.id AS team_id, team.name AS team_name,
				       assignee.id AS assignee_id, assignee.display_name AS assignee_name
				FROM ticket t
				LEFT JOIN team ON team.id = t.team_id
				LEFT JOIN app_user assignee ON assignee.id = t.assignee_id
				""" + query.where() + (lock ? "FOR UPDATE OF t\n" : ""))
				.params(query.params)
				.query((rs, rowNum) -> new Ticket(rs.getObject("id", UUID.class), rs.getLong("number"),
						rs.getLong("version"), rs.getObject("author_id", UUID.class), rs.getString("title"),
						rs.getString("description"), TicketStatus.valueOf(rs.getString("status")),
						TicketPriority.valueOf(rs.getString("priority")), category(rs), optionalRef(rs, "team"),
						optionalRef(rs, "assignee"), rs.getString("resolution"),
						optionalInstant(rs, "first_response_at"), optionalInstant(rs, "closed_at")))
				.optional();
	}

	private List<TicketComment> comments(UUID ticketId, Caller caller) {
		return jdbc.sql("""
				SELECT c.id, c.body, c.internal, c.created_at, u.id AS author_id, u.display_name AS author_name,
				       u.roles = ARRAY['SUPPORT']::text[] AS from_support
				FROM ticket_comment c
				JOIN app_user u ON u.id = c.author_id
				WHERE c.ticket_id = :ticketId AND (:includeInternal OR NOT c.internal)
				ORDER BY c.created_at, c.id
				""")
				.param("ticketId", ticketId)
				.param("includeInternal", caller instanceof Caller.Support)
				.query((rs, rowNum) -> new TicketComment(rs.getObject("id", UUID.class), ref(rs, "author"), rs.getBoolean("from_support"),
						rs.getBoolean("internal"), rs.getString("body"), instant(rs, "created_at")))
				.list();
	}

	/// A customer gets no entry about a link to a ticket they may not see, as with [#links]. Such an entry names the
	/// other ticket only in its value (`RELATED RET-1003`); one whose reference cannot be read is left out too.
	private List<TicketAuditEntry> history(UUID ticketId, Caller caller) {
		return jdbc.sql("""
				SELECT a.changed_at, a.field, a.old_value, a.new_value,
				       u.id AS actor_id, u.display_name AS actor_name
				FROM ticket_audit_entry a
				JOIN app_user u ON u.id = a.actor_id
				WHERE a.ticket_id = :ticketId
				  AND (CAST(:callerOrganizationId AS uuid) IS NULL OR a.field <> 'LINK'
				       OR EXISTS (SELECT 1 FROM ticket linked
				                  WHERE linked.number = CAST(substring(coalesce(a.new_value, a.old_value)
				                                                       FROM ' RET-([0-9]+)$') AS bigint)
				                    AND linked.organization_id = :callerOrganizationId))
				ORDER BY a.changed_at, a.id
				""")
				.param("ticketId", ticketId)
				.param("callerOrganizationId", customerOrganization(caller), Types.OTHER)
				.query((rs, rowNum) -> new TicketAuditEntry(ref(rs, "actor"), instant(rs, "changed_at"),
						AuditedField.valueOf(rs.getString("field")), rs.getString("old_value"),
						rs.getString("new_value")))
				.list();
	}

	/// Links to tickets the caller may see; a customer never learns that a link to another organization exists.
	private List<TicketLink> links(UUID ticketId, Caller caller) {
		return jdbc.sql("""
				SELECT CASE
				           WHEN l.type = 'RELATED' THEN 'RELATED'
				           WHEN l.ticket_id = :ticketId THEN 'POTENTIAL_DUPLICATE_OF'
				           ELSE 'HAS_POTENTIAL_DUPLICATE'
				       END AS relation,
				       other.number, other.title, other.status
				FROM ticket_link l
				JOIN ticket other
				    ON other.id = CASE WHEN l.ticket_id = :ticketId THEN l.linked_ticket_id ELSE l.ticket_id END
				WHERE (l.ticket_id = :ticketId OR l.linked_ticket_id = :ticketId)
				  AND (CAST(:callerOrganizationId AS uuid) IS NULL OR other.organization_id = :callerOrganizationId)
				ORDER BY other.number
				""")
				.param("ticketId", ticketId)
				.param("callerOrganizationId", customerOrganization(caller), Types.OTHER)
				.query((rs, rowNum) -> new TicketLink(LinkRelation.valueOf(rs.getString("relation")),
						new TicketReference(rs.getLong("number")).toString(), rs.getString("title"),
						TicketStatus.valueOf(rs.getString("status"))))
				.list();
	}

	private static @Nullable UUID customerOrganization(Caller caller) {
		return switch (caller) {
			case Caller.Customer customer -> customer.organizationId();
			case Caller.Support _ -> null;
		};
	}

	/// The tickets an [Attention] keeps, as a condition on [#TICKETS_WITH_SLA] against the `now` and `soon` every
	/// [Conditions] carries, so a desk count and its list filter can never disagree.
	private static String condition(Attention attention) {
		return switch (attention) {
			case PAST_DEADLINE -> """
					t.first_response_at IS NULL AND t.closed_at IS NULL \
					AND sla.response_deadline < CAST(:now AS timestamptz)""";
			case DUE_WITHIN_HOUR -> """
					t.first_response_at IS NULL AND t.closed_at IS NULL \
					AND sla.response_deadline >= CAST(:now AS timestamptz) \
					AND sla.response_deadline <= CAST(:soon AS timestamptz)""";
			case UNANSWERED_CUSTOMER_COMMENT -> "unanswered.commented_at IS NOT NULL";
			case UNASSIGNED -> "t.assignee_id IS NULL AND t.status NOT IN ('RESOLVED', 'CLOSED')";
		};
	}

	/// A caller's instant bound as a `timestamptz` parameter. `Timestamp` reaches the driver through the Julian
	/// calendar before 1582-10-15 and would arrive days off there; PostgreSQL, like [Instant], is proleptic Gregorian.
	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	/// The text as a literal PostgreSQL pattern anchored at the start of a word, so "sap" finds "SAP export" but not the
	/// "asap" of another ticket. A text that starts with no word character, such as "(beta)", has no word start to
	/// anchor to and matches anywhere.
	private static String fromWordStart(String text) {
		var literal = text.replaceAll("[^\\p{L}\\p{N}_\\s]", "\\\\$0");
		var first = text.codePointAt(0);
		return (Character.isLetterOrDigit(first) || first == '_' ? "\\m" : "") + literal;
	}

	private static List<String> names(Set<? extends Enum<?>> values) {
		return values.stream().map(Enum::name).toList();
	}

	private static TicketSummary summary(ResultSet rs) throws SQLException {
		return new TicketSummary(new TicketReference(rs.getLong("number")).toString(), rs.getString("title"),
				ref(rs, "organization"), ref(rs, "author"), category(rs),
				TicketPriority.valueOf(rs.getString("priority")), TicketStatus.valueOf(rs.getString("status")),
				optionalRef(rs, "team"), optionalRef(rs, "assignee"), instant(rs, "created_at"),
				instant(rs, "updated_at"), optionalInstant(rs, "first_response_at"), optionalInstant(rs, "closed_at"),
				new ResponseSla(instant(rs, "response_deadline"),
						Duration.of(rs.getLong("waiting_micros"), ChronoUnit.MICROS), rs.getBoolean("sla_breached")),
				optionalInstant(rs, "commented_at"));
	}

	private static @Nullable TicketCategory category(ResultSet rs) throws SQLException {
		var category = rs.getString("category");
		return category == null ? null : TicketCategory.valueOf(category);
	}

	private static NamedRef ref(ResultSet rs, String prefix) throws SQLException {
		return new NamedRef(rs.getObject(prefix + "_id", UUID.class), rs.getString(prefix + "_name"));
	}

	private static @Nullable NamedRef optionalRef(ResultSet rs, String prefix) throws SQLException {
		var id = rs.getObject(prefix + "_id", UUID.class);
		return id == null ? null : new NamedRef(id, rs.getString(prefix + "_name"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getTimestamp(column).toInstant();
	}

	private static @Nullable Instant optionalInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private record Row(UUID id, TicketSummary summary, long version, String description, @Nullable String resolution) {
	}

	/// Starts from what the caller may see and always carries `now`, the instant waiting times and SLA breaches are
	/// measured against, and `soon`, the end of the hour after it in which a response deadline is due soon.
	private static final class Conditions {

		private final List<String> clauses = new ArrayList<>();

		private final Map<String, Object> params = new HashMap<>();

		Conditions(Caller caller, Instant now) {
			params.put("now", Timestamp.from(now));
			params.put("soon", Timestamp.from(now.plus(DUE_SOON)));
			var organizationId = customerOrganization(caller);
			if (organizationId != null) {
				add("t.organization_id = :callerOrganizationId", "callerOrganizationId", organizationId);
			}
		}

		void add(String clause, String name, Object value) {
			clauses.add(clause);
			params.put(name, value);
		}

		void add(String clause) {
			clauses.add(clause);
		}

		String where() {
			return clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses) + "\n";
		}

	}

}
