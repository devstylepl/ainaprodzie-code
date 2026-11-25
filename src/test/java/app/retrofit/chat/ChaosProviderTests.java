package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.yaml.snakeyaml.Yaml;

/// What the fake provider behind `chat-chaos` answers to a question that names no failure
/// (`chaos/chaos_provider.py`), checked by `chaos/test_chaos_provider.py` in the image `compose.yaml` gives the
/// `chaos-provider` service.
class ChaosProviderTests {

	private static final String TESTS = "/tests/";

	@Test
	void theFirstAnswerOfAConversationSaysHowToMakeTheProviderFailAndLaterOnesDoNot() throws IOException {
		var output = new ToStringConsumer();
		try (var python = new GenericContainer<>(DockerImageName.parse(chaosImage()))
			.withCopyFileToContainer(script("chaos_provider.py"), TESTS + "chaos_provider.py")
			.withCopyFileToContainer(script("test_chaos_provider.py"), TESTS + "test_chaos_provider.py")
			.withWorkingDirectory(TESTS)
			.withCommand("python", "-m", "unittest", "-v", "test_chaos_provider")
			.withLogConsumer(output)
			.withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))) {
			assertThatCode(python::start).as(output::toUtf8String).doesNotThrowAnyException();
		}
	}

	private static MountableFile script(String file) {
		return MountableFile.forHostPath(Path.of("chaos", file));
	}

	@SuppressWarnings("unchecked")
	private static String chaosImage() throws IOException {
		Map<String, Object> compose = new Yaml().load(Files.readString(Path.of("compose.yaml")));
		var services = (Map<String, Map<String, Object>>) compose.get("services");
		return (String) services.get("chaos-provider").get("image");
	}

}
