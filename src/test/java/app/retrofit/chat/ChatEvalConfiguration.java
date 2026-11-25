package app.retrofit.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.yaml.snakeyaml.Yaml;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;

/// The context of the chat's evaluations (chat ADR 0003): LiteLLM in Testcontainers with the image and the
/// `config.yaml` of `compose.yaml`, so an evaluation reaches the same alias, proxy and provider as the running app,
/// whatever stack happens to run locally.
///
/// Presidio's analyzer and anonymizer run with the images of `compose.yaml` too, for the app and for LiteLLM's
/// guardrail behind it (chat ADRs 0007 and 0017), and an in-memory exporter collects what would leave the app: being a
/// `SpanExporter` bean, it sits behind the real [MaskingSpanExporter] and [PresidioMasker], as the OTLP exporter does
/// (chat ADR 0007).
@TestConfiguration(proxyBeanMethods = false)
@Import(AnsweringModelRecorder.Wiring.class)
class ChatEvalConfiguration {

	/// Keep in sync with the image in compose.yaml.
	static final String LITELLM_IMAGE = "ghcr.io/berriai/litellm:v1.108.8";

	/// Keep in sync with the image in compose.yaml.
	static final String PRESIDIO_ANALYZER_IMAGE = "ghcr.io/data-privacy-stack/presidio-analyzer:2.2.365";

	/// Keep in sync with the image in compose.yaml.
	static final String PRESIDIO_ANONYMIZER_IMAGE = "ghcr.io/data-privacy-stack/presidio-anonymizer:2.2.365";

	static final Path LITELLM_CONFIG = Path.of("litellm", "config.yaml");

	/// The recognizers of the guardrail, where `config.yaml` reads them in the container, as `compose.yaml` mounts them.
	private static final Path PRESIDIO_RECOGNIZERS = Path.of("litellm", "presidio-recognizers.json");

	private static final String CONTAINER_RECOGNIZERS = "/app/presidio-recognizers.json";

	/// The analyzer's recognizers in place of its defaults, the PESEL among them (chat ADR 0017), as `compose.yaml`
	/// mounts them.
	private static final Path ANALYZER_RECOGNIZERS = Path.of("presidio", "recognizers.yaml");

	private static final String CONTAINER_ANALYZER_RECOGNIZERS = "/app/recognizers.yaml";

	/// The tracing callback `config.yaml` loads from beside itself (chat ADR 0014), as `compose.yaml` mounts it. With no
	/// Phoenix here, its spans are dropped.
	private static final Path PHOENIX_CALLBACK = Path.of("litellm", "phoenix_callback.py");

	private static final String CONTAINER_PHOENIX_CALLBACK = "/app/phoenix_callback.py";

	/// The guardrail's class `config.yaml` names (chat ADR 0017), as `compose.yaml` mounts it.
	private static final Path PERSONAL_DATA_GUARDRAIL = Path.of("litellm", "personal_data_guardrail.py");

	private static final String CONTAINER_PERSONAL_DATA_GUARDRAIL = "/app/personal_data_guardrail.py";

	private static final String ANALYZER_HOST = "presidio-analyzer";

	private static final String ANONYMIZER_HOST = "presidio-anonymizer";

	private static final String ENVIRONMENT_REFERENCE = "os.environ/";

	static final String MASTER_KEY = "sk-retrofit-eval";

	private static final int PORT = 4000;

	static final int PRESIDIO_PORT = 3000;

	/// LiteLLM reaches Presidio by the names of `compose.yaml` on this network.
	private final Network network = Network.newNetwork();

	/// The alias of the judge (chat ADR 0003): `judge-gemini` unless `JUDGE_MODEL` names another alias of LiteLLM's
	/// `config.yaml`. It is a stronger model than the chat's, because a judge as weak as the model it grades misreads
	/// its criterion.
	static final String JUDGE_MODEL = "${JUDGE_MODEL:judge-gemini}";

	/// The alias the evaluations ask, an alias of `config.yaml` the app may call. The running app takes its alias from
	/// the settings page; the evaluations store this one there before every case.
	static final String CHAT_MODEL = "${CHAT_MODEL:chat-gemini}";

