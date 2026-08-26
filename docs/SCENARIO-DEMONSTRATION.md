# Scénario de démonstration — Plateforme de gestion des demandes internes

Ce document décrit le jeu de données chargé automatiquement par
[`DataLoader`](../Back_office/src/main/java/tn/esprit/stb/back_office/config/DataLoader.java),
les comptes qu'il crée, et un parcours de démonstration couvrant toutes les fonctions de la
plateforme.

---

## 1. Charger le jeu de données

Le chargement est automatique **au premier démarrage sur une base vide**. Rien à faire.

Si la base contient déjà des données et que vous voulez repartir du scénario propre — avant
une soutenance, par exemple — activez la réinitialisation :

```bash
MAIL_CLIENT=fake STB_DEMO_REINITIALISER=true ./mvnw spring-boot:run
```

ou, depuis IntelliJ, ajoutez dans les options de la configuration d'exécution :

```
-Dstb.demo.reinitialiser=true
```

> **Attention** — cette option **efface** les comptes, demandes, historiques et messages
> existants avant de recharger le scénario. Elle vaut `false` par défaut : un démarrage
> ordinaire ne peut donc jamais détruire de données. Pensez à la retirer après usage, sinon
> chaque redémarrage repartira de zéro.

---

## 2. Comptes du scénario

**Mot de passe commun à tous les comptes : `Password123`**

### Administration

| Nom | Identifiant | Rôle | Ce que ce compte permet de démontrer |
|---|---|---|---|
| Amel Bouzid | `admin@stb.tn` | Administrateur | Gestion des utilisateurs et des rôles, statistiques globales, boîte de réception, relève manuelle des e-mails |

### Chefs de projet

| Nom | Identifiant | Rôle | Ce que ce compte permet de démontrer |
|---|---|---|---|
| Mokhtar Ben Ali | `chef.applications@stb.tn` | Chef de projet | Affectation, changements de statut, clôture, qualification des e-mails, tableau de bord de pilotage |
| Leila Mansouri | `chef.canaux@stb.tn` | Chef de projet | Second superviseur : montre que les notifications partent bien à **tous** les chefs de projet |

### Développeurs

| Nom | Identifiant | Rôle | Ce que ce compte permet de démontrer |
|---|---|---|---|
| Wassim Trabelsi | `dev.wassim@stb.tn` | Développeur | Vue restreinte aux demandes affectées, avancement de ses propres tâches |
| Sarra Gharbi | `dev.sarra@stb.tn` | Développeur | Deux demandes en cours, une en validation |
| Anis Khelifi | `dev.anis@stb.tn` | Développeur | Charge répartie, y compris des demandes clôturées |

### Demandeurs

| Nom | Identifiant | Rôle | Ce que ce compte permet de démontrer |
|---|---|---|---|
| Sirine Ben Cheikh | `agence.sfax@stb.tn` | Demandeur | Création de demandes, suivi, messagerie. **C'est aussi l'expéditrice du premier e-mail de la boîte partagée** : la qualification proposera automatiquement de rattacher la demande à ce compte |
| Karim Jebali | `service.credit@stb.tn` | Demandeur | Demandeur le plus actif : cinq demandes couvrant tous les statuts |
| Nadia Sassi | `service.rh@stb.tn` | Demandeur | Demandes de nature administrative, dont une rejetée |

### Compte désactivé

| Nom | Identifiant | Rôle | Ce que ce compte permet de démontrer |
|---|---|---|---|
| Hedi Rezgui | `ancien.collaborateur@stb.tn` | Demandeur (inactif) | **La connexion doit être refusée.** Illustre la désactivation d'un compte sans suppression, exigée pour conserver l'historique d'un collaborateur parti |

---

## 3. Les 14 demandes du scénario

Réparties sur six mois pour que la courbe d'évolution mensuelle ait une forme réaliste.

| N° | Titre | Type | Priorité | Statut | Demandeur | Responsable |
|---|---|---|---|---|---|---|
| DEM-2026-001 | Authentification à deux facteurs | Développement | Haute | Nouvelle | Karim Jebali | — |
| DEM-2026-002 | Habilitation pour trois conseillers | Création d'accès | Moyenne | Nouvelle | Sirine Ben Cheikh | — |
| DEM-2026-003 | Erreur de calcul des intérêts | Correction de bug | **Critique** | En cours | Karim Jebali | Wassim |
| DEM-2026-004 | Export SEPA au format XML | Évolution | Haute | En cours | Karim Jebali | Sarra |
| DEM-2026-005 | Lenteur de consultation des soldes | Correction de bug | Haute | En cours | Sirine Ben Cheikh | Anis |
| DEM-2026-006 | Tableau de bord des crédits | Développement | Moyenne | En validation | Karim Jebali | Sarra |
| DEM-2026-007 | Accès reporting pour les RH | Création d'accès | Basse | En validation | Nadia Sassi | Anis |
| DEM-2026-008 | Migration PostgreSQL 16 | Maintenance | Haute | Terminée | Karim Jebali | Wassim |
| DEM-2026-009 | Assistance export des relevés | Assistance | Basse | Terminée | Sirine Ben Cheikh | Anis |
| DEM-2026-010 | Purge des journaux applicatifs | Maintenance | Moyenne | Terminée | Nadia Sassi | Wassim |
| DEM-2026-011 | Numéro de compte sur l'avis | Évolution | Basse | Terminée | Sirine Ben Cheikh | Sarra |
| DEM-2026-012 | Attestations de solde tronquées | Correction de bug | Moyenne | Terminée | Nadia Sassi | Anis |
| DEM-2026-013 | Refonte de l'écran de virement | Évolution | Moyenne | **Rejetée** | Karim Jebali | — |
| DEM-2026-014 | Logiciel de retouche photo | Assistance | Basse | **Rejetée** | Nadia Sassi | — |

