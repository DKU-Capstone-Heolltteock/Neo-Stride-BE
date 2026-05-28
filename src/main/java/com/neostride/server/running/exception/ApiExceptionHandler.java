package com.neostride.server.running.exception;

import com.neostride.server.auth.dto.LoginResponse;
import com.neostride.server.auth.dto.SignupResponse;
import com.neostride.server.auth.exception.AuthenticationRequiredException;
import com.neostride.server.auth.exception.DuplicateEmailException;
import com.neostride.server.auth.exception.DuplicateUserFieldException;
import com.neostride.server.auth.exception.ForbiddenException;
import com.neostride.server.auth.exception.InvalidCredentialsException;
import com.neostride.server.running.dto.RunningRecordResponse;
import java.util.Map;
import org.springframework.dao.DataAccessException;
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
		return ResponseEntity.badRequest().body(errorBody(request, exception.getMessage()));
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<SignupResponse> handleDuplicateEmail(DuplicateEmailException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(SignupResponse.error(exception.getMessage()));
	}

	@ExceptionHandler(DuplicateUserFieldException.class)
	public ResponseEntity<?> handleDuplicateUserField(DuplicateUserFieldException exception, WebRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(request, exception.getMessage()));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<LoginResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(LoginResponse.error(exception.getMessage()));
	}

	@ExceptionHandler(AuthenticationRequiredException.class)
	public ResponseEntity<?> handleAuthenticationRequired(AuthenticationRequiredException exception, WebRequest request) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(request, exception.getMessage()));
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<?> handleForbidden(ForbiddenException exception, WebRequest request) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(request, exception.getMessage()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException exception, WebRequest request) {
		DuplicateUserFieldException duplicateUserField = duplicateUserField(exception);
		if (duplicateUserField != null) {
			return handleDuplicateUserField(duplicateUserField, request);
		}
		if (isAuthRequest(request)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(SignupResponse.error("이미 가입된 이메일입니다."));
		}
		if (isCoachingRequest(request)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(Map.of("status", "error", "message", "사용자 또는 목표 정보를 확인할 수 없습니다."));
		}
		if (isCommunityRequest(request)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(Map.of("status", "error", "message", "사용자 또는 커뮤니티 정보를 확인할 수 없습니다."));
		}
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(RunningRecordResponse.error("사용자 또는 플랜 정보를 확인할 수 없습니다."));
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<?> handleDataAccessException(DataAccessException exception, WebRequest request) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(errorBody(request, "데이터베이스 작업을 완료할 수 없습니다."));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<?> handleIllegalStateException(IllegalStateException exception, WebRequest request) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(errorBody(request, "서버 처리 중 오류가 발생했습니다."));
	}

	private Object errorBody(WebRequest request, String message) {
		if (isAuthLoginRequest(request)) {
			return LoginResponse.error(message);
		}
		if (isAuthRequest(request)) {
			return SignupResponse.error(message);
		}
		if (isRunningRequest(request)) {
			return RunningRecordResponse.error(message);
		}
		return Map.of("status", "error", "message", message);
	}

	private boolean isAuthLoginRequest(WebRequest request) {
		return requestUri(request).startsWith("/api/auth/login");
	}

	private boolean isAuthRequest(WebRequest request) {
		return requestUri(request).startsWith("/api/auth");
	}

	private boolean isCoachingRequest(WebRequest request) {
		return requestUri(request).startsWith("/api/coaching");
	}

	private boolean isCommunityRequest(WebRequest request) {
		return requestUri(request).startsWith("/api/community");
	}

	private boolean isRunningRequest(WebRequest request) {
		return requestUri(request).startsWith("/api/running");
	}

	private DuplicateUserFieldException duplicateUserField(DataIntegrityViolationException exception) {
		String message = exception.getMostSpecificCause() == null
				? exception.getMessage()
				: exception.getMostSpecificCause().getMessage();
		if (message == null) {
			return null;
		}
		String normalized = message.toLowerCase();
		if (normalized.contains("uq_users_email") || normalized.contains(" for key 'email'") || normalized.contains("for key 'users.email'")) {
			return DuplicateUserFieldException.email();
		}
		if (normalized.contains("uq_users_name")) {
			return DuplicateUserFieldException.name();
		}
		if (normalized.contains("uq_users_community_profile_name") || normalized.contains("uq_community_users_community_profile_name")) {
			return DuplicateUserFieldException.nickname();
		}
		return null;
	}

	private String requestUri(WebRequest request) {
		if (request instanceof ServletWebRequest servletWebRequest) {
			return servletWebRequest.getRequest().getRequestURI();
		}
		return "";
	}
}
