package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import app.retrofit.TestcontainersConfiguration;
import app.retrofit.chat.ChatEvalConfiguration.ModelRequestRecorder;
import app.retrofit.chat.ChatEvalConfiguration.ToolCallRecorder;
import app.retrofit.organization.CallerLookup;
import app.retrofit.ticket.TicketQueries;
import app.retrofit.ticket.TicketSummary;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

/// What every chat eval class shares (chat ADR 0003): the context, which the classes share with their containers
/// and database, the questions asked through the real [ChatService] as a demo account, the judge and the report. Each
/// lesson's feature adds its cases to an `*Eval` class of its own that extends this one and registers its own
/// [Reporter], so a merge forward never renumbers another lesson's cases.
///
/// The cases run in order with `retrofit.eval.delay` (default 4 s) between them, and the report starts with the
/// prompt's name and version and the provider model that answered for each alias.
@Tag("eval")
@SpringBootTest(properties = { "spring.liquibase.contexts=schema,dev",
		// The spans stay in memory, behind the real masker: no OTLP endpoint is reached.
		"management.tracing.export.enabled=false", "management.opentelemetry.tracing.export.otlp.endpoint=unused" })
@Import({ TestcontainersConfiguration.class, ChatEvalConfiguration.class })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class ChatEvalSupport {

	private static final Pattern REFERENCE = Pattern.compile("\\bRET-\\d+\\b");

	/// An instant as the tools return it, such as `2026-10-04T00:55:45.078681Z`, which no answer copies (chat ADR 0010).
	private static final Pattern INSTANT = Pattern.compile("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d");

	@Autowired
	ChatService chat;

	@Autowired
	ToolCallRecorder toolCalls;

	@Autowired
	ModelRequestRecorder modelRequests;

	@Autowired
	InMemorySpanExporter exported;

	@Autowired
	SdkTracerProvider tracerProvider;

	@Autowired
	SystemPrompt systemPrompt;

	@Autowired
	ChatClient.Builder judgeClient;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TicketQueries tickets;

	@Autowired
	CallerLookup callers;

	@Autowired
	ChatSettingStore settings;

	@Value("${retrofit.eval.delay:4s}")
	Duration delay;

	/// The alias the evaluations ask, stored as the chat's model before every case; the settings page chooses it in the
	/// running app.
	@Value(ChatEvalConfiguration.CHAT_MODEL)
	String modelAlias;

	@Value(ChatEvalConfiguration.JUDGE_MODEL)
	String judgeAlias;

	private long caseStarted;

	/// The class's own report, registered with `@RegisterExtension` on a static field.
	abstract Reporter reporter();

	@BeforeEach
	void startACase() throws InterruptedException {
		reporter().prompt(systemPrompt, modelAlias, judgeAlias);
		reporter().pauseBeforeTheNextCase(delay);
		// An evaluation scores the model it names, so no call may be answered by that alias's fallback (chat ADR 0019),
		// and as its provider runs it, so no call asks for an effort left stored (chat ADR 0020).
		settings.change(new ChatSettingsChange(true, false, true, true, modelAlias, "OFF", "OFF"));
		tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
		exported.reset();
		toolCalls.clear();
		modelRequests.reset();
		caseStarted = System.nanoTime();
	}

	@AfterEach
	void recordWhatTheCaseSpent() {
		reporter().spend(modelRequests.spend(Duration.ofNanos(System.nanoTime() - caseStarted)));
	}

	void assertJudged(String question, Reply reply, String criterion) {
		var judge = judgeClient.clone().defaultOptions(OpenAiChatOptions.builder().model(judgeAlias));
		var verdict = new ChatJudge(judge, criterion)
				.evaluate(new EvaluationRequest(question, List.of(), reply.text()));
		assertThat(verdict.isPass()).as("%s%nAnswer: %s", verdict.getFeedback(), reply.text()).isTrue();
	}

	Reply ask(String email, String question) {
		return ask(email, UUID.randomUUID(), InterfaceLanguage.EN, question);
	}

	Reply ask(String email, InterfaceLanguage language, String question) {
		return ask(email, UUID.randomUUID(), language, question);
	}

	Reply ask(String email, UUID conversationId, InterfaceLanguage language, String question) {
		return ask(email, conversationId, language, question, null);
	}

	Reply askOnTicketPage(String email, UUID conversationId, String openTicket, String question) {
		return ask(email, conversationId, InterfaceLanguage.EN, question, openTicket);
	}

	/// The answer as the SPA receives it; a stream that ends in an error fails the case with its code, and an answer
	/// that writes an instant fails every case.
	Reply ask(String email, UUID conversationId, InterfaceLanguage language, String question,
			@Nullable String openTicket) {
		var events = chat.ask(new ChatService.Question(userId(email), conversationId, language, question, openTicket))
				.collectList()
				.block();
		assertThat(events).isNotEmpty();
		assertThat(events.getLast()).as("the end of the stream").isInstanceOf(ChatEvent.Done.class);
		var reply = new Reply(conversationId, events);
		assertThat(INSTANT.matcher(reply.text()).find()).as("an answer without an instant: %s", reply.text())
				.isFalse();
		return reply;
	}

	UUID userId(String email) {
		return jdbc.sql("SELECT id FROM app_user WHERE email = :email")
				.param("email", email)
				.query(UUID.class)
				.single();
	}

	/// One answer: its tool calls, in the order the model made them, and its text.
	record Reply(UUID conversationId, List<ChatEvent> events) {

		List<ChatEvent.ToolCall> calls() {
			return events.stream().filter(ChatEvent.ToolCall.class::isInstance).map(ChatEvent.ToolCall.class::cast)
					.toList();
		}

		List<Map<String, Object>> searches() {
			return calls().stream()
					.filter(call -> call.tool().equals("searchTickets"))
					.map(ChatEvent.ToolCall::arguments)
					.toList();
		}

		String text() {
			return events.stream().filter(ChatEvent.Token.class::isInstance).map(token -> ((ChatEvent.Token) token)
					.text()).collect(Collectors.joining());
		}

		Set<String> references() {
			return REFERENCE.matcher(text()).results().map(match -> match.group()).collect(Collectors.toSet());
		}

		/// The tickets the turn's tools returned, from the `tickets` events the SPA draws rows from (chat ADR 0010).
		Set<String> returned() {
			return events.stream()
					.filter(ChatEvent.Tickets.class::isInstance)
					.flatMap(event -> ((ChatEvent.Tickets) event).tickets().stream())
					.map(TicketSummary::reference)
					.collect(Collectors.toSet());
		}

	}

	/// Writes [ChatEvalReport] once a class's cases ran, to the console and to the class's file for the pull request.
	/// After each case it tells the report which provider models answered, from the context's
	/// [AnsweringModelRecorder], and fails a case answered by a model the gateway's config does not name.
	static final class Reporter implements TestWatcher, AfterEachCallback, AfterAllCallback {

		private final ChatEvalReport report = new ChatEvalReport();

		private final Path file;

		private ChatEvalReport.Spend spend = ChatEvalReport.Spend.NONE;

		private boolean started;

		Reporter(Path file) {
			this.file = file;
		}

		void prompt(SystemPrompt prompt, String modelAlias, String judgeAlias) {
			report.prompt(prompt, modelAlias, judgeAlias);
		}

		/// Every alias called since the previous case goes into the report; only the two it names are held to
		/// `litellm/config.yaml` (chat ADR 0003).
		@Override
		public void afterEach(ExtensionContext context) throws IOException {
			var answered = SpringExtension.getApplicationContext(context).getBean(AnsweringModelRecorder.class).take();
			answered.forEach((alias, providerModels) -> providerModels
					.forEach(providerModel -> report.answered(alias, providerModel)));
			var config = Files.readString(ChatEvalConfiguration.LITELLM_CONFIG);
			var foreign = report.aliases().stream()
					.distinct()
					.map(alias -> ChatEvalConfiguration.foreignAnswer(config, alias,
							answered.getOrDefault(alias, List.of())))
					.filter(Objects::nonNull)
					.toList();
			if (!foreign.isEmpty()) {
				throw new AssertionError(String.join("\n", foreign));
			}
		}

		/// The delay between cases keeps a rate-limited key from failing on HTTP 429 (chat ADR 0003).
		void pauseBeforeTheNextCase(Duration delay) throws InterruptedException {
			if (started) {
				Thread.sleep(delay);
			}
			started = true;
		}

		void spend(ChatEvalReport.Spend spend) {
			this.spend = spend;
		}

		@Override
		public void testSuccessful(ExtensionContext context) {
			report.passed(context.getDisplayName(), takeSpend());
		}

		@Override
		public void testFailed(ExtensionContext context, Throwable cause) {
			report.failed(context.getDisplayName(), takeSpend(), cause);
		}

		private ChatEvalReport.Spend takeSpend() {
			var taken = spend;
			spend = ChatEvalReport.Spend.NONE;
			return taken;
		}

		@Override
		public void afterAll(ExtensionContext context) {
			var text = report.render();
			System.out.println(text);
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, text);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

}
