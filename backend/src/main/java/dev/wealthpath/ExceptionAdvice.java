package dev.wealthpath;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ExceptionAdvice {
  private static final Logger log = LoggerFactory.getLogger(ExceptionAdvice.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> business(ApiException e, HttpServletRequest request) {
    return problem(e.status, e.code, e.getMessage(), request, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    Map<String, String> fields = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
    return problem(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Check the highlighted fields.",
        request,
        fields);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception e, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "The request contains an invalid value.",
        request,
        null);
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception e, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "CONFLICT",
        "The data changed or already exists. Refresh and try again.",
        request,
        null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
    // Keep framework-level client errors (such as an unsupported method or media type) distinct
    // from application failures, without disclosing internal exception messages or resource paths.
    if (e instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
      HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
      if (status != null) {
        String code =
            switch (status) {
              case NOT_FOUND -> "NOT_FOUND";
              case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
              case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
              default -> "INVALID_REQUEST";
            };
        String detail =
            switch (status) {
              case NOT_FOUND -> "The requested resource was not found.";
              case METHOD_NOT_ALLOWED -> "This HTTP method is not supported for this resource.";
              case UNSUPPORTED_MEDIA_TYPE -> "Use application/json for this request.";
              default -> "The request could not be processed.";
            };
        ResponseEntity<ProblemDetail> result = problem(status, code, detail, request, null);
        return ResponseEntity.status(status).headers(error.getHeaders()).body(result.getBody());
      }
    }
    log.error("Unhandled API failure", e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred. Please try again.",
        request,
        null);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status,
      String code,
      String detail,
      HttpServletRequest request,
      Map<String, String> fields) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setTitle(status.getReasonPhrase());
    body.setProperty("code", code);
    body.setProperty("requestId", request.getAttribute("requestId"));
    if (fields != null) body.setProperty("errors", fields);
    return ResponseEntity.status(status).body(body);
  }
}
