package app.retrofit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import app.retrofit.RandomPortContext;
import app.retrofit.TestOrganizations;
import app.retrofit.TestUsers;
import app.retrofit.auth.Role;

/// The display names the chat's directory of people reads (chat ADR 0009): the people the caller may see.
class OrganizationAccessDisplayNamesTests extends RandomPortContext {

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	OrganizationAccess access;

	@Autowired
	CallerLookup callers;

	TestUsers users;

	TestOrganizations organizations;

	String token;

	@BeforeEach
	void setUp() {
		users = new TestUsers(jdbc, passwordEncoder);
		organizations = new TestOrganizations(jdbc);
		token = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt());
	}

	@Test
	void aCustomerSeesTheUsersOfTheirOrganizationAndAllSupportStaff() {
		var organization = organizations.createOrganization("Acme-", 60);
		var customer = customerIn(organization, "Anna Acme " + token);
		customerIn(organization, "Adam Acme " + token);
		customerIn(organizations.createOrganization("Globex-", 60), "Greta Globex " + token);
		users.create(Role.SUPPORT, "Sam Support " + token);

		var names = access.displayNamesVisibleTo(callers.current(customer));

		assertThat(names).contains("Anna Acme " + token, "Adam Acme " + token, "Sam Support " + token)
				.doesNotContain("Greta Globex " + token);
	}

	@Test
	void supportStaffSeeEveryUser() {
		customerIn(organizations.createOrganization("Acme-", 60), "Anna Acme " + token);
		customerIn(organizations.createOrganization("Globex-", 60), "Greta Globex " + token);
		var support = users.create(Role.SUPPORT, "Sam Support " + token).id();
		users.create(Role.SUPPORT, "Ivy Support " + token);

		var names = access.displayNamesVisibleTo(callers.current(support));

		assertThat(names).contains("Anna Acme " + token, "Greta Globex " + token, "Sam Support " + token,
				"Ivy Support " + token);
	}

	/// Old tickets name the people whose accounts are disabled since.
	@Test
	void disabledAccountsAreIncluded() {
		var organization = organizations.createOrganization("Acme-", 60);
		var customer = customerIn(organization, "Anna Acme " + token);
		var gone = customerIn(organization, "Adam Acme " + token);
		var goneSupport = users.create(Role.SUPPORT, "Sam Support " + token).id();
		users.disable(gone);
		users.disable(goneSupport);

		assertThat(access.displayNamesVisibleTo(callers.current(customer))).contains("Adam Acme " + token,
				"Sam Support " + token);
		assertThat(access.displayNamesVisibleTo(callers.current(users.create(Role.SUPPORT).id())))
				.contains("Adam Acme " + token, "Sam Support " + token);
	}

	@Test
	void aNameTwoPeopleShareIsListedOnce() {
		var support = users.create(Role.SUPPORT).id();
		users.create(Role.USER, "Anna Acme " + token);
		users.create(Role.USER, "Anna Acme " + token);

		assertThat(access.displayNamesVisibleTo(callers.current(support))).containsOnlyOnce("Anna Acme " + token);
	}

	private UUID customerIn(UUID organization, String displayName) {
		var customer = users.create(Role.USER, displayName);
		organizations.moveCustomer(customer.id(), organization);
		return customer.id();
	}

}
