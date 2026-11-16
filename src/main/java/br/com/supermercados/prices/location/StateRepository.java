package br.com.supermercados.prices.location;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StateRepository extends JpaRepository<State, UUID> {}
