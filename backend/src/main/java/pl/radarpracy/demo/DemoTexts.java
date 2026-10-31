package pl.radarpracy.demo;

/** Teksty danych demonstracyjnych. Osoby i firmy są fikcyjne. */
final class DemoTexts {

    private DemoTexts() {
    }

    static final String CV_BACKEND = """
            Anna Nowak
            Java Developer
            E-mail: anna.nowak@example.com | Telefon: +48 600 100 200
            Adres: ul. Marszałkowska 10/5, 00-590 Warszawa
            Data urodzenia: 12.03.1995 | Stan cywilny: panna
            LinkedIn: linkedin.com/in/anna-nowak-dev | GitHub: github.com/anowak-dev

            PODSUMOWANIE
            Java Developer z 4 latami doświadczenia komercyjnego w fintechu. Projektuję i rozwijam mikroserwisy w Spring Boot, dbam o testy i wydajność.

            DOŚWIADCZENIE
            Java Developer — PayFlow sp. z o.o., Warszawa (2022 – obecnie)
            - Rozwój mikroserwisów płatniczych w Java 17 i Spring Boot (REST API, Hibernate, PostgreSQL).
            - Migracja komunikacji między usługami na Apache Kafka; skróciłam czas przetwarzania płatności o 40%.
            - Wdrożenie testów JUnit 5 i Mockito — pokrycie wzrosło z 45% do 80%.
            - Konteneryzacja usług w Docker i pipeline'y w GitLab CI.
            - Code review i mentoring dwóch juniorów.

            Junior Java Developer — SoftHouse, Warszawa (2021 – 2022)
            - Utrzymanie systemu rezerwacji w Spring MVC i MySQL.
            - Optymalizacja zapytań SQL, raporty w Excel.

            UMIEJĘTNOŚCI
            Java 17, Spring Boot, Spring Security, Hibernate/JPA, PostgreSQL, MySQL, Apache Kafka, Docker, REST API, JUnit, Mockito, Git, GitLab CI, Linux, Scrum.

            JĘZYKI
            Angielski — B2 (swobodna komunikacja w zespole międzynarodowym)
            Polski — ojczysty

            WYKSZTAŁCENIE
            Politechnika Warszawska — Informatyka, inżynier (2017 – 2021)

            Wyrażam zgodę na przetwarzanie moich danych osobowych dla potrzeb niezbędnych do realizacji procesu rekrutacji.
            """;

    static final String CV_FULLSTACK = """
            Anna Nowak
            Fullstack Developer (Java + React)
            E-mail: anna.nowak@example.com | Telefon: +48 600 100 200
            GitHub: github.com/anowak-dev

            PODSUMOWANIE
            Fullstack developerka: 4 lata z Java i Spring Boot, 2 lata z React i TypeScript. Lubię dowozić funkcje end-to-end.

            DOŚWIADCZENIE
            Java Developer — PayFlow sp. z o.o., Warszawa (2022 – obecnie)
            - Mikroserwisy w Java 17 i Spring Boot, REST API, PostgreSQL, Apache Kafka.
            - Panel administracyjny w React 18, TypeScript i Redux Toolkit (2 lata), testy w Jest i React Testing Library.
            - Skróciłam czas ładowania panelu o 35% dzięki code splittingowi.
            - Docker, GitLab CI, code review.

            Junior Java Developer — SoftHouse, Warszawa (2021 – 2022)
            - Spring MVC, MySQL, HTML, CSS, JavaScript.

            UMIEJĘTNOŚCI
            Java, Spring Boot, Hibernate, PostgreSQL, Kafka, React, TypeScript, Redux, HTML, CSS, Jest, Docker, Git, Scrum.

            JĘZYKI
            Angielski — B2

            WYKSZTAŁCENIE
            Politechnika Warszawska — Informatyka, inżynier (2017 – 2021)
            """;

