package tn.esprit.autoloc.autolocapi.exception;

public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resource, Long id) {
        super("%s introuvable avec l'identifiant %d".formatted(resource, id));
    }
}
