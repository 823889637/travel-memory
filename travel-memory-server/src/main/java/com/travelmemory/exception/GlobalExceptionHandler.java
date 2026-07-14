package com.travelmemory.exception;

import com.travelmemory.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        int status = e.getCode() != null && e.getCode() >= 400 && e.getCode() <= 599
                ? e.getCode()
                : HttpStatus.INTERNAL_SERVER_ERROR.value();
        return ResponseEntity.status(status).body(Result.fail(status, e.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Result<Void>> handleValidationException(Exception e) {
        return ResponseEntity.badRequest().body(Result.fail(400, "Invalid request parameters"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(Result.fail(400, "Image size cannot exceed 50MB"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("Unhandled server exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "Server error"));
    }
}
