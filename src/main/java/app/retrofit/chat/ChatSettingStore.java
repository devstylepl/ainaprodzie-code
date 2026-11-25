package app.retrofit.chat;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/// The chat settings: one row, created by the changeset.
@Repository
class ChatSettingStore {

	private static final RowMapper<ChatSettings> STORED = (row, number) -> new ChatSettings(
			row.getBoolean("app_traces_exported"), row.getBoolean("gateway_traces_joined"),
			row.getBoolean("app_llm_spans_exported"), row.getBoolean("presidio_in_app"), row.getString("model"),
			Fallback.valueOf(row.getString("fallback")), ReasoningEffort.valueOf(row.getString("reasoning")));

	private final JdbcClient jdbc;

	private final Clock clock;

	ChatSettingStore(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	ChatSettings settings() {
		return jdbc.sql("""
				SELECT app_traces_exported, gateway_traces_joined, app_llm_spans_exported, presidio_in_app, model,
				       fallback, reasoning
				FROM chat_setting""")
				.query(STORED)
				.single();
	}

	/// Writes the settings the change names, leaves the others as stored and answers what is stored then.
	ChatSettings change(ChatSettingsChange change) {
		return jdbc.sql("""
				UPDATE chat_setting
				SET app_traces_exported = COALESCE(:exported, app_traces_exported),
				    gateway_traces_joined = COALESCE(:joined, gateway_traces_joined),
				    app_llm_spans_exported = COALESCE(:llmSpans, app_llm_spans_exported),
				    presidio_in_app = COALESCE(:presidio, presidio_in_app),
				    model = COALESCE(:model, model), fallback = COALESCE(:fallback, fallback),
				    reasoning = COALESCE(:reasoning, reasoning), updated_at = :now
				RETURNING app_traces_exported, gateway_traces_joined, app_llm_spans_exported, presidio_in_app, model,
				          fallback, reasoning""")
				.param("exported", change.appTracesExported(), Types.BOOLEAN)
				.param("joined", change.gatewayTracesJoined(), Types.BOOLEAN)
				.param("llmSpans", change.appLlmSpansExported(), Types.BOOLEAN)
				.param("presidio", change.presidioInApp(), Types.BOOLEAN)
				.param("model", change.model(), Types.VARCHAR)
				.param("fallback", change.fallback(), Types.VARCHAR)
				.param("reasoning", change.reasoning(), Types.VARCHAR)
				.param("now", Timestamp.from(clock.instant()))
				.query(STORED)
				.single();
	}

}
