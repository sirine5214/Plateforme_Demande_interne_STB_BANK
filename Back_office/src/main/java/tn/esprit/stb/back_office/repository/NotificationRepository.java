package tn.esprit.stb.back_office.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.stb.back_office.entities.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByDestinataireIdOrderByDateEnvoiDesc(Integer destinataireId);

    List<Notification> findByDestinataireIdAndLuFalseOrderByDateEnvoiDesc(Integer destinataireId);

    long countByDestinataireIdAndLuFalse(Integer destinataireId);
}
