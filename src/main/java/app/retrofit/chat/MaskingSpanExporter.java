package app.retrofit.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/// Masks the chat's texts, the [TraceText] attributes, in every span before the batch reaches `delegate`, so a
/// prompt, an answer or a tool result leaves the app only masked (chat ADR 0007). A text the masker cannot mask
/// leaves as [#WITHHELD]; the rest of its span leaves unchanged.
///
/// Once the masker does not answer in time ([PersonalDataMasker.MaskerNotAnswering]), the rest of the batch leaves as
/// [#WITHHELD] without asking it again, so a hung Presidio holds the export for one text's timeout, not one for each
/// text. A text the masker refuses withholds only itself, and the next batch asks again.
///
/// A failure recorded on a span leaves as its classes and stack frames, never its messages, as the chat logs it
/// ([RedactedFailure]): the exception event's message, the messages in its stack trace and the span's status
/// description are withheld, because a message can quote the user's words (LiteLLM's error body echoes the request).
///
/// It runs on the batch span processor's thread: an answer never waits for the masker, and never fails because of it.
final class MaskingSpanExporter implements SpanExporter {

	private static final Logger log = LoggerFactory.getLogger(MaskingSpanExporter.class);

	/// What leaves the app in place of a text that could not be masked.
	static final String WITHHELD = "[withheld: masking unavailable]";

	/// What leaves the app in place of a failure's message.
	static final String MESSAGE_WITHHELD = "(message withheld)";

	private static final AttributeKey<String> EXCEPTION_MESSAGE = AttributeKey.stringKey("exception.message");

	private static final AttributeKey<String> EXCEPTION_STACKTRACE = AttributeKey.stringKey("exception.stacktrace");

	private final SpanExporter delegate;

	private final PersonalDataMasker masker;

	MaskingSpanExporter(SpanExporter delegate, PersonalDataMasker masker) {
		this.delegate = delegate;
		this.masker = masker;
	}

	@Override
	public CompletableResultCode export(Collection<SpanData> spans) {
		var failures = new Failures();
		var masked = spans.stream().map(span -> masked(span, failures)).toList();
		failures.log();
		return delegate.export(masked);
	}

	private SpanData masked(SpanData span, Failures failures) {
		var attributes = maskedTexts(span.getAttributes(), failures);
		var events = span.getEvents().stream().map(MaskingSpanExporter::withoutMessages).toList();
		var status = span.getStatus().getDescription().isEmpty() ? span.getStatus()
				: StatusData.create(span.getStatus().getStatusCode(), MESSAGE_WITHHELD);
		if (attributes == span.getAttributes() && status == span.getStatus() && events.equals(span.getEvents())) {
			return span;
		}
		return new MaskedSpanData(span, attributes, events, status);
	}

	private Attributes maskedTexts(Attributes attributes, Failures failures) {
		if (TraceText.KEYS.stream().noneMatch(key -> attributes.get(key) != null)) {
			return attributes;
		}
		var builder = attributes.toBuilder();
		for (var key : TraceText.KEYS) {
			var text = attributes.get(key);
			if (text != null) {
				builder.put(key, masked(text, failures));
			}
		}
		return builder.build();
	}

	/// An exception event without its messages; any other event unchanged.
	private static EventData withoutMessages(EventData event) {
		var attributes = event.getAttributes();
		var stackTrace = attributes.get(EXCEPTION_STACKTRACE);
		if (attributes.get(EXCEPTION_MESSAGE) == null && stackTrace == null) {
			return event;
		}
		var builder = attributes.toBuilder();
		if (attributes.get(EXCEPTION_MESSAGE) != null) {
			builder.put(EXCEPTION_MESSAGE, MESSAGE_WITHHELD);
		}
		if (stackTrace != null) {
			builder.put(EXCEPTION_STACKTRACE, withoutMessages(stackTrace));
		}
		return EventData.create(event.getEpochNanos(), event.getName(), builder.build(),
				event.getTotalAttributeCount());
	}

	/// A printed stack trace with only its classes and frames: each `Class: message` header keeps its class, and the
	/// further lines of a message that spans several are dropped.
	static String withoutMessages(String stackTrace) {
		var lines = stackTrace.lines().toList();
		var kept = new ArrayList<String>();
		for (int i = 0; i < lines.size(); i++) {
			var line = lines.get(i);
			var content = line.stripLeading();
			var indent = line.substring(0, line.length() - content.length());
			if (content.startsWith("at ") || content.startsWith("... ")) {
				kept.add(line);
			}
			else if (content.startsWith("Caused by: ") || content.startsWith("Suppressed: ")) {
				var label = content.substring(0, content.indexOf(": ") + 2);
				kept.add(indent + label + className(content.substring(label.length())));
			}
			else if (i == 0) {
				kept.add(className(line));
			}
		}
		return String.join("\n", kept);
	}

	private static String className(String header) {
		var colon = header.indexOf(": ");
		return (colon == -1 ? header : header.substring(0, colon)) + " " + MESSAGE_WITHHELD;
	}

	private String masked(String text, Failures failures) {
		if (failures.maskerNotAnswering()) {
			failures.skipped();
			return WITHHELD;
		}
		try {
			return masker.mask(text);
		}
		catch (RuntimeException ex) {
			failures.add(ex);
			return WITHHELD;
		}
	}

	@Override
	public CompletableResultCode flush() {
		return delegate.flush();
	}

	@Override
	public CompletableResultCode shutdown() {
		return delegate.shutdown();
	}

	/// The texts of one batch that could not be masked: logged once per batch, with the cause and never the text.
	private static final class Failures {

		private final List<RuntimeException> causes = new ArrayList<>();

		private int skipped;

		void add(RuntimeException cause) {
			causes.add(cause);
		}

		/// Whether the masker did not answer for a text of this batch; nothing is added after that failure.
		boolean maskerNotAnswering() {
			return !causes.isEmpty() && causes.getLast() instanceof PersonalDataMasker.MaskerNotAnswering;
		}

		/// A text withheld without asking the masker, because it did not answer for an earlier one.
		void skipped() {
			skipped++;
		}

		void log() {
			if (!causes.isEmpty()) {
				log.warn("{} trace texts could not be masked and leave as \"{}\"", causes.size() + skipped, WITHHELD,
						causes.getFirst());
			}
		}

	}

	private static final class MaskedSpanData extends DelegatingSpanData {

		private final Attributes attributes;

		private final List<EventData> events;

		private final StatusData status;

		MaskedSpanData(SpanData delegate, Attributes attributes, List<EventData> events, StatusData status) {
			super(delegate);
			this.attributes = attributes;
			this.events = events;
			this.status = status;
		}

		@Override
		public Attributes getAttributes() {
			return attributes;
		}

		@Override
		public List<EventData> getEvents() {
			return events;
		}

		@Override
		public StatusData getStatus() {
			return status;
		}

	}

}
