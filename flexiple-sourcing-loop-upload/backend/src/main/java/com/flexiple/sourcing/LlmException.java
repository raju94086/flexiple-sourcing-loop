package com.flexiple.sourcing;

public class LlmException extends Exception {

	private static final long serialVersionUID = 1L;

	public final boolean retryable;

	public LlmException(String message, boolean retryable) {
		super(message);
		this.retryable = retryable;
	}

	public LlmException(String message, boolean retryable, Throwable cause) {
		super(message, cause);
		this.retryable = retryable;
	}
}
