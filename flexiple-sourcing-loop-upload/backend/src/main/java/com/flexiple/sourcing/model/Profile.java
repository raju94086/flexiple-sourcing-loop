package com.flexiple.sourcing.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Profile {

	public String id;
	public String name;
	public String current_title;
	public int years_experience;
	public String location;
	public String current_company;
	public String current_company_type;
	public List<String> skills;
	public List<PastCompany> past_companies;
	public String education;
	public String summary;

	public Set<String> allCompanyTypes() {
		Set<String> types = new HashSet<String>();
		if (current_company_type != null) {
			types.add(current_company_type.toLowerCase());
		}
		if (past_companies != null) {
			for (PastCompany pc : past_companies) {
				if (pc.company_type != null) {
					types.add(pc.company_type.toLowerCase());
				}
			}
		}
		return types;
	}
}
