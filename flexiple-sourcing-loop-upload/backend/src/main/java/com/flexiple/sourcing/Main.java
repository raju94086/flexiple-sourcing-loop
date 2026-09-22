package com.flexiple.sourcing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import com.flexiple.sourcing.model.Rubric;
import com.flexiple.sourcing.model.SearchFilters;
import com.flexiple.sourcing.model.SearchSession;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/*
 * All endpoints return the whole session, so the frontend never has to stitch
 * partial updates together.
 */
public class Main {

	private static final Gson gson = new Gson();

	public static void main(String[] args) throws IOException {

		int port = 8080;
		String configured = System.getenv("PORT");
		if (configured != null && !configured.trim().isEmpty()) {
			port = Integer.parseInt(configured.trim());
		}

		ProfileStore store = new ProfileStore();
		GeminiClient llm = new GeminiClient();
		SourcingService service = new SourcingService(store, llm);

		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

		// LLM calls take seconds, requests must not queue behind each other
		server.setExecutor(Executors.newFixedThreadPool(8));

		server.createContext("/api/health", exchange -> {
			JsonObject health = new JsonObject();
			health.addProperty("ok", true);
			health.addProperty("has_api_key", llm.hasApiKey());
			health.addProperty("model", llm.getModel());
			health.addProperty("profiles", store.size());
			health.addProperty("shown_per_round", SourcingService.SHOWN_PER_ROUND);
			send(exchange, 200, health.toString());
		});

		server.createContext("/api/search", post(exchange -> {
			SearchRequest req = gson.fromJson(readBody(exchange), SearchRequest.class);
			if (req == null || req.query == null || req.query.trim().isEmpty()) {
				throw new IllegalArgumentException("Type what you are looking for first.");
			}
			return service.startSearch(req.query.trim());
		}));

		server.createContext("/api/refine", post(exchange -> {
			RefineRequest req = gson.fromJson(readBody(exchange), RefineRequest.class);
			if (req == null || req.feedback == null || req.feedback.trim().isEmpty()) {
				throw new IllegalArgumentException("Tell me something about these profiles first.");
			}
			return service.refine(req.session_id, req.feedback.trim());
		}));

		server.createContext("/api/edit", post(exchange -> {
			EditRequest req = gson.fromJson(readBody(exchange), EditRequest.class);
			if (req == null) {
				throw new IllegalArgumentException("Nothing to apply.");
			}
			return service.applyEdits(req.session_id, req.filters, req.rubric);
		}));

		server.createContext("/api/freeze", post(exchange -> {
			SessionRequest req = gson.fromJson(readBody(exchange), SessionRequest.class);
			return service.freeze(req.session_id);
		}));

		server.createContext("/api/unfreeze", post(exchange -> {
			SessionRequest req = gson.fromJson(readBody(exchange), SessionRequest.class);
			return service.unfreeze(req.session_id);
		}));

		server.start();

		System.out.println("[server] listening on http://localhost:" + port);
		System.out.println("[server] model: " + llm.getModel());
		if (!llm.hasApiKey()) {
			System.out.println("[server] WARNING: GEMINI_API_KEY is not set. Searches will fail until it is.");
		}
	}

	interface SessionAction {
		SearchSession run(HttpExchange exchange) throws Exception;
	}

	private static HttpHandler post(SessionAction action) {
		return exchange -> {
			cors(exchange);

			if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendError(exchange, 405, "Use POST.", false);
				return;
			}

			try {
				SearchSession session = action.run(exchange);
				send(exchange, 200, gson.toJson(session));

			} catch (LlmException e) {
				// retryable flag lets the UI decide whether to offer a Retry button
				System.out.println("[api] llm failure: " + e.getMessage());
				sendError(exchange, 502, e.getMessage(), e.retryable);

			} catch (JsonSyntaxException e) {
				sendError(exchange, 400, "That request body was not valid JSON.", false);

			} catch (IllegalArgumentException | IllegalStateException e) {
				sendError(exchange, 400, e.getMessage(), false);

			} catch (Exception e) {
				e.printStackTrace();
				sendError(exchange, 500, "Something broke on the server: " + e.getMessage(), true);
			}
		};
	}

	private static String readBody(HttpExchange exchange) throws IOException {
		InputStream in = exchange.getRequestBody();
		return new String(in.readAllBytes(), StandardCharsets.UTF_8);
	}

	private static void cors(HttpExchange exchange) {
		exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
		exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
		exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		cors(exchange);
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		OutputStream out = exchange.getResponseBody();
		out.write(bytes);
		out.close();
	}

	private static void sendError(HttpExchange exchange, int status, String message, boolean retryable)
			throws IOException {
		JsonObject error = new JsonObject();
		error.addProperty("error", message == null ? "Unknown error." : message);
		error.addProperty("retryable", retryable);
		send(exchange, status, error.toString());
	}

	static class SearchRequest {
		String query;
	}

	static class RefineRequest {
		String session_id;
		String feedback;
	}

	static class EditRequest {
		String session_id;
		SearchFilters filters;
		Rubric rubric;
	}

	static class SessionRequest {
		String session_id;
	}
}
