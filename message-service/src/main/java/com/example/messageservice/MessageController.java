package com.example.messageservice;

import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Random;

@RefreshScope
@RestController
public class MessageController {

    private AppProperties app;

    @Autowired
    public void setApp(AppProperties app) {
        this.app = app;
    }

    @Value("${service_version}")
    private String serviceVersion;

    @GetMapping("/")
    public AppProperties.Quote randomQuote()
    {
        Random r = new Random();
        AppProperties.Quote q = app.getQuotes().get(r.nextInt(app.getQuotes().size()));

        AppProperties.Quote resp = new AppProperties.Quote();
        resp.setId(q.getId());
        resp.setAuthor(q.getAuthor());
        resp.setQuote(String.format("Service version: %s - Quote: %s", serviceVersion, q.getQuote()));
        return resp;
    }

//    @GetMapping("/quotes")
//    public List<Quote> getAll()
//    {
//        return quoteRepository.findAll();
//    }

//    @GetMapping("/quotes/{id}")
//    public ResponseEntity<Quote> getQuote(@PathVariable("id") Integer id) {
//        Optional<Quote> quote = quoteRepository.findById(id);
//        if (quote.isPresent()) {
//            return new ResponseEntity<>(quote.get(), HttpStatus.OK);
//        } else {
//            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
//        }
//    }
}
