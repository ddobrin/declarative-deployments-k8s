package com.example;

public class Quote
{
    private Integer id;
    private String quote;
    private String author;
    private String version;

    public String getVersion() { return version; }

    public void setVersion(String version) { this.version = version; }

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
