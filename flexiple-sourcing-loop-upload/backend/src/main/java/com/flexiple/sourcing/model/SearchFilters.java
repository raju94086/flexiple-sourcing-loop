package com.flexiple.sourcing.model;

import java.util.ArrayList;
import java.util.List;

public class SearchFilters {

	public List<String> required_skills = new ArrayList<String>();
	public List<String> preferred_skills = new ArrayList<String>();

	public Integer min_years_experience;
	public Integer max_years_experience;

	public List<String> locations = new ArrayList<String>();
	public List<String> company_types = new ArrayList<String>();
	public List<String> title_keywords = new ArrayList<String>();

	// Gson leaves a list null if the model skips the key
	public void fillNulls() {
		if (required_skills == null) required_skills = new ArrayList<String>();
		if (preferred_skills == null) preferred_skills = new ArrayList<String>();
		if (locations == null) locations = new ArrayList<String>();
		if (company_types == null) company_types = new ArrayList<String>();
		if (title_keywords == null) title_keywords = new ArrayList<String>();
	}
}
