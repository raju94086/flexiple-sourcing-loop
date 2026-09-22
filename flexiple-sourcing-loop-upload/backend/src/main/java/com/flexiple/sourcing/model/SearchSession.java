package com.flexiple.sourcing.model;

import java.util.ArrayList;
import java.util.List;

public class SearchSession {

	public String session_id;
	public String query;

	public SearchFilters filters;
	public Rubric rubric;

	public List<ScoredProfile> results = new ArrayList<ScoredProfile>();

	public int total_profiles;
	public int matched_count;

	public int round;
	public boolean frozen;

	public List<ChatMessage> chat = new ArrayList<ChatMessage>();

	// replayed into every refine prompt so later rounds don't undo earlier ones
	public transient List<String> feedback_history = new ArrayList<String>();
}
