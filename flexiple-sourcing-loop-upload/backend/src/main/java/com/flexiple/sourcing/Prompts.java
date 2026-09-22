package com.flexiple.sourcing;

import java.util.List;

import com.flexiple.sourcing.model.PastCompany;
import com.flexiple.sourcing.model.Profile;

/*
 * All three prompts and their response schemas.
 *   PARSE  - free text        -> filters + rubric
 *   SCORE  - filtered profiles -> score + reason + evidence
 *   REFINE - feedback          -> updated filters + rubric + what changed
 */
public class Prompts {

	private static final String RECORD_SHAPE =
		"Each candidate record has exactly these fields:\n" +
		"  id, name, current_title, years_experience (integer), location,\n" +
		"  current_company, current_company_type, skills (list),\n" +
		"  past_companies (list of company / company_type / title / years),\n" +
		"  education, summary\n" +
		"company_type is always one of: startup, scaleup, enterprise, agency.\n";

	public static final String PARSE_SYSTEM =
		"You are the sourcing engine inside an AI recruiter product.\n\n" +
		"A recruiter has typed one free-text sentence describing who they want to hire.\n" +
		"Turn it into two things:\n\n" +
		"1. OBJECTIVE FILTERS - hard, checkable facts that a plain database query could\n" +
		"   apply. These are applied literally against the records, so being too strict\n" +
		"   here silently deletes good people.\n" +
		"2. A FIT RUBRIC - what \"good\" looks like for this specific role. Another model\n" +
		"   uses it to score whoever survives the filters.\n\n" +
		RECORD_SHAPE + "\n" +
		"Rules for the filters:\n" +
		"- required_skills: ONLY skills the recruiter clearly treats as mandatory, and\n" +
		"  written in the dataset's vocabulary (\"AWS RDS\", \"PostgreSQL\", \"Node.js\").\n" +
		"  Two or three at most. If you are unsure whether a skill is mandatory, it is\n" +
		"  not - put it in preferred_skills instead.\n" +
		"- preferred_skills: everything else the request implies. Never filtered on; the\n" +
		"  rubric is what rewards them.\n" +
		"- min_years_experience / max_years_experience: if the recruiter gives a range,\n" +
		"  set BOTH ends of it. \"4-7 years\" means min 4 AND max 7 - dropping the max lets\n" +
		"  a 15-year candidate through a search that asked for mid-level. \"5+ years\" means\n" +
		"  min 5 and no max. A seniority word you can defend counts too (\"senior\" -> min 5).\n" +
		"  Leave both null when they say nothing about experience, and never invent a max\n" +
		"  they did not ask for.\n" +
		"- locations: city names as the recruiter said them.\n" +
		"- company_types: only from startup, scaleup, enterprise, agency. \"has worked at\n" +
		"  startups\" means startup experience anywhere in their history, not necessarily now.\n" +
		"- title_keywords: single words that appear in the TITLES vocabulary below, and\n" +
		"  only when the recruiter names a role the titles can express. Leave empty if\n" +
		"  the skills already imply it.\n\n" +
		"THE MOST IMPORTANT RULE: never write a filter value the talent map does not\n" +
		"contain. The vocabulary below is the whole map. If the recruiter says\n" +
		"\"developers\" and every title says \"Engineer\", use \"Engineer\" or leave\n" +
		"title_keywords empty - do not filter on a word nobody's record contains. A\n" +
		"filter that matches nobody is worse than no filter at all, because the recruiter\n" +
		"cannot see what it deleted.\n\n" +
		"Rules for the rubric:\n" +
		"- role_summary: one sentence a recruiter would nod at.\n" +
		"- criteria: 3 to 5. Each must be checkable against the fields above - depth in a\n" +
		"  skill, relevance of past companies, seniority trajectory, domain match.\n" +
		"  Weights are integers adding up to 100.\n" +
		"- red_flags: 1 to 3 concrete disqualifiers for this particular role.\n\n" +
		"Return JSON only.";

	public static String parseUser(String query, String vocabulary) {
		return vocabulary + "\nRecruiter's request:\n\"" + query + "\"";
	}