	/// LiteLLM gets the keys of the alias the chat names (`CHAT_MODEL`) and of the judge's, so an evaluation runs on
	/// whichever providers they point at. A missing key fails the run: skipping would report an evaluation nobody ran
	/// as green.
	@Bean
	GenericContainer<?> litellmContainer(@Value(CHAT_MODEL) String alias,
			@Value(JUDGE_MODEL) String judgeAlias, GenericContainer<?> presidioAnalyzerContainer,
			GenericContainer<?> presidioAnonymizerContainer) throws IOException {
		var container = litellm(MountableFile.forHostPath(LITELLM_CONFIG), network)
				.dependsOn(presidioAnalyzerContainer, presidioAnonymizerContainer)
				// A local model runs on the host, as in compose.yaml.
				.withExtraHost("host.docker.internal", "host-gateway");
		var config = Files.readString(LITELLM_CONFIG);
		for (var model : List.of(alias, judgeAlias)) {
			for (var variable : providerKeyVariables(config, model)) {
				var key = System.getenv(variable);
				if (key == null || key.isBlank()) {
					throw new IllegalStateException(variable + " is not set. The evaluations call the model " + model
							+ " through LiteLLM and need its provider key: export " + variable
							+ " and run them again.");
				}
				container.withEnv(variable, key);
			}
		}
		return container;
	}

	/// LiteLLM with `config`, the guardrail's recognizers, the tracing callback, and the Presidio of `network` for the guardrail, as
	/// `compose.yaml` runs it; [#presidio] on that network answers it.
	static GenericContainer<?> litellm(Transferable config, Network network) {
		return new GenericContainer<>(DockerImageName.parse(LITELLM_IMAGE))
				.withCopyToContainer(config, "/app/config.yaml")
				.withCopyFileToContainer(MountableFile.forHostPath(PRESIDIO_RECOGNIZERS), CONTAINER_RECOGNIZERS)
				.withCopyFileToContainer(MountableFile.forHostPath(PHOENIX_CALLBACK), CONTAINER_PHOENIX_CALLBACK)
				.withCopyFileToContainer(MountableFile.forHostPath(PERSONAL_DATA_GUARDRAIL),
						CONTAINER_PERSONAL_DATA_GUARDRAIL)
				.withCommand("--config", "/app/config.yaml", "--port", String.valueOf(PORT))
				.withEnv("LITELLM_MASTER_KEY", MASTER_KEY)
				.withEnv("PRESIDIO_ANALYZER_API_BASE", "http://" + ANALYZER_HOST + ":" + PRESIDIO_PORT)
				.withEnv("PRESIDIO_ANONYMIZER_API_BASE", "http://" + ANONYMIZER_HOST + ":" + PRESIDIO_PORT)
				.withNetwork(network)
				.withExposedPorts(PORT)
				.waitingFor(Wait.forHttp("/health/readiness").forStatusCode(200))
				.withStartupTimeout(Duration.ofMinutes(3));
	}

	/// The environment variables the alias's entries in LiteLLM's `config.yaml` read (`os.environ/...`), such as
	/// `api_key: os.environ/GEMINI_API_KEY`, or none for a model that needs no key, such as a local one. Every entry
	/// of the alias counts, because LiteLLM spreads the requests across them, and every parameter, because a provider
	/// such as Bedrock reads more than one.
	static List<String> providerKeyVariables(String config, String alias) {
		return parameters(config, alias).stream()
				.flatMap(parameters -> parameters.values().stream())
				.flatMap(value -> value instanceof String text && text.startsWith(ENVIRONMENT_REFERENCE)
						? Stream.of(text.substring(ENVIRONMENT_REFERENCE.length()))
						: Stream.empty())
				.distinct()
				.toList();
	}

