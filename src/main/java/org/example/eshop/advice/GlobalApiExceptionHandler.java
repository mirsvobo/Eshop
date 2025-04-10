package org.example.eshop.advice;

import jakarta.persistence.EntityNotFoundException;
import org.example.eshop.dto.CustomPriceResponseDto;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice // Apply to all controllers, or specify basePackages = "org.example.eshop.controller"
public class GlobalApiExceptionHandler extends ResponseEntityExceptionHandler {

    // Handle @Valid errors for @RequestBody
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, @NotNull HttpHeaders headers, @NotNull HttpStatusCode status, @NotNull WebRequest request) {

        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "'" + error.getField() + "': " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        // Use your existing DTO or create a simple error DTO
        CustomPriceResponseDto errorResponse = new CustomPriceResponseDto();
        errorResponse.setErrorMessage("Validation failed: " + errorMessage);

        logger.warn("Validation failed: " + errorMessage, ex); // Log the details

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // You can add other @ExceptionHandler methods here for different exceptions
    // Example for general exceptions:
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomPriceResponseDto> handleGenericException(Exception ex, WebRequest request) {
        CustomPriceResponseDto errorResponse = new CustomPriceResponseDto();
        errorResponse.setErrorMessage("An unexpected error occurred: " + ex.getMessage());
        logger.error("Unexpected error: ", ex); // Log the full stack trace
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class) // Example for specific business logic errors
    public ResponseEntity<CustomPriceResponseDto> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        CustomPriceResponseDto errorResponse = new CustomPriceResponseDto();
        errorResponse.setErrorMessage(ex.getMessage()); // Use message from exception
        logger.warn("Bad request due to invalid argument: {}");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EntityNotFoundException.class) // Example for not found
    public ResponseEntity<CustomPriceResponseDto> handleEntityNotFoundException(EntityNotFoundException ex, WebRequest request) {
        CustomPriceResponseDto errorResponse = new CustomPriceResponseDto();
        errorResponse.setErrorMessage(ex.getMessage()); // Use message from exception
        logger.warn("Entity not found: {}");
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
}