    static final String OFFER_FINPAY = """
            Senior Java Developer — FinPay Systems
            Lokalizacja: Warszawa (hybrydowo, 2 dni w biurze)
            Wynagrodzenie: 22 000 – 28 000 PLN netto + VAT (B2B)

            O projekcie:
            Budujemy platformę płatności natychmiastowych dla banków w Europie Środkowej.

            Wymagania:
            - Minimum 5 lat doświadczenia komercyjnego w Java (17+)
            - Spring Boot i mikroserwisy w produkcji
            - Apache Kafka
            - Kubernetes
            - PostgreSQL
            - Angielski na poziomie min. B2

            Mile widziane:
            - AWS
            - Terraform
            - Doświadczenie w domenie płatności

            Oferujemy:
            - Kontrakt B2B, 26 dni płatnej przerwy
            - Prywatną opiekę medyczną
            - Budżet szkoleniowy 5 000 zł rocznie
            """;

    static final String OFFER_LOGITECH = """
            Java Developer
            LogiTech Solutions — Kraków / 100% zdalnie
            Umowa o pracę, 14 000 – 18 000 zł brutto miesięcznie

            Twoje zadania:
            - Rozwój systemu TMS do zarządzania transportem
            - Integracje REST z systemami przewoźników

            Wymagania:
            - 3 lata doświadczenia w Java
            - Spring Framework, Hibernate
            - Dobra znajomość SQL
            - REST API
            - Git
            - Komunikatywny angielski

            Mile widziane:
            - Docker
            - Angular

            Oferujemy: karta sportowa, opieka medyczna, PPK, praca zdalna.
            """;

    static final String OFFER_LOGITECH_DUP = """
            Java Developer (Mid) — LogiTech Solutions sp. z o.o.
            Kraków, praca w pełni zdalna. UoP: 14 000 – 18 000 PLN brutto/mies.

            Czym będziesz się zajmować:
            - rozwój naszego systemu TMS (zarządzanie transportem)
            - integracje REST API z przewoźnikami

            Wymagania:
            - min. 3 lata doświadczenia z Java
            - Spring, Hibernate
            - SQL
            - REST API, Git
            - angielski komunikatywny

            Mile widziane:
            - Docker, Angular

            Benefity: karta sportowa, PPK.
            """;

    static final String OFFER_MEDIKOS = """
            Fullstack Developer (Java + React) — Medikos
            Warszawa, hybrydowo. B2B: 140–170 zł/h netto + VAT.

            Tworzymy system dla sieci przychodni: rejestracja pacjentów, e-recepty, integracje z NFZ.

            Wymagania:
            - Java 11+ i Spring Boot (min. 3 lata)
            - React i TypeScript (min. 2 lata)
            - PostgreSQL
            - Testy automatyczne (JUnit, Jest)
            - Angielski B2

            Mile widziane:
            - Next.js
            - Doświadczenie w projektach medycznych
            - Docker

            Oferujemy elastyczne godziny pracy i MacBooka.
            """;

    static final String OFFER_SKLEPIK = """
            Frontend Developer (React) — Sklepik.pl
            Zdalnie, umowa o pracę 11 000 – 15 000 zł brutto.

            Wymagania:
            - 3 lata doświadczenia z React
            - TypeScript
            - Redux
            - CSS / SCSS
            - Jest i React Testing Library

            Mile widziane:
            - Next.js
            - Doświadczenie w e-commerce
            """;

    static final String OFFER_DATAWISE = """
            Junior Python Developer — DataWise
            Wrocław, praca z biura. UoP 7 000 – 9 000 zł brutto.

            Wymagania:
            - Python
            - Django lub Flask
            - SQL
            - Wykształcenie wyższe techniczne (lub ostatni rok studiów)

            Mile widziane:
            - Pandas
            - Docker
            """;