	public static final String SCORE_SYSTEM =
		"You are scoring candidate profiles against a recruiter's fit rubric.\n\n" +
		"For every candidate you are given, return:\n" +
		"- score: 0-100 against the rubric, weighted the way the rubric says. Use the\n" +
		"  whole range. Someone who passed the filters but is mediocre against the rubric\n" +
		"  belongs in the 40s, not the 80s. Do not cluster every score together.\n" +
		"- reason: one or two sentences, 40 words maximum, saying why this person fits or\n" +
		"  does not. It MUST quote concrete values from their record - a real skill, a real\n" +
		"  company name, their actual years_experience number, their actual title. Generic\n" +
		"  praise like \"strong engineer\" or \"great background\" is not acceptable.\n" +
		"- evidence: 2 to 4 very short chips lifted straight from the record, for example\n" +
		"  \"6 yrs\", \"AWS RDS\", \"NimbusPay (startup)\", \"Bangalore\".\n" +
		"- concerns: 0 to 2 short honest gaps against the rubric. Empty list if none.\n\n" +
		"The `id` field is how we join your answer back to our records - use each record's\n" +
		"exact id there. Never write an id anywhere a recruiter reads it. \"Like p03, his\n" +
		"title is...\" is meaningless to them; they see names. Judge each candidate on\n" +
		"their own record rather than comparing them to each other.\n\n" +
		"Score every candidate. Return JSON only.";

	public static String scoreUser(String roleSummary, String rubricText, List<Profile> profiles) {
		StringBuilder sb = new StringBuilder();
		sb.append("ROLE: ").append(roleSummary).append("\n\n");
		sb.append("FIT RUBRIC:\n").append(rubricText).append("\n\n");
		sb.append("CANDIDATES (").append(profiles.size()).append("):\n");
		for (Profile p : profiles) {
			sb.append(toLine(p)).append("\n");
		}
		return sb.toString();
	}

	public static final String REFINE_SYSTEM =
		"You are refining a live candidate search based on what the recruiter just said.\n\n" +
		"You get: the original request, the filters and rubric currently in force, the\n" +
		"profiles the recruiter is looking at right now (numbered exactly as they appear\n" +
		"on screen), everything they said in earlier rounds, and their newest message.\n\n" +
		"Your job:\n" +
		"1. Work out what the feedback says about the ROLE, not just about those\n" +
		"   individuals. \"1 is too junior\" is a statement about the experience bar.\n" +
		"   \"2 and 4 are right\" means whatever 2 and 4 have in common is what good looks\n" +
		"   like - reinforce that in the rubric.\n" +
		"2. Return the updated filters and rubric IN FULL. Not a diff - complete objects,\n" +
		"   carrying forward everything the recruiter has not contradicted.\n" +
		"3. Fill `changes` with one entry per real edit, showing the before and after\n" +
		"   value. If you genuinely changed nothing, return an empty list.\n" +
		"4. Write `reply`: two sentences maximum, plain English, spoken to the recruiter.\n" +
		"   Say what you changed and why. No preamble, no \"Certainly!\".\n\n" +
		"Hard rules:\n" +
		"- `changes` must describe the objects you actually returned. Never claim an edit\n" +
		"  you did not make, and never make a silent one.\n" +
		"- Prefer moving a bar in the rubric over adding a hard filter. Hard filters delete\n" +
		"  people from the search permanently; the rubric only re-ranks them.\n" +
		"- Respect earlier feedback. Round 3 must not quietly undo round 1.\n" +
		"- If the message is vague, make the smallest defensible change and say so in the\n" +
		"  reply rather than guessing big.\n" +
		"- Never write a filter value the talent map does not contain. The vocabulary\n" +
		"  below is the whole map, and a filter matching nobody empties the screen.\n\n" +
		RECORD_SHAPE + "\n" +
		"Return JSON only.";

	public static String refineUser(String query, String vocabulary, String currentState,
			String shownProfiles, List<String> earlierFeedback, String newFeedback) {

		StringBuilder sb = new StringBuilder();
		sb.append(vocabulary).append("\n");
		sb.append("ORIGINAL REQUEST:\n\"").append(query).append("\"\n\n");
		sb.append("FILTERS AND RUBRIC IN FORCE:\n").append(currentState).append("\n\n");
		sb.append("PROFILES ON SCREEN RIGHT NOW:\n").append(shownProfiles).append("\n");

		if (earlierFeedback != null && !earlierFeedback.isEmpty()) {
			sb.append("\nWHAT THE RECRUITER SAID IN EARLIER ROUNDS (oldest first):\n");
			for (int i = 0; i < earlierFeedback.size(); i++) {
				sb.append("  round ").append(i + 1).append(": ").append(earlierFeedback.get(i)).append("\n");
			}
		}

		sb.append("\nNEWEST MESSAGE FROM THE RECRUITER:\n\"").append(newFeedback).append("\"");
		return sb.toString();
	}

