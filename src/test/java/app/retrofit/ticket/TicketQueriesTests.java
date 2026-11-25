package app.retrofit.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import app.retrofit.RandomPortContext;
import app.retrofit.platform.ProblemException;

@Transactional
class TicketQueriesTests extends RandomPortContext {

	@Autowired
	TicketQueries queries;

	TicketFixtures fixtures;

	@BeforeEach
	void setUp() {
		fixtures = new TicketFixtures(jdbc);
	}

	@Test
	void customerListsOnlyTheirOrganizationsTickets() {
		var organization = fixtures.organization();
		var customer = fixtures.customer(organization);
		var colleague = fixtures.customer(organization);
		var outsider = fixtures.customer(fixtures.organization());
		var own = fixtures.ticket(customer).insert();
		var colleagues = fixtures.ticket(colleague).insert();
		fixtures.ticket(outsider).insert();

		var page = queries.list(customer, new FilterBuilder().build(), firstPage(50));

		assertThat(page.items()).extracting(TicketSummary::reference)
				.containsExactlyInAnyOrder(own.reference(), colleagues.reference());
		assertThat(page.total()).isEqualTo(2);
	}

	@Test
	void supportListsEveryOrganizationsTicketsAndCanNarrowToOne() {
		var support = fixtures.support(fixtures.team());
		var acme = fixtures.organization();
		var globex = fixtures.organization();
		// A title no other ticket has, so the list holds both however many tickets the shared database holds.
		var title = "Shared export " + UUID.randomUUID();
		var acmeTicket = fixtures.ticket(fixtures.customer(acme)).title(title).insert();
		var globexTicket = fixtures.ticket(fixtures.customer(globex)).title(title).insert();

		var everything = queries.list(support, new FilterBuilder().withText(title).build(), firstPage(100));
		var onlyAcme = queries.list(support, new FilterBuilder().withOrganization(acme).build(), firstPage(100));

		assertThat(everything.items()).extracting(TicketSummary::reference)
				.containsExactlyInAnyOrder(acmeTicket.reference(), globexTicket.reference());
		assertThat(onlyAcme.items()).extracting(TicketSummary::reference).containsExactly(acmeTicket.reference());
	}

	@Test
	void customerCannotOpenAnotherOrganizationsTicket() {
		var customer = fixtures.customer(fixtures.organization());
		var foreign = fixtures.ticket(fixtures.customer(fixtures.organization())).insert();

		assertThat(queries.find(customer, foreign.reference())).isEmpty();
	}

	@Test
	void referenceIsMatchedCaseInsensitivelyAndAnythingElseFindsNothing() {
		var customer = fixtures.customer(fixtures.organization());
		var ticket = fixtures.ticket(customer).insert();

		assertThat(queries.find(customer, " " + ticket.reference().toLowerCase() + " ")).isPresent();
		assertThat(queries.find(customer, ticket.reference().substring("RET-".length()))).isEmpty();
		assertThat(queries.find(customer, "RET-")).isEmpty();
		assertThat(queries.find(customer, "RET-99999999999999999999")).isEmpty();
	}

	@Test
	void customerReadsThePublicConversationWithoutInternalNotes() {
		var team = fixtures.team();
		var support = fixtures.support(team);
		var customer = fixtures.customer(fixtures.organization());
		var ticket = fixtures.ticket(customer).status("IN_PROGRESS").team(team).createdAgo(Duration.ofHours(5))
				.answeredAfter(Duration.ofHours(1)).insert();
		var created = ticket.createdAt();
		fixtures.comment(ticket.id(), support, false, "Could you send a screenshot?", created.plus(Duration.ofHours(1)));
		fixtures.comment(ticket.id(), support, true, "Customer is on the churn list.", created.plus(Duration.ofHours(2)));
		fixtures.comment(ticket.id(), customer, false, "Attached.", created.plus(Duration.ofHours(3)));

		var asCustomer = queries.find(customer, ticket.reference()).orElseThrow();
		var asSupport = queries.find(support, ticket.reference()).orElseThrow();

		assertThat(asCustomer.comments()).extracting(TicketComment::body)
				.containsExactly("Could you send a screenshot?", "Attached.");
		assertThat(asSupport.comments()).extracting(TicketComment::body, TicketComment::internal).containsExactly(
				tuple("Could you send a screenshot?", false), tuple("Customer is on the churn list.", true),
				tuple("Attached.", false));
		assertThat(asSupport.comments()).extracting(TicketComment::fromSupport).containsExactly(true, true, false);
	}

