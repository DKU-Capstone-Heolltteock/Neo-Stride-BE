package com.neostride.server.running.exception;

import com.neostride.server.running.dto.RunningRecordResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<RunningRecordResponse> handleInvalidRequest(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(RunningRecordResponse.error(exception.getMessage()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<RunningRecordResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(RunningRecordResponse.error("사용자 또는 플랜 정보를 확인할 수 없습니다."));
	}
}
