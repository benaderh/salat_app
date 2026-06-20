# مواقيت الصلاة — Salat Times

Application Android native (Kotlin) hors-ligne pour les horaires de prière, avec
alarmes exactes garanties et mode silencieux automatique configurable par prière.

## Compiler l'APK via GitHub Actions (sans rien installer)

### Etape 1 — Creer le depot GitHub
1. Allez sur https://github.com/new
2. Nom du depot : `salat-times` (ou ce que vous voulez)
3. Laissez "Public" ou "Private", peu importe
4. Ne cochez AUCUNE case (pas de README, pas de .gitignore) — laissez vide
5. Cliquez "Create repository"

### Etape 2 — Pousser ce projet sur GitHub
GitHub vous montre une page avec des commandes. Sur votre ordinateur, ouvrez un
terminal dans le dossier `salat_app` (celui que vous avez telecharge) et tapez :

```bash
git init
git add .
git commit -m "Premiere version Salat Times"
git branch -M main
git remote add origin https://github.com/VOTRE_NOM_UTILISATEUR/salat-times.git
git push -u origin main
```

(Remplacez `VOTRE_NOM_UTILISATEUR` par votre identifiant GitHub. Git vous
demandera de vous authentifier — utilisez un "Personal Access Token" si demande,
pas votre mot de passe habituel : https://github.com/settings/tokens)

### Etape 3 — Recuperer l'APK compile
1. Sur la page de votre depot GitHub, cliquez sur l'onglet **"Actions"**
2. Vous verrez un workflow "Build APK" en cours d'execution (icone orange qui
   tourne), attendez 3-5 minutes qu'il devienne vert
3. Cliquez sur ce workflow termine
4. Tout en bas de la page, section **"Artifacts"**, telechargez `salat-times-apk`
5. Decompressez le zip telecharge : vous obtenez `app-release.apk`

### Etape 4 — Installer sur votre telephone
1. Transferez `app-release.apk` sur votre telephone (cable USB, Bluetooth, ou
   en l'envoyant via une appli de messagerie a vous-meme)
2. Sur le telephone, ouvrez le fichier — si Android bloque, allez dans
   Parametres > Securite > Autoriser l'installation depuis cette source
3. Installez normalement

### A chaque modification future
Si je vous donne des fichiers modifies, remplacez-les dans votre dossier local
puis repetez seulement :
```bash
git add .
git commit -m "Mise a jour"
git push
```
GitHub recompilera automatiquement un nouvel APK.

## Premiere ouverture de l'application
1. L'app demandera la permission d'exemption de la batterie — **acceptez**,
   c'est indispensable pour que les alarmes sonnent a l'heure exacte
2. Android demandera aussi la permission "Alarmes et rappels" — **acceptez**
3. Pour le mode silencieux automatique, quand vous l'activez dans les reglages,
   Android vous demandera l'acces "Ne pas deranger" — **acceptez** aussi

## Dossier des sons d'alarme et des donnees
Placez vos fichiers .mp3 dans :
```
/storage/emulated/0/SalatAthan/
```
Vous pourrez ensuite les choisir individuellement pour chaque priere et chaque
type d'alarme (avant / a l'heure) dans les reglages de l'app.

## Mise a jour des donnees (annee hijri suivante, ville modifiee, etc.)
Deux methodes :

**Methode 1 — import depuis le telephone (recommandee, pas besoin de recompiler) :**
1. Demandez-moi un nouveau `salat_data.json` a partir de votre fichier xls/xlsx mis a jour
2. Placez ce fichier dans `/storage/emulated/0/SalatAthan/salat_data.json`
3. Dans l'app, menu 3 points > "استيراد بيانات (JSON)" > confirmez
4. Pour revenir aux donnees d'origine de l'app a tout moment : menu 3 points >
   "إلغاء الاستيراد والرجوع للأصل"

**Methode 2 — remplacer les donnees embarquees (necessite un nouveau build) :**
Le fichier de donnees par defaut est `app/src/main/assets/salat_data.json`.
Remplacez-le et repoussez sur GitHub pour qu'un nouvel APK soit compile.
