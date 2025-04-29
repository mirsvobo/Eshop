# Fáze 1: Sestavení aplikace pomocí Mavenu s JDK 21
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
# Zkopírujeme celý projekt (včetně pom.xml a src)
COPY . .
# Sestavíme aplikaci rovnou pomocí package (stáhne závislosti a sestaví JAR)
# Přeskakujeme dependency:go-offline
RUN mvn package -B -DskipTests

# Fáze 2: Vytvoření finálního "lehkého" image pro běh s JRE 21
FROM eclipse-temurin:21-jre
WORKDIR /app
# Zkopírujeme sestavený JAR z předchozí fáze
# Název JAR by měl odpovídat tomu, co Maven vygeneruje (obvykle target/<artifactId>-<version>.jar)
COPY --from=build /app/target/*.jar app.jar
# Vystavíme port, na kterém aplikace naslouchá
EXPOSE 8080
# Spustíme aplikaci při startu kontejneru
ENTRYPOINT ["java", "-jar", "app.jar"]