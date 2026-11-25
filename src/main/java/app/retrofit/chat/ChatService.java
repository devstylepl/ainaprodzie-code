package app.retrofit.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import app.retrofit.organization.Caller;
import app.retrofit.organization.CallerLookup;
import app.retrofit.organization.OrganizationAccess;
import app.retrofit.organization.Team;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/// Answers one question of the signed-in user as a stream of [ChatEvent]s. The model may call the tools as often as
/// the tool-call limit allows; each call reads as the caller, whose id travels in the question's [ChatTurn] and never
/// through the model (chat ADR 0002). What the model reasons streams as `reasoning` events (chat ADR 0020).
///
/// The question goes to the model behind a context block (who is signed in, the current time, the interface
/// language), so the system prompt stays the same for every request (chat ADR 0008). The conversation remembers the
/// question without that block and the final answer, and only once the answer is complete: a failed or cancelled turn
/// leaves it as it was (chat ADR 0005). A conversation nobody asks in for `retrofit.chat.conversation-idle-timeout`
/// is forgotten as if it were deleted, so the personal data behind its placeholders does not stay in the process
/// until a restart.
///
/// A turn has one deadline, `retrofit.chat.turn-timeout`, for every model call and tool call together; past it the
/// turn ends as `timeout`, like a model call past the client's timeout. A tool already running is not interrupted.
///
/// The model reads the question, the tool results and its memory with the people replaced by the conversation's
/// placeholders, and the user reads the answer with their names (chat ADR 0009). With the switch on, the directory of
/// the people the caller may see is read once per question, and its names stand for the people in the question and in
/// every tool result of the turn.
///
/// The conversation id and the stored `appLlmSpansExported` go into the advisor context too, where Spring AI and
/// [ChatTraceContentFilter] read them for the trace (chat ADR 0007, 0016). No advisor reads memory from it: the memory
/// key also holds the user.
///
/// Every model call of a question carries the prompt's name and version and the conversation id as request metadata,
/// and the caller's id as the request's `user`: LiteLLM's end user, which its spans name and its spend is counted by,
/// and which no provider receives (chat ADR 0015). While the app exports its traces, each also carries the
/// `traceparent` of the question's request, so LiteLLM's spans join the question's trace; [OpenModelCalls] then
/// rewrites it to the model call's own span as the call goes out.
@Service
class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	/// The advisor context key that says whether the question's model spans are LLM spans with the calls' tokens.
	static final String LLM_SPANS_EXPORTED = "retrofit_llm_spans_exported";

	private final ChatClient chatClient;

	private final ChatMemory memory;

	private final CallerLookup callers;

	private final OrganizationAccess organizations;

	private final Clock clock;

	private final SystemPrompt systemPrompt;

	private final ChatSettingStore settings;

	private final Tracer tracer;

	private final Propagator propagator;

	private final Duration turnTimeout;

	private final Duration idleTimeout;

	/// Each conversation's placeholders and its latest question, by its memory key: they live as long as its memory
	/// (chat ADR 0009).
	private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

	ChatService(ChatClient chatClient, ChatMemory memory, CallerLookup callers, OrganizationAccess organizations,
			Clock clock, SystemPrompt systemPrompt, ChatSettingStore settings, Tracer tracer, Propagator propagator,
			@Value("${retrofit.chat.turn-timeout}") Duration turnTimeout,
			@Value("${retrofit.chat.conversation-idle-timeout}") Duration idleTimeout) {
		if (!turnTimeout.isPositive()) {
			throw new IllegalArgumentException("retrofit.chat.turn-timeout must be positive, was " + turnTimeout);
		}
		if (idleTimeout.compareTo(turnTimeout) <= 0) {
			throw new IllegalArgumentException("retrofit.chat.conversation-idle-timeout must be longer than "
					+ "retrofit.chat.turn-timeout (" + turnTimeout + "), was " + idleTimeout);
		}
		this.chatClient = chatClient;
		this.memory = memory;
		this.callers = callers;
		this.organizations = organizations;
		this.clock = clock;
		this.systemPrompt = systemPrompt;
		this.settings = settings;
		this.tracer = tracer;
		this.propagator = propagator;
		this.turnTimeout = turnTimeout;
		this.idleTimeout = idleTimeout;
	}

	/// One question in a conversation, asked in the user interface's `language`, on the page of the ticket
	/// `openTicket` when one was open.
	record Question(UUID callerId, UUID conversationId, InterfaceLanguage language, String text,
			@Nullable String openTicket) {
	}

	/// What the process holds of one conversation besides its memory: its placeholders, and when its latest question
	/// arrived.
	private record Conversation(Pseudonyms pseudonyms, Instant lastQuestion) {
	}

	/// Who is signed in is resolved here, once for the whole question, before the stream starts: a caller refused by
	/// [CallerLookup] gets an ordinary 401 rather than an `error` event, and the context block and every tool call read
	/// as this caller, so a user disabled mid-question finishes it and is refused the next one (chat ADR 0002).
	Flux<ChatEvent> ask(Question question) {
		var key = key(question.callerId(), question.conversationId());
		var caller = callers.current(question.callerId());
		var contextBlock = contextBlock(caller, question);
		var current = settings.settings();
		var people = current.presidioInApp()
				? new PeopleDirectory(organizations.displayNamesVisibleTo(caller)) : PeopleDirectory.EMPTY;
		var options = modelRequest(question, current);
		return Flux.defer(() -> {
			var pseudonyms = conversations.compute(key, (_, held) -> new Conversation(
					held == null ? new Pseudonyms() : held.pseudonyms(), clock.instant())).pseudonyms();
			var events = Sinks.many().unicast().<ChatEvent>onBackpressureBuffer();
			var turn = new ChatTurn(caller, pseudonyms, people, current.presidioInApp(), events);
			var text = pageBlock(question) + turn.forModel(question.text());
			var prompt = chatClient.prompt()
					.messages(memory.get(key))
					.user(contextBlock + text)
					.options(options)
					.toolContext(turn.toolContext());
			var answer = new Answer(turn);
			var deadline = Mono.delay(turnTimeout).cache();
			var answerEvents = prompt
					.advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, question.conversationId().toString())
							.param(LLM_SPANS_EXPORTED, current.appLlmSpansExported()))
					.stream()
					.chatResponse()
					.timeout(deadline, _ -> deadline)
					.concatMapIterable(response -> events(response, answer))
					.concatWith(Flux.defer(() -> complete(key, pseudonyms, text, answer)))
					.doFinally(signal -> events.tryEmitComplete());
			return Flux.merge(events.asFlux(), answerEvents);
		}).onErrorResume(failure -> Flux.just(failed(key, failure)));
	}

	/// Forgets one conversation of the caller; another user's conversation with the same id is a different one.
	void forget(UUID callerId, UUID conversationId) {
		var key = key(callerId, conversationId);
		conversations.compute(key, (_, _) -> {
			memory.clear(key);
			return null;
		});
	}

	/// Forgets every conversation whose latest question arrived `retrofit.chat.conversation-idle-timeout` or longer
	/// before `now`. Its memory is cleared while the map holds its key, so a question arriving meanwhile either keeps
	/// the conversation or starts it afresh, never with the earlier memory and new placeholders.
	void forgetIdle(Instant now) {
		var forgotten = new AtomicInteger();
		conversations.keySet().forEach(key -> conversations.computeIfPresent(key, (_, conversation) -> {
			if (conversation.lastQuestion().plus(idleTimeout).isAfter(now)) {
				return conversation;
			}
			memory.clear(key);
			forgotten.incrementAndGet();
			return null;
		}));
		if (forgotten.get() > 0) {
			log.info("Forgot {} idle conversations", forgotten.get());
		}
	}

	/// A conversation is forgotten at most a minute after its idle timeout.
	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	void forgetIdleConversations() {
		forgetIdle(clock.instant());
	}

	/// The options of every model call of the question, sent to the stored model with the fields that tell LiteLLM when
	/// it may fall back (chat ADR 0019) and the stored reasoning effort (chat ADR 0020). The `traceparent` is written
	/// here, on the request's thread, where the question's request span is current; the evaluations ask without one
	/// and send none.
	private OpenAiChatOptions.Builder modelRequest(Question question, ChatSettings current) {
		var headers = new HashMap<String, String>();
		var span = tracer.currentTraceContext().context();
		if (span != null && current.traceparentSent()) {
			propagator.inject(span, headers, Map::put);
		}
		return OpenAiChatOptions.builder()
				.model(current.model())
				.metadata(Map.of("prompt_name", systemPrompt.name(), "prompt_version", systemPrompt.version(),
						"conversation_id", question.conversationId().toString()))
				.user(question.callerId().toString())
				.customHeaders(headers)
				.reasoningEffort(current.reasoning().effort())
				.extraBody(current.fallback().requestFields());
	}

	/// The memory key: the session's user decides whose conversation a request reaches, never the request alone.
	private static String key(UUID callerId, UUID conversationId) {
		return callerId + ":" + conversationId;
	}

	private String contextBlock(Caller caller, Question question) {
		return """
				<context>
				Signed in: %s
				Current time: %s
				Interface language: %s
				</context>
				""".formatted(signedIn(caller), clock.instant().truncatedTo(ChronoUnit.SECONDS),
				question.language().tag());
	}

	/// Unlike the context block, it stays with the question in memory: it says what "this ticket" meant when the
	/// question was asked, which a later turn on another page still needs (chat ADR 0013).
	private static String pageBlock(Question question) {
		return question.openTicket() == null ? "" : "<page>ticket " + question.openTicket() + "</page>\n";
	}

	private String signedIn(Caller caller) {
		return switch (caller) {
			case Caller.Customer _ ->
				"a customer of the organization " + organizations.visibleTo(caller).getFirst().name();
			case Caller.Support _ -> "support staff in the " + organizations.teamsVisibleTo(caller)
					.stream()
					.filter(Team::member)
					.map(Team::name)
					.findFirst()
					.orElseThrow() + " team";
		};
	}

	/// The events of one chunk of the model's response: its reasoning, which is never held back and never counts as
	/// the answer, then what it adds to the answer. No log line or span of the app holds the reasoning, and the
	/// conversation does not remember it (chat ADR 0020).
	private static List<ChatEvent> events(ChatResponse response, Answer answer) {
		var events = new ArrayList<ChatEvent>();
		var reasoning = reasoning(response);
		if (!reasoning.isEmpty()) {
			events.add(new ChatEvent.Reasoning(reasoning));
		}
		events.addAll(answer.append(text(response)));
		return events;
	}

	private static String reasoning(ChatResponse response) {
		var reasoning = new StringBuilder();
		for (Generation generation : response.getResults()) {
			for (ReasoningPart part : generation.getOutput().getReasoning()) {
				if (part.text() != null) {
					reasoning.append(part.text());
				}
			}
		}
		return reasoning.toString();
	}

	/// The tool loop ends a turn that reaches the tool-call limit with a generation of its own instead of an answer.
	private static String text(ChatResponse response) {
		var text = new StringBuilder();
		for (Generation generation : response.getResults()) {
			if (ToolCallLimitExceededException.FINISH_REASON.equals(generation.getMetadata().getFinishReason())) {
				throw new ChatErrors.UnusableAnswer("The tool-call limit was reached");
			}
			if (generation.getOutput().getText() != null) {
				text.append(generation.getOutput().getText());
			}
		}
		return text.toString();
	}

	/// A conversation forgotten while its answer streamed, deleted or idle, does not remember the turn: its
	/// placeholders are gone, and the next turn would number new values with the ones memory still names. The turn is
	/// remembered while the map holds its key, so forgetting the conversation never runs between the check and the
	/// write.
	private Flux<ChatEvent> complete(String key, Pseudonyms pseudonyms, String question, Answer answer) {
		if (!answer.started()) {
			throw new ChatErrors.UnusableAnswer("The answer has no letter or digit");
		}
		conversations.computeIfPresent(key, (_, held) -> {
			if (held.pseudonyms() == pseudonyms) {
				memory.add(key, List.of(new UserMessage(question), new AssistantMessage(answer.text())));
			}
			return held;
		});
		return Flux.fromIterable(answer.rest()).concatWith(Flux.just(new ChatEvent.Done()));
	}

	/// Logged once, with the conversation key and the cause as a [RedactedFailure], never with the question or the
	/// answer.
	private static ChatEvent failed(String key, Throwable failure) {
		var code = ChatErrors.code(failure);
		log.warn("Chat answer failed with {} in conversation {}", code, key, RedactedFailure.of(failure));
		return new ChatEvent.Error(code);
	}

	/// The answer so far, as the model wrote it, with the placeholders the user reads as their values (chat ADR 0009).
	/// Text is held back until it has a letter or a digit, so a degenerate answer such as `:` never reaches the user
	/// and ends as `failed`, like an empty one; an unfinished placeholder is held back until its end arrives.
	private static final class Answer {

		private final ChatTurn turn;

		private final StringBuilder text = new StringBuilder();

		private int sent;

		private boolean started;

		Answer(ChatTurn turn) {
			this.turn = turn;
		}

		List<ChatEvent> append(String chunk) {
			text.append(chunk);
			if (!started && text.codePoints().noneMatch(Character::isLetterOrDigit)) {
				return List.of();
			}
			started = true;
			return sendUpTo(Pseudonyms.unfinishedPlaceholderStart(text, sent));
		}

		/// What is still held back, once the answer is complete.
		List<ChatEvent> rest() {
			return sendUpTo(text.length());
		}

		private List<ChatEvent> sendUpTo(int end) {
			if (end <= sent) {
				return List.of();
			}
			var piece = turn.fromModel(text.substring(sent, end));
			sent = end;
			return List.of(new ChatEvent.Token(piece));
		}

		boolean started() {
			return started;
		}

		String text() {
			return text.toString();
		}

	}

}
