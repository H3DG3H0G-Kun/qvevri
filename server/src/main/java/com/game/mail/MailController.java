package com.game.mail;

import com.game.exception.ApiException;
import com.game.market.TokenHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the MAIL lane.
 *
 * <p>All endpoints require {@code Authorization: Bearer <token>} (inline via
 * {@link TokenHelper}). Ownership of the acting character is enforced before
 * delegating to {@link MailService}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/mail/send                     — send a mail (with optional attachment)</li>
 *   <li>GET  /api/mail/{characterId}             — inbox for a character</li>
 *   <li>POST /api/mail/{mailId}/read             — mark a mail as read</li>
 *   <li>POST /api/mail/{mailId}/claim            — claim the attachment</li>
 *   <li>POST /api/mail/{mailId}/delete           — delete a mail</li>
 * </ul>
 *
 * <p>Security note: {@code /api/mail/**} is already added to the {@code permitAll}
 * list in {@code SecurityConfig} per the spec. Token validation and ownership
 * checks are performed inline in each handler.
 */
@RestController
@RequestMapping("/api/mail")
public class MailController {

    private final TokenHelper tokenHelper;
    private final MailService mailService;

    public MailController(TokenHelper tokenHelper, MailService mailService) {
        this.tokenHelper = tokenHelper;
        this.mailService = mailService;
    }

    // ── POST /api/mail/send ───────────────────────────────────────────────────

    /**
     * Sends a mail. The bearer token must own {@code senderCharacterId}.
     * If an attachment is specified it is escrowed from the sender atomically.
     *
     * @return 200 with the persisted {@link Mail}
     */
    @PostMapping("/send")
    public ResponseEntity<Mail> send(
            @RequestBody SendMailRequest req,
            HttpServletRequest request) {

        if (req.getSenderCharacterId() == null) {
            throw ApiException.badRequest("senderCharacterId is required");
        }
        if (req.getRecipientCharacterId() == null) {
            throw ApiException.badRequest("recipientCharacterId is required");
        }
        if (req.getSubject() == null || req.getSubject().isBlank()) {
            throw ApiException.badRequest("subject must not be blank");
        }
        if (req.getBody() == null || req.getBody().isBlank()) {
            throw ApiException.badRequest("body must not be blank");
        }

        // Ownership of the acting (sender) character
        tokenHelper.requireOwnedCharacter(request, req.getSenderCharacterId());

        double attachAmount = req.getAttachAmount() != null ? req.getAttachAmount() : 0.0;

        Mail mail = mailService.send(
                req.getSenderCharacterId(),
                req.getRecipientCharacterId(),
                req.getSubject(),
                req.getBody(),
                req.getAttachKind(),
                req.getAttachRefId(),
                attachAmount);

        return ResponseEntity.ok(mail);
    }

    // ── GET /api/mail/{characterId} ───────────────────────────────────────────

    /**
     * Returns the inbox of the given character. Token must own the character.
     *
     * @return 200 with {@code Mail[]}
     */
    @GetMapping("/{characterId}")
    public ResponseEntity<List<Mail>> inbox(
            @PathVariable Long characterId,
            HttpServletRequest request) {

        tokenHelper.requireOwnedCharacter(request, characterId);
        return ResponseEntity.ok(mailService.inbox(characterId));
    }

    // ── POST /api/mail/{mailId}/read ──────────────────────────────────────────

    /**
     * Marks a mail as read. Token must own {@code characterId} (recipient).
     *
     * @return 200 with the updated {@link Mail}
     */
    @PostMapping("/{mailId}/read")
    public ResponseEntity<Mail> read(
            @PathVariable Long mailId,
            @RequestBody MailCharacterRequest req,
            HttpServletRequest request) {

        if (req.getCharacterId() == null) {
            throw ApiException.badRequest("characterId is required");
        }
        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());
        return ResponseEntity.ok(mailService.markRead(mailId, req.getCharacterId()));
    }

    // ── POST /api/mail/{mailId}/claim ─────────────────────────────────────────

    /**
     * Claims the attachment of a mail. Token must own {@code characterId} (recipient).
     *
     * @return 200 with the updated {@link Mail}
     */
    @PostMapping("/{mailId}/claim")
    public ResponseEntity<Mail> claim(
            @PathVariable Long mailId,
            @RequestBody MailCharacterRequest req,
            HttpServletRequest request) {

        if (req.getCharacterId() == null) {
            throw ApiException.badRequest("characterId is required");
        }
        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());
        return ResponseEntity.ok(mailService.claim(mailId, req.getCharacterId()));
    }

    // ── POST /api/mail/{mailId}/delete ────────────────────────────────────────

    /**
     * Deletes a mail. Token must own {@code characterId} (recipient).
     * Blocked if mail has an unclaimed attachment.
     *
     * @return 200 (empty body)
     */
    @PostMapping("/{mailId}/delete")
    public ResponseEntity<Void> delete(
            @PathVariable Long mailId,
            @RequestBody MailCharacterRequest req,
            HttpServletRequest request) {

        if (req.getCharacterId() == null) {
            throw ApiException.badRequest("characterId is required");
        }
        tokenHelper.requireOwnedCharacter(request, req.getCharacterId());
        mailService.delete(mailId, req.getCharacterId());
        return ResponseEntity.ok().build();
    }
}
