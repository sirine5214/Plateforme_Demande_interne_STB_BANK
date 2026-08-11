package tn.esprit.stb.back_office.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Enveloppe de pagination renvoyée au front (évite d'exposer la structure interne de Page). */
@Getter
@Setter
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> contenu;
    private int page;
    private int taille;
    private long totalElements;
    private int totalPages;
    private boolean premiere;
    private boolean derniere;

    public static <S, T> PageResponse<T> de(Page<S> page, List<T> contenu) {
        return new PageResponse<>(
                contenu,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
