#!/usr/bin/env python3
"""
bump_version.py - Automatisation de la mise à jour de version pour Ocarina
Met à jour le numéro de version de l'application à tous les endroits du code :
  1. app/build.gradle.kts (versionCode & versionName)
  2. .fdroid.yml (entrée dans Builds:)
  3. metadata/fr.galaxie.ocarina.yml (entrée dans Builds:)
  4. app/src/main/java/com/example/ui/Localization.kt (FrenchStrings & EnglishStrings)
  5. app/src/test/java/com/example/ExampleRobolectricTest.kt (tests unitaires de version)
  6. fastlane/metadata/android/{fr-FR,en-US}/changelogs/<versionCode>.txt (optionnel)
"""

import argparse
import os
import re
import sys
from pathlib import Path


def find_repo_root() -> Path:
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "app" / "build.gradle.kts").exists():
            return current
        current = current.parent
    return Path.cwd()


def get_current_versions(root: Path) -> tuple[int, str]:
    gradle_file = root / "app" / "build.gradle.kts"
    if not gradle_file.exists():
        raise FileNotFoundError(f"Fichier introuvable: {gradle_file}")

    content = gradle_file.read_text(encoding="utf-8")
    code_match = re.search(r"versionCode\s*=\s*(\d+)", content)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)

    if not code_match:
        raise ValueError("Impossible de trouver versionCode dans app/build.gradle.kts")
    if not name_match:
        raise ValueError("Impossible de trouver versionName dans app/build.gradle.kts")

    return int(code_match.group(1)), name_match.group(1)


def format_display_version(version_code: int, version_name: str) -> str:
    """Calcule une version sémantique d'affichage (ex: 2.1.4) à partir du code ou du nom."""
    if "." in version_name:
        return version_name
    digits = str(version_code)
    if len(digits) == 3:
        return f"{digits[0]}.{digits[1]}.{digits[2]}"
    elif len(digits) == 4:
        return f"{digits[0]}.{digits[1]}.{digits[2:]}"
    return digits


def update_build_gradle(root: Path, new_code: int, new_name: str, dry_run: bool) -> bool:
    gradle_file = root / "app" / "build.gradle.kts"
    content = gradle_file.read_text(encoding="utf-8")

    new_content = re.sub(
        r"versionCode\s*=\s*\d+",
        f"versionCode = {new_code}",
        content
    )
    new_content = re.sub(
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{new_name}"',
        new_content
    )

    if content == new_content:
        return False

    if not dry_run:
        gradle_file.write_text(new_content, encoding="utf-8")
    return True


def update_fdroid_yaml(file_path: Path, new_code: int, new_name: str, commit_tag: str, dry_run: bool) -> bool:
    if not file_path.exists():
        print(f"  [AVERTISSEMENT] Fichier introuvable : {file_path}")
        return False

    content = file_path.read_text(encoding="utf-8")

    # Vérifie si le versionCode existe déjà
    if re.search(rf"versionCode:\s*{new_code}\b", content):
        print(f"  [INFO] Version {new_code} déjà présente dans {file_path.name}")
        return False

    build_block = (
        f"  - versionName: '{new_name}'\n"
        f"    versionCode: {new_code}\n"
        f"    commit: {commit_tag}\n"
        f"    subdir: app\n"
        f"    gradle:\n"
        f"      - yes\n"
    )

    # Ajout à la fin de la section Builds:
    if "Builds:" in content:
        # Trouver la dernière entrée ou ajouter à la fin
        content_stripped = content.rstrip()
        new_content = content_stripped + "\n\n" + build_block
    else:
        new_content = content.rstrip() + "\n\nBuilds:\n" + build_block

    if not dry_run:
        file_path.write_text(new_content, encoding="utf-8")
    return True


def update_localization(root: Path, new_code: int, display_version: str, dry_run: bool) -> bool:
    loc_file = root / "app" / "src" / "main" / "java" / "com" / "example" / "ui" / "Localization.kt"
    if not loc_file.exists():
        print(f"  [AVERTISSEMENT] Fichier introuvable : {loc_file}")
        return False

    content = loc_file.read_text(encoding="utf-8")
    target_pattern = r'appVersionLabel\s*=\s*"Version [^"]*"'
    replacement = f'appVersionLabel = "Version {display_version} (build {new_code})"'

    new_content, count = re.subn(target_pattern, replacement, content)

    if count == 0:
        print("  [AVERTISSEMENT] Impossible de trouver 'appVersionLabel' dans Localization.kt")
        return False

    if not dry_run:
        loc_file.write_text(new_content, encoding="utf-8")
    return True


def update_robolectric_test(root: Path, new_code: int, dry_run: bool) -> bool:
    test_file = root / "app" / "src" / "test" / "java" / "com" / "example" / "ExampleRobolectricTest.kt"
    if not test_file.exists():
        return False

    content = test_file.read_text(encoding="utf-8")

    # Remplace fun `test version <ancien> labels`()
    new_content = re.sub(
        r"fun\s+`test version \d+ labels`\(\)",
        f"fun `test version {new_code} labels`()",
        content
    )

    # Remplace .contains("<ancien>") par .contains("<new_code>")
    new_content = re.sub(
        r'(\.appVersionLabel\.contains\(")\d+("\))',
        rf"\g<1>{new_code}\g<2>",
        new_content
    )

    if content == new_content:
        return False

    if not dry_run:
        test_file.write_text(new_content, encoding="utf-8")
    return True


