@echo off
echo ================================================
echo Compilation du Framework
echo ================================================

REM Compiler directement dans framework_test/build/WEB-INF/classes
set TARGET_CLASSES=..\framework_test\build\WEB-INF\classes
set TARGET_LIB=..\framework_test\build\WEB-INF\lib
if not exist "%TARGET_CLASSES%" mkdir "%TARGET_CLASSES%"
if not exist "%TARGET_LIB%" mkdir "%TARGET_LIB%"

REM Créer aussi un dossier temporaire pour le JAR
if exist "build" rmdir /s /q "build"
mkdir "build"

REM Compiler toutes les classes du framework
echo Compilation des classes du framework...
dir /s /b src\*.java util\*.java > sources.txt
javac -cp "..\framework_test\lib\servlet-api.jar" -d "build" @sources.txt
del sources.txt

if %ERRORLEVEL% NEQ 0 (
    echo Erreur de compilation!
    exit /b 1
)

echo Compilation réussie!

REM Créer le JAR
echo Création du JAR myframework.jar...
cd build
jar -cvf myframework.jar myframework\*.class
cd ..

REM Copier les classes vers framework_test/build/WEB-INF/classes
echo Copie des classes vers %TARGET_CLASSES%...
xcopy "build\myframework" "%TARGET_CLASSES%\myframework\" /E /I /Y

REM Copier le JAR vers framework_test/build/WEB-INF/lib
echo Copie du JAR vers %TARGET_LIB%...
copy "build\myframework.jar" "%TARGET_LIB%\myframework.jar"

REM Nettoyer le dossier build temporaire
rmdir /s /q "build"

echo ================================================
echo Framework compilé avec succès!
echo - Classes: %TARGET_CLASSES%\myframework\
echo - JAR: %TARGET_LIB%\myframework.jar
echo ================================================
