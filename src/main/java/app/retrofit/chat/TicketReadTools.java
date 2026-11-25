package app.retrofit.chat;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import app.retrofit.organization.Organization;
import app.retrofit.organization.OrganizationAccess;
import app.retrofit.organization.Team;
import app.retrofit.platform.FieldError;
import app.retrofit.platform.ProblemException;
import app.retrofit.ticket.TicketCategory;
import app.retrofit.ticket.TicketFilter;
import app.retrofit.ticket.TicketPageRequest;
import app.retrofit.ticket.TicketPriority;
import app.retrofit.ticket.TicketQueries;
import app.retrofit.ticket.TicketSort;
import app.retrofit.ticket.TicketStatus;

/// The chat's read tools. They read tickets through [TicketQueries] and names through [OrganizationAccess], so
/// visibility is the REST API's (ADR 0001), and always as the signed-in user the [ChatTurn] in the tool context
/// carries: the model never supplies it, so no prompt can widen what a tool reads (chat ADR 0002).
///
/// The descriptions are prompts for the model; change them only together with the evaluations. A tool that throws
/// is answered by `ChatConfiguration`'s exception processor, so the model reads a neutral sentence.
@Component
class TicketReadTools {

	static final int DEFAULT_LIMIT = 10;

	static final int MAX_LIMIT = 20;

	private final TicketQueries tickets;

	private final OrganizationAccess organizations;

	TicketReadTools(TicketQueries tickets, OrganizationAccess organizations) {
		this.tickets = tickets;
		this.organizations = organizations;
	}

	@Tool(name = "searchTickets", description = """
			Find tickets the signed-in user may see and return one page of them. Use it for any question about \
			several tickets: tickets with a status, tickets that breached the SLA, tickets assigned to me or raised \
			by me, tickets of an organization or a team. All criteria given apply together; leave a criterion out to \
			match every ticket. The result gives the total number of matching tickets, so answer "how many" from \
			`total`, not from the length of the page.""")
	TicketSearchResult searchTickets(
			@ToolParam(required = false, description = "A reference such as RET-1042, or words that must appear in "
					+ "the title or description. Not for names of statuses, organizations or teams.")
			@Nullable String text,
			@ToolParam(required = false, description = "NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER (support asked the "
					+ "customer and awaits the answer), RESOLVED (a resolution was given, not yet confirmed), CLOSED")
			@Nullable List<TicketStatus> statuses,
			@ToolParam(required = false, description = "BUG, QUESTION, FEATURE_REQUEST")
			@Nullable List<TicketCategory> categories,
			@ToolParam(required = false, description = "LOW, NORMAL, HIGH, CRITICAL")
			@Nullable List<TicketPriority> priorities,
			@ToolParam(required = false, description = "true: the waiting time ends, or so far runs, past the "
					+ "response deadline, so it includes breaches already answered or closed; false: within it. For "
					+ "the breaches still awaiting a first response, use deadlineBefore instead.")
			@Nullable Boolean slaBreached,
			@ToolParam(required = false, description = "Only tickets assigned to the signed-in support staff member")
			@Nullable Boolean assignedToMe,
			@ToolParam(required = false, description = "Only tickets the signed-in customer raised. A customer's "
					+ "\"my tickets\" means these, not every ticket of their organization.")
			@Nullable Boolean raisedByMe,
			@ToolParam(required = false, description = "An organization's name, e.g. Acme Corp. A customer sees "
					+ "only their own.")
			@Nullable String organization,
			@ToolParam(required = false, description = "A support team's name, e.g. Billing. Support staff only.")
			@Nullable String team,
			@ToolParam(required = false, description = "Created at or after this ISO-8601 instant, e.g. "
					+ "2026-10-01T00:00:00Z")
			@Nullable String createdFrom,
			@ToolParam(required = false, description = "Created before this ISO-8601 instant")
			@Nullable String createdTo,
			@ToolParam(required = false, description = "Still awaiting a first response, not closed, with the "
					+ "response deadline before this ISO-8601 instant. The current time gives the SLA breaches still "
					+ "unanswered; a later instant adds the tickets that will breach the SLA by then unless answered.")
			@Nullable String deadlineBefore,
			@ToolParam(required = false, description = "RECENTLY_UPDATED (default) or LONGEST_WAITING")
			@Nullable TicketSort sort,
			@ToolParam(required = false, description = "How many tickets to return, 1 to " + MAX_LIMIT + ", default "
					+ DEFAULT_LIMIT)
			@Nullable Integer limit,
			ToolContext toolContext) {
		var turn = ChatTurn.of(toolContext);
		var caller = turn.caller();
		var callerId = caller.id();
		try {
			var organizationId = organization == null || organization.isBlank() ? null
					: resolve("organization", organization, organizations.visibleTo(caller), Organization::name,
							Organization::id);
			var teamId = team == null || team.isBlank() ? null
					: resolve("team", team, organizations.teamsVisibleTo(caller), Team::name, Team::id);
			var filter = new TicketFilter(text, organizationId, setOf(statuses), setOf(categories), setOf(priorities),
					teamId, Boolean.TRUE.equals(assignedToMe) ? callerId : null, slaBreached,
					Boolean.TRUE.equals(raisedByMe) ? callerId : null, instant("createdFrom", createdFrom),
					instant("createdTo", createdTo), instant("deadlineBefore", deadlineBefore), null);
			var order = sort == null ? TicketSort.RECENTLY_UPDATED : sort;
			var page = tickets.list(caller, filter, new TicketPageRequest(order, 0, clamp(limit)));
			if (!page.items().isEmpty()) {
				turn.tickets(page.items(), TicketListQuery.of(filter, order));
			}
			var shown = page.items().size();
			var lines = page.items().stream().map(TicketLine::of).toList();
			if (shown == page.total()) {
				return new TicketSearchResult.Tickets(page.total(), shown, lines);
			}
			return new TicketSearchResult.FirstTickets(page.total(), shown, lines, aboutTheRest(page.total(), shown));
		}
		catch (UnresolvedCriterion ex) {
			return new TicketSearchResult.Unresolved(ex.getMessage());
		}
		catch (ProblemException ex) {
			var errors = fieldErrors(ex);
			if (errors.isEmpty()) {
				throw ex;
			}
			return new TicketSearchResult.Unresolved(errors.stream()
					.map(error -> error.field() + ": " + error.message())
					.collect(Collectors.joining(" ")));
		}
	}

