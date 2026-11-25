package app.retrofit.chat;

import static app.retrofit.chat.ScriptedChatModel.after;
import static app.retrofit.chat.ScriptedChatModel.answer;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import app.retrofit.auth.Role;

/// A conversation nobody asks in for `retrofit.chat.conversation-idle-timeout` is forgotten, its memory and its
/// placeholders together (chat ADR 0005). The suites sweep by hand: the scheduled sweep never finds a conversation
/// that idle while they run.
class ChatIdleConversationTests extends ChatServerContext {

	@Autowired
	ChatService chat;

	@Autowired
	ChatSettingStore settings;

	@BeforeEach
	void protectPersonalDataInTheApp() {
		settings.change(new ChatSettingsChange(false, false, true, true, "chat-gemini", null, null));
	}

	@Autowired
	ScheduledTaskHolder scheduling;

	@Value("${retrofit.chat.conversation-idle-timeout}")
	Duration idleTimeout;

	@Test
	void theSweepRunsOnASchedule() {
		assertThat(scheduling.getScheduledTasks()).extracting(task -> task.getTask().getRunnable().toString())
				.contains(ChatService.class.getName() + ".forgetIdleConversations");
	}

	@Test
	void aConversationIdlePastTheTimeoutIsForgottenWithItsPlaceholders() {
		users.create(Role.USER, "Anna Nowak");
		users.create(Role.USER, "Jan Kowalski");
		var session = login(users.create(Role.SUPPORT));
		var conversation = UUID.randomUUID();
		model.script(answer("<PERSON_1> is a customer."));
		ask(session, conversation, "Who is Anna Nowak?");

		chat.forgetIdle(Instant.now().plus(idleTimeout));
		model.script(answer("Unknown."));
		ask(session, conversation, "And Jan Kowalski?");

		var prompt = model.prompts().getFirst();
		assertThat(prompt.getContents()).doesNotContain("Who is <PERSON_1>?", "is a customer.");
		assertThat(prompt.getUserMessage().getText()).endsWith("And <PERSON_1>?");
	}

	@Test
	void aConversationAskedInWithinTheTimeoutKeepsItsMemory() {
		var session = login(users.create(Role.USER));
		var conversation = UUID.randomUUID();
		var asked = Instant.now();
		model.script(answer("First answer."));
		ask(session, conversation, "First question");

		chat.forgetIdle(asked.plus(idleTimeout).minusSeconds(1));
		model.script(answer("Second answer."));
		ask(session, conversation, "Second question");

		assertThat(model.prompts().getFirst().getContents()).contains("First question", "First answer.");
	}

	/// The turn's placeholders are gone with the conversation, so remembering it would leave the next turn numbering
	/// new values with the ones its memory names.
	@Test
	void aTurnStreamingWhenItsConversationIsForgottenReachesTheUserButIsNotRemembered() {
		var session = login(users.create(Role.USER));
		var conversation = UUID.randomUUID();
		model.script(answer("First answer."));
		ask(session, conversation, "First question");
		model.script(after(() -> chat.forgetIdle(Instant.now().plus(idleTimeout)), answer("Second answer.")));

		var reply = ask(session, conversation, "Second question");
		model.script(answer("Third answer."));
		ask(session, conversation, "Third question");

		assertThat(reply.text()).isEqualTo("Second answer.");
		assertThat(model.prompts().getFirst().getContents())
				.doesNotContain("First question", "Second question", "Second answer.");
	}

}