def update_changelogs(root: Path, new_code: int, changelog_fr: str, changelog_en: str, dry_run: bool) -> list[Path]:
    written = []
    base_fastlane = root / "fastlane" / "metadata" / "android"

    if changelog_fr:
        dir_fr = base_fastlane / "fr-FR" / "changelogs"
        if not dry_run:
            dir_fr.mkdir(parents=True, exist_ok=True)
            file_fr = dir_fr / f"{new_code}.txt"
            file_fr.write_text(changelog_fr.strip() + "\n", encoding="utf-8")
            written.append(file_fr)
        else:
            written.append(dir_fr / f"{new_code}.txt")

    if changelog_en:
        dir_en = base_fastlane / "en-US" / "changelogs"
        if not dry_run:
            dir_en.mkdir(parents=True, exist_ok=True)
            file_en = dir_en / f"{new_code}.txt"
            file_en.write_text(changelog_en.strip() + "\n", encoding="utf-8")
            written.append(file_en)
        else:
            written.append(dir_en / f"{new_code}.txt")

    return written


def main():
    parser = argparse.ArgumentParser(description="Automatise le changement de version dans tout le projet Ocarina.")
    parser.add_argument("--code", "-c", default="auto", help="Code de version Android (ex: 214 ou 'auto' pour incrémenter de 1)")
    parser.add_argument("--name", "-n", default="auto", help="Nom de version (ex: 214 ou 2.1.4, ou 'auto' pour matcher le code)")
    parser.add_argument("--display", "-d", default="auto", help="Version affichée dans l'interface (ex: 2.1.4 ou 'auto')")
    parser.add_argument("--changelog-fr", default="", help="Notes de version en Français pour Fastlane / F-Droid")
    parser.add_argument("--changelog-en", default="", help="Release notes in English for Fastlane / F-Droid")
    parser.add_argument("--dry-run", action="store_true", help="Simuler les modifications sans écrire sur le disque")
    args = parser.parse_args()

    root = find_repo_root()
    curr_code, curr_name = get_current_versions(root)
    print(f"📌 Version actuelle détectée : versionCode = {curr_code}, versionName = \"{curr_name}\"")

    # Calcul du nouveau versionCode
    if args.code.lower() == "auto" or not args.code.strip():
        new_code = curr_code + 1
    else:
        new_code = int(args.code.strip())

    # Calcul du nouveau versionName
    if args.name.lower() == "auto" or not args.name.strip():
        # Suit la convention Ocarina : versionName = str(versionCode) ex: "214"
        new_name = str(new_code)
    else:
        new_name = args.name.strip()

    # Calcul du display_version (ex: 2.1.4)
    if args.display.lower() == "auto" or not args.display.strip():
        display_version = format_display_version(new_code, new_name)
    else:
        display_version = args.display.strip()

    commit_tag = f"v{new_code}"

    print(f"🚀 Nouvelle version cible :")
    print(f"   • versionCode     : {new_code}")
    print(f"   • versionName     : {new_name}")
    print(f"   • display_version : {display_version}")
    print(f"   • tag Git         : {commit_tag}")
    if args.dry_run:
        print("   ⚠️  MODE DRY-RUN : Aucun fichier ne sera modifié.")

    updated_files = []

    # 1. app/build.gradle.kts
    if update_build_gradle(root, new_code, new_name, args.dry_run):
        updated_files.append("app/build.gradle.kts")

    # 2. .fdroid.yml
    if update_fdroid_yaml(root / ".fdroid.yml", new_code, new_name, commit_tag, args.dry_run):
        updated_files.append(".fdroid.yml")

    # 3. metadata/fr.galaxie.ocarina.yml
    if update_fdroid_yaml(root / "metadata" / "fr.galaxie.ocarina.yml", new_code, new_name, commit_tag, args.dry_run):
        updated_files.append("metadata/fr.galaxie.ocarina.yml")

    # 4. app/src/main/java/com/example/ui/Localization.kt
    if update_localization(root, new_code, display_version, args.dry_run):
        updated_files.append("app/src/main/java/com/example/ui/Localization.kt")

    # 5. app/src/test/java/com/example/ExampleRobolectricTest.kt
    if update_robolectric_test(root, new_code, args.dry_run):
        updated_files.append("app/src/test/java/com/example/ExampleRobolectricTest.kt")

    # 6. Fastlane changelogs
    cl_files = update_changelogs(root, new_code, args.changelog_fr, args.changelog_en, args.dry_run)
    for cl in cl_files:
        updated_files.append(str(cl.relative_to(root)))

    print("\n✅ Fichiers mis à jour :")
    for f in updated_files:
        print(f"   - {f}")

    # Exporter les variables pour GitHub Actions ($GITHUB_OUTPUT) si disponible
    github_output = os.getenv("GITHUB_OUTPUT")
    if github_output and not args.dry_run:
        with open(github_output, "a", encoding="utf-8") as out:
            out.write(f"version_code={new_code}\n")
            out.write(f"version_name={new_name}\n")
            out.write(f"display_version={display_version}\n")
            out.write(f"commit_tag={commit_tag}\n")
            out.write(f"previous_code={curr_code}\n")
            out.write(f"previous_name={curr_name}\n")


if __name__ == "__main__":
    main()
