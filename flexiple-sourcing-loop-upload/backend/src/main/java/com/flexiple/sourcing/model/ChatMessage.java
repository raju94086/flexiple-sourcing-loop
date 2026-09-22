package com.flexiple.sourcing.model;

import java.util.ArrayList;
import java.util.List;

public class ChatMessage {

	public String role; // recruiter | app
	public String text;
	public List<ChangeNote> changes = new ArrayList<ChangeNote>();

	public ChatMessage(String role, String text) {
		this.role = role;
		this.text = text;
	}
}
