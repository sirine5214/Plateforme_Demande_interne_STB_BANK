package tn.esprit.stb.back_office.exception;

/** E-mail entrant demandé par identifiant mais absent de la boîte de réception. */
public class EmailIntrouvableException extends RuntimeException {

    public EmailIntrouvableException(Integer id) {
        super("E-mail introuvable : " + id);
    }
}
