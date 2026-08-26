"""
Test fonctionnel de bout en bout de la plateforme STB Bank.

Parcourt l'API avec les comptes du jeu de demonstration et verifie chaque fonction :
authentification, habilitations, demandes, messagerie, notifications, statistiques et
boite de reception partagee. N'utilise aucune suppression : le jeu de donnees reste
exploitable apres execution.
"""

import json
import urllib.error
import urllib.request

BASE = "http://localhost:8082/api"
MDP = "TestAuto123"

# Comptes dedies au scenario : la base de developpement contient deja des utilisateurs
# reels dont les mots de passe sont inconnus. Ces comptes sont crees a la volee s'ils
# n'existent pas, et reutilises aux executions suivantes.
COMPTES = {
    "admin": "test.auto@stb.tn",
    "chef": "test.chef@stb.tn",
    "dev": "test.dev@stb.tn",
    "demandeur": "test.demandeur@stb.tn",
}

ROLES = {
    "admin": "ADMINISTRATEUR",
    "chef": "CHEF_DE_PROJET",
    "dev": "DEVELOPPEUR",
    "demandeur": "DEMANDEUR",
}

resultats = []


def appel(methode, chemin, jeton=None, corps=None, attendu=200):
    """Execute un appel HTTP et renvoie (code, donnees)."""
    url = BASE + chemin
    donnees = json.dumps(corps).encode() if corps is not None else None
    requete = urllib.request.Request(url, data=donnees, method=methode)
    requete.add_header("Content-Type", "application/json")
    if jeton:
        requete.add_header("Authorization", "Bearer " + jeton)

    try:
        with urllib.request.urlopen(requete, timeout=20) as reponse:
            brut = reponse.read().decode("utf-8")
            return reponse.status, (json.loads(brut) if brut else None)
    except urllib.error.HTTPError as e:
        brut = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(brut) if brut else None
        except json.JSONDecodeError:
            return e.code, brut
    except Exception as e:  # noqa: BLE001
        return 0, str(e)


def verifier(libelle, condition, detail=""):
    resultats.append((libelle, bool(condition), detail))
    marque = "OK  " if condition else "ECHEC"
    print(f"  [{marque}] {libelle}" + (f"  -> {detail}" if detail and not condition else ""))


def titre(texte):
    print(f"\n=== {texte} ===")


# ---------------------------------------------------------------- 1. Authentification
titre("1. Authentification")

jetons = {}
for role, email in COMPTES.items():
    code, data = appel("POST", "/auth/login", corps={"email": email, "motDePasse": MDP})
    if code != 200:
        # Premiere execution : le compte n'existe pas encore.
        appel("POST", "/auth/register", corps={
            "nom": f"Test {role}", "email": email, "motDePasse": MDP, "role": ROLES[role]})
        code, data = appel("POST", "/auth/login", corps={"email": email, "motDePasse": MDP})

    ok = code == 200 and isinstance(data, dict) and data.get("token")
    verifier(f"Connexion {role} ({email})", ok, f"code={code} rep={data}")
    if ok:
        jetons[role] = data["token"]
        verifier(f"Role {ROLES[role]} correctement porte", data.get("role") == ROLES[role],
                 f"recu={data.get('role')}")

code, _ = appel("POST", "/auth/login", corps={"email": COMPTES["admin"], "motDePasse": "mauvais"})
verifier("Mot de passe invalide rejete", code in (401, 403), f"code={code}")

code, _ = appel("GET", "/demandes")
verifier("Acces sans jeton refuse", code in (401, 403), f"code={code}")

if "admin" not in jetons:
    print("\nArret : impossible de poursuivre sans jeton administrateur.")
    raise SystemExit(1)

# ---------------------------------------------------------------- 2. Utilisateurs
titre("2. Gestion des utilisateurs (BF 2.1)")

code, data = appel("GET", "/admin/users/recherche?page=0&taille=10", jeton=jetons["admin"])
nb_utilisateurs = data.get("totalElements") if isinstance(data, dict) else 0
verifier("Admin liste les utilisateurs", code == 200 and nb_utilisateurs >= 6,
         f"code={code} total={nb_utilisateurs}")

