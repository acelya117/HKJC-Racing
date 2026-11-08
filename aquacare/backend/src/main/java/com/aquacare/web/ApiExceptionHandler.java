package com.aquacare.web;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns exceptions into JSON error responses: {"status": 404, "error": "..."}.
 * The frontend can show the "error" text directly to the user.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Thrown by controllers when a row with the given id does not exist. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown by controllers for invalid input not covered by bean validation. */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> badRequest(BadRequestException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> toSnake(f.getField()) + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST,
                "Malformed request (check JSON syntax and date/time formats: yyyy-MM-dd, HH:mm)");
    }

    /** Unique-key or foreign-key violations, e.g. duplicate tank name or unknown user_id. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
        SQLException sql = findSqlException(e);
        int code = sql == null ? 0 : sql.getErrorCode();
        String msg = switch (code) {
            case 1062 -> "Duplicate value: " + sql.getMessage();
            case 1452 -> "Referenced row does not exist (check user_id / tank_id)";
            case 3819 -> "Value violates a CHECK constraint: " + sql.getMessage();
            default -> sql == null ? "Data integrity violation" : sql.getMessage();
        };
        return body(code == 1062 ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<Map<String, Object>> dbDown(DataAccessException e) {
        return body(HttpStatus.SERVICE_UNAVAILABLE,
                "Cannot connect to MySQL. Check DB_URL / DB_USER / DB_PASSWORD and that MySQL is running.");
    }

    /** Other database errors, including SIGNAL SQLSTATE '45000' from the stored procedure. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> db(DataAccessException e) {
        SQLException sql = findSqlException(e);
        if (sql != null && "45000".equals(sql.getSQLState())) {
            return body(HttpStatus.NOT_FOUND, sql.getMessage());
        }
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                sql == null ? e.getMostSpecificCause().getMessage() : sql.getMessage());
    }

    private static SQLException findSqlException(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException s) {
                return s;
            }
            t = t.getCause();
        }
        return null;
    }

    private static String toSnake(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.value());
        m.put("error", message);
        return ResponseEntity.status(status).body(m);
    }
}