	/// What a case reports when `alias` was answered by a provider model that none of its entries in LiteLLM's
	/// `config.yaml` names (`litellm_params.model`), or null when the entries name every model of `answeredBy`. The
	/// names are compared as the gateway and the file write them. Such a case fails for the reason a missing key
	/// fails the run: its report would describe a model nobody evaluated.
	static @Nullable String foreignAnswer(String config, String alias, Collection<String> answeredBy) {
		var named = parameters(config, alias).stream()
				.flatMap(parameters -> parameters.get("model") instanceof String model
						? Stream.of(model)
						: Stream.empty())
				.distinct()
				.toList();
		var foreign = answeredBy.stream().filter(model -> !named.contains(model)).distinct().toList();
		return foreign.isEmpty() ? null
				: alias + " was answered by " + String.join(", ", foreign) + "; litellm/config.yaml names "
						+ String.join(", ", named) + " for it";
	}

	/// The `litellm_params` of every entry of `alias`.
	private static List<Map<?, ?>> parameters(String config, String alias) {
		@Nullable Object root = new Yaml().load(config);
		var models = root instanceof Map<?, ?> map && map.get("model_list") instanceof List<?> list ? list : List.of();
		var entries = models.stream()
				.filter(model -> model instanceof Map<?, ?> entry && alias.equals(entry.get("model_name")))
				.map(model -> (Map<?, ?>) model)
				.toList();
		if (entries.isEmpty()) {
			throw new IllegalStateException("litellm/config.yaml has no model " + alias
					+ ". Uncomment its entry, or set CHAT_MODEL or JUDGE_MODEL to an alias it lists.");
		}
		return entries.stream()
				.<Map<?, ?>>flatMap(entry -> entry.get("litellm_params") instanceof Map<?, ?> parameters
						? Stream.of(parameters)
						: Stream.empty())
				.toList();
	}

	@Bean
	GenericContainer<?> presidioAnalyzerContainer() {
		return presidioAnalyzer(network);
	}

	@Bean
	GenericContainer<?> presidioAnonymizerContainer() {
		return presidioAnonymizer(network);
	}

	static GenericContainer<?> presidioAnalyzer(Network network) {
		return presidioAnalyzer().withNetwork(network).withNetworkAliases(ANALYZER_HOST);
	}

	static GenericContainer<?> presidioAnalyzer() {
		return presidio(PRESIDIO_ANALYZER_IMAGE)
				.withCopyFileToContainer(MountableFile.forHostPath(ANALYZER_RECOGNIZERS), CONTAINER_ANALYZER_RECOGNIZERS)
				.withEnv("RECOGNIZER_REGISTRY_CONF_FILE", CONTAINER_ANALYZER_RECOGNIZERS);
	}

	static GenericContainer<?> presidioAnonymizer(Network network) {
		return presidio(PRESIDIO_ANONYMIZER_IMAGE).withNetwork(network).withNetworkAliases(ANONYMIZER_HOST);
	}

