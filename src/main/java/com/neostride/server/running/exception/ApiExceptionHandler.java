package com.neostride.server.running.exception;

import com.neostride.server.auth.dto.LoginResponse;
import com.neostride.server.auth.dto.SignupResponse;
import com.neostride.server.auth.exception.DuplicateEmailException;
import com.neostride.server.auth.exception.InvalidCredentialsException;
import com.neostride.server.running.dto.RunningRecordResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<?> handleInvalidRequest(IllegalArgumentException exception, WebRequest request) {
		if (isAuthRequest(request)) {
			return ResponseEntity.badRequest().body(SignupResponse.error(exception.getMessage()));
		}
		return ResponseEntity.badRequest().body(RunningRecordResponse.error(exception.getMessage()));
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<SignupResponse> handleDuplicateEmail(DuplicateEmailException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(SignupResponse.error(exception.getMessage()));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<LoginResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(LoginResponse.error(exception.getMessage()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException exception, WebRequest request) {
		if (isAuthRequest(request)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(SignupResponse.error("이미 가입된 이메일입니다."));
		}
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(RunningRecordResponse.error("사용자 또는 플랜 정보를 확인할 수 없습니다."));
	}

	private boolean isAuthRequest(WebRequest request) {
		return request instanceof ServletWebRequest servletWebRequest
				&& servletWebRequest.getRequest().getRequestURI().startsWith("/api/auth");
	}
}
