import os, sys
from xml.sax.saxutils import escape

# key: (en, cs, es, it, fr, de)
S = {
 "status_connected": ("HT-102 · connected · %1$s Hz", "HT-102 · připojeno · %1$s Hz", "HT-102 · conectada · %1$s Hz", "HT-102 · collegata · %1$s Hz", "HT-102 · connectée · %1$s Hz", "HT-102 · verbunden · %1$s Hz"),
 "status_simulator": ("Simulator · %1$s Hz", "Simulátor · %1$s Hz", "Simulador · %1$s Hz", "Simulatore · %1$s Hz", "Simulateur · %1$s Hz", "Simulator · %1$s Hz"),
 "status_connecting": ("Connecting…", "Připojuji…", "Conectando…", "Connessione…", "Connexion…", "Verbinde…"),
 "status_needs_permission": ("Allow USB access", "Povol přístup k USB", "Permite el acceso USB", "Consenti l'accesso USB", "Autorise l'accès USB", "USB-Zugriff erlauben"),
 "status_disconnected": ("Camera disconnected", "Kamera odpojena", "Cámara desconectada", "Termocamera scollegata", "Caméra déconnectée", "Kamera getrennt"),
 "status_error_no_data": ("Error: no data", "Chyba: žádná data", "Error: sin datos", "Errore: nessun dato", "Erreur : aucune donnée", "Fehler: keine Daten"),
 "status_error_protocol": ("HT-102 found · thermal data not supported yet", "HT-102 nalezena · termo data zatím nepodporována", "HT-102 detectada · datos térmicos aún no compatibles", "HT-102 rilevata · dati termici non ancora supportati", "HT-102 détectée · données thermiques pas encore prises en charge", "HT-102 erkannt · Wärmedaten noch nicht unterstützt"),
 "mode_thermal": ("Thermal", "Termo", "Térmica", "Termica", "Thermique", "Thermo"),
 "mode_overlay": ("Overlay", "Překryv", "Superpuesta", "Sovrapposta", "Superposée", "Überlagert"),
 "mode_side_by_side": ("Side by side", "Vedle sebe", "Lado a lado", "Affiancate", "Côte à côte", "Geteilt"),
 "mode_photo": ("Photo", "Běžná", "Foto", "Foto", "Photo", "Foto"),
 "label_min": ("MIN", "MIN", "MÍN", "MIN", "MIN", "MIN"),
 "label_center": ("CENTER", "STŘED", "CENTRO", "CENTRO", "CENTRE", "MITTE"),
 "label_max": ("MAX", "MAX", "MÁX", "MAX", "MAX", "MAX"),
 "blend": ("Blend", "Prolnutí", "Fusión", "Fusione", "Fondu", "Überblendung"),
 "range_auto": ("Automatic", "Automaticky", "Automático", "Automatico", "Automatique", "Automatisch"),
 "range_interior": ("Interior", "Interiér", "Interior", "Interno", "Intérieur", "Innenraum"),
 "range_facade": ("Facade outside", "Fasáda zvenku", "Fachada exterior", "Facciata esterna", "Façade extérieure", "Fassade außen"),
 "range_heating": ("Heating, pipes", "Topení, rozvody", "Calefacción, tuberías", "Riscaldamento, tubazioni", "Chauffage, conduites", "Heizung, Leitungen"),
 "range_full": ("Full range", "Celý rozsah", "Rango completo", "Intervallo completo", "Plage complète", "Gesamter Bereich"),
 "range_custom": ("Custom", "Vlastní", "Personalizado", "Personalizzato", "Personnalisée", "Benutzerdefiniert"),
 "chip_range": ("Range: %1$s", "Rozsah: %1$s", "Rango: %1$s", "Intervallo: %1$s", "Plage : %1$s", "Bereich: %1$s"),
 "palette_iron": ("Iron", "Iron", "Hierro", "Ferro", "Fer", "Eisen"),
 "palette_rainbow": ("Rainbow", "Duha", "Arcoíris", "Arcobaleno", "Arc-en-ciel", "Regenbogen"),
 "palette_gray": ("Gray", "Šedá", "Gris", "Grigio", "Gris", "Grau"),
 "palette_arctic": ("Arctic", "Arktida", "Ártico", "Artico", "Arctique", "Arktis"),
 "action_add_point": ("Point", "Bod", "Punto", "Punto", "Point", "Punkt"),
 "cd_gallery": ("Gallery", "Galerie", "Galería", "Galleria", "Galerie", "Galerie"),
 "cd_shutter": ("Save image", "Uložit snímek", "Guardar imagen", "Salva immagine", "Enregistrer l'image", "Bild speichern"),
 "cd_video": ("Record video", "Nahrát video", "Grabar vídeo", "Registra video", "Enregistrer une vidéo", "Video aufnehmen"),
 "cd_settings": ("Settings", "Nastavení", "Ajustes", "Impostazioni", "Paramètres", "Einstellungen"),
 "cd_flashlight": ("Flashlight", "Svítilna", "Linterna", "Torcia", "Lampe torche", "Taschenlampe"),
 "cd_back": ("Back", "Zpět", "Atrás", "Indietro", "Retour", "Zurück"),
 "cd_range_locked": ("Range locked", "Rozsah zamčen", "Rango bloqueado", "Intervallo bloccato", "Plage verrouillée", "Bereich gesperrt"),
 "cd_crosshair": ("Center temperature %1$s", "Teplota ve středu %1$s", "Temperatura central %1$s", "Temperatura al centro %1$s", "Température au centre %1$s", "Temperatur in der Mitte %1$s"),
 "cd_scale": ("Temperature scale from %1$s to %2$s", "Teplotní stupnice od %1$s do %2$s", "Escala de temperatura de %1$s a %2$s", "Scala di temperatura da %1$s a %2$s", "Échelle de température de %1$s à %2$s", "Temperaturskala von %1$s bis %2$s"),
 "coming_later": ("Available in a later version", "Bude v některé z dalších verzí", "Disponible en una versión posterior", "Disponibile in una versione successiva", "Disponible dans une version ultérieure", "In einer späteren Version verfügbar"),
 "badge_emissivity": ("ε %1$s", "ε %1$s", "ε %1$s", "ε %1$s", "ε %1$s", "ε %1$s"),
 "facade_warning": ("The camera is specified for 0–35 °C. Readings outdoors in frost are unreliable.", "Kamera je určená pro 0–35 °C. Měření venku v mrazu je nespolehlivé.", "La cámara está especificada para 0–35 °C. Las mediciones en exterior con helada no son fiables.", "La termocamera è specificata per 0–35 °C. Le misure all'aperto con il gelo non sono affidabili.", "La caméra est prévue pour 0–35 °C. Les mesures en extérieur par temps de gel ne sont pas fiables.", "Die Kamera ist für 0–35 °C spezifiziert. Messungen draußen bei Frost sind unzuverlässig."),
 "disconnected_title": ("Connect the HT-102 thermal camera", "Připoj termokameru HT-102", "Conecta la cámara térmica HT-102", "Collega la termocamera HT-102", "Connecte la caméra thermique HT-102", "Wärmebildkamera HT-102 anschließen"),
 "disconnected_body": ("Plug the module into the USB-C port of your phone or tablet.", "Zasuň modul do USB-C portu telefonu nebo tabletu.", "Conecta el módulo al puerto USB-C del teléfono o la tableta.", "Inserisci il modulo nella porta USB-C del telefono o del tablet.", "Branche le module sur le port USB-C du téléphone ou de la tablette.", "Stecke das Modul in den USB-C-Anschluss von Smartphone oder Tablet."),
 "disconnected_help_title": ("If the camera does not appear", "Když se kamera neobjeví", "Si la cámara no aparece", "Se la termocamera non compare", "Si la caméra n'apparaît pas", "Wenn die Kamera nicht erscheint"),
 "disconnected_help_body": ("• Push the module in all the way\n• Turn on OTG / powering of external devices\n• Unplug the camera and plug it in again", "• Zasuň modul až na doraz\n• Zapni OTG / napájení externích zařízení\n• Kameru odpoj a znovu připoj", "• Inserta el módulo hasta el fondo\n• Activa OTG / la alimentación de dispositivos externos\n• Desconecta la cámara y vuelve a conectarla", "• Inserisci il modulo fino in fondo\n• Attiva OTG / l'alimentazione dei dispositivi esterni\n• Scollega la termocamera e ricollegala", "• Enfonce le module jusqu'au bout\n• Active l'OTG / l'alimentation des appareils externes\n• Débranche la caméra puis rebranche-la", "• Modul bis zum Anschlag einstecken\n• OTG / Stromversorgung externer Geräte einschalten\n• Kamera abziehen und wieder anstecken"),
 "action_use_simulator": ("Use simulator", "Použít simulátor", "Usar simulador", "Usa il simulatore", "Utiliser le simulateur", "Simulator verwenden"),
 "settings_title": ("Settings", "Nastavení", "Ajustes", "Impostazioni", "Paramètres", "Einstellungen"),
 "settings_section_appearance": ("Appearance", "Vzhled", "Apariencia", "Aspetto", "Apparence", "Darstellung"),
 "theme_system": ("System", "Podle systému", "Sistema", "Sistema", "Système", "System"),
 "theme_light": ("Light", "Světlý", "Claro", "Chiaro", "Clair", "Hell"),
 "theme_dark": ("Dark", "Tmavý", "Oscuro", "Scuro", "Sombre", "Dunkel"),
 "settings_reduce_transparency": ("Reduce transparency", "Omezit průhlednost", "Reducir transparencia", "Riduci trasparenza", "Réduire la transparence", "Transparenz reduzieren"),
 "settings_reduce_transparency_summary": ("Solid panels instead of blurred glass", "Plné panely místo rozmazaného skla", "Paneles opacos en lugar de vidrio difuminado", "Pannelli pieni invece del vetro sfocato", "Panneaux pleins au lieu du verre flou", "Volle Flächen statt Milchglas"),
 "settings_section_language": ("App language", "Jazyk aplikace", "Idioma de la aplicación", "Lingua dell'app", "Langue de l'application", "App-Sprache"),
 "language_auto": ("By phone · %1$s", "Podle telefonu · %1$s", "Según el teléfono · %1$s", "Come il telefono · %1$s", "Comme le téléphone · %1$s", "Wie Telefon · %1$s"),
 "settings_section_advanced": ("Advanced", "Pro pokročilé", "Avanzado", "Avanzate", "Avancé", "Erweitert"),
 "settings_simulator": ("Camera simulator", "Simulátor kamery", "Simulador de cámara", "Simulatore della termocamera", "Simulateur de caméra", "Kamerasimulator"),
 "settings_simulator_summary": ("Synthetic room scene instead of the HT-102", "Syntetická scéna místnosti místo HT-102", "Escena sintética de una habitación en lugar de la HT-102", "Scena sintetica di una stanza al posto della HT-102", "Scène synthétique d'une pièce à la place de la HT-102", "Synthetische Raumszene statt der HT-102"),
 "settings_usb_diagnostics": ("USB diagnostics", "USB diagnostika", "Diagnóstico USB", "Diagnostica USB", "Diagnostic USB", "USB-Diagnose"),
 "settings_usb_diagnostics_summary": ("Devices, interfaces, descriptors, export", "Zařízení, rozhraní, deskriptory, export", "Dispositivos, interfaces, descriptores, exportación", "Dispositivi, interfacce, descrittori, esportazione", "Appareils, interfaces, descripteurs, export", "Geräte, Schnittstellen, Deskriptoren, Export"),
 "settings_section_about": ("About", "O aplikaci", "Acerca de", "Informazioni", "À propos", "Über die App"),
 "settings_version": ("Version %1$s", "Verze %1$s", "Versión %1$s", "Versione %1$s", "Version %1$s", "Version %1$s"),
 "diag_no_devices": ("No USB device is connected.", "Není připojené žádné USB zařízení.", "No hay ningún dispositivo USB conectado.", "Nessun dispositivo USB collegato.", "Aucun appareil USB n'est connecté.", "Kein USB-Gerät angeschlossen."),
 "diag_read_descriptors": ("Read descriptors", "Načíst deskriptory", "Leer descriptores", "Leggi descrittori", "Lire les descripteurs", "Deskriptoren lesen"),
 "diag_export": ("Export", "Exportovat", "Exportar", "Esporta", "Exporter", "Exportieren"),
 "diag_copy": ("Copy", "Kopírovat", "Copiar", "Copia", "Copier", "Kopieren"),
 "diag_refresh": ("Refresh", "Obnovit", "Actualizar", "Aggiorna", "Actualiser", "Aktualisieren"),
 "diag_permission_denied": ("USB access was not granted.", "Přístup k USB nebyl povolen.", "No se ha concedido el acceso USB.", "L'accesso USB non è stato concesso.", "L'accès USB n'a pas été accordé.", "USB-Zugriff wurde nicht erteilt."),
 "diag_ht102_found": ("HT-102 detected", "Nalezena HT-102", "HT-102 detectada", "HT-102 rilevata", "HT-102 détectée", "HT-102 erkannt"),
 "diag_share_subject": ("ThermoCamera – USB diagnostics", "ThermoCamera – USB diagnostika", "ThermoCamera – diagnóstico USB", "ThermoCamera – diagnostica USB", "ThermoCamera – diagnostic USB", "ThermoCamera – USB-Diagnose"),
 "diag_share_chooser": ("Send diagnostics", "Poslat diagnostiku", "Enviar diagnóstico", "Invia diagnostica", "Envoyer le diagnostic", "Diagnose senden"),
 "temp_celsius": ("%1$s\u00a0°C", "%1$s\u00a0°C", "%1$s\u00a0°C", "%1$s\u00a0°C", "%1$s\u00a0°C", "%1$s\u00a0°C"),
}