code, _ = appel("GET", "/admin/users/recherche?page=0&taille=10", jeton=jetons.get("demandeur"))
verifier("Demandeur bloque sur l'administration", code == 403, f"code={code}")

# ---------------------------------------------------------------- 3. Demandes
titre("3. Cycle de vie des demandes")

code, data = appel("GET", "/demandes", jeton=jetons["chef"])
verifier("Chef liste les demandes", code == 200 and isinstance(data, list), f"code={code}")
demandes_chef = data if isinstance(data, list) else []

nouvelle = {
    "titre": "TEST AUTO - acces a l'application de credit",
    "description": "Demande creee par le scenario de test automatise.",
    "priorite": "HAUTE",
    "type": "CREATION_ACCES",
}
code, demande = appel("POST", "/demandes", jeton=jetons["demandeur"], corps=nouvelle)
creee = isinstance(demande, dict) and demande.get("id")
verifier("Demandeur cree une demande", code in (200, 201) and creee, f"code={code} rep={demande}")

if creee:
    demande_id = demande["id"]
    verifier("Numero attribue automatiquement", bool(demande.get("numero")), str(demande.get("numero")))
    verifier("Statut initial NOUVELLE", demande.get("statut") == "NOUVELLE", str(demande.get("statut")))

    code, data = appel("GET", f"/demandes/{demande_id}", jeton=jetons["demandeur"])
    verifier("Consultation de la demande", code == 200 and data.get("id") == demande_id, f"code={code}")

    code, data = appel("GET", "/demandes/recherche?page=0&taille=5&statut=NOUVELLE", jeton=jetons["chef"])
    verifier("Recherche multicritere paginee", code == 200 and "contenu" in (data or {}), f"code={code}")

    # Affectation a un developpeur
    code, data = appel("GET", "/users/affectables", jeton=jetons["chef"])
    affectables = data if isinstance(data, list) else []
    if affectables:
        code, data = appel("PUT", f"/demandes/{demande_id}/responsable", jeton=jetons["chef"],
                           corps={"responsableId": affectables[0]["id"]})
        verifier("Chef affecte un responsable", code == 200 and data.get("responsableId"), f"code={code}")
    else:
        verifier("Liste des utilisateurs affectables", False, "liste vide")

    code, data = appel("PUT", f"/demandes/{demande_id}/statut", jeton=jetons["chef"],
                       corps={"statut": "EN_COURS"})
    verifier("Transition NOUVELLE -> EN_COURS", code == 200 and data.get("statut") == "EN_COURS",
             f"code={code} rep={data}")

    # Le retour arriere est volontairement autorise tant que la demande n'est pas cloturee
    # (voir StatutDemande.transitionsAutorisees) : on verifie qu'il fonctionne, puis on
    # repositionne la demande en cours pour la suite du scenario.
    code, data = appel("PUT", f"/demandes/{demande_id}/statut", jeton=jetons["chef"],
                       corps={"statut": "NOUVELLE"})
    verifier("Retour arriere EN_COURS -> NOUVELLE autorise",
             code == 200 and (data or {}).get("statut") == "NOUVELLE", f"code={code}")

    # Saut d'etape : NOUVELLE ne mene qu'a EN_COURS ou REJETEE
    code, data = appel("PUT", f"/demandes/{demande_id}/statut", jeton=jetons["chef"],
                       corps={"statut": "TERMINEE"})
    verifier("Saut d'etape NOUVELLE -> TERMINEE refuse", code == 409, f"code={code} rep={data}")

    appel("PUT", f"/demandes/{demande_id}/statut", jeton=jetons["chef"], corps={"statut": "EN_COURS"})
    code, data = appel("PUT", f"/demandes/{demande_id}/statut", jeton=jetons["chef"],
                       corps={"statut": "EN_VALIDATION"})
    verifier("Transition EN_COURS -> EN_VALIDATION", code == 200 and (data or {}).get("statut") == "EN_VALIDATION",
             f"code={code}")

    code, data = appel("PUT", f"/demandes/{demande_id}/statut", jeton=jetons["chef"],
                       corps={"statut": "TERMINEE"})
    verifier("Cloture par le chef de projet", code == 200 and (data or {}).get("statut") == "TERMINEE",
             f"code={code}")

    code, data = appel("PUT", f"/demandes/{demande_id}/statut", jeton=jetons["chef"],
                       corps={"statut": "EN_COURS"})
    verifier("Demande cloturee non reouvrable", code == 409, f"code={code}")

    code, data = appel("PUT", f"/demandes/{demande_id}/statut", jeton=jetons["demandeur"],
                       corps={"statut": "REJETEE"})
    verifier("Demandeur ne peut pas rejeter lui-meme", code in (403, 409), f"code={code}")

    code, data = appel("GET", f"/demandes/{demande_id}/historique", jeton=jetons["chef"])
    verifier("Historique des statuts trace", code == 200 and isinstance(data, list) and len(data) >= 2,
             f"code={code} n={len(data) if isinstance(data, list) else 0}")

    # ------------------------------------------------------------ 4. Messagerie
    titre("4. Messagerie interne")

    code, message = appel("POST", f"/demandes/{demande_id}/messages", jeton=jetons["demandeur"],
                          corps={"contenu": "Bonjour, pouvez-vous confirmer le delai ?"})
    verifier("Envoi d'un message", code in (200, 201) and message.get("id"), f"code={code}")

    code, message2 = appel("POST", f"/demandes/{demande_id}/messages", jeton=jetons["chef"],
                           corps={"contenu": "Bien recu, traitement en cours."})
    verifier("Reponse du chef de projet", code in (200, 201) and message2.get("id"), f"code={code}")

    code, fil = appel("GET", f"/demandes/{demande_id}/messages", jeton=jetons["demandeur"])
    verifier("Lecture du fil de discussion", code == 200 and len(fil or []) >= 2,
             f"code={code} n={len(fil or [])}")

    code, _ = appel("PUT", f"/demandes/{demande_id}/messages/lus", jeton=jetons["demandeur"])
    verifier("Marquage des messages comme lus", code in (200, 204), f"code={code}")

