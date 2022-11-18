# APIGenerator

## Bachelor Thesis Jan-Philipp Andresen

### Thematik

Tool zur Generierung und Update von API-Schnittstellen durch eine Spezifikationsdatei nach dem OpenAPI v. 3.0 Standard.
Geschäftslogik und generierter Code sind soweit voneinander getrennt, das die zu erstellende Geschäftslogik bei einem neu generieren
nicht überschrieben wird und damit weiter vorhanden ist. Der Compiler macht dann in der Geschäftslogik über etwaige Änderungen
aufmerksam.

### Struktur

Dokumentation liegt im Ordner `documents`. Zum Einstieg in das Projekt und wie dieses Aufgebaut ist sind hier
Dokumentationen vorhanden.

### Wichtiges

Einige Vorgaben zu der Spezifikationsdatei ist im Ordner `documents` in der Datei `Vorgaben Spezifikationsdatei` zu
finden

### Development

- IDE: JetBrains IntelliJ IDEA Ultimate
- Language: Kotlin
- SDK: Java SDK 17

### Branching

[contributing.md](contributing.md "contributing")

### Nutzung

#### Maven

Den Generator entweder auf Maven Local veröffentlichen, oder auf einem Publish Server wie Nexus veröffentlichen. Danach
kann dieser in ein Maven Projekt als Plugin integriert werden.

#### Library

Das Generator Projekt kann auch über zwei Wege als Bibliothek in ein Projekt integriert werden. Zum einen das Projekt
builden und die `apigenerator-1.0.0-SNAPSHOT.jar` in das gewünschte Projekt einbinden. Wenn das Projekt auf einen Nexus
Server veröffentlicht wird, wird ebenfalls das Projekt zur Nutzung als Bibliothek mit veröffentlicht. Die `.jar` Datei
dann von dort herunterladen und in das Projekt integrieren.

#### Projektintern Testen

Unter `openapi-generator/src/test/kotlin` befindet sich eine `Test.kt` Datei. In dieser einfach die gewünschten
Parameter anpassen und ausführen. In dem Verzeichnis `target` oder welches ggf. selbst angegeben wurde, sind dann die
generierten Dateien zu finden.
