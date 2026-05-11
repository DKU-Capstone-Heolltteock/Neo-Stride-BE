package com.neostride.server.community.service;

import com.neostride.server.community.dto.BadgeDetailResponse;
import com.neostride.server.community.dto.CommunityContentResponse;
import com.neostride.server.community.dto.FeedUploadRequest;
import com.neostride.server.community.dto.FeedUploadResponse;
import com.neostride.server.community.dto.FriendRequest;
import com.neostride.server.community.dto.FriendResponse;
import com.neostride.server.community.dto.UserProfileResponse;
import com.neostride.server.community.repository.CommunityRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CommunityService {
	private final CommunityRepository repository;
	public CommunityService(CommunityRepository repository) { this.repository = repository; }
	public UserProfileResponse getUserProfile(long userId) { return repository.getUserProfile(userId); }
	public void updateStatusMessage(long userId, Map<String, String> body) { repository.updateStatusMessage(userId, body == null ? null : body.get("status_message")); }
	public void updateProfileImage(long userId, String profileImageUrl) { repository.updateProfileImage(userId, profileImageUrl); }
	public List<CommunityContentResponse> getMyFeeds(long userId) { return repository.myFeeds(userId); }
	public List<CommunityContentResponse> getTaggedFeeds(long userId) { return repository.taggedFeeds(userId); }
	public List<CommunityContentResponse> getCommentedFeeds(long userId) { return repository.interactedFeeds(userId, "COMMENT"); }
	public List<CommunityContentResponse> getLikedFeeds(long userId) { return repository.interactedFeeds(userId, "LIKE"); }
	public List<CommunityContentResponse> getBookmarkedFeeds(long userId) { return repository.interactedFeeds(userId, "BOOKMARK"); }
	public BadgeDetailResponse getBadgeDetail(long userId) { return repository.getBadgeDetail(userId); }
	public List<FriendResponse> getFriendList(long userId, String status) { return repository.getFriendList(userId, status); }
	public Map<String, String> updateRelationship(long userId, FriendRequest request) { repository.updateRelationship(userId, request); return Map.of("status", "success", "message", "관계 상태가 변경되었습니다."); }
	public FeedUploadResponse uploadFeed(long userId, FeedUploadRequest request) { long id = repository.insertFeed(userId, request); return repository.findFeed(id); }
	public List<FeedUploadResponse> getFeedList(long userId) { return repository.listFeeds(); }
}