# ---------------------------------------------------------------- 5. Notifications
titre("5. Notifications")

code, data = appel("GET", "/notifications", jeton=jetons["chef"])
verifier("Liste des notifications", code == 200 and isinstance(data, list), f"code={code}")

code, data = appel("GET", "/notifications/non-lues/compte", jeton=jetons["chef"])
verifier("Compteur de notifications non lues", code == 200 and "nonLues" in (data or {}), f"code={code}")

# ---------------------------------------------------------------- 6. Statistiques
titre("6. Tableaux de bord")

for role in ("admin", "chef", "dev", "demandeur"):
    if role not in jetons:
        continue
    code, data = appel("GET", "/demandes/statistiques", jeton=jetons[role])
    champs = {"total", "ouvertes", "cloturees", "parStatut", "parPriorite", "parType"}
    ok = code == 200 and isinstance(data, dict) and champs.issubset(data.keys())
    verifier(f"Statistiques visibles par {role}", ok, f"code={code} cles={list((data or {}).keys())}")

# ---------------------------------------------------------------- 7. Boite de reception
titre("7. Boite de reception partagee (nouvelle fonction)")

code, data = appel("POST", "/emails/relever", jeton=jetons["admin"])
verifier("Admin declenche une releve", code == 200 and "importes" in (data or {}),
         f"code={code} rep={data}")
importes = (data or {}).get("importes", 0)

code, _ = appel("POST", "/emails/relever", jeton=jetons.get("chef"))
verifier("Releve interdite au chef de projet", code == 403, f"code={code}")

code, _ = appel("GET", "/emails", jeton=jetons.get("demandeur"))
verifier("Boite interdite au demandeur", code == 403, f"code={code}")

