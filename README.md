# Musannif — مُصَنِّف


<div align="center">

![Release](https://img.shields.io/badge/Release-1.1.0-brightgreen) ![Java](https://img.shields.io/badge/Java-20%2B-blue) ![JavaFX](https://img.shields.io/badge/JavaFX-21-orange) ![Tests](https://img.shields.io/badge/Tests-JUnit5-success) ![License](https://img.shields.io/badge/License-MIT-lightgrey) ![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-informational)

<img src="src/main/resources/org/app/musannif/icons/Document2.png" width="1280" alt="Musannif Logo">

[Features](#features) • [Screenshots](#screenshots) • [Installation](#installation)

Musannif is a lightweight, cross-platform desktop application designed to eliminate time-wasted manual file organization. Scan any folder, preview the proposed structure, and apply changes with confidence — with full undo capability powered by snapshot technology.

**Version 1.1.0** | Developed by [@Muath](https://github.com/nullMuath) & [@Osama](https://github.com/MeCaveman)

</div>

## Features

| Feature | Description                                                          |
|---|----------------------------------------------------------------------|
| **Folder Selection** | Choose any folder to organize (Downloads, Documents, Projects, etc.) |
| **Organizing Preferences** | Group files by extension, date, or type category                     |
| **Preview Changes** | See the proposed folder structure before applying anything           |
| **Undo Operations** | Snapshot taken before every operation, restore with one click        |

### Organization Modes

```
By Extension       By Date              By Type Category
├── PDF/           ├── 2024-01/         ├── Documents/
├── EXE/           ├── 2025-02/         ├── Images/
├── ZIP/           ├── 2026-05/         ├── Videos/
└── ...            └── ...              └── ...
```

---

### Basic Workflow

1. **Select a Folder**
   - Click "Browse" and choose the directory to organize
   - The app scans all files recursively

2. **Choose Organization Mode**
   - Select from: By Extension, By Date, By Type Category
   - Customize rules in preferences (future releases)

3. **Preview Changes**
   - Review the proposed folder structure
   - See which files will move to which categories
   - No files are modified until you confirm

4. **Apply or Undo**
   - Click "Organize" to execute the changes
   - A snapshot is automatically saved
   - Click "Undo" anytime to restore to the previous state

## Screenshots

<div align="center">

<img src="screenshots/demo.gif" width="750">

**Main View — With Folder Preview Toggled on**
<img src="screenshots/organize-file-type-preview.png" width="750">

| Organize by File Type | Organize by Date | Organize by Extension |
|:---:|:---:|:---:|
| <img src="screenshots/result-organize-by-file-type.png" width="260"> | <img src="screenshots/result-organize-by-date.png" width="260"> | <img src="screenshots/result-organize-by-extension.png" width="260"> |

| History Tab | Operation Details |
|:---:|:---:|
| <img src="screenshots/history-tab.png" width="420"> | <img src="screenshots/history-operation-details.png" width="420"> |

</div>

---

## Installation

### Option 1: Build from Source (Recommended for Development)

```bash
# Requirements: Maven 3.6+, Java 20+

git clone https://github.com/cpit252-spring-26-IT2/project-musannif.git
cd project-musannif
mvn clean install
mvn javafx:run
```

### Option 2: Use Pre-Built JAR

Pre-compiled releases will be available in the [Releases](https://github.com/cpit252-spring-26-IT2/project-musannif/releases) section.

### Option 3: IDE Setup (IntelliJ IDEA / Eclipse)

1. Import project as Maven project
2. Ensure Java 20+ is configured as the project SDK
3. Run `Launcher.java` as the main class
4. Right-click → Run with JavaFX configuration if needed


## AI Disclosure

We used LLMs throughout this project to understand concepts before writing code, decide how to structure classes, choose
and implement design patterns, catch and fix bugs, and generate JUnit tests. In some cases this meant taking generated
code directly, reviewing it, and adapting it to fit our needs. Every suggestion was read and understood before it went
into the code.

---

## License

MIT License © 2026 Musannif 
