package ogu1208.notificationdeliverysystem.adapter.persistence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchCursor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationChannel;
import ogu1208.notificationdeliverysystem.domain.notification.NotificationStatus;

/**
 * 알림 검색 native SQL.
 *
 * <p>커서 조건을 튜플 비교 {@code (created_at, id) < (?, ?)}로 표현해 인덱스 range condition에
 * 그대로 태우기 위해 native query를 사용한다. JPQL은 row constructor comparison을 지원하지 않아
 * OR 형태(`created_at < ? OR (created_at = ? AND id < ?)`)로 표현할 수밖에 없고, Postgres에서
 * 이 OR 조건은 인덱스에 태워지지 않고 Filter로 처리되어 깊은 페이지에서 latency가 튀는 문제가 있다.
 * 상세: {@code docs/조회성능설계.md} §4.
 */
@Repository
@RequiredArgsConstructor
class NotificationSearchDao {

	private final EntityManager entityManager;

	List<Notification> search(NotificationSearchQuery query, int fetchSize) {
		StringBuilder sql = new StringBuilder(
			"select * from notification where created_at >= :from and created_at < :to"
		);
		if (query.hasRequester()) {
			sql.append(" and requester_id = :requesterId");
		}
		if (query.hasReceiver()) {
			sql.append(" and receiver = :receiver");
		}
		if (query.hasStatusFilter()) {
			sql.append(" and status in (:statuses)");
		}
		if (query.hasChannelFilter()) {
			sql.append(" and channel in (:channels)");
		}
		if (query.hasCursor()) {
			sql.append(" and (created_at, id) < (:cursorCreatedAt, :cursorId)");
		}
		sql.append(" order by created_at desc, id desc");

		Query nativeQuery = entityManager.createNativeQuery(sql.toString(), Notification.class)
			.setParameter("from", query.from())
			.setParameter("to", query.to());

		if (query.hasRequester()) {
			nativeQuery.setParameter("requesterId", query.requesterId());
		}
		if (query.hasReceiver()) {
			nativeQuery.setParameter("receiver", query.receiver());
		}
		if (query.hasStatusFilter()) {
			nativeQuery.setParameter("statuses", query.statuses().stream().map(NotificationStatus::name).toList());
		}
		if (query.hasChannelFilter()) {
			nativeQuery.setParameter("channels", query.channels().stream().map(NotificationChannel::name).toList());
		}
		if (query.hasCursor()) {
			NotificationSearchCursor cursor = query.cursor();
			nativeQuery.setParameter("cursorCreatedAt", cursor.createdAt());
			nativeQuery.setParameter("cursorId", cursor.id());
		}

		@SuppressWarnings("unchecked")
		List<Notification> results = nativeQuery.setMaxResults(fetchSize).getResultList();
		return new ArrayList<>(results);
	}
}
