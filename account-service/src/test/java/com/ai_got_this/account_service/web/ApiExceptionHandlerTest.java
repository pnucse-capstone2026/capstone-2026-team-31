package com.ai_got_this.account_service.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returns406WithoutBodyForAnUnacceptableResponseMediaType() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/portfolio");
        request.addHeader("Accept", "text/plain");

        var response = handler.notAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)),
                request
        );

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
        assertNull(response.getBody());
    }
}
