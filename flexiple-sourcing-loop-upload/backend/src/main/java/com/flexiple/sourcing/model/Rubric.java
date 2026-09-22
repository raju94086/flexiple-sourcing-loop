package com.flexiple.sourcing.model;

import java.util.ArrayList;
import java.util.List;

public class Rubric {

	public String role_summary;
	public List<Criterion> criteria = new ArrayList<Criterion>();
	public List<String> red_flags = new ArrayList<String>();

	public static class Criterion {
		public String name;
		public String description;
		public int weight;
	}

	public void fillNulls() {
		if (role_summary == null) role_summary = "";
		if (criteria == null) criteria = new ArrayList<Criterion>();
		if (red_flags == null) red_flags = new ArrayList<String>();
	}
}
