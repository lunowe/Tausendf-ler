package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import de.uni_leipzig.eva.tausendfuessler.coordinator.api.ApiDtos.ErrorBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

/** Maps service exceptions to the HTTP codes fixed in PROTOCOL.md: 400 / 404 / 409 with body {@code {error}}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorBody badRequest(Exception e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorBody notFound(NoSuchElementException e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorBody conflict(IllegalStateException e) {
        return new ErrorBody(e.getMessage());
    }
}
