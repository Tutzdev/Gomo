package br.com.supermercados.prices.preference;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class UserPreferenceController {

    private final UserPreferenceService preferences;

    public UserPreferenceController(UserPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public UserPreferenceResponse find(@AuthenticationPrincipal AuthenticatedUser user) {return preferences.find(user.id());}

    @PutMapping("/preferred-city")
    public UserPreferenceResponse changePreferredCity(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PreferredCityRequest request) {return preferences.changePreferredCity(user.id(), request.cityId());}

    @DeleteMapping("/preferred-city")
    public UserPreferenceResponse clearPreferredCity(@AuthenticationPrincipal AuthenticatedUser user) {return preferences.clearPreferredCity(user.id());}

    @PutMapping("/favorite-stores/{storeId}")
    public UserPreferenceResponse addFavoriteStore(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID storeId) {return preferences.addFavoriteStore(user.id(), storeId);}

    @DeleteMapping("/favorite-stores/{storeId}")
    public UserPreferenceResponse removeFavoriteStore(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID storeId) {return preferences.removeFavoriteStore(user.id(), storeId);}
}
