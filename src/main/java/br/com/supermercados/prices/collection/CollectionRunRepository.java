package br.com.supermercados.prices.collection;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRunRepository extends JpaRepository<CollectionRun, UUID> {}
