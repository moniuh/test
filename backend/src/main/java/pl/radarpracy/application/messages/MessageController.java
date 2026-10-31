package pl.radarpracy.application.messages;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Szablony wiadomości (pomysł #43). */
@RestController
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping("/api/message-templates")
    List<MessageTemplate> templates() {
        return service.templates();
    }

    /**
     * @param templateId id szablonu
     * @param save       true = zapisz wiadomość jako zdarzenie MESSAGE na osi czasu
     * @param subject    temat po edycji (opcjonalnie, przy zapisie)
     * @param body       treść po edycji (opcjonalnie, przy zapisie)
     */
    record MessageRequest(String templateId, Boolean save, String subject, String body) {
    }

    @PostMapping("/api/applications/{id}/messages")
    RenderedMessage render(@PathVariable Long id, @RequestBody MessageRequest request) {
        return service.render(id, request.templateId(), Boolean.TRUE.equals(request.save()), request.subject(),
                request.body());
    }
}
