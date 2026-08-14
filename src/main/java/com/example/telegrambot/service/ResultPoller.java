package com.example.telegrambot.service;

import org.springframework.stereotype.Service;

@Service
public class ResultPoller {

    // Platzhalter – später implementieren wir das Polling
    public void subscribe(Long chatId, String jobId) {
        System.out.println("Subscribed chat " + chatId + " to job " + jobId);
        // Hier später: aktive Subscriptions verwalten und Scheduler starten
    }
}
