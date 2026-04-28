package com.neostride.server.running.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Running Records", description = "러닝 기록 API")
@RestController
@RequestMapping("/api/running/records")
public class RunningRecordController {

	private final RunningRecordService runningRecordService;

	public RunningRecordController(RunningRecordService runningRecordService) {
		this.runningRecordService = runningRecordService;
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
									  "pace": 360,
									  "calories": 235,
									  "gps_traces": [
									    {
									      "latitude": 37.5665,
									      "longitude": 126.978,
									      "time": "2026-04-28 09:30:12"
									    }
									  ]
									}
									""",
							description = """
									  "total_distance": 3.25, 	(소수점 둘째 자리까지 저장, 단위: km)
									  "duration": 1240, 		(단위: 초, 예시: 5분 30초 > 330)
									  "pace": 360,				(단위: 초, 예시: 5분 30초 > 330)
									  "calories": 235,			(데이터베이스에 정수 단위로 저장, 단위: kcal(킬로칼로리))
									  "gps_traces": [			(gps_trace 테이블에 저장할 경로 정보의 배열)
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
	public ResponseEntity<RunningRecordResponse> saveRunningRecord(@RequestBody RunningRecordRequest request) {
		long runRecordId = runningRecordService.save(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(RunningRecordResponse.success("러닝 기록이 저장되었습니다.", runRecordId));
	}
}
