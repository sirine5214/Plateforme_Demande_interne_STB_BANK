package tn.esprit.stb.back_office.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload-dir:uploads/avatars}")
    private String uploadDir;

    @Value("${app.attachments-dir:uploads/demandes}")
    private String attachmentsDir;

    @Value("${app.email-attachments-dir:uploads/emails}")
    private String emailAttachmentsDir;

    /** Taille maximale d'un avatar : inutile de conserver une photo pleine résolution. */
    private static final int TAILLE_AVATAR_PX = 256;

    public String storeAvatar(MultipartFile file) {
        String url = store(file, uploadDir, "/uploads/avatars/");
        redimensionnerAvatar(Paths.get(url.substring(1)));
        return url;
    }

    /**
     * Réduit l'avatar à {@value #TAILLE_AVATAR_PX} px de côté maximum.
     * En cas d'échec (format non géré par ImageIO), le fichier d'origine est conservé tel quel.
     */
    private void redimensionnerAvatar(Path chemin) {
        try {
            BufferedImage source = ImageIO.read(chemin.toFile());
            if (source == null || Math.max(source.getWidth(), source.getHeight()) <= TAILLE_AVATAR_PX) {
                return;
            }

            double facteur = (double) TAILLE_AVATAR_PX / Math.max(source.getWidth(), source.getHeight());
            int largeur = (int) Math.round(source.getWidth() * facteur);
            int hauteur = (int) Math.round(source.getHeight() * facteur);

            BufferedImage reduite = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = reduite.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, largeur, hauteur, null);
            g.dispose();

            ImageIO.write(reduite, "png", chemin.toFile());
        } catch (IOException e) {
            log.warn("Redimensionnement de l'avatar impossible ({}) : le fichier d'origine est conservé", e.getMessage());
        }
    }

    /** Enregistre une pièce jointe de demande et renvoie son URL publique. */
    public String storeAttachment(MultipartFile file) {
        return store(file, attachmentsDir, "/uploads/demandes/");
    }

    /**
     * Enregistre une piece jointe issue d'un e-mail entrant.
     *
     * <p>Stockee a part des pieces jointes de demandes : elle provient d'un expediteur non
     * authentifie et ne doit pas se melanger aux fichiers deposes depuis l'application. Le
     * nom d'origine n'est jamais repris comme nom de fichier, seule son extension l'est, afin
     * qu'un nom construit par un attaquant ne puisse pas influencer le chemin ecrit.
     *
     * @return l'URL publique du fichier enregistre
     */
    public String storeEmailAttachment(byte[] contenu, String nomFichierOrigine) {
        try {
            Path dir = Paths.get(emailAttachmentsDir);
            Files.createDirectories(dir);

            String filename = UUID.randomUUID() + extensionOf(nomFichierOrigine);
            Files.write(dir.resolve(filename), contenu);

            return "/uploads/emails/" + filename;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Erreur lors de l'enregistrement de la piece jointe d'e-mail", e);
        }
    }

    private String store(MultipartFile file, String directory, String urlPrefix) {
        try {
            Path dir = Paths.get(directory);
            Files.createDirectories(dir);

            String filename = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return urlPrefix + filename;
        } catch (IOException e) {
            throw new IllegalStateException("Erreur lors de l'enregistrement du fichier", e);
        }
    }

    /** Supprime le fichier physique correspondant à une URL publique (best effort). */
    public void delete(String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(url.substring(1)));
        } catch (IOException e) {
            // Le fichier peut avoir déjà été supprimé : on ne bloque pas l'opération métier
            throw new IllegalStateException("Erreur lors de la suppression du fichier", e);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
