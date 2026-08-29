package com.example.telegrambot.dto;

public enum JobStatus {
    PENDING,     // Wartet auf Start
    RUNNING,     // Läuft
    PAUSED,      // Pausiert
    COMPLETED,   // Fertig
    ABORTED,     // Abgebrochen
    FAILED       // Fehlgeschlagen
}
