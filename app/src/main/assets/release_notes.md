## 0.11.0

YUntis weckt dich jetzt selbst, schaltet im Unterricht stumm, erinnert an Entschuldigungen und teilt Hausaufgaben per QR-Code. Dazu Widget-Stile bis hin zum Post-it und eine Suche in den Einstellungen.

### Neu
* [Wecker] Eigener YUntis-Wecker: klingelt vor der ersten Stunde, die wirklich stattfindet, und verschiebt sich, wenn sie über Nacht ausfällt. Mit Vollbild über dem Sperrbildschirm, Schlummern und Probealarm
* [Ruhe] Im Unterricht automatisch „Nicht stören“ oder nur Vibration, in Pausen, Freistunden und nach der Schule wieder laut
* [Fehlzeiten] Tägliche Erinnerung, Entschuldigungen abzugeben, ab dem Tag nach der Fehlzeit, mit Restfrist. Gilt für Fehlzeiten der Schule und selbst eingetragene; „Abgegeben“ direkt in der Benachrichtigung oder in der Liste
* [Teilen] Hausaufgaben und Klausuren als QR-Code oder Link teilen und übernehmen
* [Teilen] „Meine Woche teilen“ zeigt euch die gemeinsame freie Zeit, ohne Fächer zu verraten
* [Widgets] Widget-Stil wählbar: Automatisch, Hell, Dunkel, Material You oder **Post-it** in fünf Papierfarben mit Klebestreifen und Handschrift, dazu einstellbare Deckkraft und Vorschau
* [Einstellungen] Suche über alle Einstellungen; ein Treffer springt direkt zur Stelle und hebt sie kurz hervor

### Verbessert
* [Wecker] Klingelt jetzt über einen eigenen Weckdienst auf dem Wecker-Kanal, wird langsam lauter, vibriert und läuft auch im Stumm-Modus
* [Wecker] Hinweise, wenn Benachrichtigungen aus sind oder die Weckerlautstärke auf 0 steht
* [Wecker] Während des Schlummerns zeigt eine Benachrichtigung die Weckzeit und lässt den Wecker ausschalten

### Behoben
* [Wecker] Der Weckerbildschirm erschien nicht über dem Sperrbildschirm, und der Probealarm konnte den echten Wecker überschreiben

### Intern
* Neue Tests für Weckzeiten, Teilen-Codes, Entschuldigungs-Erinnerungen und die Einstellungs-Suche

## 0.10.1

YUntis hat jetzt einen eigenen Signaturschlüssel. Updates installieren sich wie bisher einfach über die alte Version, alle Daten bleiben erhalten.

### Intern
* [Signatur] Eigener Release-Schlüssel (RSA 4096) mit Schlüssel-Rotation vom bisherigen Schlüssel, damit künftige Updates und das Auto-Update sauber funktionieren

## 0.10.0

Das bisher größte Update: Widgets, Kalender-Export, mehrere Konten nebeneinander, Klausur-Lernplan, eigene Fehlzeiten und vieles mehr.

### Neu
* [Widgets] Drei Homescreen-Widgets: **Tag**, **Nächste Stunde** und das große, frei skalierbare **Ganzer Tag** mit Freistunden und Blättern zwischen den Tagen
* [Konten] Konto wechseln mit einem Tipp aufs Profilbild
* [Konten] Zwei Stundenpläne **nebeneinander** als Tagesansicht
* [Kalender] Export in Google Kalender oder einen eigenen YUntis-Kalender: Klausuren, Veranstaltungen, Ferien, Hausaufgaben, Ausfälle und Vertretungen, automatisch oder per Knopf
* [Klausuren] Neue Seite mit Countdown, verbleibenden Stunden im Fach und Lernblöcken in Freistunden und Nachmittagen
* [Fehlzeiten] Eigene Fehlzeiten eintragen, Frist zum Entschuldigen und fertiger Entschuldigungstext zum Kopieren oder Teilen
* [Fehlzeiten] Benachrichtigung, sobald die Schule eine neue Fehlzeit einträgt
* [Stundenplan] Freistunden werden mit ihrer Länge markiert
* [Statistik] Fach-Jahresblick: ein Kalender pro Fach mit gehaltenen, vertretenen und ausgefallenen Stunden
* [Verlauf] Änderungsverlauf mit allen bemerkten Raum-, Lehrkraft- und Ausfalländerungen
* [Suche] Schnellsuche über Fächer, Lehrkräfte, Räume, Klausuren, Termine, Notizen und Mitteilungen
* [Benachrichtigungen] Wochenausblick am Sonntag
* [Benachrichtigungen] Aufsteh- und Losgeh-Rechner am Vorabend, wenn die erste Stunde ausfällt
* [Profil] Übersicht „Was meine Schule freigibt“
* [Updates] Nach neuen Versionen auf GitHub suchen, laden und installieren

### Verbessert
* [Stundenplan] Doppelstunden mit höchstens 5 Minuten Pause erscheinen als ein Block
* [Stunde] Details zeigen Hausaufgaben der Lehrkraft, „vertritt“ und Raumwechsel sowie ob die Anwesenheit kontrolliert wurde
* [Stundenplan] Sparsameres Aktualisieren über den Import-Zeitstempel der Schule, mit „Schule: 07:12“ in der Fußzeile
* [Navigation] Das Seitenmenü scrollt auf kleinen Bildschirmen

### Behoben
* [Stunde] Notizen der Schule lagen übereinander statt untereinander
* [Widgets] Widgets bleiben nach einem Update nicht mehr leer

### Intern
* Mehr als 60 neue Unit-Tests
