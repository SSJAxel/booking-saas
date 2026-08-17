@echo off
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
mvnw.cmd spring-boot:run
