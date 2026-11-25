package app.retrofit.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import app.retrofit.RandomPortContext;
import app.retrofit.TestOrganizations;
import app.retrofit.TestUsers;
import app.retrofit.auth.AuthProblems;
import app.retrofit.auth.Role;
import app.retrofit.platform.ProblemException;

/// [CallerLookup#current(UUID)]: who the signed-in user is, read fresh from `app_user`.
class CallerLookupTests extends RandomPortContext {

	private static final String PROBLEMS = "https://retrofit.app/problems/";

	@Autowired
	PasswordEncoder passwordEncoder;

	TestUsers users;

	TestOrganizations organizations;

	@BeforeEach
	void setUp() {
		users = new TestUsers(jdbc, passwordEncoder);
		organizations = new TestOrganizations(jdbc);
	}

	@Test
	void customerIsResolvedWithTheirOrganization() {
		var organization = organizations.createOrganization("Caller-", 60);
		var customer = users.create(Role.USER);
		organizations.moveCustomer(customer.id(), organization);

		assertThat(callers.current(customer.id())).isEqualTo(new Caller.Customer(customer.id(), organization));
	}

	@Test
	void supportUserIsResolvedAsSupport() {
		var support = users.create(Role.SUPPORT);

		assertThat(callers.current(support.id())).isEqualTo(new Caller.Support(support.id()));
	}

	@Test
	void disabledUserIsRefused() {
		var disabled = users.create(Role.USER);
		users.disable(disabled.id());

		assertRefusedAsUnauthenticated(() -> callers.current(disabled.id()));
	}

	@Test
	void unknownUserIsRefused() {
		assertRefusedAsUnauthenticated(() -> callers.current(UUID.randomUUID()));
	}

	/// The same 401 that `EnabledUserFilter` answers for a session whose user is gone.
	private static void assertRefusedAsUnauthenticated(ThrowingCallable lookup) {
		assertThatExceptionOfType(ProblemException.class).isThrownBy(lookup).satisfies(refusal -> {
			assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
			assertThat(refusal.getBody().getType()).hasToString(PROBLEMS + "auth/unauthenticated");
			assertThat(refusal.getBody()).isEqualTo(AuthProblems.unauthenticated().getBody());
		});
	}

}
