package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotNull;

@RestController
public class BillboardController {

    @Autowired
    private RestTemplate restTemplate;

//    public static final class RingResponse {
//        public RingResponse() {
//            this.message = "default constructor::message";
//        }
//
//        public RingResponse(@NotNull String message) {
//            super();
//            this.message = message;
//        }
//
//        @NotNull
//        private String message;
//
//        @NotNull
//        public final void setMessage(String message) {
//            this.message = message;
//        }
//
//        @NotNull
//        public final String getMessage() {
//            return this.message;
//        }
//    }

    @GetMapping("/message")
    public String get(){
        Quote quote = restTemplate.getForObject("http://message-service/", Quote.class);
        return quote.getQuote() + " -- " + quote.getAuthor();
//        return restTemplate.getForObject("https://pcf-c2c-java-frontend-chipper-parrot-vn.cfapps.io/", RingResponse.class).getMessage();
    }
}
