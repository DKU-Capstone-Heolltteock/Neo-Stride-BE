package com.neostride.server.auth.controller;

import com.neostride.server.auth.dto.LoginRequest;
import com.neostride.server.auth.dto.LoginResponse;
import com.neostride.server.auth.dto.SignupRequest;
import com.neostride.server.auth.dto.SignupResponse;
import com.neostride.server.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증/회원 API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@Operation(summary = "회원가입", description = "이메일, 이름, 비밀번호를 입력받아 사용자를 생성합니다. 비밀번호는 PBKDF2-SHA256 해시로 저장됩니다.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			required = true,
			description = "회원가입 요청",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = SignupRequest.class),
					examples = @ExampleObject(
							name = "회원가입 요청 예시",
							value = """
									{
									  "email": "runner@example.com",
									  "name": "홍길동",
									  "password": "plain-password"
									}
									"""
					)
			)
	)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "회원가입 성공", content = @Content(schema = @Schema(implementation = SignupResponse.class))),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류", content = @Content(schema = @Schema(implementation = SignupResponse.class))),
			@ApiResponse(responseCode = "409", description = "이미 가입된 이메일", content = @Content(schema = @Schema(implementation = SignupResponse.class)))
	})
	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
	}

	@Operation(summary = "로그인", description = "이메일과 비밀번호를 검증하고 JWT access token과 refresh token을 발급합니다.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			required = true,
			description = "로그인 요청",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = LoginRequest.class),
					examples = @ExampleObject(
							name = "로그인 요청 예시",
							value = """
									{
									  "email": "runner@example.com",
									  "password": "plain-password"
									}
									"""
					)
			)
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
			@ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치", content = @Content(schema = @Schema(implementation = LoginResponse.class)))
	})
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}
}
