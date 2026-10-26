package br.com.supermercados.prices.shoppinglist;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ShoppingListMemberRepository extends JpaRepository<ShoppingListMember, ShoppingListMember.Key> {

    boolean existsByShoppingListIdAndUserId(UUID shoppingListId, UUID userId);

    boolean existsByShoppingListId(UUID shoppingListId);

    long countByShoppingListId(UUID shoppingListId);

    @Query("""
            select new br.com.supermercados.prices.shoppinglist.ShoppingListMemberResponse(
                member.userId, user.name, member.joinedAt)
            from ShoppingListMember member join User user on user.id = member.userId
            where member.shoppingListId = :listId
            order by member.joinedAt, member.userId
            """)
    List<ShoppingListMemberResponse> findMembers(UUID listId);

    @Query("""
            select member.shoppingListId, count(member) from ShoppingListMember member
            where member.shoppingListId in :listIds group by member.shoppingListId
            """)
    List<Object[]> countByList(Collection<UUID> listIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from ShoppingListMember member where member.shoppingListId = :listId and member.userId = :userId")
    int remove(UUID listId, UUID userId);

    @Query("select user.name from User user where user.id = :userId")
    String findUserName(UUID userId);
}
