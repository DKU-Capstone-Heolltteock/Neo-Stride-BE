package com.neostride.server.community.repository;

import com.neostride.server.community.dto.*;
import com.neostride.server.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityRepository {
	private final JdbcTemplate jdbcTemplate;
	private final CommunityProfileRepository profileRepository;
	private final CommunityRelationshipRepository relationshipRepository;
	private final CommunityInteractionRepository interactionRepository;
	private final CommunityCommentActivityRepository commentActivityRepository;
	private final CommunityFeedRepository feedRepository;
	private final CommunityTipRepository tipRepository;
	private final CommunitySearchRepository searchRepository;

	public CommunityRepository(JdbcTemplate jdbcTemplate, NotificationRepository notificationRepository) {
		this.jdbcTemplate = jdbcTemplate;
		this.profileRepository = new CommunityProfileRepository(jdbcTemplate);
		this.relationshipRepository = new CommunityRelationshipRepository(jdbcTemplate, notificationRepository);
		this.interactionRepository = new CommunityInteractionRepository(jdbcTemplate, notificationRepository);
		this.commentActivityRepository = new CommunityCommentActivityRepository(jdbcTemplate);
		this.feedRepository = new CommunityFeedRepository(jdbcTemplate, notificationRepository, interactionRepository);
		this.tipRepository = new CommunityTipRepository(jdbcTemplate, interactionRepository);
		this.searchRepository = new CommunitySearchRepository(jdbcTemplate);
	}

	public UserProfileResponse getUserProfile(long userId) { return profileRepository.getUserProfile(userId); }
	public UserProfileResponse getUserProfile(Long viewerUserId, long userId) { return profileRepository.getUserProfile(viewerUserId, userId); }
	public AccountInfoResponse getAccountInfo(long userId) { return profileRepository.getAccountInfo(userId); }
	public void updateStatusMessage(long userId, String statusMessage) { profileRepository.updateStatusMessage(userId, statusMessage); }
	public void updateNickname(long userId, String nickname) { profileRepository.updateNickname(userId, nickname); }
	public boolean existsByCommunityProfileNameExcludingUserId(String nickname, long userId) { return profileRepository.existsByCommunityProfileNameExcludingUserId(nickname, userId); }
	public void deleteAccount(long userId) { profileRepository.deleteAccount(userId); }
	public void updateProfileImage(long userId, String profileImageUrl) { profileRepository.updateProfileImage(userId, profileImageUrl); }
	public void deleteProfileImage(long userId) { profileRepository.deleteProfileImage(userId); }

	public List<CommunityContentResponse> myFeeds(long userId) { return feedRepository.myFeeds(userId); }
	public List<CommunityContentResponse> publicFeedsByUser(long userId) { return feedRepository.publicFeedsByUser(userId); }
	public List<CommunityContentResponse> feedsByUserForViewer(Long viewerUserId, long userId) { return feedRepository.feedsByUserForViewer(viewerUserId, userId); }
	public List<CommunityContentResponse> taggedFeeds(long userId) { return feedRepository.taggedFeeds(userId); }
	public List<CommunityContentResponse> interactedFeeds(long userId, String type) { return feedRepository.interactedFeeds(userId, type); }
	public List<MyCommentActivityResponse> myCommentActivities(long userId) { return commentActivityRepository.myCommentActivities(userId); }

	public boolean toggleBookmark(long userId, long contentId) {
		return toggleInteraction(userId, contentId, "BOOKMARK");
	}

	public BadgeDetailResponse getBadgeDetail(long userId) { return profileRepository.getBadgeDetail(userId); }

	public List<FriendResponse> getFriendList(long userId, String status) { return relationshipRepository.getFriendList(userId, status); }
	public List<FriendResponse> getUserFriendList(long viewerUserId, long userId) { return relationshipRepository.getUserFriendList(viewerUserId, userId); }
	public void updateRelationship(long userId, FriendRequest request) { relationshipRepository.updateRelationship(userId, request); }

	public long insertFeed(long userId, FeedUploadRequest request) { return feedRepository.insertFeed(userId, request); }
	public FeedUploadResponse findFeed(long feedId) { return feedRepository.findFeed(feedId); }
	public List<FeedUploadResponse> listFeeds() { return feedRepository.listFeeds(); }
	public List<FeedUploadResponse> listFeeds(Long viewerUserId) { return feedRepository.listFeeds(viewerUserId); }
	public List<FeedUploadResponse> listFeedsPage(Long viewerUserId, LocalDateTime cursorCreatedAt, Long cursorId, int limit) { return feedRepository.listFeedsPage(viewerUserId, cursorCreatedAt, cursorId, limit); }
	public FeedDetailResponse findFeedDetail(long userId, long feedId) { return feedRepository.findFeedDetail(userId, feedId, null); }
	public FeedDetailResponse findFeedDetail(long userId, long feedId, Integer commentLimit) { return feedRepository.findFeedDetail(userId, feedId, commentLimit); }

	public boolean toggleInteraction(long userId, long contentId, String type) { return interactionRepository.toggleInteraction(userId, contentId, type); }
	public CommentResponse createComment(long userId, long contentId, CommentRequest request) { return interactionRepository.createComment(userId, contentId, request); }
	public CommentResponse updateComment(long userId, long contentId, long commentId, CommentRequest request) { return interactionRepository.updateComment(userId, contentId, commentId, request); }
	public void deleteComment(long userId, long contentId, long commentId) { interactionRepository.deleteComment(userId, contentId, commentId); }
	public List<CommentResponse> commentsPage(long viewerUserId, long contentId, LocalDateTime cursorCreatedAt, Long cursorId, int limit) { return interactionRepository.commentsPage(viewerUserId, contentId, cursorCreatedAt, cursorId, limit); }

	public void updateFeed(long userId, long feedId, FeedUploadRequest request) { feedRepository.updateFeed(userId, feedId, request); }

	public void deleteContent(long userId, long contentId, String contentType) {
		jdbcTemplate.update("""
			DELETE ci FROM community_interactions ci
			JOIN community_contents cc ON cc.content_id = ci.content_id
			WHERE ci.content_id = ? AND cc.author_user_id = ? AND cc.content_type = ?
			""", contentId, userId, contentType);
		jdbcTemplate.update("DELETE FROM community_contents WHERE content_id=? AND author_user_id=? AND content_type=?", contentId, userId, contentType);
	}

	public List<FriendResponse> getTaggedUsers(long feedId) { return feedRepository.getTaggedUsers(feedId); }

	public long insertTip(long userId, TipUploadRequest request) { return tipRepository.insertTip(userId, request); }
	public TipUploadResponse findTip(long tipId) { return tipRepository.findTip(tipId); }
	public List<TipUploadResponse> listTips() { return tipRepository.listTips(); }
	public List<TipUploadResponse> listTips(long viewerUserId) { return tipRepository.listTips(viewerUserId); }
	public List<TipUploadResponse> listTipsByUser(long userId) { return tipRepository.listTipsByUser(userId); }
	public List<TipUploadResponse> listTipsByUser(Long viewerUserId, long userId) { return tipRepository.listTipsByUser(viewerUserId, userId); }
	public List<TipUploadResponse> listTipsLikedByUser(long userId) { return tipRepository.listTipsLikedByUser(userId); }
	public List<TipUploadResponse> listTipsBookmarkedByUser(long userId) { return tipRepository.listTipsBookmarkedByUser(userId); }
	public List<TipUploadResponse> listTipsCommentedByUser(long userId) { return tipRepository.listTipsCommentedByUser(userId); }
	public TipDetailResponse findTipDetail(long userId, long tipId) { return tipRepository.findTipDetail(userId, tipId, null); }
	public TipDetailResponse findTipDetail(long userId, long tipId, Integer commentLimit) { return tipRepository.findTipDetail(userId, tipId, commentLimit); }
	public void updateTip(long userId, long tipId, TipUploadRequest request) { tipRepository.updateTip(userId, tipId, request); }

	public List<FeedUploadResponse> searchFeeds(String keyword, int page, int size) { return searchRepository.searchFeeds(keyword, page, size); }
	public List<FeedUploadResponse> searchFeeds(Long viewerUserId, String keyword, int page, int size) { return searchRepository.searchFeeds(viewerUserId, keyword, page, size); }
	public List<TipUploadResponse> searchTips(String keyword, String category, int page, int size) { return searchRepository.searchTips(keyword, category, page, size); }
	public List<TipUploadResponse> searchTips(Long viewerUserId, String keyword, String category, int page, int size) { return searchRepository.searchTips(viewerUserId, keyword, category, page, size); }
	public List<SearchUserResponse> searchProfiles(String keyword, int page, int size) { return searchRepository.searchProfiles(keyword, page, size); }
	public List<SearchUserResponse> searchProfiles(Long viewerUserId, String keyword, int page, int size) { return searchRepository.searchProfiles(viewerUserId, keyword, page, size); }
	public List<SearchUserResponse> searchFriends(long userId, String keyword) { return searchRepository.searchFriends(userId, keyword); }
	public List<SearchUserResponse> getTopProfiles(int page, int size) { return searchRepository.getTopProfiles(page, size); }
	public List<SearchUserResponse> getTopProfiles(Long viewerUserId, int page, int size) { return searchRepository.getTopProfiles(viewerUserId, page, size); }
	public List<SearchUserResponse> getMyFriends(long userId) { return searchRepository.getMyFriends(userId); }


}
