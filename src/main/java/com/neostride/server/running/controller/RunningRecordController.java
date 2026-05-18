package com.neostride.server.running.controller;

import com.neostride.server.auth.service.AuthenticatedUserService;
import com.neostride.server.running.dto.RunningRecordRequest;
import com.neostride.server.running.dto.RunningRecordResponse;
import com.neostride.server.running.service.RunningRecordService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Running Records", description = "러닝 기록 API")
@RestController
@RequestMapping("/api/running/records")
public class RunningRecordController {

	private final RunningRecordService runningRecordService;
	private final AuthenticatedUserService authenticatedUserService;

	public RunningRecordController(RunningRecordService runningRecordService, AuthenticatedUserService authenticatedUserService) {
		this.runningRecordService = runningRecordService;
		this.authenticatedUserService = authenticatedUserService;
	}

	@Operation(summary = "러닝 기록 저장", description = "러닝 측정 결과와 GPS 좌표 목록을 저장합니다.")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(
			required = true,
			description = "러닝 측정 결과 저장 요청",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = RunningRecordRequest.class),
					examples = @ExampleObject(
							name = "러닝 기록 저장 요청 예시",
							value = """
									{
									  "user_id": 1,
									  "plan_id": null,
									  "total_distance": 3.25,
									  "duration": 1240,
									  "pace": 6.36,
									  "calories": 235.69,
									  "route_detail": "",
									  "gps_traces": [
									    {
									      "latitude": 37.5665,
									      "longitude": 126.978,
									      "time": "2026-04-28 09:30:12",
									      "heart_rate": 150.0,
									      "cadence": 171.0
									    }
									  ]
									}
									"""
					)
			)
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "201",
					description = "러닝 기록 저장 성공",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = RunningRecordResponse.class),
							examples = @ExampleObject(
									name = "성공 응답 예시",
									value = """
											{
											  "status": "success",
											  "message": "러닝 기록이 저장되었습니다.",
											  "run_record_id": 10
											}
											"""
							)
					)
			),
			@ApiResponse(responseCode = "400", description = "요청 형식 오류", content = @Content),
			@ApiResponse(responseCode = "409", description = "사용자 또는 플랜 FK 오류", content = @Content)
	})
	@PostMapping
	public ResponseEntity<RunningRecordResponse> saveRunningRecord(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody RunningRecordRequest request
	) {
		long authenticatedUserId = authenticatedUserService.requireUserId(authorization);
		authenticatedUserService.requireSameUser(authenticatedUserId, request == null ? null : request.userId(), "user_id");
		long runRecordId = runningRecordService.save(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(RunningRecordResponse.success("러닝 기록이 저장되었습니다.", runRecordId));
	}

	@Operation(summary = "유저별 전체 러닝 기록 조회", description = "특정 유저의 전체 러닝 기록 목록을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping("/user/{user_id}")
	public ResponseEntity<List<RunningRecordResponse>> fetchUserRecords(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable("user_id") long userId
	) {
		long authenticatedUserId = authenticatedUserService.requireUserId(authorization);
		authenticatedUserService.requireSameUser(authenticatedUserId, userId, "user_id");
		return ResponseEntity.ok(runningRecordService.findByUserId(userId));
	}

	@Operation(summary = "월별 러닝 기록 조회", description = "특정 연/월의 러닝 기록 목록을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@GetMapping
	public ResponseEntity<List<RunningRecordResponse>> getMonthlyRecords(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam int year,
			@RequestParam int month
	) {
		long authenticatedUserId = authenticatedUserService.requireUserId(authorization);
		return ResponseEntity.ok(runningRecordService.findByUserIdAndMonth(authenticatedUserId, year, month));
	}

	@Operation(summary = "러닝 기록 상세 조회", description = "특정 러닝 기록의 상세 정보와 GPS 경로를 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "404", description = "러닝 기록 없음", content = @Content)
	})
	@GetMapping("/{record_id}")
	public ResponseEntity<RunningRecordResponse> getRecordDetail(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable("record_id") long recordId
	) {
		long authenticatedUserId = authenticatedUserService.requireUserId(authorization);
		return runningRecordService.findByRecordIdForUser(authenticatedUserId, recordId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
