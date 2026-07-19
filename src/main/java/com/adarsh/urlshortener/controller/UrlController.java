package com.adarsh.urlshortener.controller;

import com.adarsh.urlshortener.dto.ShortenRequest;
import com.adarsh.urlshortener.dto.ShortenResponse;
import com.adarsh.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService service;

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request){
        return ResponseEntity.ok(service.shorten(request));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code){

        String url=service.getOriginalUrl(code);

        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION,url)
                .build();
    }

}