	@Test
	void unansweredTicketKeepsWaitingAndIsBreachedOncePastItsDeadline() {
		var customer = fixtures.customer(fixtures.organization(Duration.ofHours(4)));
		var overdue = fixtures.ticket(customer).createdAgo(Duration.ofHours(5)).insert();
		var fresh = fixtures.ticket(customer).createdAgo(Duration.ofHours(1)).insert();

		var overdueSla = summary(customer, overdue).sla();
		var freshSla = summary(customer, fresh).sla();

		assertThat(overdueSla.responseDeadline()).isEqualTo(overdue.createdAt().plus(Duration.ofHours(4)));
		assertThat(overdueSla.waitingTime()).isBetween(Duration.ofHours(5), Duration.ofHours(5).plusMinutes(1));
		assertThat(overdueSla.breached()).isTrue();
		assertThat(freshSla.responseDeadline()).isEqualTo(fresh.createdAt().plus(Duration.ofHours(4)));
		assertThat(freshSla.waitingTime()).isBetween(Duration.ofHours(1), Duration.ofHours(1).plusMinutes(1));
		assertThat(freshSla.breached()).isFalse();
	}

	@Test
	void answeredTicketStopsWaitingAtItsFirstResponse() {
		var customer = fixtures.customer(fixtures.organization(Duration.ofHours(4)));
		var late = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(2))
				.answeredAfter(Duration.ofHours(6)).insert();
		var onTime = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(2))
				.answeredAfter(Duration.ofHours(4)).insert();

		var lateSla = summary(customer, late).sla();
		var onTimeSla = summary(customer, onTime).sla();

		assertThat(lateSla.waitingTime()).isEqualTo(Duration.ofHours(6));
		assertThat(lateSla.breached()).isTrue();
		assertThat(onTimeSla.waitingTime()).isEqualTo(Duration.ofHours(4));
		assertThat(onTimeSla.breached()).as("an answer exactly at the deadline is in time").isFalse();
	}

	@Test
	void ticketClosedWithoutAnyResponseStopsWaitingAtItsClosing() {
		var customer = fixtures.customer(fixtures.organization(Duration.ofHours(4)));
		var closedInTime = fixtures.ticket(customer).createdAgo(Duration.ofDays(2)).closedAfter(Duration.ofHours(1))
				.insert();
		var closedLate = fixtures.ticket(customer).createdAgo(Duration.ofDays(2)).closedAfter(Duration.ofHours(5))
				.insert();

		var inTimeSla = summary(customer, closedInTime).sla();
		var lateSla = summary(customer, closedLate).sla();

		assertThat(inTimeSla.waitingTime()).isEqualTo(Duration.ofHours(1));
		assertThat(inTimeSla.breached()).isFalse();
		assertThat(lateSla.waitingTime()).isEqualTo(Duration.ofHours(5));
		assertThat(lateSla.breached()).as("closing does not undo a deadline already missed").isTrue();
		assertThat(summary(customer, closedLate).closedAt()).isEqualTo(closedLate.createdAt().plus(Duration.ofHours(5)));
	}

	@Test
	void unansweredCustomerCommentIsTheLatestPublicCommentByACustomerOnATicketInProgressAfterItsFirstResponse() {
		var support = fixtures.support(fixtures.team());
		var customer = fixtures.customer(fixtures.organization());
		var answeredBack = conversation(support, customer, "IN_PROGRESS", true, "customer", "support");
		var awaitingSupport = conversation(support, customer, "IN_PROGRESS", true, "support", "customer");
		var onlyANoteSince = conversation(support, customer, "IN_PROGRESS", true, "support", "customer", "note");
		var setToWaitingForCustomer = conversation(support, customer, "WAITING_FOR_CUSTOMER", true, "support",
				"customer");
		var resolved = conversation(support, customer, "RESOLVED", true, "support", "customer");
		var beforeTheFirstResponse = conversation(support, customer, "NEW", false, "customer");

		assertThat(summary(support, awaitingSupport.ticket()).unansweredCustomerCommentAt())
				.isEqualTo(awaitingSupport.lastPublicAt());
		assertThat(summary(customer, awaitingSupport.ticket()).unansweredCustomerCommentAt())
				.as("the customer reads the same public conversation").isEqualTo(awaitingSupport.lastPublicAt());
		assertThat(summary(support, onlyANoteSince.ticket()).unansweredCustomerCommentAt())
				.as("an internal note answers nobody").isEqualTo(onlyANoteSince.lastPublicAt());
		assertThat(List.of(answeredBack, setToWaitingForCustomer, resolved, beforeTheFirstResponse))
				.extracting(conversation -> summary(support, conversation.ticket()).unansweredCustomerCommentAt())
				.containsOnlyNulls();
	}

	@Test
	void attentionFilterKeepsWhatEachDeskCountHoldsWithinWhatTheCallerMaySee() {
		var team = fixtures.team();
		var support = fixtures.support(team);
		var organization = fixtures.organization(Duration.ofHours(4));
		var customer = fixtures.customer(organization);
		var pastDeadline = fixtures.ticket(customer).createdAgo(Duration.ofHours(5)).team(team).assignee(support)
				.insert();
		var dueWithinHour = fixtures.ticket(customer).createdAgo(Duration.ofMinutes(210)).team(team)
				.assignee(support).insert();
		var unassigned = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(1))
				.answeredAfter(Duration.ofHours(1)).insert();
		var awaitingSupport = conversation(support, customer, "IN_PROGRESS", true, "support", "customer").ticket();
		var foreign = conversation(support, fixtures.customer(fixtures.organization()), "IN_PROGRESS", true,
				"support", "customer").ticket();

		assertThat(references(support, inOrganization(organization).withAttention(Attention.PAST_DEADLINE)))
				.containsExactly(pastDeadline.reference());
		assertThat(references(support, inOrganization(organization).withAttention(Attention.DUE_WITHIN_HOUR)))
				.containsExactly(dueWithinHour.reference());
		assertThat(references(support, inOrganization(organization).withAttention(Attention.UNASSIGNED)))
				.containsExactlyInAnyOrder(unassigned.reference(), awaitingSupport.reference());
		assertThat(references(support,
				inOrganization(organization).withAttention(Attention.UNANSWERED_CUSTOMER_COMMENT)))
				.containsExactly(awaitingSupport.reference());
		assertThat(references(customer, new FilterBuilder().withAttention(Attention.UNANSWERED_CUSTOMER_COMMENT)))
				.containsExactly(awaitingSupport.reference())
				.doesNotContain(foreign.reference());
	}

	@Test
	void eachAttentionFilterHoldsAsManyTicketsAsItsDeskCount() {
		var team = fixtures.team();
		var support = fixtures.support(team);
		var customer = fixtures.customer(fixtures.organization(Duration.ofHours(4)));
		fixtures.ticket(customer).createdAgo(Duration.ofHours(5)).insert();
		fixtures.ticket(customer).createdAgo(Duration.ofMinutes(210)).insert();
		conversation(support, customer, "IN_PROGRESS", true, "support", "customer");

		var counts = queries.counts(support);

		assertThat(total(support, Attention.PAST_DEADLINE)).isEqualTo(counts.pastDeadline());
		assertThat(total(support, Attention.DUE_WITHIN_HOUR)).isEqualTo(counts.dueWithinHour());
		assertThat(total(support, Attention.UNASSIGNED)).isEqualTo(counts.unassigned());
		assertThat(total(support, Attention.UNANSWERED_CUSTOMER_COMMENT))
				.isEqualTo(counts.unansweredCustomerComment());
	}

	@Test
	void customerSeesOnlyLinksToTicketsTheyMaySee() {
		var support = fixtures.support(fixtures.team());
		var customer = fixtures.customer(fixtures.organization());
		var ticket = fixtures.ticket(customer).insert();
		var related = fixtures.ticket(customer).title("Same problem last week").insert();
		var foreignOriginal = fixtures.ticket(fixtures.customer(fixtures.organization())).insert();
		fixtures.link(related.id(), ticket.id(), "RELATED", support);
		fixtures.link(ticket.id(), foreignOriginal.id(), "POTENTIAL_DUPLICATE", support);

		var asCustomer = queries.find(customer, ticket.reference()).orElseThrow();
		var asSupport = queries.find(support, ticket.reference()).orElseThrow();
		var originalAsSupport = queries.find(support, foreignOriginal.reference()).orElseThrow();

		assertThat(asCustomer.links()).extracting(TicketLink::relation, TicketLink::reference, TicketLink::title)
				.containsExactly(tuple(LinkRelation.RELATED, related.reference(), "Same problem last week"));
		assertThat(asSupport.links()).extracting(TicketLink::relation, TicketLink::reference).containsExactlyInAnyOrder(
				tuple(LinkRelation.RELATED, related.reference()),
				tuple(LinkRelation.POTENTIAL_DUPLICATE_OF, foreignOriginal.reference()));
		assertThat(originalAsSupport.links()).extracting(TicketLink::relation, TicketLink::reference)
				.containsExactly(tuple(LinkRelation.HAS_POTENTIAL_DUPLICATE, ticket.reference()));
	}

	@Test
	void customerHistoryLeavesOutLinksToTicketsTheyMayNotSee() {
		var support = fixtures.support(fixtures.team());
		var customer = fixtures.customer(fixtures.organization());
		var ticket = fixtures.ticket(customer).insert();
		var related = fixtures.ticket(customer).insert();
		var foreign = fixtures.ticket(fixtures.customer(fixtures.organization())).insert();
		var now = Instant.now();
		fixtures.audit(ticket.id(), support, now.minusSeconds(60), "LINK", null, "RELATED " + related.reference());
		fixtures.audit(ticket.id(), support, now, "LINK", null, "POTENTIAL_DUPLICATE " + foreign.reference());

		var asCustomer = queries.find(customer, ticket.reference()).orElseThrow().history();
		var asSupport = queries.find(support, ticket.reference()).orElseThrow().history();

		assertThat(asCustomer).extracting(TicketAuditEntry::newValue)
				.containsExactly("RELATED " + related.reference());
		assertThat(asSupport).extracting(TicketAuditEntry::newValue).containsExactly(
				"RELATED " + related.reference(), "POTENTIAL_DUPLICATE " + foreign.reference());
	}

	@Test
	void historyListsEveryChangeInTheOrderItHappened() {
		var team = fixtures.team();
		var support = fixtures.support(team);
		var customer = fixtures.customer(fixtures.organization());
		var ticket = fixtures.ticket(customer).status("RESOLVED").team(team).createdAgo(Duration.ofDays(1)).insert();
		var triagedAt = ticket.createdAt().plus(Duration.ofHours(1));
		fixtures.audit(ticket.id(), support, triagedAt.plusSeconds(60), "STATUS", "NEW", "IN_PROGRESS");
		fixtures.audit(ticket.id(), support, triagedAt, "TEAM", null, "Identity");
		fixtures.audit(ticket.id(), support, triagedAt.plus(Duration.ofHours(2)), "STATUS", "IN_PROGRESS", "RESOLVED");

		var history = queries.find(customer, ticket.reference()).orElseThrow().history();

		assertThat(history).extracting(TicketAuditEntry::field, TicketAuditEntry::oldValue, TicketAuditEntry::newValue)
				.containsExactly(tuple(AuditedField.TEAM, null, "Identity"),
						tuple(AuditedField.STATUS, "NEW", "IN_PROGRESS"),
						tuple(AuditedField.STATUS, "IN_PROGRESS", "RESOLVED"));
		assertThat(history).extracting(TicketAuditEntry::changedAt).first().isEqualTo(triagedAt);
		assertThat(history).extracting(entry -> entry.actor().id()).containsOnly(support);
	}

	@Test
	void eachFilterNarrowsTheListAndFiltersCombine() {
		var identity = fixtures.team();
		var ivy = fixtures.support(identity);
		var organization = fixtures.organization();
		var customer = fixtures.customer(organization);
		var untriaged = fixtures.ticket(customer).insert();
		var bug = fixtures.ticket(customer).status("IN_PROGRESS").category("BUG").priority("HIGH").team(identity)
				.assignee(ivy).answeredAfter(Duration.ofMinutes(10)).insert();
		var question = fixtures.ticket(customer).status("WAITING_FOR_CUSTOMER").category("QUESTION").team(identity)
				.answeredAfter(Duration.ofMinutes(10)).insert();

		assertThat(references(ivy,
				inOrganization(organization).withStatuses(Set.of(TicketStatus.NEW, TicketStatus.IN_PROGRESS))))
				.containsExactlyInAnyOrder(untriaged.reference(), bug.reference());
		assertThat(references(ivy, inOrganization(organization).withCategories(Set.of(TicketCategory.QUESTION))))
				.containsExactly(question.reference());
		assertThat(references(ivy, inOrganization(organization).withPriorities(Set.of(TicketPriority.HIGH))))
				.containsExactly(bug.reference());
		assertThat(references(ivy, inOrganization(organization).withTeam(identity)))
				.containsExactlyInAnyOrder(bug.reference(), question.reference());
		assertThat(references(ivy, inOrganization(organization).withAssignee(ivy))).containsExactly(bug.reference());
		assertThat(references(ivy,
				inOrganization(organization).withTeam(identity).withStatuses(Set.of(TicketStatus.NEW)))).isEmpty();
	}

	@Test
	void authorFilterKeepsTheTicketsTheCustomerRaisedAndNeverReachesAnotherOrganization() {
		var organization = fixtures.organization();
		var customer = fixtures.customer(organization);
		var colleague = fixtures.customer(organization);
		var outsider = fixtures.customer(fixtures.organization());
		var support = fixtures.support(fixtures.team());
		var raised = fixtures.ticket(customer).insert();
		fixtures.ticket(colleague).insert();
		var outsiders = fixtures.ticket(outsider).insert();

		assertThat(references(customer, new FilterBuilder().withAuthor(customer))).containsExactly(raised.reference());
		assertThat(references(customer, new FilterBuilder().withAuthor(outsider))).isEmpty();
		assertThat(references(support, new FilterBuilder().withAuthor(outsider)))
				.containsExactly(outsiders.reference());
	}

	@Test
	void creationRangeIncludesItsStartAndExcludesItsEndSoConsecutiveRangesNeverOverlap() {
		var customer = fixtures.customer(fixtures.organization());
		var threeHoursOld = fixtures.ticket(customer).createdAgo(Duration.ofHours(3)).insert();
		var twoHoursOld = fixtures.ticket(customer).createdAgo(Duration.ofHours(2)).insert();
		var oneHourOld = fixtures.ticket(customer).createdAgo(Duration.ofHours(1)).insert();
		fixtures.ticket(fixtures.customer(fixtures.organization())).createdAgo(Duration.ofHours(2)).insert();
		var from = twoHoursOld.createdAt();

		assertThat(references(customer, new FilterBuilder().withCreatedFrom(from)))
				.as("another organization's ticket in the range stays invisible")
				.containsExactlyInAnyOrder(twoHoursOld.reference(), oneHourOld.reference());
		assertThat(references(customer, new FilterBuilder().withCreatedTo(from)))
				.containsExactly(threeHoursOld.reference());
		assertThat(references(customer,
				new FilterBuilder().withCreatedFrom(threeHoursOld.createdAt()).withCreatedTo(oneHourOld.createdAt())))
				.containsExactlyInAnyOrder(threeHoursOld.reference(), twoHoursOld.reference());
		assertThat(references(customer, new FilterBuilder().withCreatedFrom(from).withCreatedTo(from))).isEmpty();
	}

	@Test
	void instantsBeforeTheGregorianReformAreComparedOnTheProlepticCalendar() {
		var customer = fixtures.customer(fixtures.organization());
		var ticket = fixtures.ticket(customer).insert();
		jdbc.sql("UPDATE ticket SET created_at = TIMESTAMPTZ '0999-12-30 00:00:00+00' WHERE id = :id")
				.param("id", ticket.id())
				.update();
		var year1000 = Instant.parse("1000-01-01T00:00:00Z");

		assertThat(references(customer, new FilterBuilder().withCreatedFrom(year1000))).isEmpty();
		assertThat(references(customer, new FilterBuilder().withCreatedTo(year1000)))
				.containsExactly(ticket.reference());
	}

	@Test
	void instantsInTheFirstAndLastSupportedYearsAreAccepted() {
		var organization = fixtures.organization();
		var customer = fixtures.customer(organization);
		var ticket = fixtures.ticket(customer).insert();
		var firstInstant = Instant.parse("0001-01-01T00:00:00Z");
		var lastInstant = Instant.parse("9999-12-31T23:59:59.999999Z");

		assertThat(references(customer, new FilterBuilder().withOrganization(organization).withCreatedFrom(firstInstant)
				.withCreatedTo(lastInstant).withDeadlineBefore(lastInstant))).containsExactly(ticket.reference());
	}

	@Test
	void deadlineFilterKeepsOpenTicketsStillAwaitingAFirstResponseWhoseDeadlineFallsBeforeTheInstant() {
		var customer = fixtures.customer(fixtures.organization(Duration.ofHours(4)));
		var alreadyBreached = fixtures.ticket(customer).createdAgo(Duration.ofHours(5)).insert();
		var deadlineInAnHour = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofHours(3))
				.insert();
		var deadlineInThreeHours = fixtures.ticket(customer).createdAgo(Duration.ofHours(1)).insert();
		fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofHours(3))
				.answeredAfter(Duration.ofMinutes(10)).insert();
		fixtures.ticket(customer).createdAgo(Duration.ofHours(3)).closedAfter(Duration.ofMinutes(10)).insert();
		fixtures.ticket(fixtures.customer(fixtures.organization(Duration.ofHours(4)))).createdAgo(Duration.ofHours(5))
				.insert();
		var inTwoHours = Instant.now().plus(Duration.ofHours(2));
		var anHourAway = deadlineInAnHour.createdAt().plus(Duration.ofHours(4));

		assertThat(references(customer, new FilterBuilder().withDeadlineBefore(inTwoHours)))
				.as("another organization's breached ticket stays invisible")
				.containsExactlyInAnyOrder(alreadyBreached.reference(), deadlineInAnHour.reference());
		assertThat(references(customer, new FilterBuilder().withDeadlineBefore(anHourAway)))
				.as("a deadline exactly at the instant does not fall before it")
				.containsExactly(alreadyBreached.reference());
		assertThat(references(customer, new FilterBuilder().withDeadlineBefore(inTwoHours.plus(Duration.ofHours(2)))))
				.containsExactlyInAnyOrder(alreadyBreached.reference(), deadlineInAnHour.reference(),
						deadlineInThreeHours.reference());
	}

	@Test
	void slaBreachFilterSeparatesBreachedFromTimelyTickets() {
		var organization = fixtures.organization(Duration.ofHours(4));
		var customer = fixtures.customer(organization);
		var unansweredOverdue = fixtures.ticket(customer).createdAgo(Duration.ofHours(5)).insert();
		var answeredLate = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(1))
				.answeredAfter(Duration.ofHours(5)).insert();
		var unansweredInTime = fixtures.ticket(customer).createdAgo(Duration.ofHours(1)).insert();
		var answeredInTime = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(1))
				.answeredAfter(Duration.ofHours(1)).insert();

		assertThat(references(customer, inOrganization(organization).withSlaBreached(true)))
				.containsExactlyInAnyOrder(unansweredOverdue.reference(), answeredLate.reference());
		assertThat(references(customer, inOrganization(organization).withSlaBreached(false)))
				.containsExactlyInAnyOrder(unansweredInTime.reference(), answeredInTime.reference());
	}

	@Test
	void textMatchesFromTheStartOfAWord() {
		var organization = fixtures.organization();
		var customer = fixtures.customer(organization);
		var sap = fixtures.ticket(customer).title("SAP export fails").insert();
		var asap = fixtures.ticket(customer).title("Export does not work").description("Please fix asap.").insert();
		var beta = fixtures.ticket(customer).title("Crash in the (beta) build").insert();
		var polish = fixtures.ticket(customer).title("Zażółć gęślą jaźń").insert();

		assertThat(references(customer, inOrganization(organization).withText("sap"))).containsExactly(sap.reference());
		assertThat(references(customer, inOrganization(organization).withText("expo")))
				.containsExactlyInAnyOrder(sap.reference(), asap.reference());
		assertThat(references(customer, inOrganization(organization).withText("port"))).isEmpty();
		assertThat(references(customer, inOrganization(organization).withText("(beta)")))
				.containsExactly(beta.reference());
		assertThat(references(customer, inOrganization(organization).withText("b.ild"))).isEmpty();
		assertThat(references(customer, inOrganization(organization).withText("GĘŚL"))).containsExactly(polish.reference());
		assertThat(references(customer, inOrganization(organization).withText("żółć"))).isEmpty();
	}

	@Test
	void textMatchesTheReferenceExactlyOrAppearsLiterallyInTitleOrDescription() {
		var organization = fixtures.organization();
		var customer = fixtures.customer(organization);
		var discount = fixtures.ticket(customer).title("Is a 100% discount possible?").insert();
		var price = fixtures.ticket(customer).title("Price list").description("We pay 1000 EUR per month.").insert();
		var other = fixtures.ticket(customer).title("Login problem").insert();

		assertThat(references(customer, inOrganization(organization).withText("100%"))).containsExactly(discount.reference());
		assertThat(references(customer, inOrganization(organization).withText("PER MONTH")))
				.containsExactly(price.reference());
		assertThat(references(customer, inOrganization(organization).withText(other.reference().toLowerCase())))
				.containsExactly(other.reference());
		assertThat(references(customer, inOrganization(organization).withText("  price  ")))
				.containsExactly(price.reference());
		assertThat(references(customer, inOrganization(organization).withText("   ")))
				.containsExactlyInAnyOrder(discount.reference(), price.reference(), other.reference());
	}

	@Test
	void longestWaitingFirstAcrossPages() {
		var organization = fixtures.organization();
		var customer = fixtures.customer(organization);
		var waitedTwoHours = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(3))
				.answeredAfter(Duration.ofHours(2)).updatedAgo(Duration.ofMinutes(1)).insert();
		var waitingFiveHours = fixtures.ticket(customer).createdAgo(Duration.ofHours(5)).insert();
		var waitingOneHour = fixtures.ticket(customer).createdAgo(Duration.ofHours(1)).insert();

		var first = queries.list(customer, inOrganization(organization).build(),
				new TicketPageRequest(TicketSort.LONGEST_WAITING, 0, 2));
		var second = queries.list(customer, inOrganization(organization).build(),
				new TicketPageRequest(TicketSort.LONGEST_WAITING, 1, 2));
		var recentlyUpdated = queries.list(customer, inOrganization(organization).build(), firstPage(10));

		assertThat(first.items()).extracting(TicketSummary::reference)
				.containsExactly(waitingFiveHours.reference(), waitedTwoHours.reference());
		assertThat(second.items()).extracting(TicketSummary::reference).containsExactly(waitingOneHour.reference());
		assertThat(first.total()).isEqualTo(3);
		assertThat(second.total()).isEqualTo(3);
		assertThat(recentlyUpdated.items()).extracting(TicketSummary::reference).containsExactly(
				waitedTwoHours.reference(), waitingOneHour.reference(), waitingFiveHours.reference());
	}

	@Test
	void responseDeadlineOrderPutsTicketsAwaitingAFirstResponseFirstEarliestDeadlineFirst() {
		var organization = fixtures.organization(Duration.ofHours(4));
		var customer = fixtures.customer(organization);
		var closed = fixtures.ticket(customer).createdAgo(Duration.ofDays(2)).closedAfter(Duration.ofHours(1))
				.updatedAgo(Duration.ofMinutes(1)).insert();
		var answeredLongAgo = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(3))
				.answeredAfter(Duration.ofHours(1)).updatedAgo(Duration.ofDays(2)).insert();
		var answeredRecently = fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(1))
				.answeredAfter(Duration.ofHours(1)).updatedAgo(Duration.ofMinutes(5)).insert();
		var deadlineInThreeHours = fixtures.ticket(customer).createdAgo(Duration.ofHours(1)).insert();
		var breached = fixtures.ticket(customer).createdAgo(Duration.ofHours(6)).insert();
		var deadlineInAnHour = fixtures.ticket(customer).createdAgo(Duration.ofHours(3)).insert();

		var first = queries.list(customer, inOrganization(organization).build(),
				new TicketPageRequest(TicketSort.RESPONSE_DEADLINE, 0, 4));
		var second = queries.list(customer, inOrganization(organization).build(),
				new TicketPageRequest(TicketSort.RESPONSE_DEADLINE, 1, 4));

		assertThat(first.items()).extracting(TicketSummary::reference).containsExactly(breached.reference(),
				deadlineInAnHour.reference(), deadlineInThreeHours.reference(), answeredRecently.reference());
		assertThat(second.items()).extracting(TicketSummary::reference)
				.containsExactly(answeredLongAgo.reference(), closed.reference());
	}

	@Test
	void countsHoldEachViewAndWhatNeedsAPersonNow() {
		var team = fixtures.team();
		var support = fixtures.support(team);
		var customer = fixtures.customer(fixtures.organization(Duration.ofHours(4)));
		// Counts run over the shared database, so only what these tickets add is compared.
		var before = queries.counts(support);
		fixtures.ticket(customer).team(team).assignee(support).createdAgo(Duration.ofHours(5)).insert();
		fixtures.ticket(customer).createdAgo(Duration.ofMinutes(210)).insert();
		fixtures.ticket(customer).status("IN_PROGRESS").createdAgo(Duration.ofDays(2))
				.answeredAfter(Duration.ofHours(6)).insert();
		fixtures.ticket(customer).status("RESOLVED").team(team).insert();
		fixtures.ticket(customer).createdAgo(Duration.ofDays(2)).closedAfter(Duration.ofHours(1)).insert();
		conversation(support, customer, "WAITING_FOR_CUSTOMER", true, "customer", "support");
		conversation(support, customer, "IN_PROGRESS", true, "support", "customer");

		var after = queries.counts(support);

		assertThat(after.all() - before.all()).isEqualTo(7);
		assertThat(after.assignedToMe() - before.assignedToMe()).isEqualTo(1);
		assertThat(after.myTeam() - before.myTeam()).isEqualTo(2);
		// Answered late is no longer past the deadline awaiting a response.
		assertThat(after.pastDeadline() - before.pastDeadline()).isEqualTo(1);
		assertThat(after.dueWithinHour() - before.dueWithinHour()).isEqualTo(1);
		// The resolved and the closed ones need no assignee any more; the two conversations have none.
		assertThat(after.unassigned() - before.unassigned()).isEqualTo(4);
		assertThat(after.unansweredCustomerComment() - before.unansweredCustomerComment()).isEqualTo(1);
	}

	@Test
	void countsAreRefusedToACustomer() {
		var customer = fixtures.customer(fixtures.organization());

		var problem = catchThrowableOfType(ProblemException.class, () -> queries.counts(customer));

		assertThat(problem).isNotNull();
		assertThat(problem.getBody().getType().toString()).endsWith("/ticket/forbidden");
	}

	@Test
	void customerCountsHoldTheirOrganizationsViewsAndNothingOfAnotherOrganization() {
		var organization = fixtures.organization();
		var customer = fixtures.customer(organization);
		var colleague = fixtures.customer(organization);
		var outsider = fixtures.customer(fixtures.organization());
		fixtures.ticket(customer).status("WAITING_FOR_CUSTOMER").insert();
		fixtures.ticket(customer).status("IN_PROGRESS").insert();
		fixtures.ticket(colleague).status("WAITING_FOR_CUSTOMER").insert();
		fixtures.ticket(colleague).status("RESOLVED").insert();
		fixtures.ticket(outsider).status("WAITING_FOR_CUSTOMER").insert();
		fixtures.ticket(outsider).insert();

		var counts = queries.customerCounts(customer);

		// Each view is the organization's: waiting for any of its customers' answer, raised by any of them or by me.
		assertThat(counts).isEqualTo(new CustomerTicketCounts(4, 2, 2));
	}

	@Test
	void customerCountsAreRefusedToSupportStaff() {
		var support = fixtures.support(fixtures.team());

		var problem = catchThrowableOfType(ProblemException.class, () -> queries.customerCounts(support));

		assertThat(problem).isNotNull();
		assertThat(problem.getBody().getType().toString()).endsWith("/ticket/forbidden");
	}

	@Test
	void stateOfAnotherOrganizationsTicketIsNotFoundForACustomer() {
		var customer = callers.current(fixtures.customer(fixtures.organization()));
		var foreign = fixtures.ticket(fixtures.customer(fixtures.organization())).insert();

		assertThat(queries.findState(customer, foreign.reference(), false)).isEmpty();
		assertThat(queries.findState(customer, foreign.reference(), true)).isEmpty();
	}

	@Test
	void stateOfAVisibleTicketCarriesTheFactsAWriteDecidesOn() {
		var team = fixtures.team();
		var assignee = fixtures.support(team);
		var author = fixtures.customer(fixtures.organization());
		var ticket = fixtures.ticket(author).title("Charged twice").description("Two invoices for March.")
				.status("RESOLVED").priority("HIGH").category("BUG").team(team).assignee(assignee).resolution("Refunded.")
				.insert();
		jdbc.sql("UPDATE ticket SET version = 3 WHERE id = :id").param("id", ticket.id()).update();
		var teamName = jdbc.sql("SELECT name FROM team WHERE id = :id").param("id", team).query(String.class)
				.single();
		var assigneeName = jdbc.sql("SELECT display_name FROM app_user WHERE id = :id").param("id", assignee)
				.query(String.class)
				.single();
		var expected = new Ticket(ticket.id(), Long.parseLong(ticket.reference().substring("RET-".length())), 3, author,
				"Charged twice", "Two invoices for March.", TicketStatus.RESOLVED, TicketPriority.HIGH,
				TicketCategory.BUG, new NamedRef(team, teamName), new NamedRef(assignee, assigneeName), "Refunded.",
				null, null);

		var asAuthor = queries.findState(callers.current(author), ticket.reference(), false);
		var lockedForSupport = queries.findState(callers.current(assignee), ticket.reference(), true);

		assertThat(asAuthor).contains(expected);
		assertThat(lockedForSupport).contains(expected);
	}

	/// A ticket answered an hour after it was raised a day ago, then the public comments and internal notes in
	/// `turns` order ("support", "customer" or "note"), an hour apart.
	private Conversation conversation(UUID support, UUID customer, String status, boolean answered,
			String... turns) {
		var ticket = fixtures.ticket(customer).status(status).createdAgo(Duration.ofDays(1));
		var inserted = (answered ? ticket.answeredAfter(Duration.ofHours(1)) : ticket).insert();
		Instant lastPublicAt = null;
		for (var turn = 0; turn < turns.length; turn++) {
			var at = inserted.createdAt().plus(Duration.ofHours(turn + 1));
			var note = turns[turn].equals("note");
			fixtures.comment(inserted.id(), turns[turn].equals("customer") ? customer : support, note, turns[turn], at);
			lastPublicAt = note ? lastPublicAt : at;
		}
		return new Conversation(inserted, lastPublicAt);
	}

	private record Conversation(TicketFixtures.Inserted ticket, @Nullable Instant lastPublicAt) {
	}

	private long total(UUID callerId, Attention attention) {
		return queries.list(callerId, new FilterBuilder().withAttention(attention).build(), firstPage(1)).total();
	}

	private List<String> references(UUID callerId, FilterBuilder filter) {
		return queries.list(callerId, filter.build(), firstPage(100)).items().stream()
				.map(TicketSummary::reference)
				.toList();
	}

	private static FilterBuilder inOrganization(UUID organizationId) {
		return new FilterBuilder().withOrganization(organizationId);
	}

	private static TicketPageRequest firstPage(int size) {
		return new TicketPageRequest(TicketSort.RECENTLY_UPDATED, 0, size);
	}

	private TicketSummary summary(UUID callerId, TicketFixtures.Inserted ticket) {
		return queries.find(callerId, ticket.reference()).orElseThrow().summary();
	}

}
