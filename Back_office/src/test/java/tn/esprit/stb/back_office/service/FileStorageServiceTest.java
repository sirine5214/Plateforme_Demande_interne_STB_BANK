package tn.esprit.stb.back_office.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tests unitaires du stockage des fichiers. Les répertoires sont redirigés vers
 * un dossier temporaire pour ne jamais écrire dans le projet.
 */
@DisplayName("FileStorageService — stockage des fichiers")
class FileStorageServiceTest {

    @TempDir
    Path dossierTemporaire;

    private FileStorageService fileStorageService;
    private Path dossierAvatars;
    private Path dossierPiecesJointes;

    @BeforeEach
    void preparerService() {
        fileStorageService = new FileStorageService();

        dossierAvatars = dossierTemporaire.resolve("avatars");
        dossierPiecesJointes = dossierTemporaire.resolve("demandes");

        ReflectionTestUtils.setField(fileStorageService, "uploadDir", dossierAvatars.toString());
        ReflectionTestUtils.setField(fileStorageService, "attachmentsDir", dossierPiecesJointes.toString());
    }

    private MockMultipartFile fichier(String nom, String contenu) {
        return new MockMultipartFile("file", nom, "application/octet-stream", contenu.getBytes());
    }

    @Test
    @DisplayName("une pièce jointe est écrite sur disque et son URL publique est renvoyée")
    void pieceJointeEcriteEtUrlRenvoyee() throws IOException {
        String url = fileStorageService.storeAttachment(fichier("cahier des charges.pdf", "contenu du fichier"));

        assertThat(url).startsWith("/uploads/demandes/").endsWith(".pdf");

        // Le nom d'origine est remplacé par un UUID : deux dépôts ne peuvent pas s'écraser
        assertThat(url).doesNotContain("cahier des charges");

        Path ecrit = dossierPiecesJointes.resolve(url.substring("/uploads/demandes/".length()));
        assertThat(ecrit).exists();
        assertThat(Files.readString(ecrit)).isEqualTo("contenu du fichier");
    }

    @Test
    @DisplayName("deux dépôts du même fichier produisent deux noms distincts")
    void nomsUniques() {
        String premier = fileStorageService.storeAttachment(fichier("rapport.pdf", "a"));
        String second = fileStorageService.storeAttachment(fichier("rapport.pdf", "b"));

        assertThat(premier).isNotEqualTo(second);
    }

    @Test
    @DisplayName("un fichier sans extension est accepté et stocké sans suffixe")
    void fichierSansExtension() {
        String url = fileStorageService.storeAttachment(fichier("NOTES", "texte"));

        assertThat(url).startsWith("/uploads/demandes/").doesNotContain(".");
    }

    @Test
    @DisplayName("un avatar est stocké sous le préfixe des avatars")
    void avatarStocke() {
        String url = fileStorageService.storeAvatar(fichier("photo.png", "pixels"));

        assertThat(url).startsWith("/uploads/avatars/").endsWith(".png");
        assertThat(dossierAvatars.resolve(url.substring("/uploads/avatars/".length()))).exists();
    }

    @Test
    @DisplayName("le répertoire cible est créé s'il n'existe pas encore")
    void repertoireCreeALaVolee() {
        assertThat(dossierPiecesJointes).doesNotExist();

        fileStorageService.storeAttachment(fichier("a.txt", "x"));

        assertThat(dossierPiecesJointes).isDirectory();
    }

    @Test
    @DisplayName("une erreur de lecture du flux est convertie en IllegalStateException")
    void erreurDeLectureRemontee() throws IOException {
        MultipartFile defaillant = Mockito.mock(MultipartFile.class);
        Mockito.when(defaillant.getOriginalFilename()).thenReturn("panne.txt");
        Mockito.when(defaillant.getInputStream()).thenThrow(new IOException("flux illisible"));

        assertThatThrownBy(() -> fileStorageService.storeAttachment(defaillant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Erreur lors de l'enregistrement");
    }

    @Test
    @DisplayName("supprimer une URL nulle ou hors /uploads/ ne fait rien")
    void suppressionIgnoreeHorsPerimetre() {
        assertThatCode(() -> fileStorageService.delete(null)).doesNotThrowAnyException();
        assertThatCode(() -> fileStorageService.delete("/etc/passwd")).doesNotThrowAnyException();
        assertThatCode(() -> fileStorageService.delete("../../secret.txt")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("supprimer un fichier déjà absent ne bloque pas l'opération métier")
    void suppressionFichierAbsent() {
        assertThatCode(() -> fileStorageService.delete("/uploads/demandes/inexistant.pdf"))
                .doesNotThrowAnyException();
    }
}
