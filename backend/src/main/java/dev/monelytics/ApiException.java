package dev.monelytics;

import org.springframework.http.HttpStatus;

class ApiException extends RuntimeException {
  final HttpStatus status;
  final String code;

  ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  static ApiException missing() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.");
  }

  static ApiException invalid(String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", message);
  }

  static ApiException unauthorized() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED,
        "INVALID_CREDENTIALS",
        "The email, password, or verification code is incorrect.");
  }
}
