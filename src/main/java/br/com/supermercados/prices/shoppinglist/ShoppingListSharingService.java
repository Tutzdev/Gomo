package br.com.supermercados.prices.shoppinglist;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.auth.SecureTokenValues;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.subscription.PlanLimit;
import br.com.supermercados.prices.subscription.PlanLimitException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sharing a list with the household. The owner (Premium) creates a link valid for {@link #INVITE_LIFETIME};
 * anyone signed in who opens it joins the list, up to {@link #MAXIMUM_MEMBERS} people besides the owner. A new
 * link replaces the previous one, and "stop sharing" ends the link without removing who already joined. The link
 * carries a random token in the address fragment, never sent to the server or written in its logs; only the
 * token's hash is stored.
 */
@Service
public class ShoppingListSharingService {

    static final Duration INVITE_LIFETIME = Duration.ofDays(7);
    static final int MAXIMUM_MEMBERS = 5;

    private final ShoppingListRepository lists;
    private final ShoppingListMemberRepository members;
    private final ShoppingListInviteRepository invites;
    private final ShoppingListItemRepository items;
    private final Clock clock;

    public ShoppingListSharingService(ShoppingListRepository lists, ShoppingListMemberRepository members,
            ShoppingListInviteRepository invites, ShoppingListItemRepository items, Clock clock) {
        this.lists = lists;
        this.members = members;
        this.invites = invites;
        this.items = items;
        this.clock = clock;
    }

    /** @param token shown once: only its hash stays in the database */
    public record InviteResponse(String token, Instant expiresAt) {
    }

    /** What the invited person sees before joining. */
    public record InvitePreviewResponse(UUID listId, String listName, String ownerName, int items, int members,
            boolean alreadyIn, Instant expiresAt) {
    }

    @Transactional
    public InviteResponse invite(AuthenticatedUser user, UUID listId) {
        if (!user.premium()) {
            throw new PlanLimitException(PlanLimit.SHARED_LISTS,
                    "Compartilhar a lista faz parte do Premium. Quem recebe o convite não precisa assinar.");
        }
        requireOwned(user.id(), listId);
        Instant now = clock.instant();
        invites.revokeAll(listId, now);
        String token = SecureTokenValues.random();
        Instant expiresAt = now.plus(INVITE_LIFETIME);
        invites.save(new ShoppingListInvite(listId, SecureTokenValues.hash(token), user.id(), now, expiresAt));
        return new InviteResponse(token, expiresAt);
    }

    @Transactional
    public void stopSharing(UUID userId, UUID listId) {
        requireOwned(userId, listId);
        invites.revokeAll(listId, clock.instant());
    }

    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(UUID userId, String token) {
        ShoppingListInvite invite = usableInvite(token);
        ShoppingList list = lists.findById(invite.getShoppingListId()).orElseThrow(this::invalidInvite);
        boolean alreadyIn = list.getUserId().equals(userId)
                || members.existsByShoppingListIdAndUserId(list.getId(), userId);
        return new InvitePreviewResponse(list.getId(), list.getName(), members.findUserName(list.getUserId()),
                (int) items.countByShoppingListId(list.getId()), (int) members.countByShoppingListId(list.getId()),
                alreadyIn, invite.getExpiresAt());
    }

    /** Joining twice, or the owner opening their own link, just leads to the list. */
    @Transactional
    public UUID accept(UUID userId, String token) {
        ShoppingListInvite invite = usableInvite(token);
        ShoppingList list = lists.lockById(invite.getShoppingListId()).orElseThrow(this::invalidInvite);
        if (list.getUserId().equals(userId) || members.existsByShoppingListIdAndUserId(list.getId(), userId)) {
            return list.getId();
        }
        if (members.countByShoppingListId(list.getId()) >= MAXIMUM_MEMBERS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Esta lista já está compartilhada com " + MAXIMUM_MEMBERS + " pessoas.");
        }
        members.save(new ShoppingListMember(list.getId(), userId, clock.instant()));
        return list.getId();
    }

    /** The owner removes anyone; a member can only leave. */
    @Transactional
    public void removeMember(UUID actorId, UUID listId, UUID memberId) {
        ShoppingList list = lists.findAccessible(listId, actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lista não encontrada."));
        if (!list.getUserId().equals(actorId) && !actorId.equals(memberId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Só quem criou a lista tira outras pessoas dela.");
        }
        if (members.remove(listId, memberId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Essa pessoa não está na lista.");
        }
    }

    private void requireOwned(UUID userId, UUID listId) {
        if (lists.lockOwnedList(listId, userId).isPresent()) {
            return;
        }
        throw lists.findAccessible(listId, userId).isPresent()
                ? new ApiException(HttpStatus.FORBIDDEN, "Só quem criou a lista pode compartilhar.")
                : new ApiException(HttpStatus.NOT_FOUND, "Lista não encontrada.");
    }

    private ShoppingListInvite usableInvite(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw invalidInvite();
        }
        return invites.findByTokenHash(SecureTokenValues.hash(token.strip()))
                .filter(invite -> invite.isUsable(clock.instant()))
                .orElseThrow(this::invalidInvite);
    }

    private ApiException invalidInvite() {
        return new ApiException(HttpStatus.NOT_FOUND, "Este convite não vale mais. Peça um link novo a quem compartilhou.");
    }
}
