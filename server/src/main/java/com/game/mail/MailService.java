package com.game.mail;

import com.game.character.CharacterService;
import com.game.exception.ApiException;
import com.game.goods.GoodsService;
import com.game.goods.OwnedGoodRepository;
import com.game.market.CellarItem;
import com.game.market.CellarItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for the MAIL lane.
 *
 * <p>Attachment escrow model:
 * <ul>
 *   <li>GEL — debited from sender wallet at send-time via
 *       {@link CharacterService#adjustWallet}; credited to recipient at claim-time.</li>
 *   <li>GOODS — decremented from sender's OwnedGood at send-time via
 *       {@link GoodsService#decrement}; granted to recipient at claim-time via
 *       {@link GoodsService#grant}.</li>
 *   <li>CELLAR_ITEM — {@code escrowed=true} set on the CellarItem at send-time;
 *       {@code characterId} reassigned to recipient and {@code escrowed=false}
 *       at claim-time.</li>
 * </ul>
 *
 * <p>All mutating operations that touch multiple resources (send with attachment,
 * claim) are {@code @Transactional} to guarantee atomicity.
 */
@Service
@Transactional
public class MailService {

    static final String KIND_GEL         = "GEL";
    static final String KIND_GOODS       = "GOODS";
    static final String KIND_CELLAR_ITEM = "CELLAR_ITEM";

    private final MailRepository      mailRepo;
    private final CharacterService    characterService;
    private final GoodsService        goodsService;
    private final OwnedGoodRepository ownedGoodRepo;
    private final CellarItemRepository cellarItemRepo;

    public MailService(MailRepository mailRepo,
                       CharacterService characterService,
                       GoodsService goodsService,
                       OwnedGoodRepository ownedGoodRepo,
                       CellarItemRepository cellarItemRepo) {
        this.mailRepo        = mailRepo;
        this.characterService = characterService;
        this.goodsService    = goodsService;
        this.ownedGoodRepo   = ownedGoodRepo;
        this.cellarItemRepo  = cellarItemRepo;
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Sends a mail from {@code senderCharacterId} to {@code recipientCharacterId}.
     *
     * <p>If an attachment is present it is escrowed from the sender immediately:
     * <ul>
     *   <li>GEL → wallet debited (400 if insufficient funds).</li>
     *   <li>GOODS → OwnedGood row decremented (400 if not owned or insufficient).</li>
     *   <li>CELLAR_ITEM → escrowed=true (400 if not owned or already escrowed).</li>
     * </ul>
     *
     * @return the persisted Mail
     */
    public Mail send(Long senderCharacterId,
                     Long recipientCharacterId,
                     String subject,
                     String body,
                     String attachKind,
                     String attachRefId,
                     double attachAmount) {

        if (attachKind != null) {
            escrowFromSender(senderCharacterId, attachKind, attachRefId, attachAmount);
        }

        Mail mail = new Mail(
                recipientCharacterId,
                senderCharacterId,
                subject,
                body,
                attachKind,
                attachRefId,
                attachAmount);

        return mailRepo.save(mail);
    }

    /**
     * System-mail helper: creates a mail with no sender and no escrow.
     * Not exposed via a public REST endpoint.
     */
    public Mail sendSystem(Long recipientCharacterId,
                           String subject,
                           String body) {
        Mail mail = new Mail(
                recipientCharacterId,
                null,
                subject,
                body,
                null,
                null,
                0.0);
        return mailRepo.save(mail);
    }

    // ── Inbox ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Mail> inbox(Long recipientCharacterId) {
        return mailRepo.findByRecipientCharacterId(recipientCharacterId);
    }

    // ── Mark read ─────────────────────────────────────────────────────────────

    /**
     * Marks a mail as read. The acting character must be the recipient.
     *
     * @throws ApiException 404 if mail not found or actor is not the recipient
     */
    public Mail markRead(Long mailId, Long characterId) {
        Mail mail = requireMail(mailId);
        requireRecipient(mail, characterId);
        mail.setRead(true);
        return mailRepo.save(mail);
    }

    // ── Claim ─────────────────────────────────────────────────────────────────

    /**
     * Claims the attachment of a mail, delivering the escrowed asset to the
     * recipient and marking the mail as claimed.
     *
     * <p>Idempotency guard: 400 if already claimed or mail has no attachment.
     *
     * @throws ApiException 404 if mail not found or actor is not the recipient
     * @throws ApiException 400 if already claimed or no attachment
     */
    public Mail claim(Long mailId, Long characterId) {
        Mail mail = requireMail(mailId);
        requireRecipient(mail, characterId);

        if (mail.isClaimed()) {
            throw ApiException.badRequest("Mail " + mailId + " has already been claimed");
        }
        if (mail.getAttachKind() == null) {
            throw ApiException.badRequest("Mail " + mailId + " has no attachment to claim");
        }

        deliverToRecipient(characterId, mail.getAttachKind(),
                mail.getAttachRefId(), mail.getAttachAmount());

        mail.setClaimed(true);
        return mailRepo.save(mail);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a mail. The acting character must be the recipient.
     * Blocked (400) if the mail has an unclaimed attachment to prevent
     * stranded escrowed assets.
     *
     * @throws ApiException 404 if mail not found or actor is not the recipient
     * @throws ApiException 400 if mail has an unclaimed attachment
     */
    public void delete(Long mailId, Long characterId) {
        Mail mail = requireMail(mailId);
        requireRecipient(mail, characterId);

        if (mail.getAttachKind() != null && !mail.isClaimed()) {
            throw ApiException.badRequest(
                    "Cannot delete mail " + mailId
                            + " while it has an unclaimed attachment. Claim or ignore it first.");
        }

        mailRepo.delete(mail);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Mail requireMail(Long mailId) {
        return mailRepo.findById(mailId)
                .orElseThrow(() -> ApiException.notFound("Mail " + mailId + " not found"));
    }

    private void requireRecipient(Mail mail, Long characterId) {
        if (!mail.getRecipientCharacterId().equals(characterId)) {
            throw ApiException.notFound(
                    "Mail " + mail.getId() + " not found for character " + characterId);
        }
    }

    /**
     * Escrow an attachment from the sender at send-time.
     */
    private void escrowFromSender(Long senderCharacterId,
                                  String attachKind,
                                  String attachRefId,
                                  double attachAmount) {
        switch (attachKind) {
            case KIND_GEL -> {
                if (attachAmount <= 0) {
                    throw ApiException.badRequest(
                            "GEL attachment amount must be > 0 (got " + attachAmount + ")");
                }
                try {
                    characterService.adjustWallet(senderCharacterId, -attachAmount);
                } catch (ApiException ex) {
                    if ("INSUFFICIENT_FUNDS".equals(ex.getCode())) {
                        throw ApiException.badRequest(
                                "Insufficient funds to attach " + attachAmount + " GEL");
                    }
                    throw ex;
                }
            }
            case KIND_GOODS -> {
                if (attachAmount <= 0) {
                    throw ApiException.badRequest(
                            "GOODS attachment amount must be > 0 (got " + attachAmount + ")");
                }
                if (attachRefId == null || attachRefId.isBlank()) {
                    throw ApiException.badRequest(
                            "GOODS attachment requires a non-blank attachRefId (goodTypeId)");
                }
                var ownedGood = ownedGoodRepo
                        .findByCharacterIdAndGoodTypeId(senderCharacterId, attachRefId)
                        .orElseThrow(() -> ApiException.badRequest(
                                "Character " + senderCharacterId
                                        + " does not own any '" + attachRefId + "'"));
                if (ownedGood.getQuantity() < attachAmount) {
                    throw ApiException.badRequest(
                            "Character " + senderCharacterId
                                    + " owns only " + ownedGood.getQuantity()
                                    + " of '" + attachRefId
                                    + "' but attachment requires " + attachAmount);
                }
                goodsService.decrement(ownedGood.getId(), attachAmount);
            }
            case KIND_CELLAR_ITEM -> {
                if (attachRefId == null || attachRefId.isBlank()) {
                    throw ApiException.badRequest(
                            "CELLAR_ITEM attachment requires a non-blank attachRefId (cellarItemId)");
                }
                long cellarItemId = parseCellarItemId(attachRefId);
                CellarItem item = cellarItemRepo
                        .findByIdAndCharacterId(cellarItemId, senderCharacterId)
                        .orElseThrow(() -> ApiException.badRequest(
                                "CellarItem " + cellarItemId
                                        + " not found or not owned by character "
                                        + senderCharacterId));
                if (item.isEscrowed()) {
                    throw ApiException.badRequest(
                            "CellarItem " + cellarItemId
                                    + " is already escrowed under another listing or offer");
                }
                item.setEscrowed(true);
                cellarItemRepo.save(item);
            }
            default -> throw ApiException.badRequest(
                    "Unknown attachKind '" + attachKind
                            + "'; expected GEL, GOODS, or CELLAR_ITEM");
        }
    }

    /**
     * Deliver the escrowed asset to the recipient at claim-time.
     */
    private void deliverToRecipient(Long recipientCharacterId,
                                    String attachKind,
                                    String attachRefId,
                                    double attachAmount) {
        switch (attachKind) {
            case KIND_GEL ->
                    characterService.adjustWallet(recipientCharacterId, +attachAmount);
            case KIND_GOODS ->
                    goodsService.grant(recipientCharacterId, attachRefId, attachAmount);
            case KIND_CELLAR_ITEM -> {
                long cellarItemId = parseCellarItemId(attachRefId);
                CellarItem item = cellarItemRepo.findById(cellarItemId)
                        .orElseThrow(() -> ApiException.badRequest(
                                "CellarItem " + cellarItemId
                                        + " no longer exists; cannot deliver attachment"));
                item.setCharacterId(recipientCharacterId);
                item.setEscrowed(false);
                cellarItemRepo.save(item);
            }
            default -> throw ApiException.badRequest(
                    "Unknown attachKind '" + attachKind + "' during claim");
        }
    }

    private static long parseCellarItemId(String ref) {
        try {
            return Long.parseLong(ref);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(
                    "Invalid cellarItemId in attachRefId: '" + ref + "'");
        }
    }
}
