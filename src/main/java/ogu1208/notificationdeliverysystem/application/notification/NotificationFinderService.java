package ogu1208.notificationdeliverysystem.application.notification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ogu1208.notificationdeliverysystem.application.notification.provided.NotificationFinder;
import ogu1208.notificationdeliverysystem.application.notification.required.NotificationRepository;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchCursor;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchQuery;
import ogu1208.notificationdeliverysystem.application.notification.search.NotificationSearchResult;
import ogu1208.notificationdeliverysystem.domain.notification.Notification;

@Service
@RequiredArgsConstructor
public class NotificationFinderService implements NotificationFinder {

	private final NotificationRepository notificationRepository;

	@Override
	@Transactional(readOnly = true)
	public NotificationSearchResult search(NotificationSearchQuery query) {
		int fetchSize = query.limit() + 1;
		List<Notification> found = notificationRepository.search(query, fetchSize);

		boolean hasNext = found.size() > query.limit();
		List<Notification> items = hasNext ? found.subList(0, query.limit()) : found;
		NotificationSearchCursor nextCursor = hasNext ? toCursor(items.get(items.size() - 1)) : null;

		return new NotificationSearchResult(items, nextCursor);
	}

	@Override
	@Transactional(readOnly = true)
	public Notification findById(Long id) {
		return notificationRepository.findById(id)
			.orElseThrow(() -> new NotificationNotFoundException(id));
	}

	private NotificationSearchCursor toCursor(Notification last) {
		return new NotificationSearchCursor(last.getCreatedAt(), last.getId());
	}
}
