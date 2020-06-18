package com.example.messageservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RefreshScope
@RestController
public class MessageController {

    private final QuoteRepository quoteRepository;

    public MessageController(QuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
    }

    @Value("${service_version}")
    private String serviceVersion;

    @GetMapping("/")
    public Quote randomQuote()
    {
        Quote q = quoteRepository.findRandomQuote();
        q.setQuote(String.format("Service version: %s - Quote: %s", serviceVersion, q.getQuote()));
        return q;
    }

    @GetMapping("/quotes")
    public List<Quote> getAll()
    {
        return quoteRepository.findAll();
    }

    @GetMapping("/quotes/{id}")
    public ResponseEntity<Quote> getQuote(@PathVariable("id") Integer id) {
        Optional<Quote> quote = quoteRepository.findById(id);
        if (quote.isPresent()) {
            return new ResponseEntity<>(quote.get(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
