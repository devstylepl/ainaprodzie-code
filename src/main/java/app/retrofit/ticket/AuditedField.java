package app.retrofit.ticket;

/// Spelled exactly as the `ticket_audit_entry_field_check` constraint spells it.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public enum AuditedField {

	CREATED, TITLE, DESCRIPTION, CATEGORY, PRIORITY, STATUS, TEAM, ASSIGNEE, RESOLUTION, LINK

}
