package app.retrofit.organization;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrganizationRepository {

	private static final String SELECT_ORGANIZATION = """
			SELECT id, name, first_response_sla_minutes
			FROM organization
			""";

	private final JdbcClient jdbc;

	JdbcOrganizationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Organization> findAll() {
		return jdbc.sql(SELECT_ORGANIZATION + "ORDER BY lower(name)")
				.query(JdbcOrganizationRepository::mapRow)
				.list();
	}

	Optional<Organization> findById(UUID id) {
		return jdbc.sql(SELECT_ORGANIZATION + "WHERE id = :id")
				.param("id", id)
				.query(JdbcOrganizationRepository::mapRow)
				.optional();
	}

	List<String> findAllDisplayNames() {
		return jdbc.sql("SELECT DISTINCT display_name FROM app_user ORDER BY display_name")
				.query(String.class)
				.list();
	}

	List<String> findDisplayNamesOfOrganizationAndSupport(UUID organizationId) {
		return jdbc.sql("""
				SELECT DISTINCT display_name
				FROM app_user
				WHERE organization_id = :organizationId OR 'SUPPORT' = ANY (roles)
				ORDER BY display_name
				""")
				.param("organizationId", organizationId)
				.query(String.class)
				.list();
	}

	private static Organization mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new Organization(rs.getObject("id", UUID.class), rs.getString("name"),
				rs.getInt("first_response_sla_minutes"));
	}

}
