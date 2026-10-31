package pl.radarpracy.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Gdy frontend jest zbudowany do {@code classpath:/static}, ścieżki aplikacji React (np. /oferty/5)
 * otwierane bezpośrednio w przeglądarce są przekazywane do index.html.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/oferty/**", "/aplikacje/**", "/profil", "/rozmowy", "/wynagrodzenia", "/analityka", "/raport"})
    String forward() {
        return "forward:/index.html";
    }
}
