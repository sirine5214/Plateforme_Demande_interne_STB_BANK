package tn.esprit.stb.back_office.exception;

/** Levée lorsqu'un changement de statut ne respecte pas le cycle de vie de la demande. */
public class TransitionInvalideException extends RuntimeException {
    public TransitionInvalideException(String message) {
        super(message);
    }
}