Couverture obtenue : **6 types sur 6**, **4 priorités sur 4**, **5 statuts sur 5**, avec des
demandes affectées et non affectées.

### Fils de discussion pré-remplis

Deux demandes portent une conversation à trois voix, pour que la messagerie ait du contenu
dès la première ouverture :

- **DEM-2026-003** — le service crédit signale, le chef de projet affecte, le développeur
  diagnostique (3 messages)
- **DEM-2026-004** — question sur le format attendu et réponse du développeur (2 messages)

---

## 4. Boîte de réception partagée

Trois e-mails sont fournis par le client de messagerie en mémoire
(`FakeMailboxClient`), actif tant que `MAIL_CLIENT` vaut `fake` :

| Expéditeur | Objet | Pré-qualification attendue | Cas illustré |
|---|---|---|---|
| `agence.sfax@stb.tn` | Demande de création d'accès | Création d'accès · Moyenne | **Expéditeur reconnu** : la qualification propose de rattacher la demande au compte de Sirine Ben Cheikh |
| `sonia.brahmi@stb.com.tn` | URGENT - erreur bloquante en production | Correction de bug · **Critique** | Expéditeur **hors annuaire** ; « production » et « bloquant » font monter la priorité au maximum |
| `karim.jendoubi@stb.com.tn` | Évolution du module de reporting | Évolution · **Basse** | « Ce n'est pas urgent » est correctement interprété comme une minoration, et non comme une urgence |

Ces trois messages ne sont importés **qu'une seule fois** : la contrainte d'unicité sur le
`Message-ID` empêche tout doublon, y compris après redémarrage de l'application.

---

## 5. Parcours de démonstration recommandé

Un enchaînement d'une dizaine de minutes qui traverse toutes les fonctions.

### Étape 1 — Sécurité et rôles (2 min)

1. Tenter de se connecter avec `ancien.collaborateur@stb.tn` → **refus**, compte désactivé.
2. Se connecter en `agence.sfax@stb.tn` → le menu ne montre que l'espace demandeur.
3. Se connecter en `admin@stb.tn` → le menu expose l'administration et la boîte de réception.

### Étape 2 — Cycle de vie d'une demande (3 min)

1. En `agence.sfax@stb.tn`, créer une demande.
2. En `chef.applications@stb.tn`, l'affecter à Wassim, puis la passer **En cours**.
3. Tenter de la passer directement à **Terminée** → refus, saut d'étape interdit.
4. Passer par **En validation**, puis **Terminée**.
5. Tenter de la rouvrir → refus, une demande clôturée est définitive.
6. Ouvrir l'onglet historique : les quatre transitions sont horodatées et signées.

### Étape 3 — Messagerie (2 min)

Ouvrir **DEM-2026-003** en `service.credit@stb.tn`, lire le fil, répondre. Le séparateur de
journée, le regroupement par auteur et l'accusé de lecture sont visibles.

### Étape 4 — Boîte de réception (3 min)

1. En `admin@stb.tn`, ouvrir **Boîte de réception**, cliquer sur **Relever maintenant**.
2. Ouvrir le premier e-mail : la mention « Compte reconnu : Sirine Ben Cheikh » apparaît.
3. Cliquer **Qualifier en demande** : le formulaire est déjà rempli, type et priorité
   proposés. Corriger si besoin, valider.
4. Sur le deuxième e-mail, **Écarter** avec un motif : il bascule dans l'onglet « Écartés »,
   sans être supprimé.
5. Revenir sur l'onglet « Convertis » : le numéro de demande est affiché en regard de
   l'e-mail d'origine — la traçabilité est complète.

### Étape 5 — Tableaux de bord (2 min)

Se connecter successivement en développeur, chef de projet puis administrateur : le même
écran affiche un périmètre différent, cadré côté serveur. Montrer le donut par statut avec
son total au centre, la répartition par priorité dont la couleur suit la gravité, et la
courbe d'évolution sur six mois.

---

## 6. Vérification automatisée

Un scénario de test parcourt l'ensemble de l'API et vérifie 43 points de contrôle —
authentification, habilitations, cycle de vie, messagerie, notifications, statistiques et
boîte de réception :

```bash
python devops/tests/test_fonctionnel.py
```

Il crée ses propres comptes (`test.*@stb.tn`, mot de passe `TestAuto123`) afin de ne pas
dépendre du jeu de démonstration, et n'effectue aucune suppression.

---

## 7. Point de vigilance sécurité

L'inscription publique `POST /api/auth/register` accepte actuellement un champ `role` :
n'importe qui peut donc créer un compte **administrateur** sans être authentifié. À corriger
avant toute mise en service — forcer le rôle `DEMANDEUR` à l'inscription et ne laisser
l'attribution de rôle qu'aux points d'entrée `/api/admin/users`, déjà protégés.
