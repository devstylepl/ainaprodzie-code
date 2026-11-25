package app.retrofit.ticket;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/// A ticket against its organization's SLA: the waiting time runs from creation to the first response, for a ticket
/// closed without one to its closing, otherwise to now, and the ticket is breached when that end lies past the
/// response deadline.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public record ResponseSla(@Schema(requiredMode = REQUIRED) Instant responseDeadline, @JsonIgnore Duration waitingTime,
		@Schema(requiredMode = REQUIRED) boolean breached) {

	@JsonProperty
	@Schema(requiredMode = REQUIRED, description = "Waiting time in whole seconds")
	long waitingSeconds() {
		return waitingTime.toSeconds();
	}

}
