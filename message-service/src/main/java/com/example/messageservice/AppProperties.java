package com.example.messageservice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties("app")
public class AppProperties {

    public List<Quote> getQuotes() {
        return quotes;
    }

    public void setQuotes(List<Quote> quotes) {
        this.quotes = quotes;
    }

    private List<Quote> quotes = new ArrayList<>();

    public static class Quote
    {
        private Integer id;
        private String quote;
        private String author;

        public Integer getId() {
            return id;
        }
        public void setId(Integer id) {
            this.id = id;
        }

        public String getQuote() {
            return quote;
        }
        public void setQuote(String quote) {
            this.quote = quote;
        }

        public String getAuthor() {
            return author;
        }
        public void setAuthor(String author) {
            this.author = author;
        }
    }

}
