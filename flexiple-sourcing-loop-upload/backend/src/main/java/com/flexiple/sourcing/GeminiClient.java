package com.flexiple.sourcing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GeminiClient {

	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
	// alias, so it survives Google retiring a specific version
	private static final String DEFAULT_MODEL = "gemini-flash-lite-latest";

	private static final int MAX_ATTEMPTS = 3;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

	private final String apiKey;
	private final String model;
	private final HttpClient http;
	private final Gson gson = new Gson();

	// Gemini 3 thinks before answering and that tripled every round trip. None of
	// our calls need it. Older models reject the field, so drop it if one complains.
	private volatile boolean thinkingConfigSupported = true;

	public GeminiClient() {
		this.apiKey = System.getenv("GEMINI_API_KEY");

		String configured = System.getenv("GEMINI_MODEL");
		this.model = (configured == null || configured.trim().isEmpty()) ? DEFAULT_MODEL : configured.trim();

		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(15))
				.build();
	}

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.trim().isEmpty();
	}

	public String getModel() {
		return model;
	}

	public <T> T generate(String systemPrompt, String userPrompt, String responseSchema, Class<T> type)
			throws LlmException {

		if (!hasApiKey()) {
			throw new LlmException(
				"GEMINI_API_KEY is not set on the server. Set it and restart the backend.", false);
		}

		String lastProblem = "";

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				// rebuilt each attempt so a dropped thinkingConfig applies on the retry
				String raw = callOnce(buildRequestBody(systemPrompt, userPrompt, responseSchema));
				String text = extractText(raw);
				return parseInto(text, type);

			} catch (LlmException e) {
				if (!e.retryable) {
					throw e;
				}
				lastProblem = e.getMessage();
				System.out.println("[gemini] attempt " + attempt + "/" + MAX_ATTEMPTS + " failed: " + lastProblem);

			} catch (Exception e) {
				lastProblem = e.getClass().getSimpleName() + ": " + e.getMessage();
				System.out.println("[gemini] attempt " + attempt + "/" + MAX_ATTEMPTS + " failed: " + lastProblem);
			}

			if (attempt < MAX_ATTEMPTS) {
				sleep(attempt * 1500L);
			}
		}

		throw new LlmException("The model did not answer after " + MAX_ATTEMPTS
				+ " attempts (" + lastProblem + ").", true);
	}

	private String buildRequestBody(String systemPrompt, String userPrompt, String responseSchema) {
		JsonObject systemPart = new JsonObject();
		systemPart.addProperty("text", systemPrompt);
		JsonArray systemParts = new JsonArray();
		systemParts.add(systemPart);
		JsonObject systemInstruction = new JsonObject();
		systemInstruction.add("parts", systemParts);

		JsonObject userPart = new JsonObject();
		userPart.addProperty("text", userPrompt);
		JsonArray userParts = new JsonArray();
		userParts.add(userPart);
		JsonObject content = new JsonObject();
		content.addProperty("role", "user");
		content.add("parts", userParts);
		JsonArray contents = new JsonArray();
		contents.add(content);

		JsonObject generationConfig = new JsonObject();
		generationConfig.addProperty("temperature", 0.2);
		generationConfig.addProperty("responseMimeType", "application/json");
		generationConfig.add("responseSchema", JsonParser.parseString(responseSchema));

		if (thinkingConfigSupported) {
			JsonObject thinking = new JsonObject();
			thinking.addProperty("thinkingBudget", 0);
			generationConfig.add("thinkingConfig", thinking);
		}

		JsonObject request = new JsonObject();
		request.add("system_instruction", systemInstruction);
		request.add("contents", contents);
		request.add("generationConfig", generationConfig);

		return request.toString();
	}

	private String callOnce(String body) throws Exception {
		URI uri = URI.create(BASE_URL + model + ":generateContent?key=" + apiKey);

		HttpRequest request = HttpRequest.newBuilder(uri)
				.header("Content-Type", "application/json")
				.timeout(REQUEST_TIMEOUT)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		int status = response.statusCode();

		if (status == 200) {
			return response.body();
		}

		String detail = shorten(response.body());

		if (status == 429) {
			throw new LlmException("Gemini rate limit hit (429).", true);
		}
		if (status >= 500) {
			throw new LlmException("Gemini is unavailable right now (" + status + ").", true);
		}
		if (status == 400 && detail.contains("API key")) {
			throw new LlmException("Gemini rejected the API key. Check GEMINI_API_KEY.", false);
		}
		// Models that don't take thinkingConfig just say "invalid argument", so any 400
		// is worth one retry without it before giving up.
		if (status == 400 && thinkingConfigSupported) {
			thinkingConfigSupported = false;
			System.out.println("[gemini] " + model + " rejected thinkingConfig - dropping it and retrying");
			throw new LlmException("Retrying without thinkingConfig.", true);
		}
		if (status == 403) {
			throw new LlmException("Gemini rejected the API key (403). Check GEMINI_API_KEY.", false);
		}
		if (status == 404) {
			throw new LlmException("Your key cannot use the model \"" + model + "\". Set GEMINI_MODEL to one "
					+ "your key does have - list them with "
					+ "https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY", false);
		}

		throw new LlmException("Gemini returned " + status + ": " + detail, false);
	}

	private String extractText(String raw) throws LlmException {
		JsonObject root;
		try {
			root = JsonParser.parseString(raw).getAsJsonObject();
		} catch (Exception e) {
			throw new LlmException("Gemini's response was not JSON at all.", true);
		}

		if (!root.has("candidates") || root.getAsJsonArray("candidates").size() == 0) {
			String reason = root.has("promptFeedback") ? root.get("promptFeedback").toString() : "no candidates";
			throw new LlmException("Gemini returned no answer (" + shorten(reason) + ").", true);
		}

		JsonObject candidate = root.getAsJsonArray("candidates").get(0).getAsJsonObject();

		if (candidate.has("finishReason")) {
			String finish = candidate.get("finishReason").getAsString();
			if ("MAX_TOKENS".equals(finish)) {
				throw new LlmException("The answer was cut off before it finished.", true);
			}
			if ("SAFETY".equals(finish) || "RECITATION".equals(finish)) {
				throw new LlmException("Gemini blocked this request (" + finish + ").", false);
			}
		}

		if (!candidate.has("content")) {
			throw new LlmException("Gemini returned an empty candidate.", true);
		}
		JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
		if (parts == null || parts.size() == 0) {
			throw new LlmException("Gemini returned an empty answer.", true);
		}

		// thinking models put reasoning in their own parts, and the answer can be split
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			JsonObject part = parts.get(i).getAsJsonObject();
			boolean isThought = part.has("thought") && part.get("thought").getAsBoolean();
			if (!isThought && part.has("text")) {
				text.append(part.get("text").getAsString());
			}
		}

		if (text.length() == 0) {
			throw new LlmException("Gemini thought about it but did not answer.", true);
		}
		return text.toString();
	}

	private <T> T parseInto(String text, Class<T> type) throws LlmException {
		String cleaned = stripCodeFence(text);
		try {
			T value = gson.fromJson(cleaned, type);
			if (value == null) {
				throw new LlmException("The model returned an empty object.", true);
			}
			return value;
		} catch (LlmException e) {
			throw e;
		} catch (Exception e) {
			System.out.println("[gemini] unreadable output: " + shorten(cleaned));
			throw new LlmException("The model returned something we could not read as JSON.", true, e);
		}
	}

	private String stripCodeFence(String text) {
		String t = text.trim();
		if (t.startsWith("```")) {
			int firstNewline = t.indexOf('\n');
			if (firstNewline > -1) {
				t = t.substring(firstNewline + 1);
			}
			if (t.endsWith("```")) {
				t = t.substring(0, t.length() - 3);
			}
		}
		return t.trim();
	}

	private String shorten(String s) {
		if (s == null) return "";
		s = s.replaceAll("\\s+", " ").trim();
		return s.length() > 200 ? s.substring(0, 200) + "..." : s;
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
