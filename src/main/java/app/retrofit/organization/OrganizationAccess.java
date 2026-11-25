package app.retrofit.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

/// Support sees every organization; a customer sees only their own, and never queries another one's id.
///
/// Public because the chat resolves organization and team names through [#visibleTo(Caller)] and
/// [#teamsVisibleTo(Caller)], and the names of the people through [#displayNamesVisibleTo(Caller)], from outside this
/// package, for the caller its question looked up.
@Service
public class OrganizationAccess {

	private final JdbcOrganizationRepository organizations;

	private final JdbcTeamRepository teams;

	OrganizationAccess(JdbcOrganizationRepository organizations, JdbcTeamRepository teams) {
		this.organizations = organizations;
		this.teams = teams;
	}

	public List<Organization> visibleTo(Caller caller) {
		return switch (caller) {
			case Caller.Support _ -> organizations.findAll();
			case Caller.Customer(var _, var organizationId) -> organizations.findById(organizationId).stream().toList();
		};
	}

	/// Every team, with the caller's own marked, for support staff; none for a customer, who sees no team.
	public List<Team> teamsVisibleTo(Caller caller) {
		return switch (caller) {
			case Caller.Support(var id) -> teams.findAllWithMembership(id);
			case Caller.Customer _ -> List.of();
		};
	}

	/// The display names of the people the caller may see, each once, in alphabetical order: a customer sees the
	/// customers of their organization and all support staff, support staff see everyone. Disabled accounts are
	/// included, because old tickets name them.
	public List<String> displayNamesVisibleTo(Caller caller) {
		return switch (caller) {
			case Caller.Support _ -> organizations.findAllDisplayNames();
			case Caller.Customer(var _, var organizationId) ->
				organizations.findDisplayNamesOfOrganizationAndSupport(organizationId);
		};
	}

	Organization get(Caller caller, UUID id) {
		var organization = switch (caller) {
			case Caller.Support _ -> organizations.findById(id);
			case Caller.Customer(var _, var organizationId) ->
				organizationId.equals(id) ? organizations.findById(id) : Optional.<Organization>empty();
		};
		return organization.orElseThrow(OrganizationProblems::notFound);
	}

}
