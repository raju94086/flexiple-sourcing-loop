package com.flexiple.sourcing.model;

import java.util.ArrayList;
import java.util.List;

public class ScoredProfile {

	public Profile profile;
	public int score;
	public String reason;
	public List<String> evidence = new ArrayList<String>();
	public List<String> concerns = new ArrayList<String>();
}
