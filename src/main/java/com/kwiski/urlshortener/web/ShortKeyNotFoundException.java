package com.kwiski.urlshortener.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ShortKeyNotFoundException extends RuntimeException {
    public ShortKeyNotFoundException(String key) {
        super("No link found for key: " + key);
    }
}