code, page = appel("GET", "/emails?statut=NON_TRAITE&page=0&taille=10", jeton=jetons["chef"])
emails = (page or {}).get("contenu", [])
verifier("Chef consulte la boite", code == 200 and isinstance(emails, list),
         f"code={code} n={len(emails)}")

# Idempotence : une seconde releve ne doit rien reimporter
code, data = appel("POST", "/emails/relever", jeton=jetons["admin"])
verifier("Seconde releve sans doublon", code == 200 and (data or {}).get("importes") == 0,
         f"importes={(data or {}).get('importes')}")

if emails:
    premier = emails[0]
    verifier("Pre-qualification : type propose", bool(premier.get("typePropose")),
             str(premier.get("typePropose")))
    verifier("Pre-qualification : priorite proposee", bool(premier.get("prioriteProposee")),
             str(premier.get("prioriteProposee")))
    verifier("Aucun HTML conserve dans le corps",
             "<script" not in (premier.get("corpsTexte") or "").lower(), "")

    code, detail = appel("GET", f"/emails/{premier['id']}", jeton=jetons["chef"])
    verifier("Consultation d'un e-mail", code == 200 and detail.get("id") == premier["id"], f"code={code}")

    # Conversion en demande
    code, demande_mail = appel("POST", f"/emails/{premier['id']}/convertir", jeton=jetons["chef"], corps={
        "titre": premier["sujet"][:120],
        "description": premier.get("corpsTexte") or "",
        "type": premier.get("typePropose") or "ASSISTANCE",
        "priorite": premier.get("prioriteProposee") or "MOYENNE",
    })
    converti = isinstance(demande_mail, dict) and demande_mail.get("numero")
    verifier("Conversion d'un e-mail en demande", code == 200 and converti,
             f"code={code} rep={demande_mail}")

    # Double conversion interdite
    code, _ = appel("POST", f"/emails/{premier['id']}/convertir", jeton=jetons["chef"], corps={
        "titre": "Seconde tentative", "type": "ASSISTANCE", "priorite": "BASSE",
    })
    verifier("Double conversion refusee", code == 409, f"code={code}")

    code, page = appel("GET", "/emails?statut=CONVERTI&page=0&taille=10", jeton=jetons["chef"])
    convertis = (page or {}).get("contenu", [])
    trace = any(e["id"] == premier["id"] and e.get("numeroDemande") for e in convertis)
    verifier("E-mail relie a sa demande (tracabilite)", trace, f"n={len(convertis)}")

code, page = appel("GET", "/emails?statut=NON_TRAITE&page=0&taille=10", jeton=jetons["chef"])
restants = (page or {}).get("contenu", [])
if restants:
    second = restants[0]
    code, _ = appel("POST", f"/emails/{second['id']}/ignorer", jeton=jetons["chef"],
                    corps={"motif": ""})
    verifier("Motif obligatoire pour ecarter", code == 400, f"code={code}")

    code, ignore = appel("POST", f"/emails/{second['id']}/ignorer", jeton=jetons["chef"],
                         corps={"motif": "Hors perimetre - test automatise"})
    verifier("Mise a l'ecart avec motif", code == 200 and ignore.get("statut") == "IGNORE",
             f"code={code} rep={ignore}")

code, data = appel("GET", "/emails/non-traites/compte", jeton=jetons["chef"])
verifier("Compteur d'e-mails a qualifier", code == 200 and "nonTraites" in (data or {}),
         f"code={code} rep={data}")

# ---------------------------------------------------------------- Synthese
titre("Synthese")

total = len(resultats)
succes = sum(1 for _, ok, _ in resultats if ok)
echecs = [(libelle, detail) for libelle, ok, detail in resultats if not ok]

print(f"\n{succes}/{total} verifications reussies")
if echecs:
    print(f"\n{len(echecs)} echec(s) :")
    for libelle, detail in echecs:
        print(f"  - {libelle}")
        if detail:
            print(f"      {detail}")
    raise SystemExit(1)

print("\nToutes les fonctions repondent conformement.")
