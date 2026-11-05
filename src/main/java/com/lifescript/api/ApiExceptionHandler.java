package com.lifescript.api;

import com.lifescript.storage.InvalidPlanException;
import com.lifescript.storage.PlanConflictException;
import com.lifescript.storage.PlanNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Turns the storage exceptions into HTTP statuses, with the standard ProblemDetail JSON body.
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PlanNotFoundException.class)
    public ProblemDetail notFound(PlanNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PlanConflictException.class)
    public ProblemDetail conflict(PlanConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidPlanException.class)
    public ProblemDetail invalid(InvalidPlanException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