    static final String OFFER_REDFLAGS = """
            Młodszy specjalista ds. obsługi klienta
            Dynamiczny Zespół sp. z o.o., Warszawa

            Dołącz do młodego, dynamicznego zespołu, w którym panuje rodzinna atmosfera!

            Wymagania:
            - Dyspozycyjność 24/7, gotowość do pracy w nadgodzinach i w weekendy
            - Odporność na stres i pracę pod presją czasu
            - Wysoka kultura osobista
            - Komunikatywność

            Oferujemy:
            - Umowę o dzieło
            - Bezpłatny okres próbny (2 tygodnie)
            - Atrakcyjne wynagrodzenie uzależnione od wyników
            - Możliwość szybkiego awansu

            Prosimy o CV ze zdjęciem, datą urodzenia i informacją o stanie cywilnym.
            """;

    static final String OFFER_SCAM = """
            PRACA ZDALNA!!! 500 zł DZIENNIE bez doświadczenia
            Szukamy osób do przyjmowania paczek w domu i przesyłania ich dalej pod wskazany adres.

            Wymagania:
            - Pełnoletność
            - Dostęp do telefonu

            Jak zacząć:
            - Wymagana jednorazowa opłata 199 zł za pakiet startowy i szkolenie
            - Prześlij skan dowodu osobistego oraz numer konta bankowego, abyśmy mogli przelać zaliczkę
            - Kontakt wyłącznie przez WhatsApp lub Telegram
            - Możliwa wypłata w kryptowalutach
            """;

    static final String OFFER_CLOUDNINE = """
            DevOps Engineer — CloudNine
            Gdańsk / zdalnie. B2B 25 000 – 32 000 PLN netto + VAT.

            Wymagania:
            - 4+ lata doświadczenia jako DevOps / SRE
            - Kubernetes w produkcji
            - Terraform
            - AWS
            - CI/CD (GitHub Actions lub GitLab CI)
            - Linux, Bash
            - Angielski C1

            Mile widziane:
            - Prometheus, Grafana
            - Python lub Go
            """;

    static final String OFFER_BANKPOL = """
            Tech Lead Java — BankPol
            Warszawa, praca z biura (4 dni w tygodniu). Umowa o pracę: 28 000 – 34 000 zł brutto + premia roczna.

            Wymagania:
            - 7 lat doświadczenia w Java
            - Architektura mikroserwisów
            - Zarządzanie zespołem min. 2 lata
            - Spring Boot
            - Oracle lub PostgreSQL
            - Angielski C1

            Mile widziane:
            - Doświadczenie w sektorze bankowym
            - Kafka
            - Mentoring
            """;

    static final String OFFER_NORDIC = """
            Java Developer (remote, EU) — Nordlys Software AB
            Fully remote within the EU. B2B contract: 5 500 – 6 500 EUR per month.

            Requirements:
            - 3+ years of commercial experience with Java
            - Spring Boot
            - PostgreSQL
            - Docker
            - Fluent English (C1)

            Nice to have:
            - Kotlin
            - AWS
            - Experience with event-driven architecture (Kafka)
            """;

    static final String OFFER_KREDYTO = """
            Backend Developer (Kotlin) — Kredyto
            Warszawa / zdalnie. UoP 16 000 – 20 000 zł brutto lub B2B.

            Wymagania:
            - 3 lata doświadczenia w Kotlin lub Java
            - Spring Boot
            - PostgreSQL
            - Testy automatyczne
            - Angielski B2

            Mile widziane:
            - Kafka
            - Kubernetes
            """;

    static final String OFFER_RETAILONE = """
            Java Developer — RetailOne
            Warszawa, hybrydowo. UoP 13 000 – 16 000 zł brutto.

            Wymagania:
            - 2 lata doświadczenia w Java
            - Spring Boot
            - SQL
            - Git

            Mile widziane:
            - React
            """;

    static final String OFFER_INSURETECH = """
            Java/Spring Developer — InsureTech
            Łódź / zdalnie. B2B 18 000 – 22 000 PLN netto + VAT.

            Wymagania:
            - 4 lata doświadczenia w Java
            - Spring Boot
            - Kafka
            - AWS
            - Docker

            Mile widziane:
            - Kubernetes
            - Terraform
            """;
}