NONTRANSLATABLE = {
 "app_name": "ThermoCamera",
 "lang_code_cs": "CZE", "lang_code_en": "ENG", "lang_code_es": "ESP", "lang_code_it": "ITA", "lang_code_fr": "FRA", "lang_code_de": "DEU",
 "lang_name_cs": "Čeština", "lang_name_en": "English", "lang_name_es": "Español", "lang_name_it": "Italiano", "lang_name_fr": "Français", "lang_name_de": "Deutsch",
}

LANGS = ["", "cs", "es", "it", "fr", "de"]

def esc(v):
    v = escape(v).replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if v.startswith("@") or v.startswith("?"):
        v = "\\" + v
    return v

root = sys.argv[1]
for i, lang in enumerate(LANGS):
    d = os.path.join(root, "values" + ("-" + lang if lang else ""))
    os.makedirs(d, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    if not lang:
        for k, v in NONTRANSLATABLE.items():
            lines.append(f'    <string name="{k}" translatable="false">{esc(v)}</string>')
    for k, vals in S.items():
        v = vals[i]
        fmt = ' formatted="true"' if "%" in v else ""
        lines.append(f'    <string name="{k}">{esc(v)}</string>')
    lines.append("</resources>")
    with open(os.path.join(d, "strings.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
print("ok", len(S), "keys")
