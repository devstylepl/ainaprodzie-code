package app.retrofit.chat;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.yaml.snakeyaml.Yaml;

/// Runs a Python unittest of `litellm/` in the LiteLLM image `compose.yaml` runs, so the test goes through the LiteLLM
/// the app runs.
final class LiteLlmPython {

	private static final String TESTS = "/tests/";

	private LiteLlmPython() {
	}

	/// Runs the tests of `litellm/<test>.py`, with the `litellm/` modules it imports, and fails with the container's
	/// output when one fails.
	static void run(String test, String... imported) {
		var output = new ToStringConsumer();
		try (var litellm = new GenericContainer<>(DockerImageName.parse(image()))
			.withCopyFileToContainer(file(test), TESTS + test + ".py")
			.withWorkingDirectory(TESTS)
			.withCreateContainerCmdModifier(command -> command.withEntrypoint("python"))
			.withCommand("-m", "unittest", "-v", test)
			.withLogConsumer(output)
			.withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))) {
			for (var module : imported) {
				litellm.withCopyFileToContainer(file(module), TESTS + module + ".py");
			}
			assertThatCode(litellm::start).as(output::toUtf8String).doesNotThrowAnyException();
		}
	}

	private static MountableFile file(String module) {
		return MountableFile.forHostPath(Path.of("litellm", module + ".py"));
	}

	@SuppressWarnings("unchecked")
	private static String image() {
		try {
			Map<String, Object> compose = new Yaml().load(Files.readString(Path.of("compose.yaml")));
			var services = (Map<String, Map<String, Object>>) compose.get("services");
			return (String) services.get("litellm").get("image");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
