package com.neostride.server.auth.exception;

public class AuthenticationRequiredException extends RuntimeException {
	public AuthenticationRequiredException(String message) {
		super(message);
	}
}
