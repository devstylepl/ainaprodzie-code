package app.retrofit.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.micrometer.observation.ObservationRegistry;

/// Gives every regular test context the [ScriptedChatModel] in place of the real model, which the `test` profile
/// switches off (`spring.ai.model.chat: none`). A suite that turns the real model back on gets no scripted one.
///
/// A plain `@Configuration`, not a `@TestConfiguration`, so that component scanning finds it for every suite without an
/// import in the shared test bases: the chat's test folder goes away together with the chat (chat ADR 0001). The
/// evaluations run without the `test` profile and get the real model.
@Configuration(proxyBeanMethods = false)
@Profile("test")
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none")
class ScriptedChatModelConfiguration {

	@Bean
	ScriptedChatModel scriptedChatModel(ObservationRegistry observations) {
		return new ScriptedChatModel(observations);
	}

}
