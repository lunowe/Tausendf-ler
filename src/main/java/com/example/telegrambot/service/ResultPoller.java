package com.example.telegrambot.service;

import org.springframework.stereotype.Service;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class ResultPoller {


    private final Map<Long, String> activeSubscriptions = new ConcurrentHashMap<>();
    // Platzhalter – später implementieren wir das Polling
    public void subscribe(Long chatId, String jobId) {
        System.out.println("Subscribed chat " + chatId + " to job " + jobId);

    }

    public String getSubscribedJobId(Long chatId) {
        return activeSubscriptions.get(chatId);
    }

    public boolean isSubscribed(Long chatId) {
        return activeSubscriptions.containsKey(chatId);
    }

    public void unsubscribe(Long chatId) {
        String jobId = activeSubscriptions.remove(chatId);
        if (jobId != null) {
            System.out.println("Chat " + chatId + " von Job " + jobId + " abgemeldet");
        }
    }

}
