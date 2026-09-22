package com.flexiple.sourcing;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.flexiple.sourcing.model.Profile;
import com.flexiple.sourcing.model.SearchFilters;
import com.google.gson.Gson;

public class ProfileStore {

	private final List<Profile> profiles;

	public ProfileStore() {
		this.profiles = load();
		System.out.println("[store] loaded " + profiles.size() + " profiles");
	}

	private List<Profile> load() {
		InputStream in = getClass().getClassLoader().getResourceAsStream("profiles.json");
		if (in == null) {
			throw new IllegalStateException("profiles.json not found on the classpath");
		}
		InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
		Profile[] parsed = new Gson().fromJson(reader, Profile[].class);

		List<Profile> list = new ArrayList<Profile>();
		for (Profile p : parsed) {
			list.add(p);
		}
		return list;
	}

	public int size() {
		return profiles.size();
	}

	/*
	 * Handed to the model so it writes filters the data can actually satisfy.
	 * Without this it produced title_keywords ["Developer"] for "RDS developers",
	 * and every record here says "Engineer" - the search came back empty.
	 */
	public String vocabularyText() {
		StringBuilder sb = new StringBuilder();
		sb.append("The talent map contains only these values. Write filters using this vocabulary.\n");
		sb.append("TITLES: ").append(String.join(", ", distinctTitles())).append("\n");
		sb.append("LOCATIONS: ").append(String.join(", ", distinctLocations())).append("\n");
		sb.append("SKILLS: ").append(String.join(", ", distinctSkills())).append("\n");
		return sb.toString();
	}

	private TreeSet<String> distinctTitles() {
		TreeSet<String> values = new TreeSet<String>();
		for (Profile p : profiles) {
			if (p.current_title != null) {
				values.add(p.current_title);
			}
		}
		return values;
	}

	private TreeSet<String> distinctLocations() {
		TreeSet<String> values = new TreeSet<String>();
		for (Profile p : profiles) {
			if (p.location != null) {
				values.add(p.location);
			}
		}
		return values;
	}

	private TreeSet<String> distinctSkills() {
		TreeSet<String> values = new TreeSet<String>();
		for (Profile p : profiles) {
			if (p.skills != null) {
				values.addAll(p.skills);
			}
		}
		return values;
	}

	public List<Profile> filter(SearchFilters f) {
		f.fillNulls();
		List<Profile> matched = new ArrayList<Profile>();

		for (Profile p : profiles) {
			if (matches(p, f)) {
				matched.add(p);
			}
		}
		return matched;
	}

	private boolean matches(Profile p, SearchFilters f) {

		for (String required : f.required_skills) {
			if (!hasSkill(p, required)) {
				return false;
			}
		}

		if (f.min_years_experience != null && p.years_experience < f.min_years_experience) {
			return false;
		}
		if (f.max_years_experience != null && p.years_experience > f.max_years_experience) {
			return false;
		}

		if (!f.locations.isEmpty() && !anyContains(f.locations, p.location)) {
			return false;
		}

		// company type counts if it shows up anywhere in their history, not just now
		if (!f.company_types.isEmpty()) {
			Set<String> theirs = p.allCompanyTypes();
			boolean hit = false;
			for (String wanted : f.company_types) {
				if (wanted != null && theirs.contains(wanted.trim().toLowerCase())) {
					hit = true;
					break;
				}
			}
			if (!hit) {
				return false;
			}
		}

		if (!f.title_keywords.isEmpty() && !anyContains(f.title_keywords, p.current_title)) {
			return false;
		}

		return true;
	}

	// loose both ways so "RDS" finds "AWS RDS" and "AWS RDS" finds "RDS"
	private boolean hasSkill(Profile p, String wanted) {
		if (wanted == null || wanted.trim().isEmpty() || p.skills == null) {
			return true;
		}
		String w = wanted.trim().toLowerCase();

		for (String skill : p.skills) {
			if (skill == null) continue;
			String s = skill.toLowerCase();
			if (s.contains(w) || w.contains(s)) {
				return true;
			}
		}
		return false;
	}

	private boolean anyContains(List<String> needles, String haystack) {
		if (haystack == null) {
			return false;
		}
		String h = haystack.toLowerCase();
		for (String needle : needles) {
			if (needle != null && !needle.trim().isEmpty() && h.contains(needle.trim().toLowerCase())) {
				return true;
			}
		}
		return false;
	}
}
