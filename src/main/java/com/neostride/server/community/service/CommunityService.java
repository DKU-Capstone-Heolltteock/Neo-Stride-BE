package com.neostride.server.community.service;

import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.AccountInfoResponse;
import com.neostride.server.community.dto.TipListResponse;
import com.neostride.server.community.dto.TipUploadRequest;
import com.neostride.server.community.dto.TipUploadResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.community.repository.CommunityRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CommunityService {
	private final CommunityRepository repository;
	public CommunityService(CommunityRepository repository) { this.repository = repository; }
	public UserProfileResponse getUserProfile(long userId) { validatePositive(userId, "user_id"); return repository.getUserProfile(userId); }
	public AccountInfoResponse getAccountInfo(long userId) { validatePositive(userId, "user_id"); return repository.getAccountInfo(userId); }
	public void updateStatusMessage(long userId, Map<String, String> body) { validatePositive(userId, "user_id"); requireBody(body); repository.updateStatusMessage(userId, body.get("status_message")); }
	public void updateNickname(long userId, Map<String, String> body) { validatePositive(userId, "user_id"); requireBody(body); repository.updateNickname(userId, body.get("nickname")); }
	public void deleteAccount(long userId) { validatePositive(userId, "user_id"); repository.deleteAccount(userId); }
	public void updateProfileImage(long userId, String profileImageUrl) { validatePositive(userId, "user_id"); repository.updateProfileImage(userId, profileImageUrl); }
	public List<CommunityContentResponse> getMyFeeds(long userId) { validatePositive(userId, "user_id"); return repository.myFeeds(userId); }
	public List<CommunityContentResponse> getTaggedFeeds(long userId) { validatePositive(userId, "user_id"); return repository.taggedFeeds(userId); }
	public List<CommunityContentResponse> getCommentedFeeds(long userId) { validatePositive(userId, "user_id"); return repository.interactedFeeds(userId, "COMMENT"); }
	public List<CommunityContentResponse> getLikedFeeds(long userId) { validatePositive(userId, "user_id"); return repository.interactedFeeds(userId, "LIKE"); }
	public List<CommunityContentResponse> getBookmarkedFeeds(long userId) { validatePositive(userId, "user_id"); return repository.interactedFeeds(userId, "BOOKMARK"); }
	public List<CommunityContentResponse> getUserFeeds(long userId) { validatePositive(userId, "user_id"); return repository.myFeeds(userId); }
	public Map<String, String> toggleBookmark(long userId, long contentId) { validatePositive(userId, "user_id"); validatePositive(contentId, "content_id"); boolean bookmarked = repository.toggleBookmark(userId, contentId); return Map.of("status", "success", "bookmarked", String.valueOf(bookmarked)); }
	public BadgeDetailResponse getBadgeDetail(long userId) { validatePositive(userId, "user_id"); return repository.getBadgeDetail(userId); }
	public List<FriendResponse> getFriendList(long userId, String status) { validatePositive(userId, "user_id"); return repository.getFriendList(userId, status); }
	public List<FriendResponse> getUserFriendList(long userId) { validatePositive(userId, "user_id"); return repository.getFriendList(userId, "friends"); }
	public Map<String, String> updateRelationship(long userId, FriendRequest request) { validatePositive(userId, "user_id"); requireBody(request); repository.updateRelationship(userId, request); return Map.of("status", "success", "message", "관계 상태가 변경되었습니다."); }
	public FeedUploadResponse uploadFeed(long userId, FeedUploadRequest request) { validatePositive(userId, "user_id"); requireBody(request); long id = repository.insertFeed(userId, request); return repository.findFeed(id); }
	public List<FeedUploadResponse> getFeedList(long userId) { validatePositive(userId, "user_id"); return repository.listFeeds(); }
	public TipUploadResponse uploadTip(long userId, TipUploadRequest request) { validatePositive(userId, "user_id"); requireBody(request); long id = repository.insertTip(userId, request); return repository.findTip(id); }
	public TipListResponse getTips() { return new TipListResponse(repository.listTips()); }

	private void requireBody(Object body) {
		if (body == null) {
			throw new IllegalArgumentException("요청 본문이 필요합니다.");
		}
	}

	private void validatePositive(long value, String fieldName) {
		if (value <= 0) {
			throw new IllegalArgumentException(fieldName + "는 1 이상의 값이어야 합니다.");
		}
	}
}
