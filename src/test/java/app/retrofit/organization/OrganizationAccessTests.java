package app.retrofit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import app.retrofit.RandomPortContext;
import app.retrofit.TestOrganizations;
import app.retrofit.TestUsers;
import app.retrofit.auth.Role;

/// `GET /api/organizations` and `GET /api/organizations/{id}`: who sees which organization.
class OrganizationAccessTests extends RandomPortContext {

	private static final String PROBLEMS = "https://retrofit.app/problems/";

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY = new ParameterizedTypeReference<>() {
	};

	@Autowired
	PasswordEncoder passwordEncoder;

	TestUsers users;

	TestOrganizations organizations;

	UUID organizationA;

	UUID organizationB;

	@BeforeEach
	void setUp() {
		users = new TestUsers(jdbc, passwordEncoder);
		organizations = new TestOrganizations(jdbc);
		var token = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt());
		organizationA = organizations.createOrganization("alpha-" + token + "-", 60);
		organizationB = organizations.createOrganization("Bravo-" + token + "-", 240);
	}

	@Test
	void customerListsOnlyTheirOwnOrganization() {
		var session = customerIn(organizationA);

		var response = list(session);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsExactly(expected(organizationA, 60));
	}

	@Test
	void customerReadsTheirOwnOrganizationById() {
		var session = customerIn(organizationA);

		var response = get(session, organizationA.toString());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(expected(organizationA, 60));
	}

	@Test
	void customerReadingAnotherOrganizationGetsTheSameNotFoundAsAnUnknownId() {
		var session = customerIn(organizationA);

		var foreign = get(session, organizationB.toString());
		var unknown = get(session, UUID.randomUUID().toString());

		assertNotFound(foreign);
		assertNotFound(unknown);
		assertThat(foreign.getBody()).isEqualTo(unknown.getBody());
	}

	@Test
	void supportSeesEveryOrganizationSortedByNameIgnoringCase() {
		var support = users.create(Role.SUPPORT);
		var session = login(support.email(), support.password());

		var listed = list(session);

		assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
		var ids = listed.getBody().stream().map(organization -> organization.get("id")).toList();
		assertThat(ids).contains(organizationA.toString(), organizationB.toString());
		assertThat(ids.indexOf(organizationA.toString())).isLessThan(ids.indexOf(organizationB.toString()));
		var byId = get(session, organizationB.toString());
		assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(byId.getBody()).isEqualTo(expected(organizationB, 240));
		assertNotFound(get(session, UUID.randomUUID().toString()));
	}

	@Test
	void anonymousListIsUnauthenticatedAndNonUuidIdIsMalformed() {
		var anonymous = http().get().uri("/api/organizations").retrieve().toEntity(JSON_OBJECT);
		assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(anonymous.getBody()).containsEntry("type", PROBLEMS + "auth/unauthenticated");

		var session = customerIn(organizationA);
		var malformed = get(session, "not-a-uuid");
		assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(malformed.getBody()).containsEntry("type", PROBLEMS + "malformed-request");
	}

	private String customerIn(UUID organizationId) {
		var customer = users.create(Role.USER);
		organizations.moveCustomer(customer.id(), organizationId);
		return login(customer.email(), customer.password());
	}

	private ResponseEntity<List<Map<String, Object>>> list(String session) {
		return http().get().uri("/api/organizations")
				.header(HttpHeaders.COOKIE, "SESSION=" + session)
				.retrieve()
				.toEntity(JSON_ARRAY);
	}

	private ResponseEntity<Map<String, Object>> get(String session, String id) {
		return http().get().uri("/api/organizations/" + id)
				.header(HttpHeaders.COOKIE, "SESSION=" + session)
				.retrieve()
				.toEntity(JSON_OBJECT);
	}

	private Map<String, Object> expected(UUID id, int slaMinutes) {
		var name = jdbc.sql("SELECT name FROM organization WHERE id = :id").param("id", id).query(String.class).single();
		return Map.of("id", id.toString(), "name", name, "firstResponseSlaMinutes", slaMinutes);
	}

	private static void assertNotFound(ResponseEntity<Map<String, Object>> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).containsEntry("type", PROBLEMS + "organization/not-found");
	}

}
