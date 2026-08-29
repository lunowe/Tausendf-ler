package de.uni_leipzig.eva.tausendfuessler.bot.service;

/** An error answer of the coordinator; the message is the {@code error} field of its body, ready to show the user. */
public class CoordinatorException extends RuntimeException {

    public CoordinatorException(String message) {
        super(message);
    }
}
