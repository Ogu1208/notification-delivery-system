package ogu1208.notificationdeliverysystem.adapter.webapi;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiControllerAdvice extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		org.springframework.http.HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		ProblemDetail problemDetail = getProblemDetail(
			HttpStatus.BAD_REQUEST,
			validationDescription(exception),
			exception
		);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		IllegalStateException.class
	})
	public ProblemDetail handleBadRequestException(RuntimeException exception) {
		return getProblemDetail(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleException(Exception exception) {
		return getProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다", exception);
	}

	private String validationDescription(MethodArgumentNotValidException exception) {
		return exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.map(FieldError::getDefaultMessage)
			.orElse("요청 값이 올바르지 않습니다");
	}

	private static ProblemDetail getProblemDetail(HttpStatus status, String description, Exception exception) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, description);
		problemDetail.setProperty("timestamp", LocalDateTime.now());
		problemDetail.setProperty("exception", exception.getClass().getSimpleName());
		return problemDetail;
	}
}
