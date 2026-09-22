package com.flexiple.sourcing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.flexiple.sourcing.model.ChangeNote;
import com.flexiple.sourcing.model.ChatMessage;
import com.flexiple.sourcing.model.Profile;
import com.flexiple.sourcing.model.Rubric;
import com.flexiple.sourcing.model.ScoredProfile;
import com.flexiple.sourcing.model.SearchFilters;
import com.flexiple.sourcing.model.SearchSession;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SourcingService {

	public static final int SHOWN_PER_ROUND = 5;

	// scoring is the slow call - a search still matching 40 people needs better
	// filters, not a bigger prompt
	private static final int MAX_SCORED = 30;

	private final ProfileStore store;
	private final GeminiClient llm;
	private final Map<String, SearchSession> sessions = new ConcurrentHashMap<String, SearchSession>();
	private final Gson pretty = new GsonBuilder().setPrettyPrinting().create();

	public SourcingService(ProfileStore store, GeminiClient llm) {
		this.store = store;
		this.llm = llm;
	}

	public SearchSession get(String sessionId) {
		return sessions.get(sessionId);
	}

	public SearchSession startSearch(String query) throws LlmException {

		ParseResult parsed = llm.generate(
				Prompts.PARSE_SYSTEM,
				Prompts.parseUser(query, store.vocabularyText()),
				Prompts.PARSE_SCHEMA,
				ParseResult.class);

		if (parsed.filters == null || parsed.rubric == null) {
			throw new LlmException("The model did not return both filters and a rubric.", true);
		}
		parsed.filters.fillNulls();
		parsed.rubric.fillNulls();
		if (parsed.rubric.role_summary.isEmpty()) {
			parsed.rubric.role_summary = query;
		}

		SearchSession session = new SearchSession();
		session.session_id = UUID.randomUUID().toString();
		session.query = query;
		session.filters = parsed.filters;
		session.rubric = parsed.rubric;
		session.total_profiles = store.size();
		session.round = 1;

		runSearch(session);

		session.chat.add(new ChatMessage("app",
				"I read your request as: " + session.rubric.role_summary
				+ " Here are the top matches - tell me which ones are right and which are not."));

		sessions.put(session.session_id, session);
		return session;
	}

	public SearchSession refine(String sessionId, String feedback) throws LlmException {

		SearchSession session = requireSession(sessionId);
		if (session.frozen) {
			throw new IllegalStateException("This search is frozen.");
		}

		RefineResult refined = llm.generate(
				Prompts.REFINE_SYSTEM,
				Prompts.refineUser(
						session.query,
						store.vocabularyText(),
						pretty.toJson(new StateSnapshot(session.filters, session.rubric)),
						shownProfilesText(session),
						session.feedback_history,
						feedback),
				Prompts.REFINE_SCHEMA,
				RefineResult.class);

		if (refined.filters == null || refined.rubric == null) {
			throw new LlmException("The model did not return an updated search.", true);
		}
		refined.filters.fillNulls();
		refined.rubric.fillNulls();
		if (refined.rubric.role_summary.isEmpty()) {
			refined.rubric.role_summary = session.rubric.role_summary;
		}

		// keep the old search until the new one succeeds, otherwise a failure
		// half way through leaves the recruiter on a blank screen
		SearchFilters previousFilters = session.filters;
		Rubric previousRubric = session.rubric;

		session.filters = refined.filters;
		session.rubric = refined.rubric;

		try {
			runSearch(session);
		} catch (LlmException e) {
			session.filters = previousFilters;
			session.rubric = previousRubric;
			throw e;
		}

		session.round = session.round + 1;
		session.feedback_history.add(feedback);
		session.chat.add(new ChatMessage("recruiter", feedback));

		ChatMessage reply = new ChatMessage("app",
				(refined.reply == null || refined.reply.trim().isEmpty())
					? "Updated the search and re-ran it."
					: refined.reply.trim());
		reply.changes = (refined.changes == null) ? new ArrayList<ChangeNote>() : refined.changes;
		session.chat.add(reply);

		return session;
	}

	// recruiter edited the panels directly - no LLM call, just re-run what they typed
	public SearchSession applyEdits(String sessionId, SearchFilters filters, Rubric rubric) throws LlmException {

		SearchSession session = requireSession(sessionId);
		if (session.frozen) {
			throw new IllegalStateException("This search is frozen.");
		}

		SearchFilters previousFilters = session.filters;
		Rubric previousRubric = session.rubric;

		if (filters != null) {
			filters.fillNulls();
			session.filters = filters;
		}
		if (rubric != null) {
			rubric.fillNulls();
			session.rubric = rubric;
		}

		try {
			runSearch(session);
		} catch (LlmException e) {
			session.filters = previousFilters;
			session.rubric = previousRubric;
			throw e;
		}

		session.round = session.round + 1;
		session.chat.add(new ChatMessage("app", "You edited the search yourself, so I re-ran it as written."));
		return session;
	}

	public SearchSession freeze(String sessionId) {
		SearchSession session = requireSession(sessionId);
		session.frozen = true;
		session.chat.add(new ChatMessage("app",
				"Search frozen after " + session.round + " round" + (session.round == 1 ? "" : "s")
				+ ". " + session.results.size() + " candidates shortlisted."));
		return session;
	}

	public SearchSession unfreeze(String sessionId) {
		SearchSession session = requireSession(sessionId);
		session.frozen = false;
		session.chat.add(new ChatMessage("app", "Search reopened. Keep going."));
		return session;
	}

	private void runSearch(SearchSession session) throws LlmException {

		List<Profile> matched = store.filter(session.filters);

		if (matched.isEmpty()) {
			session.matched_count = 0;
			session.results = new ArrayList<ScoredProfile>();
			return;
		}

		List<Profile> toScore = matched;
		if (matched.size() > MAX_SCORED) {
			toScore = matched.subList(0, MAX_SCORED);
		}

		ScoreResult scored = llm.generate(
				Prompts.SCORE_SYSTEM,
				Prompts.scoreUser(session.rubric.role_summary, rubricAsText(session.rubric), toScore),
				Prompts.SCORE_SCHEMA,
				ScoreResult.class);

		session.matched_count = matched.size();
		session.results = merge(toScore, scored);
	}

	private List<ScoredProfile> merge(List<Profile> profiles, ScoreResult scored) {

		Map<String, ScoreItem> byId = new HashMap<String, ScoreItem>();
		if (scored.scores != null) {
			for (ScoreItem item : scored.scores) {
				if (item != null && item.id != null) {
					byId.put(item.id.trim(), item);
				}
			}
		}

		List<ScoredProfile> results = new ArrayList<ScoredProfile>();

		for (Profile p : profiles) {
			ScoredProfile sp = new ScoredProfile();
			sp.profile = p;

			ScoreItem item = byId.get(p.id);
			if (item == null) {
				// still show them - dropping someone who passed the filters is worse
				sp.score = 0;
				sp.reason = "The scorer did not return a verdict for this profile, so it is unranked. "
						+ "It still passed every objective filter.";
				sp.concerns.add("Not scored this round");
			} else {
				sp.score = Math.max(0, Math.min(100, item.score));
				sp.reason = (item.reason == null) ? "" : item.reason.trim();
				if (item.evidence != null) sp.evidence = item.evidence;
				if (item.concerns != null) sp.concerns = item.concerns;
			}
			results.add(sp);
		}

		Collections.sort(results, new Comparator<ScoredProfile>() {
			public int compare(ScoredProfile a, ScoredProfile b) {
				if (b.score != a.score) {
					return b.score - a.score;
				}
				return b.profile.years_experience - a.profile.years_experience;
			}
		});

		return results;
	}

	private String rubricAsText(Rubric rubric) {
		StringBuilder sb = new StringBuilder();
		for (Rubric.Criterion c : rubric.criteria) {
			sb.append("- ").append(c.name).append(" (weight ").append(c.weight).append("): ")
			  .append(c.description).append("\n");
		}
		if (!rubric.red_flags.isEmpty()) {
			sb.append("Red flags: ").append(String.join("; ", rubric.red_flags)).append("\n");
		}
		return sb.toString();
	}

	// numbered the same way the screen numbers them, so "2 and 4" lines up
	private String shownProfilesText(SearchSession session) {
		StringBuilder sb = new StringBuilder();
		int shown = Math.min(SHOWN_PER_ROUND, session.results.size());

		if (shown == 0) {
			return "(no profiles matched the current filters - the recruiter is looking at an empty result)";
		}

		for (int i = 0; i < shown; i++) {
			ScoredProfile sp = session.results.get(i);
			sb.append(i + 1).append(". [score ").append(sp.score).append("] ")
			  .append(Prompts.toLine(sp.profile)).append("\n");
		}
		return sb.toString();
	}

	private SearchSession requireSession(String sessionId) {
		SearchSession session = sessions.get(sessionId);
		if (session == null) {
			throw new IllegalArgumentException("Unknown session. Start a new search.");
		}
		return session;
	}

	static class ParseResult {
		SearchFilters filters;
		Rubric rubric;
	}

	static class ScoreResult {
		List<ScoreItem> scores;
	}

	static class ScoreItem {
		String id;
		int score;
		String reason;
		List<String> evidence;
		List<String> concerns;
	}

	static class RefineResult {
		String reply;
		List<ChangeNote> changes;
		SearchFilters filters;
		Rubric rubric;
	}

	static class StateSnapshot {
		SearchFilters filters;
		Rubric rubric;

		StateSnapshot(SearchFilters filters, Rubric rubric) {
			this.filters = filters;
			this.rubric = rubric;
		}
	}
}
