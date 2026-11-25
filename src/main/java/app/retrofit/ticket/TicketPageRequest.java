package app.retrofit.ticket;

import java.util.ArrayList;
import java.util.List;

import app.retrofit.platform.FieldError;
import app.retrofit.platform.Validations;

/// A page and size out of range is a validation problem (422) for every caller, the API's included.
///
/// Public because the chat reads tickets through [TicketQueries] from outside this package.
public record TicketPageRequest(TicketSort sort, int page, int size) {

	static final int MAX_SIZE = 100;

	public TicketPageRequest {
		var errors = new ArrayList<FieldError>();
		validate(errors, page, size);
		Validations.throwIfInvalid(errors);
	}

	/// The constructor's checks without throwing, so a caller can report them with the filter's in one problem.
	static void validate(List<FieldError> errors, int page, int size) {
		if (page < 0) {
			errors.add(new FieldError("page", "page.invalid", "Must be 0 or more."));
		}
		if (size < 1 || size > MAX_SIZE) {
			errors.add(new FieldError("size", "size.invalid", "Must be between 1 and " + MAX_SIZE + "."));
		}
	}

}
