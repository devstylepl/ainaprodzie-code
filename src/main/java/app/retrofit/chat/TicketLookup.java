package app.retrofit.chat;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import app.retrofit.ticket.AuditedField;
import app.retrofit.ticket.LinkRelation;
import app.retrofit.ticket.TicketAuditEntry;
import app.retrofit.ticket.TicketComment;
import app.retrofit.ticket.TicketDetails;
import app.retrofit.ticket.TicketLink;
import app.retrofit.ticket.TicketStatus;

/// What `getTicket` returns to the model: one ticket, or that the user can see no ticket with that reference. Only
/// the latest comments and changes are sent, and customer-written text is cut short, so one ticket cannot fill the
/// model's context.
sealed interface TicketLookup {

	int DESCRIPTION_MAX = 2000;

	int COMMENT_MAX = 1000;

	int LATEST_COMMENTS = 10;

	int RECENT_CHANGES = 10;

	String TRUNCATED = " [truncated]";

	static TicketLookup of(TicketDetails ticket) {
		var comments = ticket.comments();
		var changes = ticket.history().stream().filter(entry -> entry.field() != AuditedField.CREATED).toList();
		return new Ticket(TicketLine.of(ticket.summary()), truncate(ticket.description(), DESCRIPTION_MAX),
				ticket.resolution(), latest(comments, LATEST_COMMENTS).stream().map(Comment::of).toList(),
				comments.size(), ticket.links().stream().map(Link::of).toList(),
				latest(changes, RECENT_CHANGES).stream().map(Change::of).toList());
	}

	/// The ticket the user asked for. `latestComments` and `recentChanges` are the latest ones, oldest first;
	/// `commentCount` counts every comment the user may see. The `CREATED` entry is left out of the changes: the
	/// summary already gives the author and the creation time.
	record Ticket(TicketLine summary, String description, @Nullable String resolution, List<Comment> latestComments,
			int commentCount, List<Link> links, List<Change> recentChanges) implements TicketLookup {
	}

	/// The same for a reference that does not exist and for one the user may not see, as the REST API's 404 is.
	record NotFound(String message) implements TicketLookup {
	}

	/// A comment by name. A customer's ticket never holds an internal note: `find` leaves them out.
	record Comment(String author, boolean fromSupport, boolean internal, Instant createdAt, String body) {

		static Comment of(TicketComment comment) {
			return new Comment(comment.author().name(), comment.fromSupport(), comment.internal(),
					comment.createdAt(), truncate(comment.body(), COMMENT_MAX));
		}

	}

	record Link(LinkRelation relation, String reference, String title, TicketStatus status) {

		static Link of(TicketLink link) {
			return new Link(link.relation(), link.reference(), link.title(), link.status());
		}

	}

	record Change(String actor, Instant changedAt, AuditedField field, @Nullable String oldValue,
			@Nullable String newValue) {

		static Change of(TicketAuditEntry entry) {
			return new Change(entry.actor().name(), entry.changedAt(), entry.field(), truncateValue(entry.oldValue()),
					truncateValue(entry.newValue()));
		}

		/// A changed resolution is free text of up to 2,000 characters on both sides of the change.
		private static @Nullable String truncateValue(@Nullable String value) {
			return value == null ? null : truncate(value, COMMENT_MAX);
		}

	}

	private static <T> List<T> latest(List<T> chronological, int count) {
		return chronological.subList(Math.max(0, chronological.size() - count), chronological.size());
	}

	/// Text of at most `max` characters, counting code points, so a cut never splits a character.
	private static String truncate(String text, int max) {
		if (text.codePointCount(0, text.length()) <= max) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, max)) + TRUNCATED;
	}

}
