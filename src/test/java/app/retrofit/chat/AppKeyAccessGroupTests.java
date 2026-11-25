package app.retrofit.chat;

import static app.retrofit.chat.LiteLlmConfig.APP_GROUP;
import static app.retrofit.chat.LiteLlmConfig.alias;
import static app.retrofit.chat.LiteLlmConfig.groups;
import static app.retrofit.chat.LiteLlmConfig.models;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// The app's virtual key reaches the aliases of one access group in LiteLLM's `config.yaml` (chat ADR 0002), so an
/// alias the app calls but the group misses answers 403, and a judge in the group lets the app spend on it.
class AppKeyAccessGroupTests {

	private static final String JUDGE_PREFIX = "judge-";

	@Test
	void everyAliasButTheJudgesIsInTheAppsGroup() {
		assertThat(models()).filteredOn(model -> !alias(model).startsWith(JUDGE_PREFIX))
			.isNotEmpty()
			.allSatisfy(model -> assertThat(groups(model)).as(alias(model)).contains(APP_GROUP));
	}

	@Test
	void noJudgeIsInTheAppsGroup() {
		assertThat(models()).filteredOn(model -> alias(model).startsWith(JUDGE_PREFIX))
			.isNotEmpty()
			.allSatisfy(model -> assertThat(groups(model)).as(alias(model)).doesNotContain(APP_GROUP));
	}

}