	@Tool(name = "getTicket", description = """
			Read one ticket by its reference, such as RET-1042: its description, resolution, public comments (and \
			internal notes, for support staff), related tickets and recent changes. Use it when the user asks about \
			a specific ticket or needs details a search result does not have. A ticket the user may not see is \
			reported as not found, exactly like one that does not exist.""")
	TicketLookup getTicket(@ToolParam(description = "The ticket's reference, such as RET-1042") String reference,
			ToolContext toolContext) {
		var turn = ChatTurn.of(toolContext);
		var found = tickets.find(turn.caller(), reference);
		found.ifPresent(ticket -> turn.tickets(List.of(ticket.summary()), null));
		return found.map(TicketLookup::of)
				.orElseGet(() -> new TicketLookup.NotFound("There is no ticket " + reference.strip()
						+ " you can see."));
	}

	/// A single exact match ignoring case first, then a single match containing the name. Several matches are
	/// listed for the model to choose from; the server never picks one. The names listed are ones the user may see.
	private static <T> UUID resolve(String kind, String name, List<T> visible, Function<T, String> nameOf,
			Function<T, UUID> idOf) {
		var wanted = name.strip().toLowerCase(Locale.ROOT);
		var exact = visible.stream().filter(item -> nameOf.apply(item).toLowerCase(Locale.ROOT).equals(wanted))
				.toList();
		var matches = exact.isEmpty()
				? visible.stream().filter(item -> nameOf.apply(item).toLowerCase(Locale.ROOT).contains(wanted)).toList()
				: exact;
		if (matches.size() == 1) {
			return idOf.apply(matches.getFirst());
		}
		if (matches.size() > 1) {
			throw new UnresolvedCriterion("'" + name + "' matches several " + kind + "s you can see: "
					+ names(matches, nameOf) + ". Ask the user which one.");
		}
		if (visible.isEmpty()) {
			throw new UnresolvedCriterion("No " + kind + " named '" + name + "': you can see no " + kind + "s.");
		}
		throw new UnresolvedCriterion("No " + kind + " named '" + name + "' among those you can see: "
				+ names(visible, nameOf) + ".");
	}

	private static <T> String names(List<T> items, Function<T, String> nameOf) {
		return items.stream().map(nameOf).collect(Collectors.joining(", "));
	}

	private static @Nullable Instant instant(String parameter, @Nullable String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(value.strip());
		}
		catch (DateTimeParseException ex) {
			throw new UnresolvedCriterion(parameter + ": '" + value + "' is not an ISO-8601 instant such as "
					+ "2026-10-01T00:00:00Z.");
		}
	}

	/// The field errors of a validation problem, such as [TicketFilter]'s refusal of its instants.
	private static List<FieldError> fieldErrors(ProblemException problem) {
		var properties = problem.getBody().getProperties();
		if (properties != null && properties.get("errors") instanceof List<?> errors) {
			return errors.stream().filter(FieldError.class::isInstance).map(FieldError.class::cast).toList();
		}
		return List.of();
	}

	private static String aboutTheRest(long total, int shown) {
		if (total <= MAX_LIMIT) {
			return "This result holds " + shown + " of the " + total + " matching tickets. Before you answer, call "
					+ "searchTickets again with the same criteria and limit " + total + ", and name only tickets a "
					+ "search returned.";
		}
		var held = "This result holds the first " + shown + " of the " + total + " matching tickets, and a search "
				+ "returns at most " + MAX_LIMIT + ". ";
		var firstOnes = "that " + total + " match and that you name the first ones, and name only tickets a search "
				+ "returned.";
		if (shown < MAX_LIMIT) {
			return held + "Before you answer, call searchTickets again with the same criteria and limit " + MAX_LIMIT
					+ ", then say " + firstOnes;
		}
		return held + "Say " + firstOnes;
	}

	/// A limit out of range is clamped, never an error the model would have to recover from.
	private static int clamp(@Nullable Integer limit) {
		return limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
	}

	private static <T> Set<T> setOf(@Nullable List<T> values) {
		return values == null ? Set.of() : Set.copyOf(values);
	}

	/// A criterion that cannot be resolved; its message is what the model reads.
	private static final class UnresolvedCriterion extends RuntimeException {

		UnresolvedCriterion(String message) {
			super(message);
		}

	}

}
