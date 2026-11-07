package com.lifescript.api;

import com.lifescript.account.EmailTakenException;
import com.lifescript.account.InvalidAccountException;
import com.lifescript.storage.InvalidPlanException;
import com.lifescript.storage.PlanConflictException;
import com.lifescript.storage.PlanNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Turns the plan, account and login exceptions into HTTP statuses, with the standard ProblemDetail JSON body.
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

    @ExceptionHandler({InvalidPlanException.class, InvalidAccountException.class})
    public ProblemDetail invalid(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(EmailTakenException.class)
    public ProblemDetail emailTaken(EmailTakenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // The same for an unknown email and a wrong password, so nobody can find out which emails have accounts.
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail loginFailed(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Wrong email or password");
    }
}