	/// Without the control socket, as in compose.yaml: gunicorn 25 in these images starts it in a thread that may hold
	/// stderr's lock when the worker is forked, and the worker then hangs on its first log line.
	static GenericContainer<?> presidio(String image) {
		return new GenericContainer<>(DockerImageName.parse(image))
				.withEnv("GUNICORN_CMD_ARGS", "--no-control-socket")
				.withExposedPorts(PRESIDIO_PORT)
				.waitingFor(Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(60)));
	}

	@Bean
	DynamicPropertyRegistrar evalProperties(GenericContainer<?> litellmContainer,
			GenericContainer<?> presidioAnalyzerContainer, GenericContainer<?> presidioAnonymizerContainer) {
		return registry -> {
			registry.add("spring.ai.openai.base-url", () -> litellmUrl(litellmContainer));
			registry.add("spring.ai.openai.api-key", () -> MASTER_KEY);
			registry.add("retrofit.chat.presidio.analyzer-url", () -> url(presidioAnalyzerContainer, PRESIDIO_PORT));
			registry.add("retrofit.chat.presidio.anonymizer-url",
					() -> url(presidioAnonymizerContainer, PRESIDIO_PORT));
		};
	}

	static String litellmUrl(GenericContainer<?> litellm) {
		return url(litellm, PORT);
	}

	static String url(GenericContainer<?> container, int port) {
		return "http://" + container.getHost() + ":" + container.getMappedPort(port);
	}

	@Bean
	InMemorySpanExporter inMemorySpanExporter() {
		return InMemorySpanExporter.create();
	}

	@Bean
	ToolCallRecorder toolCallRecorder() {
		return new ToolCallRecorder();
	}

	@Bean
	ModelRequestRecorder modelRequestRecorder() {
		return new ModelRequestRecorder();
	}

	/// One tool call as the model asked for it, and what the tool answered.
	record ToolCall(String name, String arguments, @Nullable String result) {
	}

	/// Collects the tool calls of every question from Spring AI's tool calling observations.
	static final class ToolCallRecorder implements ObservationHandler<ToolCallingObservationContext> {

		private final List<ToolCall> calls = new CopyOnWriteArrayList<>();

		@Override
		public boolean supportsContext(Observation.Context context) {
			return context instanceof ToolCallingObservationContext;
		}

		@Override
		public void onStop(ToolCallingObservationContext context) {
			calls.add(new ToolCall(context.getToolDefinition().name(), context.getToolCallArguments(),
					context.getToolCallResult()));
		}

		List<ToolCall> calls() {
			return List.copyOf(calls);
		}

		void clear() {
			calls.clear();
		}

	}

	/// One model call as the app made it, before the gateway masks the request and before the app restores the
	/// answer: every message the model was sent, as text, and the text it wrote.
	record ModelCall(String read, String written) {
	}

	/// Counts the model calls, the judge's included, and the tokens they spent, for the report: each is one request
	/// against the provider's quota, or two when the SDK retries it. It keeps what each call read and wrote too, so a
	/// case can follow a value through the model's input and output.
	static final class ModelRequestRecorder implements ObservationHandler<ChatModelObservationContext> {

		private final AtomicInteger count = new AtomicInteger();

		private final AtomicLong inputTokens = new AtomicLong();

		private final AtomicLong cachedInputTokens = new AtomicLong();

		private final AtomicLong cacheWriteTokens = new AtomicLong();

		private final AtomicLong outputTokens = new AtomicLong();

		private final List<ModelCall> calls = new CopyOnWriteArrayList<>();

		@Override
		public boolean supportsContext(Observation.Context context) {
			return context instanceof ChatModelObservationContext;
		}

		@Override
		public void onStop(ChatModelObservationContext context) {
			count.incrementAndGet();
			var response = context.getResponse();
			calls.add(new ModelCall(textSent(context.getRequest()), response == null ? "" : textAnswered(response)));
			if (response != null) {
				var usage = response.getMetadata().getUsage();
				inputTokens.addAndGet(Objects.requireNonNullElse(usage.getPromptTokens(), 0));
				cachedInputTokens.addAndGet(Objects.requireNonNullElse(usage.getCacheReadInputTokens(), 0L));
				cacheWriteTokens.addAndGet(Objects.requireNonNullElse(usage.getCacheWriteInputTokens(), 0L));
				outputTokens.addAndGet(Objects.requireNonNullElse(usage.getCompletionTokens(), 0));
			}
		}

		/// The calls since the last reset, oldest first.
		List<ModelCall> calls() {
			return List.copyOf(calls);
		}

		private static String textSent(Prompt prompt) {
			return String.join("\n", prompt.getInstructions()
					.stream()
					.flatMap(message -> message instanceof ToolResponseMessage tools
							? tools.getResponses().stream().map(ToolResponseMessage.ToolResponse::responseData)
							: Stream.ofNullable(message.getText()))
					.toList());
		}

		private static String textAnswered(ChatResponse response) {
			return String.join("", response.getResults()
					.stream()
					.map(generation -> generation.getOutput().getText())
					.filter(Objects::nonNull)
					.toList());
		}

		ChatEvalReport.Spend spend(Duration duration) {
			return new ChatEvalReport.Spend(count.get(), inputTokens.get(), cachedInputTokens.get(),
					cacheWriteTokens.get(), outputTokens.get(), duration);
		}

		void reset() {
			count.set(0);
			inputTokens.set(0);
			cachedInputTokens.set(0);
			cacheWriteTokens.set(0);
			outputTokens.set(0);
			calls.clear();
		}

	}

}
