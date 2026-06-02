package com.neostride.server.coaching.service;

import com.neostride.server.coaching.ai.AiCoachingFeedbackRequest;
import com.neostride.server.coaching.dto.FeedbackRequest;
import com.neostride.server.coaching.repository.PlanDayRow;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class CoachingFeedbackPolicy {
	AiCoachingFeedbackRequest aiRequest(long planDayId, FeedbackRequest request, PlanDayRow planDay) {
		return new AiCoachingFeedbackRequest(
				planDayId,
				planDay.planDate(),
				planDay.targetDistance(),
				planDay.targetPace(),
				delta(request.actualDistanceKm(), planDay.targetDistance()),
				paceDelta(request.actualPaceSecPerKm(), planDay.targetPace()),
				request
		);
	}

	String fallbackFeedback(FeedbackRequest request, PlanDayRow planDay) {
		BigDecimal distanceDelta = delta(request.actualDistanceKm(), planDay.targetDistance());
		Integer paceDelta = paceDelta(request.actualPaceSecPerKm(), planDay.targetPace());
		return "평균 페이스 " + formatPace(request.actualPaceSecPerKm())
				+ "/km로 마무리했습니다. " + distanceDeltaText(distanceDelta) + ", " + paceDeltaText(paceDelta)
				+ " 다음 훈련 전에는 가벼운 스트레칭과 수분 보충으로 회복을 챙기세요.";
	}

	String fallbackFeedback(FeedbackRequest request) {
		return "러닝을 완료했습니다. " + request.actualDistanceKm().setScale(2, RoundingMode.HALF_UP)
				+ "km를 평균 페이스 " + formatPace(request.actualPaceSecPerKm())
				+ "/km로 마무리했으니, 다음 훈련 전에는 가벼운 스트레칭과 수분 보충으로 회복을 챙기세요.";
	}

	String formatPace(Integer seconds) {
		if (seconds == null) {
			return "-";
		}
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}

	BigDecimal delta(BigDecimal actual, BigDecimal target) {
		if (actual == null || target == null) {
			return null;
		}
		return actual.subtract(target).setScale(2, RoundingMode.HALF_UP);
	}

	Integer paceDelta(Integer actual, Integer target) {
		if (actual == null || target == null) {
			return null;
		}
		return actual - target;
	}

	private String distanceDeltaText(BigDecimal distanceDelta) {
		if (distanceDelta == null || distanceDelta.compareTo(BigDecimal.ZERO) == 0) {
			return "목표 거리와 동일한 거리를 달렸고";
		}
		String amount = distanceDelta.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
		return distanceDelta.compareTo(BigDecimal.ZERO) > 0
				? "목표보다 " + amount + "km 더 달렸고"
				: "목표보다 " + amount + "km 부족했고";
	}

	private String paceDeltaText(Integer paceDelta) {
		if (paceDelta == null || Math.abs(paceDelta) <= 3) {
			return "목표 페이스에 가깝게 마무리했습니다.";
		}
		String amount = formatDuration(Math.abs(paceDelta));
		return paceDelta > 0
				? "목표보다 " + amount + "/km 느렸습니다."
				: "목표보다 " + amount + "/km 빨랐습니다.";
	}

	private String formatDuration(int seconds) {
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}
}
