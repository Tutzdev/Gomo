package br.com.supermercados.prices.preference;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {}
