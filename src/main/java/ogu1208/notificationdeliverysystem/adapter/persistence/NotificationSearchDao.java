package ogu1208.notificationdeliverysystem.adapter.persistence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchCursor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Repository
@RequiredArgsConstructor
class NotificationSearchDao {

	private final EntityManager entityManager;

	List<Notification> search(NotificationSearchQuery query, int fetchSize) {
		StringBuilder jpql = new StringBuilder(
			"select n from Notification n where n.createdAt >= :from and n.createdAt < :to"
		);
		if (query.hasRequester()) {
			jpql.append(" and n.requesterId = :requesterId");
		}
		if (query.hasReceiver()) {
			jpql.append(" and n.receiver = :receiver");
		}
		if (query.hasStatusFilter()) {
			jpql.append(" and n.status in :statuses");
		}
		if (query.hasChannelFilter()) {
			jpql.append(" and n.channel in :channels");
		}
		if (query.hasCursor()) {
			jpql.append(" and (n.createdAt < :cursorCreatedAt")
				.append(" or (n.createdAt = :cursorCreatedAt and n.id < :cursorId))");
		}
		jpql.append(" order by n.createdAt desc, n.id desc");

		TypedQuery<Notification> typedQuery = entityManager.createQuery(jpql.toString(), Notification.class)
			.setParameter("from", query.from())
			.setParameter("to", query.to());

		if (query.hasRequester()) {
			typedQuery.setParameter("requesterId", query.requesterId());
		}
		if (query.hasReceiver()) {
			typedQuery.setParameter("receiver", query.receiver());
		}
		if (query.hasStatusFilter()) {
			typedQuery.setParameter("statuses", query.statuses());
		}
		if (query.hasChannelFilter()) {
			typedQuery.setParameter("channels", query.channels());
		}
		if (query.hasCursor()) {
			NotificationSearchCursor cursor = query.cursor();
			typedQuery.setParameter("cursorCreatedAt", cursor.createdAt());
			typedQuery.setParameter("cursorId", cursor.id());
		}

		return new ArrayList<>(typedQuery.setMaxResults(fetchSize).getResultList());
	}
}
