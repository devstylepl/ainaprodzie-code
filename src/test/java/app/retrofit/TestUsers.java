package app.retrofit;

import java.sql.Types;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import app.retrofit.auth.Role;

/// Creates `app_user` rows for server suites, hashed with the application's own encoder.
public final class TestUsers {

	private static final String PASSWORD = "correct horse battery staple";

	private final JdbcClient jdbc;

	private final PasswordEncoder passwordEncoder;

	public TestUsers(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
	}

	/// An enabled user with a unique mixed-case email, language `en` and a known password. A customer (`USER`) gets
	/// an organization of their own and support staff (`SUPPORT`) a team of their own, as the schema requires.
	public TestUser create(Role role) {
		return create(role, "Test User");
	}

	/// The same, with the display name that tool results and the chat's directory of people read.
	public TestUser create(Role role, String displayName) {
		var id = UUID.randomUUID();
		var suffix = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt());
		var email = "Test.User-" + suffix + "@Retrofit.test";
		var affiliationId = UUID.randomUUID();
		if (role == Role.USER) {
			jdbc.sql("""
					INSERT INTO organization (id, name, first_response_sla_minutes, created_at, updated_at)
					VALUES (:id, :name, 240, now(), now())
					""").param("id", affiliationId).param("name", "Test Organization " + suffix).update();
		}
		else {
			jdbc.sql("""
					INSERT INTO team (id, name, description, created_at, updated_at)
					VALUES (:id, :name, 'Test team', now(), now())
					""").param("id", affiliationId).param("name", "Test Team " + suffix).update();
		}
		jdbc.sql("""
				INSERT INTO app_user (id, email, display_name, password_hash, roles, organization_id, team_id, language,
				                      created_at, updated_at)
				VALUES (:id, :email, :displayName, :passwordHash, ARRAY[:role]::text[], :organizationId, :teamId, 'en',
				        now(), now())
				""")
				.param("id", id)
				.param("email", email)
				.param("displayName", displayName)
				.param("passwordHash", passwordEncoder.encode(PASSWORD))
				.param("role", role.name())
				.param("organizationId", role == Role.USER ? affiliationId : null, Types.OTHER)
				.param("teamId", role == Role.SUPPORT ? affiliationId : null, Types.OTHER)
				.update();
		return new TestUser(id, email, PASSWORD);
	}

	public void disable(UUID id) {
		jdbc.sql("UPDATE app_user SET enabled = false WHERE id = :id").param("id", id).update();
	}

	public void enable(UUID id) {
		jdbc.sql("UPDATE app_user SET enabled = true WHERE id = :id").param("id", id).update();
	}

	public record TestUser(UUID id, String email, String password) {
	}

}