	// one line per profile - cheaper than JSON and the model reads it fine
	public static String toLine(Profile p) {
		StringBuilder sb = new StringBuilder();
		sb.append(p.id).append(" | ").append(p.name);
		sb.append(" | ").append(p.current_title);
		sb.append(" | ").append(p.years_experience).append(" yrs");
		sb.append(" | ").append(p.location);
		sb.append(" | now: ").append(p.current_company).append(" (").append(p.current_company_type).append(")");
		sb.append(" | skills: ").append(p.skills == null ? "" : String.join(", ", p.skills));

		sb.append(" | past: ");
		if (p.past_companies == null || p.past_companies.isEmpty()) {
			sb.append("none");
		} else {
			for (int i = 0; i < p.past_companies.size(); i++) {
				PastCompany pc = p.past_companies.get(i);
				if (i > 0) sb.append("; ");
				sb.append(pc.company).append(" (").append(pc.company_type)
				  .append(", ").append(pc.title).append(", ").append(pc.years).append("y)");
			}
		}

		sb.append(" | edu: ").append(p.education);
		sb.append(" | summary: ").append(p.summary);
		return sb.toString();
	}

	private static final String FILTERS_SCHEMA =
		"{\"type\":\"OBJECT\",\"properties\":{" +
		"\"required_skills\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"STRING\"}}," +
		"\"preferred_skills\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"STRING\"}}," +
		"\"min_years_experience\":{\"type\":\"INTEGER\",\"nullable\":true}," +
		"\"max_years_experience\":{\"type\":\"INTEGER\",\"nullable\":true}," +
		"\"locations\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"STRING\"}}," +
		"\"company_types\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"STRING\"}}," +
		"\"title_keywords\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"STRING\"}}" +
		"},\"required\":[\"required_skills\",\"preferred_skills\",\"locations\",\"company_types\",\"title_keywords\"]}";

	private static final String RUBRIC_SCHEMA =
		"{\"type\":\"OBJECT\",\"properties\":{" +
		"\"role_summary\":{\"type\":\"STRING\"}," +
		"\"criteria\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"OBJECT\",\"properties\":{" +
			"\"name\":{\"type\":\"STRING\"}," +
			"\"description\":{\"type\":\"STRING\"}," +
			"\"weight\":{\"type\":\"INTEGER\"}" +
		"},\"required\":[\"name\",\"description\",\"weight\"]}}," +
		"\"red_flags\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"STRING\"}}" +
		"},\"required\":[\"role_summary\",\"criteria\",\"red_flags\"]}";

	public static final String PARSE_SCHEMA =
		"{\"type\":\"OBJECT\",\"properties\":{" +
		"\"filters\":" + FILTERS_SCHEMA + "," +
		"\"rubric\":" + RUBRIC_SCHEMA +
		"},\"required\":[\"filters\",\"rubric\"]}";

	public static final String SCORE_SCHEMA =
		"{\"type\":\"OBJECT\",\"properties\":{" +
		"\"scores\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"OBJECT\",\"properties\":{" +
			"\"id\":{\"type\":\"STRING\"}," +
			"\"score\":{\"type\":\"INTEGER\"}," +
			"\"reason\":{\"type\":\"STRING\"}," +
			"\"evidence\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"STRING\"}}," +
			"\"concerns\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"STRING\"}}" +
		"},\"required\":[\"id\",\"score\",\"reason\",\"evidence\",\"concerns\"]}}" +
		"},\"required\":[\"scores\"]}";

	public static final String REFINE_SCHEMA =
		"{\"type\":\"OBJECT\",\"properties\":{" +
		"\"reply\":{\"type\":\"STRING\"}," +
		"\"changes\":{\"type\":\"ARRAY\",\"items\":{\"type\":\"OBJECT\",\"properties\":{" +
			"\"field\":{\"type\":\"STRING\"}," +
			"\"change\":{\"type\":\"STRING\"}," +
			"\"why\":{\"type\":\"STRING\"}" +
		"},\"required\":[\"field\",\"change\",\"why\"]}}," +
		"\"filters\":" + FILTERS_SCHEMA + "," +
		"\"rubric\":" + RUBRIC_SCHEMA +
		"},\"required\":[\"reply\",\"changes\",\"filters\",\"rubric\"]}";
}
